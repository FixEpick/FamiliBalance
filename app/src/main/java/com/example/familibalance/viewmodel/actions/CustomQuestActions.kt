package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.push.PushNotifier
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import com.example.familibalance.util.PhotoStorage
import kotlinx.coroutines.launch
import java.time.LocalDate

fun MainViewModel.addCustomQuest(userId: String, title: String, reward: Int, frequency: QuestFrequency, description: String = "", requiresPhoto: Boolean = true) {
    val profile = familyRepository.getChild(userId) ?: return
    val newQuest = CustomQuest(
        title = title,
        reward = reward,
        description = description,
        frequency = frequency,
        lastResetDate = LocalDate.now().toString(),
        requiresPhoto = requiresPhoto
    )
    updateChildProfile(profile.copy(customQuests = profile.customQuests + newQuest))
}

fun MainViewModel.deleteCustomQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = profile.customQuests.firstOrNull { it.id == questId }?.photoUrl
    updateChildProfile(profile.copy(customQuests = profile.customQuests.filterNot { it.id == questId }))
    if (!photoUrlToDelete.isNullOrBlank()) {
        PhotoStorage.deleteLocalFileIfPresent(getApplication(), photoUrlToDelete)
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}

fun MainViewModel.updateCustomQuest(
    userId: String,
    questId: String,
    title: String,
    desc: String,
    reward: Int,
    frequency: QuestFrequency? = null,
    requiresPhoto: Boolean? = null
) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedQuests = profile.customQuests.map { quest ->
        if (quest.id == questId) {
            quest.copy(
                title = title,
                description = desc,
                reward = reward,
                frequency = frequency ?: quest.frequency,
                requiresPhoto = requiresPhoto ?: quest.requiresPhoto
            )
        } else quest
    }
    if (updatedQuests != profile.customQuests) {
        updateChildProfile(profile.copy(customQuests = updatedQuests))
    }
}

fun MainViewModel.toggleQuestVisibility(userId: String, questId: String, isVisible: Boolean) {
    val profile = familyRepository.getChild(userId) ?: return
    val updated = when (questId) {
        "closet", "шкаф" -> profile.copy(isWardrobeVisible = isVisible)
        "room", "комната" -> profile.copy(isRoomVisible = isVisible)
        "diary", "дневник", "итоги" -> profile.copy(isDiaryVisible = isVisible)
        else -> {
            val updatedQuests = profile.customQuests.map { quest ->
                if (quest.id == questId) quest.copy(isVisible = isVisible) else quest
            }
            profile.copy(customQuests = updatedQuests)
        }
    }
    updateChildProfile(updated)
}

fun MainViewModel.submitCustomQuestPhoto(userId: String, questId: String, photoUrl: String, comment: String = "") {
    val profile = familyRepository.getChild(userId) ?: return
    val persistedPhotoUrl = PhotoStorage.persistIfNeeded(getApplication(), photoUrl)
    val updatedQuests = profile.customQuests.map { quest ->
        if (quest.id == questId) {
            quest.copy(
                status = QuestStatus.PENDING,
                // Не затираем фото пустой строкой: если persist не дал результата —
                // оставляем прежний URL, чтобы не потерять уже сохранённое фото.
                photoUrl = persistedPhotoUrl?.takeIf { it.isNotBlank() } ?: quest.photoUrl,
                rejectReason = null,
                comment = comment.trim()
            )
        } else quest
    }
    if (updatedQuests != profile.customQuests) {
        updateChildProfile(profile.copy(customQuests = updatedQuests))
        val cq = profile.customQuests.firstOrNull { it.id == questId }
        PushNotifier.photoReportSubmitted(
            profile.parentUserId, profile.name,
            cq?.title ?: "", cq?.reward ?: 0,
            RewardCurrency.symbolOf(profile.rewardCurrency)
        )
    }
}

fun MainViewModel.claimCustomQuestReward(userId: String, questId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.customQuests.firstOrNull { it.id == questId } ?: return
    if (target.status == QuestStatus.READY_TO_CLAIM) {
        val outcome = computeQuestReward(profile, target.reward, GamificationEngine.QuestKind.BONUS_OR_CUSTOM)
        // Недельный лимит заработка: монеты ограничиваются остатком лимита,
        // XP и level-up начисляются как обычно.
        val weeklyCap = applyWeeklyEarnLimit(profile, outcome.coins)
        val grantedCoins = weeklyCap.coins
        val time = getCurrentTimeString()
        val date = getCurrentDateString()
        val updatedQuests = profile.customQuests.map { quest ->
            if (quest.id == questId) quest.copy(status = QuestStatus.CLAIMED) else quest
        }
        val limitEntries = if (weeklyCap.capped) {
            listOf(
                HistoryData(
                    title = "Недельный лимит заработка",
                    time = time,
                    date = date,
                    amount = "",
                    isIncome = false,
                    category = "Бонусы",
                    type = HistoryType.WEEKLY_LIMIT,
                    arg = profile.weeklyEarnLimit.toString()
                )
            )
        } else emptyList()
        var updated = profile.copy(
            customQuests = updatedQuests,
            balance = profile.balance + grantedCoins + outcome.levelUp.coinBonus,
            xp = outcome.levelUp.newXp,
            level = outcome.levelUp.newLevel,
            historyItems = limitEntries + listOf(
                HistoryData(
                    title = "Квест выполнен: ${target.title}",
                    time = time,
                    date = date,
                    amount = "+$grantedCoins ₽",
                    isIncome = true,
                    category = "Кастомные квесты",
                    type = HistoryType.CUSTOM_QUEST_DONE,
                    amountValue = grantedCoins,
                    arg = target.title
                )
            ) + gamificationHistoryEntries(outcome, time, date, 0) + profile.historyItems
        )
        // Бонусный сундук: шанс из настроек экономики (0% — не выпадает).
        val chest = rollChest(profile)?.let { applyBonusChest(updated, it, time, date) }
        if (chest != null) {
            updated = updated.copy(
                balance = updated.balance + chest.balanceDelta,
                xp = chest.xp,
                level = chest.level,
                historyItems = chest.historyEntries + updated.historyItems
            )
        }
        updateChildProfile(updated)
        notifyLevelUp(outcome)
        if (chest != null) notifyBonusChest(chest)
    }
    } finally {
        endProcessingAction()
    }
}
