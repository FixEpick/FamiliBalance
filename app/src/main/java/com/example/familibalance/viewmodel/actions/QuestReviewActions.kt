package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import kotlinx.coroutines.launch

/** Админ одобряет квест (шкаф/комната/дневник): статус READY_TO_CLAIM. */
fun MainViewModel.adminApproveQuest(userId: String, questType: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    // Дневник одобряется только через applyDiaryGrades — он сохраняет
    // разбивку оценок (diaryCount*). Generic-approve без оценок дал бы
    // READY_TO_CLAIM с diaryApprovedReward=100 и пустой историей — блокируем.
    if (questType.lowercase() in listOf("diary", "дневник", "итоги") &&
        profile.diaryCount5 + profile.diaryCount4 +
        profile.diaryCount3 + profile.diaryCount2 == 0
    ) return
    val photoUrlToDelete = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.closetPhotoUrl
        "room", "комната" -> profile.roomPhotoUrl
        "diary", "дневник", "итоги" -> profile.diaryPhotoUrl
        else -> null
    }
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetStatus = QuestStatus.READY_TO_CLAIM,
            closetComment = "",
            closetPhotoUrl = null,
            closetRejectReason = null
        )
        "room", "комната" -> profile.copy(
            roomStatus = QuestStatus.READY_TO_CLAIM,
            roomComment = "",
            roomPhotoUrl = null,
            roomRejectReason = null
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryComment = "",
            diaryPhotoUrl = null,
            diaryRejectReason = null
        )
        else -> profile
    }
    updateChildProfile(updated)
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
    } finally {
        endProcessingAction()
    }
}

/** Админ отклоняет квест (шкаф/комната/дневник): статус AVAILABLE + удаление фото. Запись в историю не добавляется — отклонение не является операцией со средствами. */
fun MainViewModel.adminRejectQuest(userId: String, questType: String, reason: String, questTitle: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val photoUrlToDelete = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.closetPhotoUrl
        "room", "комната" -> profile.roomPhotoUrl
        "diary", "дневник", "итоги" -> profile.diaryPhotoUrl
        else -> null
    }
    val updated = when (questType.lowercase()) {
        "closet", "шкаф" -> profile.copy(
            closetStatus = QuestStatus.AVAILABLE,
            closetComment = "",
            closetPhotoUrl = null,
            closetRejectReason = reason
        )
        "room", "комната" -> profile.copy(
            roomStatus = QuestStatus.AVAILABLE,
            roomComment = "",
            roomPhotoUrl = null,
            roomRejectReason = reason
        )
        "diary", "дневник", "итоги" -> profile.copy(
            diaryStatus = QuestStatus.AVAILABLE,
            diaryComment = "",
            diaryPhotoUrl = null,
            diaryRejectReason = reason,
            diaryCount5 = 0,
            diaryCount4 = 0,
            diaryCount3 = 0,
            diaryCount2 = 0
        )
        else -> profile
    }
    updateChildProfile(updated)
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
}

/**
 * Админ утверждает оценки дневника: статус READY_TO_CLAIM, оценки
 * сохраняются в профиле (diaryCount5/4/3/2). Баланс, история, fivesCount
 * и штрафы применяются только когда ребёнок нажмёт «Забрать»
 * (claimQuestReward → diary-ветка) — как у шкафа и комнаты.
 */
fun MainViewModel.applyDiaryGrades(childId: String, count5: Int, count4: Int, count3: Int, count2: Int) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(childId) ?: return
    val c5 = count5.coerceAtLeast(0)
    val c4 = count4.coerceAtLeast(0)
    val c3 = count3.coerceAtLeast(0)
    val c2 = count2.coerceAtLeast(0)
    // Положительные оценки (5→50, 4→30) — в diaryApprovedReward;
    // штрафные (3, 2) применятся штрафами при получении награды.
    val netReward = 50 * c5 + 30 * c4

    val photoUrlToDelete = profile.diaryPhotoUrl
    updateChildProfile(
        profile.copy(
            diaryStatus = QuestStatus.READY_TO_CLAIM,
            diaryComment = "",
            diaryPhotoUrl = null,
            diaryRejectReason = null,
            diaryApprovedReward = netReward,
            diaryCount5 = c5,
            diaryCount4 = c4,
            diaryCount3 = c3,
            diaryCount2 = c2
        )
    )
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
    } finally {
        endProcessingAction()
    }
}

