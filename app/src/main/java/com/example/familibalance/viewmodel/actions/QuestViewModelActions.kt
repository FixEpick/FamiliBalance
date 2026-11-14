package com.example.familibalance.viewmodel.actions

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.util.PhotoStorage
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
            closetComment = "",
            closetStatus = QuestStatus.READY_TO_CLAIM,
            closetRejectReason = null
        )
        "room", "комната" -> profile.copy(
            roomPhotoUrl = null,
            roomComment = "",
            roomStatus = QuestStatus.READY_TO_CLAIM,
            roomRejectReason = null
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryPhotoUrl = null,
            diaryComment = "",
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
            closetComment = "",
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = reason
        )
        "room", "комната" -> profile.copy(
            roomPhotoUrl = null,
            roomComment = "",
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = reason
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryPhotoUrl = null,
            diaryComment = "",
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
fun MainViewModel.submitQuestForReview(userId: String, questType: String, photoUrl: String?, comment: String = "") {
    val profile = familyRepository.getChild(userId) ?: return
    val persistedPhotoUrl = PhotoStorage.persistIfNeeded(getApplication(), photoUrl)
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetStatus = QuestStatus.PENDING,
            closetRejectReason = null,
            closetPhotoUrl = persistedPhotoUrl,
            closetComment = comment.trim()
        )
        "room", "комната" -> profile.copy(
            roomStatus = QuestStatus.PENDING,
            roomRejectReason = null,
            roomPhotoUrl = persistedPhotoUrl,
            roomComment = comment.trim()
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryStatus = QuestStatus.PENDING,
            diaryRejectReason = null,
            diaryPhotoUrl = persistedPhotoUrl,
            diaryComment = comment.trim()
        )
        else -> profile
    }
    updateChildProfile(updated)
}

/**
 * Ребёнок отменяет отправку квеста на проверку (из BottomSheet в истории).
 * questKey: "closet" | "room" | "diary" | "bonus:<id>" | "custom:<id>".
 * Статус возвращается в AVAILABLE, фото и причина отклонения очищаются.
 */
fun MainViewModel.cancelPendingQuest(userId: String, questKey: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val key = questKey.lowercase()

    when {
        key.startsWith("bonus:") -> {
            val questId = key.removePrefix("bonus:")
            val photoUrlToDelete = profile.bonusQuests.firstOrNull { it.id == questId }?.photoUrl
            val updatedQuests = profile.bonusQuests.map { q ->
                if (q.id == questId && q.status == QuestStatus.PENDING) {
                    q.copy(status = QuestStatus.AVAILABLE, photoUrl = null, rejectReason = null)
                } else q
            }
            if (updatedQuests != profile.bonusQuests) {
                updateChildProfile(profile.copy(bonusQuests = updatedQuests))
                if (!photoUrlToDelete.isNullOrBlank()) {
                    viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
                }
            }
        }
        key.startsWith("custom:") -> {
            val questId = key.removePrefix("custom:")
            val photoUrlToDelete = profile.customQuests.firstOrNull { it.id == questId }?.photoUrl
            val updatedQuests = profile.customQuests.map { q ->
                if (q.id == questId && q.status == QuestStatus.PENDING) {
                    q.copy(status = QuestStatus.AVAILABLE, photoUrl = null, rejectReason = null)
                } else q
            }
            if (updatedQuests != profile.customQuests) {
                updateChildProfile(profile.copy(customQuests = updatedQuests))
                if (!photoUrlToDelete.isNullOrBlank()) {
                    viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
                }
            }
        }
        else -> {
            val photoUrlToDelete = when (key) {
                "closet", "шкаф" -> profile.closetPhotoUrl.takeIf { profile.closetStatus == QuestStatus.PENDING }
                "room", "комната" -> profile.roomPhotoUrl.takeIf { profile.roomStatus == QuestStatus.PENDING }
                "diary", "дневник", "итоги" -> profile.diaryPhotoUrl.takeIf { profile.diaryStatus == QuestStatus.PENDING }
                else -> null
            }
            val updated = when (key) {
                "closet", "шкаф" -> profile.copy(
                    closetStatus = QuestStatus.AVAILABLE,
                    closetPhotoUrl = null,
                    closetComment = "",
                    closetRejectReason = null
                )
                "room", "комната" -> profile.copy(
                    roomStatus = QuestStatus.AVAILABLE,
                    roomPhotoUrl = null,
                    roomComment = "",
                    roomRejectReason = null
                )
                "diary", "дневник", "итоги" -> profile.copy(
                    diaryStatus = QuestStatus.AVAILABLE,
                    diaryPhotoUrl = null,
                    diaryComment = "",
                    diaryRejectReason = null
                )
                else -> profile
            }
            if (updated != profile) {
                clearQuestPhotoIfNeeded(updated, key, photoUrlToDelete)
                updateChildProfile(updated)
            }
        }
    }
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
    val persistedPhotoUrl = PhotoStorage.persistIfNeeded(getApplication(), photoUri?.toString())
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) {
            q.copy(
                status = QuestStatus.PENDING,
                rejectReason = null,
                photoUrl = persistedPhotoUrl ?: q.photoUrl,
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
