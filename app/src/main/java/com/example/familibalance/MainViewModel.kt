package com.example.familibalance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.data.auth.AuthRepository
import com.example.familibalance.data.PinHasher
import io.github.jan.supabase.auth.auth
import com.example.familibalance.data.repository.FamilyRepository
import com.example.familibalance.data.repository.FamilyRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

class MainViewModel(
    application: Application,
    val familyRepository: FamilyRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, FamilyRepositoryImpl(application))

    /** Снимок семейного состояния для реактивного UI (StateFlow). */
    data class FamilyUiState(
        val children: Map<String, ChildProfileState> = emptyMap(),
        val selectedChildId: String = "user_1"
    )

    private val _familyState = MutableStateFlow(snapshotFamilyState())
    val familyState: StateFlow<FamilyUiState> = _familyState.asStateFlow()

    /** Статус синхронизации с Supabase: false — есть неподтверждённые локальные изменения. */
    val isSynced: StateFlow<Boolean> = familyRepository.isSynced

    private fun snapshotFamilyState() = FamilyUiState(
        children = familyRepository.childrenSnapshot(),
        selectedChildId = familyRepository.selectedChildId
    )

    private fun emitFamilyState() {
        _familyState.value = snapshotFamilyState()
    }

    /** Единая точка обновления профиля: локальное сохранение + push в Supabase. */
    fun updateChildProfile(profile: ChildProfileState) {
        familyRepository.updateChildProfile(profile)
        emitFamilyState()
    }

    init {
        // Парсинг JSON всех профилей и дневные сбросы — на IO, чтобы не фризить первый кадр
        viewModelScope.launch(Dispatchers.IO) {
            familyRepository.loadState()
            // Разовая чистка легаси-дефолтов «Пользователь 1/2» из локального стейта.
            // В Supabase такие строки удаляются вручную через дашборд.
            val legacyIds = setOf("user_1", "user_2")
            if (AppSharedState.children.keys.any { it in legacyIds }) {
                AppSharedState.children.keys.removeAll(legacyIds)
                if (AppSharedState.selectedChildId in legacyIds ||
                    AppSharedState.children[AppSharedState.selectedChildId] == null
                ) {
                    AppSharedState.selectedChildId =
                        AppSharedState.children.keys.firstOrNull() ?: "user_1"
                }
                AppStateStorage.saveLocalOnly(getApplication())
            }
            checkResets()
            emitFamilyState()
        }
        // Realtime/первичная загрузка из облака меняет AppSharedState напрямую —
        // подписываемся, чтобы UI получил новый снимок состояния.
        viewModelScope.launch {
            familyRepository.onRemoteStateChanged.collect { emitFamilyState() }
        }
        SupabaseManager.startRealtimeSync(getApplication())
    }

    /** Сохраняет состояние локально. Push в Supabase — через pushChildProfile явно. */
    fun saveState() {
        familyRepository.saveLocal()
        emitFamilyState()
    }

    /** Сохраняет локально и пушит все профили в Supabase. */
    fun saveAndSync() {
        familyRepository.saveAndSync()
        emitFamilyState()
    }

    /** Принудительная синхронизация: отправляет текущего ребёнка и все dirty-профили. */
    fun forceSync() {
        familyRepository.syncNow()
    }

    /** Покупка экранного времени: списывает баланс, добавляет минуты и запись в историю. */
    fun buyScreenTime(childId: String, cost: Int, minutes: Int) {
        val child = familyRepository.getChild(childId) ?: return
        val updated = child.copy(
            balance = child.balance - cost,
            boughtMinutesToday = child.boughtMinutesToday + minutes,
            historyItems = listOf(
                HistoryData(
                    title = "Покупка экранного времени ($minutes мин)",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "-$cost ₽",
                    isIncome = false,
                    category = "Экранное время"
                )
            ) + child.historyItems
        )
        updateChildProfile(updated)
    }

    /** Отправка честного признания на проверку папе. Кулдаун — 1 раз в 7 дней. */
    fun submitHonestAdmission(childId: String, text: String) {
        val child = familyRepository.getChild(childId) ?: return
        val today = LocalDate.now()
        val lastDate = runCatching { LocalDate.parse(child.lastHonestAdmissionDate) }.getOrNull()
        if (lastDate != null && lastDate.isAfter(today.minusDays(7))) return
        updateChildProfile(
            child.copy(
                honestAdmissionStatus = QuestStatus.PENDING,
                honestAdmissionText = text.trim(),
                lastHonestAdmissionDate = today.toString()
            )
        )
    }

    /** Сброс купленных минут экранного времени при смене дня. */
    fun resetDailyScreenTime(childId: String) {
        val child = familyRepository.getChild(childId) ?: return
        updateChildProfile(child.copy(boughtMinutesToday = 0))
    }

    /** Скрытие баннера бонуса за стрик. */
    fun dismissStreakBonus(childId: String) {
        val child = familyRepository.getChild(childId) ?: return
        updateChildProfile(child.copy(streakBonusMessage = ""))
    }

    /** Переключает активного ребёнка и эмитит новое состояние. pushRemote — полная синхронизация с Supabase. */
    fun selectChild(childId: String, pushRemote: Boolean = false) {
        familyRepository.selectChild(childId, pushRemote)
        emitFamilyState()
    }

    /**
     * Создаёт новый профиль ребёнка из формы регистрации:
     * генерирует childId, сохраняет профиль (локально + push в Supabase),
     * пытается связать с родителем по email и выбирает профиль активным.
     */
    fun createChildProfile(
        nickname: String,
        pin: String,
        avatar: String,
        parentEmail: String,
        onResult: ((ChildProfileState) -> Unit)? = null
    ) {
        viewModelScope.launch {
            // Если сессии нет (устройство ребёнка) — входим анонимно,
            // чтобы у ребёнка был собственный auth.uid для своей строки в family_profiles.
            var sessionUser = runCatching {
                SupabaseManager.client?.auth?.currentUserOrNull()
            }.getOrNull()
            if (sessionUser == null) {
                runCatching { SupabaseManager.client?.auth?.signInAnonymously() }
                sessionUser = runCatching {
                    SupabaseManager.client?.auth?.currentUserOrNull()
                }.getOrNull()
            }

            val sessionEmail = sessionUser?.email
            val sessionUid = sessionUser?.id.orEmpty()
            val email = sessionEmail?.trim().orEmpty().ifBlank { parentEmail.trim() }
            // Родительская сессия (есть email) -> профиль сразу active.
            // Анонимная сессия ребёнка -> pending, ждёт подтверждения родителя.
            val isParentSession = !sessionEmail.isNullOrBlank()
            val status = if (isParentSession) "active" else "pending"
            if (email.isBlank()) {
                android.util.Log.w("MainViewModel", "createChildProfile: нет email родителя — запрос не сможет быть подтверждён")
            }

            val childId = "user_${System.currentTimeMillis()}"
            val profile = ChildProfileState(
                id = childId,
                name = nickname.ifBlank { "Игрок" },
                avatar = avatar.ifBlank { "🧒" },
                pinCode = PinHasher.hash(pin.ifBlank { "0000" }),
                parentUserId = if (isParentSession) sessionUid else "",
                parentEmail = email,
                status = status
            )
            // updateChildProfile кладёт профиль в AppSharedState и пушит в Supabase
            updateChildProfile(profile)
            selectChild(childId)

            // Best-effort привязка к родителю по email (только для родительской сессии)
            if (isParentSession && email.isNotBlank()) {
                runCatching { AuthRepository().linkChildToParent(childId, email) }
            }
            onResult?.invoke(profile)
        }
    }

    /** Смена ПИН-кода ребёнка из админки: copy() + updateChildProfile (push в Supabase). */
    fun updateChildPin(childId: String, newPin: String) {
        val child = familyRepository.getChild(childId) ?: return
        updateChildProfile(child.copy(pinCode = PinHasher.hash(newPin)))
    }

    /** Очистка состояния при выходе из аккаунта: профили и выбор не должны «протекать» другой семье. */
    fun clearState() {
        AppSharedState.children.clear()
        AppSharedState.selectedChildId = "user_1"
        emitFamilyState()
    }

    fun checkResets() {
        familyRepository.checkResets()
        resetDailyQuestsIfNeeded()
        
    }

    private fun resetDailyQuestsIfNeeded() {
        val today = LocalDate.now().toString()
        val todayDate = LocalDate.now()
        val weekStart = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val children = familyRepository.getAllChildren()

        children.forEach { child ->
            var updated = child

            if (child.lastDailyResetDate != today) {
                updated = updated.copy(
                    lastDailyResetDate = today,
                    closetStatus = QuestStatus.AVAILABLE,
                    closetRejectReason = null,
                    closetPhotoUrl = null,
                    closetComment = "",
                    roomStatus = QuestStatus.AVAILABLE,
                    roomRejectReason = null,
                    roomPhotoUrl = null,
                    roomComment = ""
                )
            }

            // Auto-reset visible custom quests based on frequency
            val newQuests = updated.customQuests.map { quest ->
                if (!quest.isVisible) return@map quest

                fun parseResetDate(): LocalDate? =
                    if (quest.lastResetDate.isBlank()) null
                    else runCatching { LocalDate.parse(quest.lastResetDate) }.getOrNull()

                val shouldReset = when (quest.frequency) {
                    QuestFrequency.DAILY -> {
                        val questResetDate = parseResetDate()
                        questResetDate == null || questResetDate.isBefore(todayDate)
                    }
                    QuestFrequency.WEEKLY -> {
                        val questResetDate = parseResetDate()
                        questResetDate == null || questResetDate.isBefore(weekStart)
                    }
                    QuestFrequency.ONCE -> false
                }

                if (shouldReset) {
                    quest.copy(
                        status = QuestStatus.AVAILABLE,
                        photoUrl = null,
                        rejectReason = null,
                        lastResetDate = today
                    )
                } else quest
            }

            if (newQuests != updated.customQuests) {
                updated = updated.copy(customQuests = newQuests)
            }

            // Затухание стрика: за каждый пропущенный день сгорает 1 огонёк.
            // lastStreakDate очищается — стрик не горит, пока ребёнок не отметится.
            if (updated.lastStreakDate.isNotBlank()) {
                val lastStreak = runCatching { LocalDate.parse(updated.lastStreakDate) }.getOrNull()
                if (lastStreak != null && lastStreak.isBefore(todayDate.minusDays(1))) {
                    val missedDays = java.time.temporal.ChronoUnit.DAYS.between(lastStreak, todayDate).toInt()
                    updated = updated.copy(
                        streakDays = (updated.streakDays - missedDays).coerceAtLeast(0),
                        lastStreakDate = ""
                    )
                }
            }

            if (updated != child) {
                updateChildProfile(updated)
            }
        }
    }
}
