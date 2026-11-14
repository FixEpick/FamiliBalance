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
        // Парсинг JSON всех профилей — на IO, чтобы не фризить первый кадр
        viewModelScope.launch(Dispatchers.IO) {
            familyRepository.loadState()
            emitFamilyState()
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
        child.balance -= cost
        child.boughtMinutesToday += minutes
        child.historyItems.add(
            0,
            HistoryData(
                title = "Покупка экранного времени ($minutes мин)",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-$cost ₽",
                isIncome = false,
                category = "Экранное время"
            )
        )
        saveAndSync()
    }

    /** Отправка честного признания на проверку папе. */
    fun submitHonestAdmission(childId: String) {
        val child = familyRepository.getChild(childId) ?: return
        child.honestAdmissionStatus = QuestStatus.PENDING
        saveAndSync()
    }

    /** Сброс купленных минут экранного времени при смене дня. */
    fun resetDailyScreenTime(childId: String) {
        val child = familyRepository.getChild(childId) ?: return
        child.boughtMinutesToday = 0
        saveAndSync()
    }

    /** Скрытие баннера бонуса за стрик. */
    fun dismissStreakBonus(childId: String) {
        val child = familyRepository.getChild(childId) ?: return
        child.streakBonusMessage = ""
        saveAndSync()
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
        val children = familyRepository.getAllChildren()

        children.forEach { child ->
            var childChanged = false

            if (child.lastDailyResetDate != today) {
                child.lastDailyResetDate = today

                child.closetStatus = QuestStatus.AVAILABLE
                child.closetRejectReason = null
                child.closetPhotoUrl = null

                child.roomStatus = QuestStatus.AVAILABLE
                child.roomRejectReason = null
                child.roomPhotoUrl = null

                childChanged = true
            }

            // Auto-reset visible custom quests based on frequency
            val todayDate = LocalDate.now()
            val weekStart = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            child.customQuests.forEach { quest ->
                if (!quest.isVisible) return@forEach

                fun parseResetDate(): LocalDate? =
                    if (quest.lastResetDate.isBlank()) null
                    else runCatching { LocalDate.parse(quest.lastResetDate) }.getOrNull()

                when (quest.frequency) {
                    QuestFrequency.DAILY -> {
                        val questResetDate = parseResetDate()
                        if (questResetDate == null || questResetDate.isBefore(todayDate)) {
                            val idx = child.customQuests.indexOfFirst { it.id == quest.id }
                            if (idx != -1) {
                                child.customQuests[idx] = quest.copy(
                                    status = QuestStatus.AVAILABLE,
                                    photoUrl = null,
                                    rejectReason = null,
                                    lastResetDate = today
                                )
                                childChanged = true
                            }
                        }
                    }
                    QuestFrequency.WEEKLY -> {
                        val questResetDate = parseResetDate()
                        if (questResetDate == null || questResetDate.isBefore(weekStart)) {
                            val idx = child.customQuests.indexOfFirst { it.id == quest.id }
                            if (idx != -1) {
                                child.customQuests[idx] = quest.copy(
                                    status = QuestStatus.AVAILABLE,
                                    photoUrl = null,
                                    rejectReason = null,
                                    lastResetDate = today
                                )
                                childChanged = true
                            }
                        }
                    }
                    QuestFrequency.ONCE -> {
                        // Do not reset
                    }
                }
            }

            if (childChanged) {
                familyRepository.saveAndSync()
            }
        }
    }
}
