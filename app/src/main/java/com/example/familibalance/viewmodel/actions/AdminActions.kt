package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.*

/** Админ одобряет квест (шкаф/комната/дневник): статус READY_TO_CLAIM. */
fun MainViewModel.adminApproveQuest(userId: String, questType: String) {
    val profile = familyRepository.getChild(userId) ?: return
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            profile.closetStatus = QuestStatus.READY_TO_CLAIM
            profile.closetRejectReason = null
        }
        "room", "комната" -> {
            profile.roomStatus = QuestStatus.READY_TO_CLAIM
            profile.roomRejectReason = null
        }
        "diary", "дневник", "итоги" -> {
            profile.diaryStatus = QuestStatus.READY_TO_CLAIM
            profile.diaryRejectReason = null
        }
    }
    updateChildProfile(profile)
}

/** Админ отклоняет квест (шкаф/комната/дневник): статус AVAILABLE + запись в историю. */
fun MainViewModel.adminRejectQuest(userId: String, questType: String, reason: String, questTitle: String) {
    val profile = familyRepository.getChild(userId) ?: return
    when (questType.lowercase()) {
        "closet", "шкаф" -> {
            profile.closetStatus = QuestStatus.AVAILABLE
            profile.closetRejectReason = reason
        }
        "room", "комната" -> {
            profile.roomStatus = QuestStatus.AVAILABLE
            profile.roomRejectReason = reason
        }
        "diary", "дневник", "итоги" -> {
            profile.diaryStatus = QuestStatus.AVAILABLE
            profile.diaryRejectReason = reason
        }
    }
    profile.historyItems.add(
        0,
        HistoryData(
            title = "Отклонен квест: $questTitle",
            time = getCurrentTimeString(),
            date = getCurrentDateString(),
            amount = "0 ₽",
            isIncome = false,
            category = "Квесты"
        )
    )
    updateChildProfile(profile)
}

/** Админ одобряет дневник с рассчитанной наградой и числом пятёрок. */
fun MainViewModel.adminApproveDiary(userId: String, calculatedReward: Int, fivesCount: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.diaryStatus = QuestStatus.READY_TO_CLAIM
    profile.diaryRejectReason = null
    profile.diaryApprovedReward = calculatedReward
    profile.fivesCount += fivesCount
    updateChildProfile(profile)
}

/** Админ одобряет бонус-квест: READY_TO_CLAIM + запись в историю. */
fun MainViewModel.adminApproveBonusQuest(userId: String, questId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        profile.bonusQuests[index] = q.copy(status = QuestStatus.READY_TO_CLAIM, rejectReason = null)
        profile.historyItems.add(
            0,
            HistoryData(
                title = "Одобрен бонус-квест: ${q.title}",
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

/** Админ отклоняет бонус-квест: AVAILABLE + причина + запись в историю. */
fun MainViewModel.adminRejectBonusQuest(userId: String, questId: String, reason: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val index = profile.bonusQuests.indexOfFirst { it.id == questId }
    if (index != -1) {
        val q = profile.bonusQuests[index]
        profile.bonusQuests[index] = q.copy(status = QuestStatus.AVAILABLE, rejectReason = reason)
        profile.historyItems.add(
            0,
            HistoryData(
                title = "Отклонен бонус-квест: ${q.title}",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "0 ₽",
                isIncome = false,
                category = "Бонус-квесты"
            )
        )
        updateChildProfile(profile)
    }
}

/** Админ одобряет честное признание. */
fun MainViewModel.adminApproveHonestAdmission(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.honestAdmissionStatus = QuestStatus.APPROVED
    profile.historyItems.add(
        0,
        HistoryData(
            title = "Честное признание одобрено",
            time = getCurrentTimeString(),
            date = getCurrentDateString(),
            amount = "Скидка 50%",
            isIncome = true,
            category = "Достижения"
        )
    )
    updateChildProfile(profile)
}

/** Админ отклоняет честное признание. */
fun MainViewModel.adminRejectHonestAdmission(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.honestAdmissionStatus = QuestStatus.AVAILABLE
    profile.historyItems.add(
        0,
        HistoryData(
            title = "Честное признание отклонено",
            time = getCurrentTimeString(),
            date = getCurrentDateString(),
            amount = "0 ₽",
            isIncome = false,
            category = "Достижения"
        )
    )
    updateChildProfile(profile)
}

/** Админ начисляет сумму на баланс с описанием. */
fun MainViewModel.adminDeposit(userId: String, amount: Int, description: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.balance += amount
    val title = if (description.isNotBlank()) "Пополнение: ${description.trim()}" else "Пополнение от папы"
    profile.historyItems.add(
        0,
        HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "+$amount ₽", true, "Квесты")
    )
    updateChildProfile(profile)
}

/** Админ списывает сумму с баланса (не ниже 0), сбрасывает стрик. */
fun MainViewModel.adminWithdraw(userId: String, amount: Int, description: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.balance = (profile.balance - amount).coerceAtLeast(0)
    val title = if (description.isNotBlank()) "Списание: ${description.trim()}" else "Списание от папы"
    profile.historyItems.add(
        0,
        HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "-$amount ₽", false, "Штрафы")
    )
    resetStreak(profile)
    updateChildProfile(profile)
}

/** Админ выдаёт наличные: обнуляет баланс и пишет в историю. */
fun MainViewModel.adminCashoutAll(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val amount = profile.balance
    if (amount > 0) {
        profile.balance = 0
        profile.historyItems.add(
            0,
            HistoryData(
                title = "Выдача наличных папой (${profile.name})",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-$amount ₽",
                isIncome = false,
                category = "Штрафы"
            )
        )
        updateChildProfile(profile)
    }
}

/** Админ удаляет встроенный квест (шкаф/комната/дневник). */
fun MainViewModel.deleteBuiltInQuest(userId: String, kind: String) {
    val profile = familyRepository.getChild(userId) ?: return
    when (kind) {
        "wardrobe", "closet", "шкаф" -> profile.isWardrobeDeleted = true
        "room", "комната" -> profile.isRoomDeleted = true
        "diary", "дневник" -> profile.isDiaryDeleted = true
    }
    updateChildProfile(profile)
}

/** Админ редактирует встроенный квест (название/описание/награда). */
fun MainViewModel.updateBuiltInQuest(userId: String, kind: String, title: String, description: String, reward: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    when (kind) {
        "wardrobe", "closet", "шкаф" -> {
            profile.wardrobeTitle = title
            profile.wardrobeDesc = description
            profile.wardrobeReward = reward
        }
        "room", "комната" -> {
            profile.roomTitle = title
            profile.roomDesc = description
            profile.roomReward = reward
        }
        "diary", "дневник" -> {
            profile.diaryTitle = title
            profile.diaryDesc = description
        }
    }
    updateChildProfile(profile)
}
