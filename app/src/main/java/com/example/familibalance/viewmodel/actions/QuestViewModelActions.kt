package com.example.familibalance.viewmodel.actions

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import kotlinx.coroutines.launch

fun MainViewModel.approveQuest(userId: String, questType: String, customReward: Int? = null) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.closetPhotoUrl
        "room", "комната" -> profile.roomPhotoUrl
        "diary", "дневник", "итоги" -> profile.diaryPhotoUrl
        else -> null
    }
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            profile.closetPhotoUrl = null
            profile.closetStatus = QuestStatus.READY_TO_CLAIM
            profile.closetRejectReason = null
        }
        "room", "комната" -> {
            profile.roomPhotoUrl = null
            profile.roomStatus = QuestStatus.READY_TO_CLAIM
            profile.roomRejectReason = null
        }
        "diary", "дневник", "итоги" -> {
            profile.diaryPhotoUrl = null
            profile.diaryStatus = QuestStatus.READY_TO_CLAIM
            profile.diaryRejectReason = null
            if (customReward != null) {
                profile.diaryApprovedReward = customReward
            }
        }
    }
    clearQuestPhotoIfNeeded(profile, questType, photoUrlToDelete)
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.rejectQuest(userId: String, questType: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.closetPhotoUrl
        "room", "комната" -> profile.roomPhotoUrl
        "diary", "дневник", "итоги" -> profile.diaryPhotoUrl
        else -> null
    }
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            profile.closetPhotoUrl = null
            profile.closetStatus = QuestStatus.AVAILABLE
            profile.closetRejectReason = reason
        }
        "room", "комната" -> {
            profile.roomPhotoUrl = null
            profile.roomStatus = QuestStatus.AVAILABLE
            profile.roomRejectReason = reason
        }
        "diary", "дневник", "итоги" -> {
            profile.diaryPhotoUrl = null
            profile.diaryStatus = QuestStatus.AVAILABLE
            profile.diaryRejectReason = reason
        }
    }
    clearQuestPhotoIfNeeded(profile, questType, photoUrlToDelete)
    saveState()
    familyRepository.pushChildProfile(profile)
}

private fun MainViewModel.clearQuestPhotoIfNeeded(profile: ChildProfileState, questType: String, photoUrl: String?) {
    if (photoUrl.isNullOrBlank()) {
        return
    }

    val childId = profile.id
    val photoUrlToDelete = photoUrl

    viewModelScope.launch {
        try {
            val deleted = SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
            if (!deleted) {
                Log.w("MainViewModel", "Не удалось удалить фото квеста $questType для ребёнка $childId")
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Ошибка при удалении фото квеста $questType: ${e.message}")
        }
    }
}

/** Ребёнок отправляет фото квеста (шкаф/комната/дневник) на проверку папе. */
fun MainViewModel.submitQuestForReview(userId: String, questType: String, photoUrl: String?) {
    val profile = familyRepository.getChild(userId) ?: return
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            profile.closetStatus = QuestStatus.PENDING
            profile.closetRejectReason = null
            profile.closetPhotoUrl = photoUrl
        }
        "room", "комната" -> {
            profile.roomStatus = QuestStatus.PENDING
            profile.roomRejectReason = null
            profile.roomPhotoUrl = photoUrl
        }
        "diary", "дневник", "итоги" -> {
            profile.diaryStatus = QuestStatus.PENDING
            profile.diaryRejectReason = null
            profile.diaryPhotoUrl = photoUrl
        }
    }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.claimQuestReward(userId: String, questType: String) {
    val profile = familyRepository.getChild(userId) ?: return
    var rewardAmount = 50
    var questTitle = ""
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            if (profile.closetStatus == QuestStatus.READY_TO_CLAIM) {
                profile.closetStatus = QuestStatus.CLAIMED
                profile.wardrobeCount += 1
                questTitle = "Фото шкафа"
                rewardAmount = profile.wardrobeReward
            }
        }
        "room", "комната" -> {
            if (profile.roomStatus == QuestStatus.READY_TO_CLAIM) {
                profile.roomStatus = QuestStatus.CLAIMED
                profile.roomCount += 1
                questTitle = "Фото комнаты"
                rewardAmount = profile.roomReward
            }
        }
        "diary", "дневник", "итоги" -> {
            if (profile.diaryStatus == QuestStatus.READY_TO_CLAIM) {
                profile.diaryStatus = QuestStatus.CLAIMED
                questTitle = "📚 Итоги недели (Дневник)"
                rewardAmount = profile.diaryApprovedReward
            }
        }
    }
    if (questTitle.isNotEmpty()) {
        profile.balance += rewardAmount
        profile.historyItems.add(
            0,
            HistoryData(
                title = "Награда получена: $questTitle",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "+$rewardAmount ₽",
                isIncome = true,
                category = "Квесты"
            )
        )
        updateChildProfile(profile)
    }
}

fun MainViewModel.addBonusQuest(userId: String, title: String, reward: Int, description: String = "") {
    val profile = familyRepository.getChild(userId) ?: return
    profile.bonusQuests.add(
        BonusQuest(
            title = title,
            reward = reward,
            description = description,
            status = QuestStatus.AVAILABLE,
            createdDate = getCurrentDateString()
        )
    )
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.submitBonusQuestByChild(userId: String, questId: String, photoUri: Uri?, comment: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        profile.bonusQuests[index] = q.copy(
            status = QuestStatus.PENDING,
            rejectReason = null,
            photoUrl = photoUri?.toString() ?: q.photoUrl,
            comment = comment
        )
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.approveBonusQuest(userId: String, questId: String, customReward: Int? = null) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        val finalReward = customReward ?: q.reward
        profile.bonusQuests[index] = q.copy(
            status = QuestStatus.READY_TO_CLAIM,
            rejectReason = null,
            reward = finalReward
        )
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.rejectBonusQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        val photoUrlToDelete = q.photoUrl
        profile.bonusQuests[index] = q.copy(
            status = QuestStatus.AVAILABLE,
            rejectReason = reason,
            photoUrl = null
        )
        saveState()
        familyRepository.pushChildProfile(profile)
        if (!photoUrlToDelete.isNullOrBlank()) {
            viewModelScope.launch {
                SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
            }
        }
    }
}

fun MainViewModel.claimBonusQuestReward(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        if (q.status == QuestStatus.READY_TO_CLAIM) {
            profile.bonusQuests[index] = q.copy(status = QuestStatus.CLAIMED)
            profile.balance += q.reward
            profile.historyItems.add(
                0,
                HistoryData(
                    title = "Бонус-квест выполнен: ${q.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${q.reward} ₽",
                    isIncome = true,
                    category = "Бонус-квесты"
                )
            )
            updateChildProfile(profile)
        }
    }
}

fun MainViewModel.deleteBonusQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = profile.bonusQuests.firstOrNull { it.id == questId }?.photoUrl
    profile.bonusQuests.removeAll { it.id == questId }
    saveState()
    familyRepository.pushChildProfile(profile)
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}
