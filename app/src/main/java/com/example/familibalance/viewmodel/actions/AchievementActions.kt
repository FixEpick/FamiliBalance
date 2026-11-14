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
        val streakDays = if (lastDate == null || lastDate.isBefore(yesterday)) 1 else profile.streakDays + 1

        var updated = profile.copy(
            streakDays = streakDays,
            lastStreakDate = todayString
        )

        if (streakDays > 0 && streakDays % 7 == 0) {
            updated = updated.copy(
                balance = updated.balance + 50,
                historyItems = listOf(
                    HistoryData(
                        title = "🔥 Бонус за 7 дней без штрафов",
                        time = getCurrentTimeString(),
                        date = getCurrentDateString(),
                        amount = "+50 ₽",
                        isIncome = true,
                        category = "Бонусы"
                    )
                ) + updated.historyItems,
                streakBonusMessage = "🎉 $streakDays дней без штрафов! Бонус +50 ₽"
            )
        }
        updateChildProfile(updated)
    }
}

fun MainViewModel.resetStreak(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(streakDays = 0, lastStreakDate = ""))
}

fun MainViewModel.clearHistory(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(historyItems = emptyList()))
}

fun MainViewModel.resetAchievements(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            boughtMinutesToday = 0,
            dreamTitle = "",
            dreamTarget = 0,
            fivesCount = 0,
            wardrobeCount = 0,
            roomCount = 0,
            achievements = profile.achievements.map { it.copy(isClaimed = false) }
        )
    )
}

fun MainViewModel.addAchievement(userId: String, title: String, description: String, targetCount: Int, reward: Int, branch: AchievementBranch) {
    val profile = familyRepository.getChild(userId) ?: return
    val newAchievement = AchievementConfig(
        title = title,
        description = description,
        targetCount = targetCount,
        reward = reward,
        branch = branch
    )
    updateChildProfile(profile.copy(achievements = profile.achievements + newAchievement))
}

fun MainViewModel.updateAchievement(userId: String, achievementId: String, title: String, description: String, targetCount: Int, reward: Int, branch: AchievementBranch) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedAchievements = profile.achievements.map { ach ->
        if (ach.id == achievementId) {
            ach.copy(
                title = title,
                description = description,
                targetCount = targetCount,
                reward = reward,
                branch = branch
            )
        } else ach
    }
    if (updatedAchievements != profile.achievements) {
        updateChildProfile(profile.copy(achievements = updatedAchievements))
    }
}

fun MainViewModel.deleteAchievement(userId: String, achievementId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(achievements = profile.achievements.filterNot { it.id == achievementId }))
}

fun MainViewModel.toggleAchievementVisibility(userId: String, achievementId: String, isVisible: Boolean) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedAchievements = profile.achievements.map { ach ->
        if (ach.id == achievementId) ach.copy(isVisible = isVisible) else ach
    }
    if (updatedAchievements != profile.achievements) {
        updateChildProfile(profile.copy(achievements = updatedAchievements))
    }
}

fun MainViewModel.claimAchievementReward(userId: String, achievementId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val target = profile.achievements.firstOrNull { it.id == achievementId } ?: return
    if (!target.isClaimed && !isAchievementLocked(profile, target)) {
        val updatedAchievements = profile.achievements.map { ach ->
            if (ach.id == achievementId) ach.copy(isClaimed = true) else ach
        }
        val updated = profile.copy(
            achievements = updatedAchievements,
            balance = profile.balance + target.reward,
            historyItems = listOf(
                HistoryData(
                    title = "Достижение открыто: ${target.title}",
                    time = getCurrentTimeString(),
                    date = getCurrentDateString(),
                    amount = "+${target.reward} ₽",
                    isIncome = true,
                    category = "Достижения"
                )
            ) + profile.historyItems
        )
        updateChildProfile(updated)
    }
}

fun MainViewModel.resetActiveQuests(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val updatedQuests = profile.customQuests.map { quest ->
        // ONCE-квесты не сбрасываем — они выполняются один раз
        if (quest.isVisible && quest.frequency != QuestFrequency.ONCE) {
            quest.copy(
                status = QuestStatus.AVAILABLE,
                photoUrl = null,
                rejectReason = null
            )
        } else quest
    }
    updateChildProfile(
        profile.copy(
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = null,
            closetPhotoUrl = null,
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = null,
            roomPhotoUrl = null,
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = null,
            diaryPhotoUrl = null,
            customQuests = updatedQuests
        )
    )
}

fun MainViewModel.resetStreakAndTime(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            streakDays = 0,
            lastStreakDate = "",
            boughtMinutesToday = 0
        )
    )
}

fun MainViewModel.resetBalance(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(balance = 0))
}

fun MainViewModel.resetAllChildData(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            balance = 0,
            isWardrobeDeleted = false,
            isRoomDeleted = false,
            isDiaryDeleted = false,
            wardrobeTitle = "Фото шкафа",
            wardrobeDesc = "Наведи идеальный порядок на полках и сфотографируй результат для папы",
            wardrobeReward = 50,
            roomTitle = "Фото комнаты",
            roomDesc = "Заправь кровать без складок, собери игрушки и пришли фото",
            roomReward = 50,
            diaryTitle = "📚 Дневник",
            diaryDesc = "Квест выполняется раз в неделю. Папа проверяет все оценки в дневнике за прошедшую неделю.",
            cashoutRequests = emptyList(),
            bonusQuests = emptyList(),
            historyItems = emptyList(),
            achievements = defaultAchievements(),
            boughtMinutesToday = 0,
            dreamTitle = "",
            dreamTarget = 0,
            fivesCount = 0,
            wardrobeCount = 0,
            roomCount = 0,
            streakDays = 0,
            lastStreakDate = "",
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = null,
            closetPhotoUrl = null,
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = null,
            roomPhotoUrl = null,
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = null,
            diaryPhotoUrl = null,
            customQuests = profile.customQuests.map { quest ->
                if (quest.isVisible && quest.frequency != QuestFrequency.ONCE) {
                    quest.copy(status = QuestStatus.AVAILABLE, photoUrl = null, rejectReason = null)
                } else quest
            }
        )
    )
}
