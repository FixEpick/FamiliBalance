package com.example.familibalance

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.data.auth.AuthRepository
import com.example.familibalance.data.PinHasher
import io.github.jan.supabase.auth.auth
import com.example.familibalance.data.repository.FamilyRepository
import com.example.familibalance.data.repository.FamilyRepositoryImpl
import com.example.familibalance.util.ScanDiaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

sealed class DiaryScanState {
    object Idle : DiaryScanState()
    object Loading : DiaryScanState()
    data class Success(val gradeCounts: Map<Int, Int>) : DiaryScanState()
    data class Error(val message: String) : DiaryScanState()
}

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

    private val scanDiaryUseCase = ScanDiaryUseCase(application)

    private val _diaryScanState = MutableStateFlow<DiaryScanState>(DiaryScanState.Idle)
    val diaryScanState: StateFlow<DiaryScanState> = _diaryScanState.asStateFlow()

    /** OCR-сканирование фото дневника: результат — только через diaryScanState. */
    fun scanDiaryPhoto(photoUrl: String) {
        viewModelScope.launch {
            _diaryScanState.value = DiaryScanState.Loading
            try {
                _diaryScanState.value = DiaryScanState.Success(scanDiaryUseCase(photoUrl))
            } catch (e: Exception) {
                _diaryScanState.value = DiaryScanState.Error(e.message ?: "Ошибка распознавания")
            }
        }
    }

    /**
     * ID ребёнка для автовхода при перезапуске приложения.
     * Заполняется в init после загрузки локального стейта, если в app_prefs
     * стоит флаг is_logged_in и профиль найден. MainActivity по этому
     * значению сразу открывает ChildSection без WelcomeScreen.
     */
    private val _autoLoginChildId = MutableStateFlow<String?>(null)
    val autoLoginChildId: StateFlow<String?> = _autoLoginChildId.asStateFlow()

    // Rate-limit входа ребёнка: ключ "email|childName" -> число неудачных попыток,
    // и время (epoch ms), до которого вход для ключа заблокирован.
    private val loginAttempts = ConcurrentHashMap<String, Int>()
    private val loginLockUntil = ConcurrentHashMap<String, Long>()

    private companion object {
        const val LOGIN_MAX_ATTEMPTS = 5
        const val LOGIN_LOCK_BASE_MS = 60_000L
    }

    fun resetDiaryScanState() {
        _diaryScanState.value = DiaryScanState.Idle
    }

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

            // Автовход ребёнка: локальные флаги в app_prefs + профиль должен
            // существовать в загруженном стейте. Иначе флаг сбрасываем.
            val prefs = getApplication<Application>()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val savedChildId = prefs.getString("logged_in_child_id", null)
            if (prefs.getBoolean("is_logged_in", false)) {
                if (!savedChildId.isNullOrBlank() &&
                    AppSharedState.children.containsKey(savedChildId)
                ) {
                    AppSharedState.selectedChildId = savedChildId
                    emitFamilyState()
                    _autoLoginChildId.value = savedChildId
                } else {
                    prefs.edit()
                        .putBoolean("is_logged_in", false)
                        .remove("logged_in_child_id")
                        .apply()
                }
            }
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
        // Сбрасываем стейт OCR-дневника: результат скана привязан к конкретному ребёнку
        _diaryScanState.value = DiaryScanState.Idle
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
                pinCode = PinHasher.hash(pin.ifBlank { "0000" }, PinHasher.generateSalt()),
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
            // Новый ребёнок сразу считается вошедшим (status = active)
            if (status == "active") {
                getApplication<Application>()
                    .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_logged_in", true)
                    .putString("logged_in_child_id", childId)
                    .putBoolean("is_admin_logged_in", false)
                    .apply()
            }
            onResult?.invoke(profile)
        }
    }

    /** Смена ПИН-кода ребёнка из админки: copy() + updateChildProfile (push в Supabase). */
    fun updateChildPin(childId: String, newPin: String) {
        val child = familyRepository.getChild(childId) ?: return
        updateChildProfile(child.copy(pinCode = PinHasher.hash(newPin, PinHasher.generateSalt())))
    }

    /**
     * Вход ребёнка по email родителя + имени + PIN.
     * При успехе: профиль импортируется в локальный стейт, сохраняется
     * ANDROID_ID устройства (для будущих пушей) и выбирается активным.
     */
    fun loginChild(
        parentEmail: String,
        childName: String,
        pin: String,
        onResult: (Boolean) -> Unit
    ) {
        // Локальный rate-limit: после LOGIN_MAX_ATTEMPTS неудач — блокировка.
        val rateKey = "${parentEmail.trim().lowercase()}|${childName.trim().lowercase()}"
        val now = System.currentTimeMillis()
        val lockedUntil = loginLockUntil[rateKey] ?: 0L
        if (now < lockedUntil) {
            onResult(false)
            return
        }

        fun registerFailure() {
            val attempts = (loginAttempts[rateKey] ?: 0) + 1
            if (attempts >= LOGIN_MAX_ATTEMPTS) {
                // Блокировка растёт с каждой новой серией неудач: 60с, 120с, ...
                val lockMs = LOGIN_LOCK_BASE_MS * attempts.coerceAtMost(10)
                loginLockUntil[rateKey] = System.currentTimeMillis() + lockMs
                loginAttempts.remove(rateKey)
            } else {
                loginAttempts[rateKey] = attempts
            }
        }

        viewModelScope.launch {
            val repo = runCatching { AuthRepository() }.getOrNull()
            if (repo == null) {
                registerFailure()
                onResult(false)
                return@launch
            }
            val profile = runCatching {
                repo.findChildProfile(parentEmail, childName, pin)
            }.getOrNull()
            if (profile == null) {
                registerFailure()
                onResult(false)
                return@launch
            }
            // Успешный вход — сбрасываем счётчик и блокировку.
            loginAttempts.remove(rateKey)
            loginLockUntil.remove(rateKey)
            val deviceId = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty()
            val updated = profile.copy(deviceId = deviceId)
            AppSharedState.children[updated.id] = updated
            selectChild(updated.id)
            // Досылаем deviceId в Supabase (профиль пометится dirty до подтверждения)
            familyRepository.pushChildProfile(updated)
            // Флаги автовхода: при следующем запуске откроем ChildSection без Welcome
            getApplication<Application>()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putString("logged_in_child_id", updated.id)
                .putBoolean("is_admin_logged_in", false)
                .apply()
            onResult(true)
        }
    }

    /** Выход ребёнка: сбрасываем выбор активного профиля, UI-стейты и флаги автовхода. */
    fun logoutChild() {
        AppSharedState.selectedChildId = ""
        _diaryScanState.value = DiaryScanState.Idle
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", false)
            .remove("logged_in_child_id")
            .apply()
        familyRepository.saveLocal()
        emitFamilyState()
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

        fun parseResetDate(raw: String): LocalDate? =
            if (raw.isBlank()) null
            else runCatching { LocalDate.parse(raw) }.getOrNull()

        /**
         * Проверяет встроенный квест: истёк ли его период (день/неделя).
         * Возвращает новую дату сброса и флаг очистки статуса.
         * Пустая дата — первый запуск: только ставим метку, статус не трогаем.
         * PENDING-квесты не сбрасываются (родитель ещё не проверил).
         */
        fun checkBuiltInQuest(
            frequency: QuestFrequency,
            lastResetDate: String,
            status: QuestStatus
        ): Pair<String, Boolean> {
            val parsed = parseResetDate(lastResetDate)
                ?: return today to false
            val elapsed = when (frequency) {
                QuestFrequency.DAILY -> parsed.isBefore(todayDate)
                QuestFrequency.WEEKLY -> parsed.isBefore(weekStart)
                QuestFrequency.ONCE -> false
            }
            return if (elapsed) today to (status != QuestStatus.PENDING) else lastResetDate to false
        }

        children.forEach { child ->
            var updated = child

            if (child.lastDailyResetDate != today) {
                updated = updated.copy(lastDailyResetDate = today)
            }

            // Шкаф
            val (closetDate, closetClear) = checkBuiltInQuest(
                child.wardrobeFrequency, child.closetResetDate, child.closetStatus
            )
            if (closetDate != child.closetResetDate || closetClear) {
                updated = updated.copy(
                    closetResetDate = closetDate,
                    closetStatus = if (closetClear) QuestStatus.AVAILABLE else child.closetStatus,
                    closetRejectReason = if (closetClear) null else child.closetRejectReason,
                    closetPhotoUrl = if (closetClear) null else child.closetPhotoUrl,
                    closetComment = if (closetClear) "" else child.closetComment
                )
            }

            // Комната
            val (roomDate, roomClear) = checkBuiltInQuest(
                child.roomFrequency, child.roomResetDate, child.roomStatus
            )
            if (roomDate != child.roomResetDate || roomClear) {
                updated = updated.copy(
                    roomResetDate = roomDate,
                    roomStatus = if (roomClear) QuestStatus.AVAILABLE else child.roomStatus,
                    roomRejectReason = if (roomClear) null else child.roomRejectReason,
                    roomPhotoUrl = if (roomClear) null else child.roomPhotoUrl,
                    roomComment = if (roomClear) "" else child.roomComment
                )
            }

            // Дневник
            val (diaryDate, diaryClear) = checkBuiltInQuest(
                child.diaryFrequency, child.diaryResetDate, child.diaryStatus
            )
            if (diaryDate != child.diaryResetDate || diaryClear) {
                updated = updated.copy(
                    diaryResetDate = diaryDate,
                    diaryStatus = if (diaryClear) QuestStatus.AVAILABLE else child.diaryStatus,
                    diaryRejectReason = if (diaryClear) null else child.diaryRejectReason,
                    diaryPhotoUrl = if (diaryClear) null else child.diaryPhotoUrl,
                    diaryComment = if (diaryClear) "" else child.diaryComment
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
