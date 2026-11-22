package com.example.familibalance.feature.admin.settings

import com.example.familibalance.MainViewModel
import com.example.familibalance.viewmodel.actions.*

/**
 * Тип операции сброса данных ребёнка.
 *
 * Enum вместо лямбды `() -> Unit`: значение saveable через rememberSaveable,
 * поэтому выбранная операция переживает пересоздание Activity (смена темы,
 * сворачивание/разворачивание, process death) — диалог подтверждения
 * не теряет действие.
 */
enum class ResetKind {
    QUESTS,
    VISIBILITY,
    ACHIEVEMENTS,
    COUNTERS,
    HONEST,
    HISTORY,
    BALANCE,
    STREAK,
    DREAM,
    THEME,
    ALL;

    fun execute(viewModel: MainViewModel, childId: String) {
        when (this) {
            QUESTS -> viewModel.resetActiveQuests(childId)
            VISIBILITY -> viewModel.resetQuestVisibility(childId)
            ACHIEVEMENTS -> viewModel.resetAchievements(childId)
            COUNTERS -> viewModel.resetGradeCounters(childId)
            HONEST -> viewModel.resetHonestAdmission(childId)
            HISTORY -> viewModel.clearHistory(childId)
            BALANCE -> viewModel.resetBalance(childId)
            STREAK -> viewModel.resetStreakAndTime(childId)
            DREAM -> viewModel.resetDream(childId)
            THEME -> viewModel.resetTheme(childId)
            ALL -> viewModel.resetAllChildData(childId)
        }
    }
}
