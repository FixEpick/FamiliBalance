package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import java.time.LocalDate

fun MainViewModel.checkInStreak(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val today = LocalDate.now()
    val todayString = today.toString()
    if (profile.lastStreakDate != todayString) {
        // Если с прошлого чек-ина прошёл больше суток — стрик прерывается
        val lastDate = runCatching { LocalDate.parse(profile.lastStreakDate) }.getOrNull()
        val yesterday = today.minusDays(1)
        if (lastDate == null || lastDate.isBefore(yesterday)) {
            profile.streakDays = 0
        }
        profile.streakDays += 1
        profile.lastStreakDate = todayString
        if (profile.streakDays > 0 && profile.streakDays % 7 == 0) {
            profile.balance += 50
            profile.historyItems.add(
                0,
                HistoryData(
                    title = "🔥 Бонус за 7 дней без штрафов",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+50 ₽",
                    isIncome = true,
                    category = "Бонусы"
                )
            )
            profile.streakBonusMessage = "🎉 ${profile.streakDays} дней без штрафов! Бонус +50 ₽"
        }
        updateChildProfile(profile)
    }
}

fun MainViewModel.resetStreak(child: ChildProfileState) {
    child.streakDays = 0
    child.lastStreakDate = ""
    saveState()
    familyRepository.pushChildProfile(child)
}

fun MainViewModel.clearHistory(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.historyItems.clear()
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.resetAchievements(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.boughtMinutesToday = 0
    profile.dreamTitle = ""
    profile.dreamTarget = 0
    profile.fivesCount = 0
    profile.wardrobeCount = 0
    profile.roomCount = 0
    profile.achievements.forEachIndexed { index, ach ->
        profile.achievements[index] = ach.copy(isClaimed = false)
    }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.addAchievement(userId: String, title: String, description: String, targetCount: Int, reward: Int, branch: AchievementBranch) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.achievements.add(
        AchievementConfig(
            title = title,
            description = description,
            targetCount = targetCount,
            reward = reward,
            branch = branch
        )
    )
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.updateAchievement(userId: String, achievementId: String, title: String, description: String, targetCount: Int, reward: Int, branch: AchievementBranch) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.achievements.indexOfFirst { it.id == achievementId }
    if (idx != -1) {
        profile.achievements[idx] = profile.achievements[idx].copy(
            title = title,
            description = description,
            targetCount = targetCount,
            reward = reward,
            branch = branch
        )
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.deleteAchievement(userId: String, achievementId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.achievements.removeIf { it.id == achievementId }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.toggleAchievementVisibility(userId: String, achievementId: String, isVisible: Boolean) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.achievements.indexOfFirst { it.id == achievementId }
    if (idx != -1) {
        profile.achievements[idx] = profile.achievements[idx].copy(isVisible = isVisible)
        saveState()
        familyRepository.pushChildProfile(profile)
    }
}

fun MainViewModel.claimAchievementReward(userId: String, achievementId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val idx = profile.achievements.indexOfFirst { it.id == achievementId }
    if (idx != -1) {
        val ach = profile.achievements[idx]
        if (!ach.isClaimed && !isAchievementLocked(profile, ach)) {
            profile.achievements[idx] = ach.copy(isClaimed = true)
            profile.balance += ach.reward
            profile.historyItems.add(
                0,
                HistoryData(
                    title = "Достижение открыто: ${ach.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${ach.reward} ₽",
                    isIncome = true,
                    category = "Достижения"
                )
            )
            updateChildProfile(profile)
        }
    }
}

fun MainViewModel.resetActiveQuests(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.closetStatus = QuestStatus.AVAILABLE
    profile.closetRejectReason = null
    profile.closetPhotoUrl = null
    profile.roomStatus = QuestStatus.AVAILABLE
    profile.roomRejectReason = null
    profile.roomPhotoUrl = null
    profile.diaryStatus = QuestStatus.AVAILABLE
    profile.diaryRejectReason = null
    profile.diaryPhotoUrl = null

    profile.customQuests.forEachIndexed { index, quest ->
        // ONCE-квесты не сбрасываем — они выполняются один раз
        if (quest.isVisible && quest.frequency != QuestFrequency.ONCE) {
            profile.customQuests[index] = quest.copy(
                status = QuestStatus.AVAILABLE,
                photoUrl = null,
                rejectReason = null
            )
        }
    }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.resetStreakAndTime(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.streakDays = 0
    profile.lastStreakDate = ""
    profile.boughtMinutesToday = 0
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.resetBalance(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.balance = 0
    updateChildProfile(profile)
}

fun MainViewModel.resetAllChildData(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    profile.balance = 0
    profile.isWardrobeDeleted = false
    profile.isRoomDeleted = false
    profile.isDiaryDeleted = false
    profile.wardrobeTitle = "Фото шкафа"
    profile.wardrobeDesc = "Наведи идеальный порядок на полках и сфотографируй результат для папы"
    profile.wardrobeReward = 50
    profile.roomTitle = "Фото комнаты"
    profile.roomDesc = "Заправь кровать без складок, собери игрушки и пришли фото"
    profile.roomReward = 50
    profile.diaryTitle = "📚 Дневник"
    profile.diaryDesc = "Квест выполняется раз в неделю. Папа проверяет все оценки в дневнике за прошедшую неделю."
    profile.cashoutRequests = emptyList()
    profile.bonusQuests.clear()
    clearHistory(userId)
    profile.achievements.clear()
    profile.achievements.addAll(defaultAchievements())
    resetAchievements(userId)
    resetActiveQuests(userId)
    resetStreakAndTime(userId)
    saveState()
    familyRepository.pushChildProfile(profile)
}
