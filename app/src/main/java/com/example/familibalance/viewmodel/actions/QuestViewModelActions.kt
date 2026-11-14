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
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetPhotoUrl = null,
            closetStatus = QuestStatus.READY_TO_CLAIM,
            closetRejectReason = null
        )
        "room", "комната" -> profile.copy(
            roomPhotoUrl = null,
            roomStatus = QuestStatus.READY_TO_CLAIM,
            roomRejectReason = null
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryPhotoUrl = null,
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryRejectReason = null,
            diaryApprovedReward = customReward ?: profile.diaryApprovedReward
        )
        else -> profile
    }
    clearQuestPhotoIfNeeded(updated, questType, photoUrlToDelete)
    updateChildProfile(updated)
}

fun MainViewModel.rejectQuest(userId: String, questType: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.closetPhotoUrl
        "room", "комната" -> profile.roomPhotoUrl
        "diary", "дневник", "итоги" -> profile.diaryPhotoUrl
        else -> null
    }
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetPhotoUrl = null,
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = reason
        )
        "room", "комната" -> profile.copy(
            roomPhotoUrl = null,
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = reason
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryPhotoUrl = null,
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = reason
        )
        else -> profile
    }
    clearQuestPhotoIfNeeded(updated, questType, photoUrlToDelete)
    updateChildProfile(updated)
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
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetStatus = QuestStatus.PENDING,
            closetRejectReason = null,
            closetPhotoUrl = photoUrl
        )
        "room", "комната" -> profile.copy(
            roomStatus = QuestStatus.PENDING,
            roomRejectReason = null,
            roomPhotoUrl = photoUrl
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryStatus = QuestStatus.PENDING,
            diaryRejectReason = null,
            diaryPhotoUrl = photoUrl
        )
        else -> profile
    }
    updateChildProfile(updated)
}

fun MainViewModel.claimQuestReward(userId: String, questType: String) {
    val profile = familyRepository.getChild(userId) ?: return
    var updated = profile
    var rewardAmount = 50
    var questTitle = ""
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            if (profile.closetStatus == QuestStatus.READY_TO_CLAIM) {
                updated = updated.copy(
                    closetStatus = QuestStatus.CLAIMED,
                    wardrobeCount = updated.wardrobeCount + 1
                )
                questTitle = "Фото шкафа"
                rewardAmount = profile.wardrobeReward
            }
        }
        "room", "комната" -> {
            if (profile.roomStatus == QuestStatus.READY_TO_CLAIM) {
                updated = updated.copy(
                    roomStatus = QuestStatus.CLAIMED,
                    roomCount = updated.roomCount + 1
                )
                questTitle = "Фото комнаты"
                rewardAmount = profile.roomReward
            }
        }
        "diary", "дневник", "итоги" -> {
            if (profile.diaryStatus == QuestStatus.READY_TO_CLAIM) {
                updated = updated.copy(diaryStatus = QuestStatus.CLAIMED)
                questTitle = "📚 Итоги недели (Дневник)"
                rewardAmount = profile.diaryApprovedReward
            }
        }
    }
    if (questTitle.isNotEmpty()) {
        updated = updated.copy(
            balance = updated.balance + rewardAmount,
            historyItems = listOf(
                HistoryData(
                    title = "Награда получена: $questTitle",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+$rewardAmount ₽",
                    isIncome = true,
                    category = "Квесты"
                )
            ) + updated.historyItems
        )
        updateChildProfile(updated)
    }
}

fun MainViewModel.addBonusQuest(userId: String, title: String, reward: Int, description: String = "") {
    val profile = familyRepository.getChild(userId) ?: return
    val newQuest = BonusQuest(
        title = title,
        reward = reward,
        description = description,
        status = QuestStatus.AVAILABLE,
        createdDate = getCurrentDateString()
    )
    updateChildProfile(profile.copy(bonusQuests = profile.bonusQuests + newQuest))
}

fun MainViewModel.submitBonusQuestByChild(userId: String, questId: String, photoUri: Uri?, comment: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) {
            q.copy(
                status = QuestStatus.PENDING,
                rejectReason = null,
                photoUrl = photoUri?.toString() ?: q.photoUrl,
                comment = comment
            )
        } else q
    }
    if (updatedQuests != profile.bonusQuests) {
        updateChildProfile(profile.copy(bonusQuests = updatedQuests))
    }
}

fun MainViewModel.approveBonusQuest(userId: String, questId: String, customReward: Int? = null) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    val finalReward = customReward ?: target.reward
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) {
            q.copy(status = QuestStatus.READY_TO_CLAIM, rejectReason = null, reward = finalReward, photoUrl = null)
        } else q
    }
    updateChildProfile(profile.copy(bonusQuests = updatedQuests))
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}

fun MainViewModel.rejectBonusQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) {
            q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason, photoUrl = null)
        } else q
    }
    updateChildProfile(profile.copy(bonusQuests = updatedQuests))
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}

fun MainViewModel.claimBonusQuestReward(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    if (target.status == QuestStatus.READY_TO_CLAIM) {
        val updatedQuests = profile.bonusQuests.map { q ->
            if (q.id == questId) q.copy(status = QuestStatus.CLAIMED) else q
        }
        val updated = profile.copy(
            bonusQuests = updatedQuests,
            balance = profile.balance + target.reward,
            historyItems = listOf(
                HistoryData(
                    title = "Бонус-квест выполнен: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${target.reward} ₽",
                    isIncome = true,
                    category = "Бонус-квесты"
                )
            ) + profile.historyItems
        )
        updateChildProfile(updated)
    }
}

fun MainViewModel.deleteBonusQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = profile.bonusQuests.firstOrNull { it.id == questId }?.photoUrl
    updateChildProfile(profile.copy(bonusQuests = profile.bonusQuests.filterNot { it.id == questId }))
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}
