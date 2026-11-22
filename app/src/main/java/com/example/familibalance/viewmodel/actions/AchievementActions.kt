package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate

fun MainViewModel.checkInStreak(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val today = LocalDate.now()
    val todayString = today.toString()
    if (profile.lastStreakDate != todayString) {
        // Если с прошлого чек-ина прошёл больше суток — стрик прерывается
        val lastDate = runCatching { LocalDate.parse(profile.lastStreakDate) }.getOrNull()
        // Откат времени/часового пояса: lastStreakDate «в будущем» —
        // не трогаем стрик и не регрессируем маркер даты.
        if (lastDate != null && lastDate.isAfter(today)) return
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
                        category = "Бонусы",
                        type = HistoryType.STREAK_BONUS,
                        amountValue = 50
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
            dreamSetDate = "",
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
    // Защита от прямого вызова в обход UI: награда только при достигнутом targetCount.
    val progress = when (target.branch) {
        AchievementBranch.DIARY -> profile.fivesCount
        AchievementBranch.WARDROBE -> profile.wardrobeCount
        AchievementBranch.ROOM -> profile.roomCount
    }
    if (progress < target.targetCount) return
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
                    category = "Достижения",
                    type = HistoryType.ACHIEVEMENT_UNLOCKED,
                    amountValue = target.reward,
                    arg = target.title
                )
            ) + profile.historyItems
        )
        updateChildProfile(updated)
    }
}

/** Удаляет фото квестов профиля из Supabase Storage (best-effort, fire-and-forget). */
private fun MainViewModel.deleteQuestPhotos(urls: List<String?>) {
    urls.filterNot { it.isNullOrBlank() }.forEach { url ->
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(url!!) }
    }
}

fun MainViewModel.resetActiveQuests(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    // Полный сброс в изначальное состояние: ВСЕ квесты → AVAILABLE,
    // включая ONCE, бонусные и «в полёте» (PENDING/READY_TO_CLAIM).
    val updatedQuests = profile.customQuests.map { quest ->
        quest.copy(
            status = QuestStatus.AVAILABLE,
            photoUrl = null,
            rejectReason = null,
            lastResetDate = ""
        )
    }
    val updatedBonusQuests = profile.bonusQuests.map { bq ->
        bq.copy(
            status = QuestStatus.AVAILABLE,
            photoUrl = null,
            rejectReason = null
        )
    }
    // Чистим фото всех сбрасываемых квестов из Storage.
    deleteQuestPhotos(
        listOf(profile.closetPhotoUrl, profile.roomPhotoUrl, profile.diaryPhotoUrl) +
            profile.customQuests.map { it.photoUrl } +
            profile.bonusQuests.map { it.photoUrl }
    )
    updateChildProfile(
        profile.copy(
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = null,
            closetPhotoUrl = null,
            closetComment = "",
            closetResetDate = "",
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = null,
            roomPhotoUrl = null,
            roomComment = "",
            roomResetDate = "",
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = null,
            diaryPhotoUrl = null,
            diaryComment = "",
            diaryResetDate = "",
            diaryCount5 = 0,
            diaryCount4 = 0,
            diaryCount3 = 0,
            diaryCount2 = 0,
            customQuests = updatedQuests,
            bonusQuests = updatedBonusQuests,
            pendingQuests = emptyList()
        )
    )
}

fun MainViewModel.updateTheme(userId: String, themeId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(selectedThemeId = themeId))
}

fun MainViewModel.resetDream(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(dreamTitle = "", dreamTarget = 0, dreamSetDate = "")
    )
}

fun MainViewModel.resetHonestAdmission(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            honestAdmissionStatus = QuestStatus.AVAILABLE,
            honestAdmissionText = "",
            lastHonestAdmissionDate = ""
        )
    )
}

fun MainViewModel.resetTheme(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            selectedThemeId = "COSMIC_BLUE",
            unlockedThemes = listOf("COSMIC_BLUE")
        )
    )
}

fun MainViewModel.resetStreakAndTime(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            streakDays = 0,
            lastStreakDate = "",
            streakBonusMessage = "",
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
    // Удаляем все фото квестов из Storage до обнуления профиля.
    deleteQuestPhotos(
        listOf(profile.closetPhotoUrl, profile.roomPhotoUrl, profile.diaryPhotoUrl) +
            profile.bonusQuests.map { it.photoUrl } +
            profile.customQuests.map { it.photoUrl }
    )
    updateChildProfile(
        profile.copy(
            balance = 0,
            isWardrobeDeleted = false,
            isRoomDeleted = false,
            isDiaryDeleted = false,
            wardrobeTitle = "Фото шкафа",
            wardrobeDesc = "Наведи идеальный порядок на полках и сфотографируй результат для родителя",
            wardrobeReward = 50,
            roomTitle = "Фото комнаты",
            roomDesc = "Заправь кровать без складок, собери игрушки и пришли фото",
            roomReward = 50,
            diaryTitle = "📚 Дневник",
            diaryDesc = "Квест выполняется раз в неделю. Родитель проверяет все оценки в дневнике за прошедшую неделю.",
            diaryApprovedReward = 100,
            lastDailyResetDate = "",
            cashoutRequests = emptyList(),
            bonusQuests = emptyList(),
            historyItems = emptyList(),
            achievements = defaultAchievements(),
            boughtMinutesToday = 0,
            dreamTitle = "",
            dreamTarget = 0,
            dreamSetDate = "",
            fivesCount = 0,
            wardrobeCount = 0,
            roomCount = 0,
            streakDays = 0,
            lastStreakDate = "",
            streakBonusMessage = "",
            honestAdmissionStatus = QuestStatus.AVAILABLE,
            honestAdmissionText = "",
            lastHonestAdmissionDate = "",
            pendingQuests = emptyList(),
            closetResetDate = "",
            roomResetDate = "",
            diaryResetDate = "",
            closetStatus = QuestStatus.AVAILABLE,
            closetRejectReason = null,
            closetPhotoUrl = null,
            closetComment = "",
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = null,
            roomPhotoUrl = null,
            roomComment = "",
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = null,
            diaryPhotoUrl = null,
            diaryComment = "",
            customQuests = emptyList()
        )
    )
}
