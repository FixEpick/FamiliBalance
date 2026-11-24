package com.example.familibalance.viewmodel.actions

import android.net.Uri
import android.util.Log
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
    // Пуш родителю о новом фотоотчёте (только для валидных типов квестов).
    if (updated != profile) {
        val (reportTitle, reportReward) = when (questType.lowercase()) {
            "closet", "шкаф" -> profile.wardrobeTitle to profile.wardrobeReward
            "room", "комната" -> profile.roomTitle to profile.roomReward
            else -> profile.diaryTitle to profile.diaryApprovedReward
        }
        PushNotifier.photoReportSubmitted(profile.parentUserId, profile.name, reportTitle, reportReward, RewardCurrency.symbolOf(profile.rewardCurrency))
    }
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
                    PhotoStorage.deleteLocalFileIfPresent(getApplication(), photoUrlToDelete)
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
                    PhotoStorage.deleteLocalFileIfPresent(getApplication(), photoUrlToDelete)
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
                PhotoStorage.deleteLocalFileIfPresent(getApplication(), photoUrlToDelete)
                clearQuestPhotoIfNeeded(updated, key, photoUrlToDelete)
                updateChildProfile(updated)
            }
        }
    }
}

fun MainViewModel.claimQuestReward(userId: String, questType: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    var updated = profile
    var rewardAmount = 50
    var questTitle = ""
    // Для дневника записи в историю уже добавлены по каждой оценке
    // (gradeEntries) — сводная запись «Награда получена» не нужна,
    // иначе в истории появляется лишний дубль суммы.
    var addRewardHistoryEntry = true
    // Штрафные оценки дневника (3 и 2) — применяются после updateChildProfile,
    // когда статус уже CLAIMED (applyFineInternal читает свежий профиль).
    var diaryFine3 = 0
    var diaryFine2 = 0
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
                val c5 = profile.diaryCount5
                val c4 = profile.diaryCount4
                diaryFine3 = profile.diaryCount3
                diaryFine2 = profile.diaryCount2
                // Записи по оценкам — как раньше при мгновенном применении.
                val time = getCurrentTimeString()
                val date = getCurrentDateString()
                val gradeEntries = mutableListOf<HistoryData>()
                // Прозрачная расшифровка расчёта: «5×1 (+50), 4×1 (+30), 3×1 (-20)».
                // Ребёнок видит в истории, из чего сложилась награда за дневник.
                val formula = buildList {
                    if (c5 > 0) add("5×$c5 (+50)")
                    if (c4 > 0) add("4×$c4 (+30)")
                    if (profile.diaryCount3 > 0) add("3×${profile.diaryCount3} (-20)")
                    if (profile.diaryCount2 > 0) add("2×${profile.diaryCount2} (-50)")
                }.joinToString(", ")
                if (formula.isNotEmpty()) {
                    // Сводная запись — только расшифровка формулы, без суммы:
                    // amountValue = null, иначе сумма historyItems разойдётся
                    // с дельтой баланса (инвариант сверки в тестах/БД).
                    gradeEntries += HistoryData(
                        "Итоги дневника", time, date, "", true, "Дневник",
                        type = HistoryType.DIARY_SUMMARY, arg = formula
                    )
                }
                repeat(c5) {
                    gradeEntries += HistoryData("Оценка 5", time, date, "+50 ₽", true, "Дневник", type = HistoryType.DIARY_GRADE_5, amountValue = 50)
                }
                repeat(c4) {
                    gradeEntries += HistoryData("Оценка 4", time, date, "+30 ₽", true, "Дневник", type = HistoryType.DIARY_GRADE_4, amountValue = 30)
                }
                updated = updated.copy(
                    diaryStatus = QuestStatus.CLAIMED,
                    diaryCount5 = 0,
                    diaryCount4 = 0,
                    diaryCount3 = 0,
                    diaryCount2 = 0,
                    fivesCount = updated.fivesCount + c5,
                    historyItems = gradeEntries + updated.historyItems
                )
                questTitle = "📚 Итоги недели (Дневник)"
                rewardAmount = profile.diaryApprovedReward
                addRewardHistoryEntry = false
            }
        }
    }
    if (questTitle.isNotEmpty()) {
        updated = updated.copy(balance = updated.balance + rewardAmount)
        if (addRewardHistoryEntry) {
            updated = updated.copy(
                historyItems = listOf(
                    HistoryData(
                        title = "Награда получена: $questTitle",
                        time = getCurrentTimeString(),
                        date = getCurrentDateString(),
                        amount = "+$rewardAmount ₽",
                        isIncome = true,
                        category = "Квесты",
                        type = HistoryType.QUEST_REWARD,
                        amountValue = rewardAmount,
                        arg = questTitle
                    )
                ) + updated.historyItems
            )
        }
        updateChildProfile(updated)
        // Штрафные оценки дневника — после фиксации CLAIMED.
        repeat(diaryFine3) { applyFineInternal(userId, 20, "Оценка 3 (дневник)") }
        repeat(diaryFine2) { applyFineInternal(userId, 50, "Оценка 2 (дневник)") }
    }
    } finally {
        endProcessingAction()
    }
}

fun MainViewModel.addBonusQuest(userId: String, title: String, reward: Int, description: String = "", requiresPhoto: Boolean = true) {
    val profile = familyRepository.getChild(userId) ?: return
    val newQuest = BonusQuest(
        title = title,
        reward = reward,
        description = description,
        status = QuestStatus.AVAILABLE,
        createdDate = getCurrentDateString(),
        requiresPhoto = requiresPhoto
    )
    updateChildProfile(profile.copy(bonusQuests = profile.bonusQuests + newQuest))
    PushNotifier.bonusQuestAdded(userId, title.trim(), reward, RewardCurrency.symbolOf(profile.rewardCurrency))
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
        val bq = profile.bonusQuests.firstOrNull { it.id == questId }
        PushNotifier.photoReportSubmitted(
            profile.parentUserId, profile.name,
            bq?.title ?: "", bq?.reward ?: 0,
            RewardCurrency.symbolOf(profile.rewardCurrency)
        )
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
    if (!tryStartProcessingAction()) return
    try {
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
                    category = "Бонус-квесты",
                    type = HistoryType.BONUS_QUEST_DONE,
                    amountValue = target.reward,
                    arg = target.title
                )
            ) + profile.historyItems
        )
        updateChildProfile(updated)
    }
    } finally {
        endProcessingAction()
    }
}

fun MainViewModel.deleteBonusQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = profile.bonusQuests.firstOrNull { it.id == questId }?.photoUrl
    updateChildProfile(profile.copy(bonusQuests = profile.bonusQuests.filterNot { it.id == questId }))
    if (!photoUrlToDelete.isNullOrBlank()) {
        PhotoStorage.deleteLocalFileIfPresent(getApplication(), photoUrlToDelete)
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}