/** Админ одобряет бонус-квест: READY_TO_CLAIM + запись в историю. */
fun MainViewModel.adminApproveBonusQuest(userId: String, questId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.READY_TO_CLAIM, rejectReason = null, photoUrl = null) else q
    }
    updateChildProfile(
        profile.copy(
            bonusQuests = updatedQuests,
            historyItems = listOf(
                HistoryData(
                    title = "Одобрен бонус-квест: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${target.reward} ₽",
                    isIncome = true,
                    category = "Бонус-квесты",
                    type = HistoryType.BONUS_QUEST_APPROVED,
                    amountValue = target.reward,
                    arg = target.title
                )
            ) + profile.historyItems
        )
    )
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
    } finally {
        endProcessingAction()
    }
}

/** Админ отклоняет бонус-квест: AVAILABLE + причина + удаление фото (без записи в историю). */
fun MainViewModel.adminRejectBonusQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason, photoUrl = null) else q
    }
    updateChildProfile(profile.copy(bonusQuests = updatedQuests))
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
}

/** Админ одобряет кастомный квест: READY_TO_CLAIM + запись в историю + удаление фото. */
fun MainViewModel.adminApproveCustomQuest(userId: String, questId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.customQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.customQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.READY_TO_CLAIM, rejectReason = null, photoUrl = null) else q
    }
    updateChildProfile(
        profile.copy(
            customQuests = updatedQuests,
            historyItems = listOf(
                HistoryData(
                    title = "Одобрен квест: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${target.reward} ₽",
                    isIncome = true,
                    category = "Кастомные квесты",
                    type = HistoryType.QUEST_APPROVED,
                    amountValue = target.reward,
                    arg = target.title
                )
            ) + profile.historyItems
        )
    )
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
    } finally {
        endProcessingAction()
    }
}

/** Админ отклоняет кастомный квест: AVAILABLE + причина + удаление фото (без записи в историю). */
fun MainViewModel.adminRejectCustomQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.customQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.customQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason, photoUrl = null) else q
    }
    updateChildProfile(profile.copy(customQuests = updatedQuests))
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch {
            SupabaseManager.deleteQuestPhoto(photoUrlToDelete)
        }
    }
}

/** Админ одобряет честное признание. */
fun MainViewModel.adminApproveHonestAdmission(userId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            honestAdmissionStatus = QuestStatus.APPROVED,
            historyItems = listOf(
                HistoryData(
                    title = "Честное признание одобрено",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "Скидка 50%",
                    isIncome = true,
                    category = "Достижения",
                    type = HistoryType.HONEST_APPROVED,
                    amountValue = null
                )
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}

/** Админ отклоняет честное признание (без записи в историю — отклонение не операция со средствами). */
fun MainViewModel.adminRejectHonestAdmission(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(honestAdmissionStatus = QuestStatus.AVAILABLE)
    )
}

/** Админ удаляет встроенный квест (шкаф/комната/дневник). */
fun MainViewModel.deleteBuiltInQuest(userId: String, kind: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val updated = when (kind) {
        "wardrobe", "closet", "шкаф" -> profile.copy(isWardrobeDeleted = true)
        "room", "комната" -> profile.copy(isRoomDeleted = true)
        "diary", "дневник" -> profile.copy(isDiaryDeleted = true)
        else -> profile
    }
    updateChildProfile(updated)
}

/** Админ редактирует встроенный квест (название/описание/награда). */
fun MainViewModel.updateBuiltInQuest(userId: String, kind: String, title: String, description: String, reward: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    val updated = when (kind) {
        "wardrobe", "closet", "шкаф" -> profile.copy(
            wardrobeTitle = title,
            wardrobeDesc = description,
            wardrobeReward = reward
        )
        "room", "комната" -> profile.copy(
            roomTitle = title,
            roomDesc = description,
            roomReward = reward
        )
        "diary", "дневник" -> profile.copy(
            diaryTitle = title,
            diaryDesc = description
        )
        else -> profile
    }
    updateChildProfile(updated)
}

/** Админ меняет частоту встроенного квеста (шкаф/комната/дневник): DAILY / WEEKLY. */
fun MainViewModel.updateBuiltInQuestFrequency(userId: String, kind: String, frequency: QuestFrequency) {
    val profile = familyRepository.getChild(userId) ?: return
    val updated = when (kind) {
        "wardrobe", "closet", "шкаф" -> profile.copy(wardrobeFrequency = frequency)
        "room", "комната" -> profile.copy(roomFrequency = frequency)
        "diary", "дневник" -> profile.copy(diaryFrequency = frequency)
        else -> profile
    }
    updateChildProfile(updated)
}
