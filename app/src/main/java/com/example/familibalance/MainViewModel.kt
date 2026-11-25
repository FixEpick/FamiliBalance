package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.push.PushNotifier
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.ProfileSync
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.data.SecurePrefs
import io.github.jan.supabase.auth.auth
import com.example.familibalance.data.repository.FamilyRepository
import com.example.familibalance.data.repository.FamilyRepositoryImpl
import com.example.familibalance.util.ScanDiaryUseCase
import com.example.familibalance.viewmodel.actions.resetDailyQuestsIfNeeded
import com.example.familibalance.viewmodel.actions.syncAchievementThemesOnLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

sealed class DiaryScanState {
    object Idle : DiaryScanState()
    object Loading : DiaryScanState()
    data class Success(val gradeCounts: Map<Int, Int>, val childId: String) : DiaryScanState()
    data class Error(val message: UiText) : DiaryScanState()
}

/** Результат попытки входа ребёнка — для разных сообщений об ошибках в UI. */
sealed class LoginResult {
    object Success : LoginResult()
    object InvalidCredentials : LoginResult()
    object RateLimited : LoginResult()
    object NetworkError : LoginResult()
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

    /** true, пока идёт первичная загрузка локального стейта и автовхода. */
    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    /** Статус синхронизации с Supabase: false — есть неподтверждённые локальные изменения. */
    val isSynced: StateFlow<Boolean> = familyRepository.isSynced

    /** Идёт ли сетевая отправка профилей в Supabase прямо сейчас. */
    val isSyncing: StateFlow<Boolean> = familyRepository.isSyncing

    /** true пока выполняется денежная операция (покупка экранного времени,
     *  клейм награды, вывод и т.п.) — UI блокирует повторные нажатия. */
    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction: StateFlow<Boolean> = _isProcessingAction.asStateFlow()

    /** Атомарно занимает «денежный» слот. false — операция уже выполняется. */
    internal fun tryStartProcessingAction(): Boolean {
        if (_isProcessingAction.value) return false
        _isProcessingAction.value = true
        return true
    }

    internal fun endProcessingAction() {
        _isProcessingAction.value = false
    }

    private val scanDiaryUseCase = ScanDiaryUseCase(application)

    internal val _diaryScanState = MutableStateFlow<DiaryScanState>(DiaryScanState.Idle)
    val diaryScanState: StateFlow<DiaryScanState> = _diaryScanState.asStateFlow()

