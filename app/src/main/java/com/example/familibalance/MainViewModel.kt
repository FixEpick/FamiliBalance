package com.example.familibalance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
