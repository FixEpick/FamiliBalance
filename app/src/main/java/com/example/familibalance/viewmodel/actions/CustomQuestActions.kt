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
    profile.customQuests.add(newQuest)
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.deleteCustomQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.customQuests.removeAll { it.id == questId }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.updateCustomQuest(userId: String, questId: String, title: String, desc: String, reward: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.customQuests.indexOfFirst { it.id == questId }
    if (idx != -1) {
        profile.customQuests[idx] = profile.customQuests[idx].copy(
            title = title,
            description = desc,
            reward = reward
        )
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.toggleQuestVisibility(userId: String, questId: String, isVisible: Boolean) {
    val profile = familyRepository.getChild(userId) ?: return
    val questIndex = profile.customQuests.indexOfFirst { it.id == questId }
    if (questIndex != -1) {
        val quest = profile.customQuests[questIndex]
        profile.customQuests[questIndex] = quest.copy(isVisible = isVisible)
    } else {
        when (questId) {
            "closet", "шкаф" -> profile.isWardrobeVisible = isVisible
            "room", "комната" -> profile.isRoomVisible = isVisible
            "diary", "дневник", "итоги" -> profile.isDiaryVisible = isVisible
        }
    }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.submitCustomQuestPhoto(userId: String, questId: String, photoUrl: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.customQuests.indexOfFirst { it.id == questId }
    if (idx != -1) {
        val quest = profile.customQuests[idx]
        profile.customQuests[idx] = quest.copy(
            status = QuestStatus.PENDING,
            photoUrl = photoUrl,
            rejectReason = null
        )
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.claimCustomQuestReward(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.customQuests.indexOfFirst { it.id == questId }
    if (idx != -1) {
        val quest = profile.customQuests[idx]
        if (quest.status == QuestStatus.READY_TO_CLAIM) {
            profile.customQuests[idx] = quest.copy(status = QuestStatus.CLAIMED)
            profile.balance += quest.reward
            profile.historyItems.add(
                0,
                HistoryData(
                    title = "Квест выполнен: ${quest.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${quest.reward} ₽",
                    isIncome = true,
                    category = "Кастомные квесты"
                )
            )
            updateChildProfile(profile)
        }
    }
}