    /** OCR-сканирование фото дневника: результат — только через diaryScanState.
     *  childId сохраняется в Success, чтобы UI применял оценки только
     *  к тому ребёнку, для которого выполнялось сканирование. */
    fun scanDiaryPhoto(childId: String, photoUrl: String) {
        viewModelScope.launch {
            _diaryScanState.value = DiaryScanState.Loading
            try {
                _diaryScanState.value = DiaryScanState.Success(
                    scanDiaryUseCase(photoUrl),
                    childId
                )
            } catch (e: Exception) {
                _diaryScanState.value = DiaryScanState.Error(
                    if (BuildConfig.DEBUG) {
                        UiText.RawString(e.message ?: "error")
                    } else {
                        UiText.Res(R.string.diary_scan_error_generic)
                    }
                )
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

    /** Событие повышения уровня: новый уровень. UI показывает confetti + звук. */
    internal val _levelUpEvent = MutableStateFlow<Int?>(null)
    val levelUpEvent: StateFlow<Int?> = _levelUpEvent.asStateFlow()

    fun consumeLevelUpEvent() {
        _levelUpEvent.value = null
    }

    /** Событие бонусного сундука: выпавшая награда. UI показывает диалог + confetti. */
    internal val _bonusChestEvent = MutableStateFlow<GamificationEngine.BonusChestReward?>(null)
    val bonusChestEvent: StateFlow<GamificationEngine.BonusChestReward?> = _bonusChestEvent.asStateFlow()

    fun consumeBonusChestEvent() {
        _bonusChestEvent.value = null
    }

    /**
     * Источник броска бонусного сундука после клейма награды.
     * Аргумент — шанс в процентах (bonusChestChancePercent профиля).
     * Переопределяется в unit-тестах для детерминизма.
     */
    internal var bonusChestRoller: (Int) -> GamificationEngine.BonusChestReward? = { percent ->
        GamificationEngine.rollBonusChest(percent / 100.0)
    }

    fun resetDiaryScanState() {
        _diaryScanState.value = DiaryScanState.Idle
    }

    private fun snapshotFamilyState() = FamilyUiState(
        children = familyRepository.childrenSnapshot(),
        selectedChildId = familyRepository.selectedChildId
    )

    internal fun emitFamilyState() {
        _familyState.value = snapshotFamilyState()
    }

    /** Единая точка обновления профиля: локальное сохранение + push в Supabase. */
    fun updateChildProfile(profile: ChildProfileState) {
        familyRepository.updateChildProfile(profile)
        emitFamilyState()
    }

    /**
     * Валюта наград — семейная настройка: применяется ко ВСЕМ профилям,
     * чтобы ребёнок видел тот же символ на своём устройстве после синхронизации.
     * Только смена символа отображения, без конвертации курса.
     */
    fun setRewardCurrency(code: String) {
        val now = System.currentTimeMillis()
        AppSharedState.children.replaceAll { _, child ->
            if (child.rewardCurrency == code) child
            else child.copy(rewardCurrency = code, stateUpdatedAt = now)
        }
        familyRepository.saveAndSync()
        emitFamilyState()
    }

    init {
        // Парсинг JSON всех профилей и дневные сбросы — на IO, чтобы не фризить первый кадр
        viewModelScope.launch(Dispatchers.IO) {
            _isInitializing.value = true
            try {
            // Прогрев SecurePrefs-кэша на IO: первое создание
            // EncryptedSharedPreferences дешифрует ключи и может пропустить
            // кадры, если случится на main (логин, ProfileActions).
            runCatching {
                SecurePrefs.get(getApplication(), "app_prefs")
                SecurePrefs.get(getApplication(), "familibalance_prefs")
                SecurePrefs.get(getApplication(), "login_rate_limit_prefs")
            }
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
            // Достижения, заявленные ранее, дооткрывают привязанные темы.
            syncAchievementThemesOnLoad()
            emitFamilyState()

            // Автовход ребёнка: локальные флаги в app_prefs + профиль должен
            // существовать в загруженном стейте. Иначе флаг сбрасываем.
            val prefs = getApplication<Application>()
                .let { SecurePrefs.get(it, "app_prefs") }
            // Режим бенчмарка админки: подставляем тестовых детей,
            // чтобы AdminRoute открылся с наполненным кабинетом.
            // parentUserId берём из анонимной сессии — AdminScreen
            // фильтрует детей по uid родителя.
            if (com.example.familibalance.util.BenchmarkSession.adminRequested) {
                SupabaseManager.ensureSession()
                val uid = runCatching {
                    SupabaseManager.client?.auth?.currentUserOrNull()?.id
                }.getOrNull().orEmpty()
                val benchChildren =
                    com.example.familibalance.util.BenchmarkSession.createAdminChildren(uid)
                benchChildren.forEach { AppSharedState.children[it.id] = it }
                AppSharedState.selectedChildId = benchChildren.first().id
                prefs.edit {
                    putBoolean("is_admin_logged_in", true)
                    putBoolean("onboarding_done", true)
                    putBoolean("is_logged_in", false)
                    remove("logged_in_child_id")
                }
                emitFamilyState()
            }
            // Режим бенчмарка Baseline Profile: collect переустанавливает
            // приложение (свежий стейт), поэтому подставляем тестовый
            // профиль и флаги автовхода — ChildSection открывается сразу,
            // минуя онбординг и форму входа. В обычной жизни флаг false.
            if (com.example.familibalance.util.BenchmarkSession.requested) {
                val bench = com.example.familibalance.util.BenchmarkSession.createChildProfile()
                AppSharedState.children[bench.id] = bench
                AppSharedState.selectedChildId = bench.id
                prefs.edit {
                    putBoolean("is_logged_in", true)
                    putString("logged_in_child_id", bench.id)
                    putBoolean("onboarding_done", true)
                    putBoolean("is_admin_logged_in", false)
                }
                emitFamilyState()
            }
            val savedChildId = prefs.getString("logged_in_child_id", null)
            if (prefs.getBoolean("is_logged_in", false)) {
                if (!savedChildId.isNullOrBlank() &&
                    AppSharedState.children.containsKey(savedChildId)
                ) {
                    AppSharedState.selectedChildId = savedChildId
                    emitFamilyState()
                    _autoLoginChildId.value = savedChildId
                } else {
                    prefs.edit {
                        putBoolean("is_logged_in", false)
                        remove("logged_in_child_id")
                    }
                }
            }
            } finally {
                _isInitializing.value = false
            }
        }
        // Realtime/первичная загрузка из облака меняет AppSharedState напрямую —
        // подписываемся, чтобы UI получил новый снимок состояния.
        viewModelScope.launch {
            familyRepository.onRemoteStateChanged.collect { emitFamilyState() }
        }
        // Создание SupabaseClient и запуск sync — на IO, чтобы не блокировать первый кадр.
        // ensureSession: при авто-входе ребёнка сессии может не быть —
        // без неё push отклоняется и isSynced навсегда остаётся false.
        viewModelScope.launch(Dispatchers.IO) {
            SupabaseManager.ensureSession()
            // Принудительный pull после установки сессии: гарантирует, что при
            // открытии приложения всегда стягивается актуальный баланс с сервера
            // (иначе возможен рассинхрон локального и облачного состояния).
            ProfileSync.loadProfilesFromCloud(getApplication())
            SupabaseManager.startRealtimeSync(getApplication())
        }
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
        if (_isProcessingAction.value) return
        _isProcessingAction.value = true
        try {
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
                        category = "Экранное время",
                        type = HistoryType.SCREEN_TIME_PURCHASE,
                        amountValue = -cost,
                        arg = minutes.toString()
                    )
                ) + child.historyItems
            )
            updateChildProfile(updated)
        } finally {
            _isProcessingAction.value = false
        }
    }

