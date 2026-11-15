package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import kotlinx.coroutines.launch

/** Админ одобряет квест (шкаф/комната/дневник): статус READY_TO_CLAIM. */
fun MainViewModel.adminApproveQuest(userId: String, questType: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
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

/** Админ отклоняет квест (шкаф/комната/дневник): статус AVAILABLE + запись в историю. */
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
            diaryRejectReason = reason
        )
        else -> profile
    }
    updateChildProfile(
        updated.copy(
            historyItems = listOf(
                HistoryData(
                    title = "Отклонен квест: $questTitle",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "0 ₽",
                    isIncome = false,
                    category = "Квесты"
                )
            ) + updated.historyItems
        )
    )
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }
}

/**
 * Применяет дневник по оценкам: каждая оценка — отдельная запись в истории.
 * Положительные оценки (5, 4) начисляются сразу; штрафные (3, 2) проходят
 * через [applyFine] — со скидкой за честное признание и сбросом стрика.
 * Статус дневника сразу становится CLAIMED — награда уже применена.
 */
fun MainViewModel.applyDiaryGrades(childId: String, count5: Int, count4: Int, count3: Int, count2: Int) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(childId) ?: return
    val time = getCurrentTimeString()
    val date = getCurrentDateString()

    var balance = profile.balance
    var netReward = 0
    val entries = mutableListOf<HistoryData>()

    fun addGrade(grade: Int, points: Int, count: Int) {
        repeat(count.coerceAtLeast(0)) {
            balance += points
            netReward += points
            entries += HistoryData(
                title = "Оценка $grade",
                time = time,
                date = date,
                amount = if (points >= 0) "+$points ₽" else "$points ₽",
                isIncome = points >= 0,
                category = "Дневник"
            )
        }
    }

    addGrade(5, 50, count5)
    addGrade(4, 30, count4)

    val photoUrlToDelete = profile.diaryPhotoUrl
    updateChildProfile(
        profile.copy(
            balance = balance,
            diaryStatus = QuestStatus.CLAIMED,
            diaryComment = "",
            diaryPhotoUrl = null,
            diaryRejectReason = null,
            diaryApprovedReward = netReward,
            fivesCount = profile.fivesCount + count5.coerceAtLeast(0),
            historyItems = entries + profile.historyItems
        )
    )
    if (!photoUrlToDelete.isNullOrBlank()) {
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(photoUrlToDelete) }
    }

    repeat(count3.coerceAtLeast(0)) { applyFineInternal(childId, 20, "Оценка 3 (дневник)") }
    repeat(count2.coerceAtLeast(0)) { applyFineInternal(childId, 50, "Оценка 2 (дневник)") }
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
                    category = "Бонус-квесты"
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

/** Админ отклоняет бонус-квест: AVAILABLE + причина + запись в историю. */
fun MainViewModel.adminRejectBonusQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.bonusQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.bonusQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason, photoUrl = null) else q
    }
    updateChildProfile(
        profile.copy(
            bonusQuests = updatedQuests,
            historyItems = listOf(
                HistoryData(
                    title = "Отклонен бонус-квест: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "0 ₽",
                    isIncome = false,
                    category = "Бонус-квесты"
                )
            ) + profile.historyItems
        )
    )
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
                    category = "Кастомные квесты"
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

/** Админ отклоняет кастомный квест: AVAILABLE + причина + запись в историю + удаление фото. */
fun MainViewModel.adminRejectCustomQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.customQuests.firstOrNull { it.id == questId } ?: return
    val photoUrlToDelete = target.photoUrl
    val updatedQuests = profile.customQuests.map { q ->
        if (q.id == questId) q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason, photoUrl = null) else q
    }
    updateChildProfile(
        profile.copy(
            customQuests = updatedQuests,
            historyItems = listOf(
                HistoryData(
                    title = "Отклонен квест: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "0 ₽",
                    isIncome = false,
                    category = "Кастомные квесты"
                )
            ) + profile.historyItems
        )
    )
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
                    category = "Достижения"
                )
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}

