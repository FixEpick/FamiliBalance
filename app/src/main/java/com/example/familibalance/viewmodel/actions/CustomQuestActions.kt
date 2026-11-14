package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import java.time.LocalDate

fun MainViewModel.addCustomQuest(userId: String, title: String, reward: Int, frequency: QuestFrequency, description: String = "") {
    val profile = familyRepository.getChild(userId) ?: return
    val newQuest = CustomQuest(
        title = title,
        reward = reward,
        description = description,
        frequency = frequency,
        lastResetDate = LocalDate.now().toString()
    )
    updateChildProfile(profile.copy(customQuests = profile.customQuests + newQuest))
}

fun MainViewModel.deleteCustomQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(customQuests = profile.customQuests.filterNot { it.id == questId }))
}

fun MainViewModel.updateCustomQuest(userId: String, questId: String, title: String, desc: String, reward: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedQuests = profile.customQuests.map { quest ->
        if (quest.id == questId) {
            quest.copy(title = title, description = desc, reward = reward)
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

fun MainViewModel.submitCustomQuestPhoto(userId: String, questId: String, photoUrl: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedQuests = profile.customQuests.map { quest ->
        if (quest.id == questId) {
            quest.copy(
                status = QuestStatus.PENDING,
                photoUrl = photoUrl,
                rejectReason = null
            )
        } else quest
    }
    if (updatedQuests != profile.customQuests) {
        updateChildProfile(profile.copy(customQuests = updatedQuests))
    }
}

fun MainViewModel.claimCustomQuestReward(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.customQuests.firstOrNull { it.id == questId } ?: return
    if (target.status == QuestStatus.READY_TO_CLAIM) {
        val updatedQuests = profile.customQuests.map { quest ->
            if (quest.id == questId) quest.copy(status = QuestStatus.CLAIMED) else quest
        }
        val updated = profile.copy(
            customQuests = updatedQuests,
            balance = profile.balance + target.reward,
            historyItems = listOf(
                HistoryData(
                    title = "Квест выполнен: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${target.reward} ₽",
                    isIncome = true,
                    category = "Кастомные квесты"
                )
            ) + profile.historyItems
        )
        updateChildProfile(updated)
    }
}