    /** Событиеlevel-up — уже есть; событие отправки подарка родителю (giftTypeId). */
    internal val _parentGiftEvent = MutableStateFlow<String?>(null)
    val parentGiftEvent: StateFlow<String?> = _parentGiftEvent.asStateFlow()

    fun consumeParentGiftEvent() {
        _parentGiftEvent.value = null
    }

    /** Отправка честного признания на проверку папе. Кулдаун — 1 раз в 7 дней. */
    fun submitHonestAdmission(childId: String, text: String) {
        val child = familyRepository.getChild(childId) ?: return
        val today = LocalDate.now()
        val lastDate = runCatching { LocalDate.parse(child.lastHonestAdmissionDate) }.getOrNull()
        // Кулдаун 7 дней. lastDate «в будущем» (откат времени/часового
        // пояса) не блокирует отправку — считаем кулдаун прошедшим.
        if (lastDate != null && !lastDate.isAfter(today) && lastDate.isAfter(today.minusDays(7))) return
        // Жетон честности +1 и +15 XP (с возможным level-up и его coinBonus).
        // Инвариант: balance += coinBonus; история получает LEVEL_UP —
        // сумма записей равна дельте баланса.
        val levelUp = GamificationEngine.applyXp(child.xp, child.level, 15)
        val time = getCurrentTimeString()
        val date = getCurrentDateString()
        val entries = mutableListOf<HistoryData>()
        var balance = child.balance
        if (levelUp.levelsGained > 0) {
            balance += levelUp.coinBonus
            entries += HistoryData(
                title = "Новый уровень!",
                time = time,
                date = date,
                amount = "+${levelUp.coinBonus} ₽",
                isIncome = true,
                category = "Достижения",
                type = HistoryType.LEVEL_UP,
                amountValue = levelUp.coinBonus,
                arg = levelUp.rank.name
            )
        }
        updateChildProfile(
            child.copy(
                honestAdmissionStatus = QuestStatus.PENDING,
                honestAdmissionText = text.trim(),
                lastHonestAdmissionDate = today.toString(),
                honestyTokens = child.honestyTokens + 1,
                xp = levelUp.newXp,
                level = levelUp.newLevel,
                balance = balance,
                historyItems = entries + child.historyItems
            )
        )
        if (levelUp.levelsGained > 0) {
            _levelUpEvent.value = levelUp.newLevel
        }
        PushNotifier.honestAdmissionSubmitted(child.parentUserId, child.name)
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

    /** Выход ребёнка: сбрасываем выбор активного профиля, UI-стейты и флаги автовхода. */
    fun logoutChild() {
        AppSharedState.selectedChildId = ""
        _diaryScanState.value = DiaryScanState.Idle
        // Ждём завершения текущей синхронизации (макс 10 сек), чтобы
        // фоновые pushChildProfile не записали профиль после очистки состояния.
        viewModelScope.launch {
            withTimeoutOrNull(10_000) {
                familyRepository.isSyncing.first { !it }
            }
            getApplication<Application>()
                .let { SecurePrefs.get(it, "app_prefs") }
                .edit {
                    putBoolean("is_logged_in", false)
                    remove("logged_in_child_id")
                }
            familyRepository.saveLocal()
            emitFamilyState()
        }
    }

    /**
     * Полное удаление аккаунта родителя (Google Play).
     * SupabaseManager.deleteAccount чистит Storage, вызывает RPC
     * delete_my_account и делает signOut. При успехе локально сбрасываем
     * флаги сессии и стейт — UI вернётся на экран входа.
     */
    fun deleteAccount(onResult: (Boolean) -> Unit) {
        if (!tryStartProcessingAction()) {
            onResult(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = SupabaseManager.deleteAccount(getApplication())
                if (ok) {
                    getApplication<Application>()
                        .let { SecurePrefs.get(it, "app_prefs") }
                        .edit {
                            putBoolean("is_logged_in", false)
                            putBoolean("is_admin_logged_in", false)
                            remove("logged_in_child_id")
                        }
                    clearState()
                }
                onResult(ok)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "deleteAccount failed", e)
                onResult(false)
            } finally {
                endProcessingAction()
            }
        }
    }

    /**
     * Удаление профиля ребёнка по инициативе родителя.
     * SupabaseManager.deleteChildProfile чистит Storage, удаляет строку
     * family_profiles и локальный профиль. При успехе обновляем UI.
     */
    fun deleteChildProfile(childId: String, onResult: (Boolean) -> Unit) {
        if (!tryStartProcessingAction()) {
            onResult(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = SupabaseManager.deleteChildProfile(getApplication(), childId)
                if (ok) emitFamilyState()
                onResult(ok)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "deleteChildProfile failed", e)
                onResult(false)
            } finally {
                endProcessingAction()
            }
        }
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

}