/** Админ отклоняет честное признание. */
fun MainViewModel.adminRejectHonestAdmission(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            honestAdmissionStatus = QuestStatus.AVAILABLE,
            historyItems = listOf(
                HistoryData(
                    title = "Честное признание отклонено",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "0 ₽",
                    isIncome = false,
                    category = "Достижения"
                )
            ) + profile.historyItems
        )
    )
}

/** Админ начисляет сумму на баланс с описанием. */
fun MainViewModel.adminDeposit(userId: String, amount: Int, description: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val title = if (description.isNotBlank()) "Пополнение: ${description.trim()}" else "Пополнение от папы"
    updateChildProfile(
        profile.copy(
            balance = profile.balance + amount,
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "+$amount ₽", true, "Квесты")
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}

/**
 * Штраф: если есть одобренное честное признание (APPROVED) — сумма делится
 * пополам, статус становится CLAIMED и скидка больше не применяется повторно.
 * Любой штраф обнуляет стрик. Обычные списания используйте [adminWithdraw].
 */
fun MainViewModel.applyFine(childId: String, amount: Int, reason: String) {
    if (!tryStartProcessingAction()) return
    try {
        applyFineInternal(childId, amount, reason)
    } finally {
        endProcessingAction()
    }
}

/** Тело штрафа без guard'а — вызывается из applyDiaryGrades, который уже держит слот. */
private fun MainViewModel.applyFineInternal(childId: String, amount: Int, reason: String) {
    val profile = familyRepository.getChild(childId) ?: return
    val discounted = profile.honestAdmissionStatus == QuestStatus.APPROVED
    val finalAmount = if (discounted) amount / 2 else amount
    val baseTitle = if (reason.isNotBlank()) "Штраф: ${reason.trim()}" else "Штраф от папы"
    val title = if (discounted) "$baseTitle (Скидка 50% за честное признание)" else baseTitle
    updateChildProfile(
        profile.copy(
            balance = (profile.balance - finalAmount).coerceAtLeast(0),
            streakDays = 0,
            lastStreakDate = "",
            honestAdmissionStatus = if (discounted) QuestStatus.CLAIMED else profile.honestAdmissionStatus,
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "-$finalAmount ₽", false, "Штрафы")
            ) + profile.historyItems
        )
    )
}

/** Админ списывает сумму с баланса (не ниже 0). Обычное списание — стрик не трогает. */
fun MainViewModel.adminWithdraw(userId: String, amount: Int, description: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val title = if (description.isNotBlank()) "Списание: ${description.trim()}" else "Списание от папы"
    updateChildProfile(
        profile.copy(
            balance = (profile.balance - amount).coerceAtLeast(0),
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "-$amount ₽", false, "Штрафы")
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}

/** Админ выдаёт наличные: обнуляет баланс, гасит все заявки на вывод и пишет в историю. */
fun MainViewModel.adminCashoutAll(userId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val amount = profile.balance
    if (amount > 0) {
        updateChildProfile(
            profile.copy(
                balance = 0,
                // Заявки на вывод гасим: после обнуления баланса их нельзя
                // ни одобрить (amount > balance), ни оставить — они бы блокировали
                // новые заявки (availableBalance ушёл бы в минус).
                cashoutRequests = emptyList(),
                historyItems = listOf(
                    HistoryData(
                        title = "Выдача наличных папой (${profile.name})",
                        time = getCurrentTimeString(),
                        date = getCurrentDateString(),
                        amount = "-$amount ₽",
                        isIncome = false,
                        category = "Вывод средств"
                    )
                ) + profile.historyItems
            )
        )
    } else if (profile.cashoutRequests.isNotEmpty()) {
        // Баланс уже 0, но висят заявки — просто очищаем их.
        updateChildProfile(profile.copy(cashoutRequests = emptyList()))
    }
    } finally {
        endProcessingAction()
    }
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
