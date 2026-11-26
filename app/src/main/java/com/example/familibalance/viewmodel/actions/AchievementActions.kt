package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.ui.theme.AllAppThemes
import com.example.familibalance.ui.theme.AvatarFrameType
import com.example.familibalance.ui.theme.DefaultUnlockedFrameIds
import com.example.familibalance.ui.theme.DefaultUnlockedThemeIds
import com.example.familibalance.ui.theme.ThemeUnlockRule
import com.example.familibalance.ui.theme.CloudAvatarType
import com.example.familibalance.ui.theme.achievementAvatarIds
import com.example.familibalance.ui.theme.achievementThemeIds
import com.example.familibalance.ui.theme.cloudAvatarTypeOf
import com.example.familibalance.ui.theme.isFrameUnlocked
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

/**
 * Возвращает профиль с темами, открытыми заявленными достижениями:
 * для каждого ThemeUnlockRule.Achievement, чьё достижение isClaimed,
 * id темы добавляется в unlockedThemes (идемпотентно).
 */
internal fun ChildProfileState.withAchievementThemesUnlocked(): ChildProfileState {
    val claimedTitles = achievements
        .filter { it.isClaimed }
        .map { it.title }
        .toSet()
    val earned = achievementThemeIds(claimedTitles)
    val earnedAvatars = achievementAvatarIds(claimedTitles)
    if (earned.isEmpty() && earnedAvatars.isEmpty()) return this
    val missing = earned.filterNot { it in unlockedThemes }
    val missingAvatars = earnedAvatars.filterNot { it in unlockedAvatars }
    if (missing.isEmpty() && missingAvatars.isEmpty()) return this
    return copy(
        unlockedThemes = if (missing.isEmpty()) unlockedThemes else unlockedThemes + missing,
        unlockedAvatars = if (missingAvatars.isEmpty()) unlockedAvatars else unlockedAvatars + missingAvatars
    )
}

/**
 * Разовая сверка при загрузке/синхронизации: если достижение было
 * заявлено раньше, связанная тема дооткрывается в unlockedThemes.
 */
fun MainViewModel.syncAchievementThemesOnLoad() {
    var changed = false
    AppSharedState.children.replaceAll { _, child ->
        val synced = child.withAchievementThemesUnlocked()
        if (synced != child) {
            changed = true
            // Локальная мутация: бампаем версию, чтобы LWW при синхронизации
            // не счёл этот снапшот устаревшим и не откатил чужие правки.
            synced.copy(
                stateUpdatedAt = System.currentTimeMillis(),
                stateVersion = synced.stateVersion + 1
            )
        } else child
    }
    if (changed) {
        familyRepository.saveAndSync()
        emitFamilyState()
    }
}

/**
 * Покупка темы за монеты: проверяет баланс, списывает цену,
 * добавляет themeId в unlockedThemes и сразу выбирает её.
 * Запись в историю — тип THEME_PURCHASE, arg = themeId.
 * @return true при успешной покупке/выборе уже купленной темы.
 */
fun MainViewModel.purchaseTheme(userId: String, themeId: String): Boolean {
    val profile = familyRepository.getChild(userId) ?: return false
    val theme = AllAppThemes.firstOrNull { it.id == themeId } ?: return false
    val rule = theme.unlockRule as? ThemeUnlockRule.Paid ?: return false
    if (themeId in profile.unlockedThemes) {
        // Уже куплена — просто применяем.
        updateChildProfile(profile.copy(selectedThemeId = themeId))
        return true
    }
    if (profile.balance < rule.price) return false
    val updated = profile.copy(
        balance = profile.balance - rule.price,
        unlockedThemes = profile.unlockedThemes + themeId,
        selectedThemeId = themeId,
        historyItems = listOf(
            HistoryData(
                title = "Покупка темы: ${theme.title}",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-${rule.price} ₽",
                isIncome = false,
                category = "Темы",
                type = HistoryType.THEME_PURCHASE,
                amountValue = -rule.price,
                arg = themeId
            )
        ) + profile.historyItems
    )
    updateChildProfile(updated)
    return true
}

/**
 * Покупка аватара за монеты: проверяет баланс, списывает цену,
 * добавляет avatarId в unlockedAvatars и сразу выбирает его.
 * Запись в историю — тип AVATAR_PURCHASE, arg = avatarId.
 * @return true при успешной покупке/выборе уже открытого аватара.
 */
fun MainViewModel.purchaseAvatar(userId: String, avatarId: String): Boolean {
    val profile = familyRepository.getChild(userId) ?: return false
    val type = CloudAvatarType.entries.firstOrNull { it.name == avatarId } ?: return false
    val rule = type.unlockRule as? ThemeUnlockRule.Paid ?: return false
    if (avatarId in profile.unlockedAvatars) {
        // Уже открыт — просто применяем.
        updateChildProfile(profile.copy(avatar = avatarId))
        return true
    }
    if (profile.balance < rule.price) return false
    val updated = profile.copy(
        balance = profile.balance - rule.price,
        unlockedAvatars = profile.unlockedAvatars + avatarId,
        avatar = avatarId,
        historyItems = listOf(
            HistoryData(
                title = "Покупка аватара: ${cloudAvatarTypeOf(avatarId).name}",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-${rule.price} ₽",
                isIncome = false,
                category = "Аватары",
                type = HistoryType.AVATAR_PURCHASE,
                amountValue = -rule.price,
                arg = avatarId
            )
        ) + profile.historyItems
    )
    updateChildProfile(updated)
    return true
}

/**
 * Выбор рамки аватара: применяется только если рамка открыта
 * (бесплатная, купленная или достигнут нужный уровень).
 */
fun MainViewModel.selectFrame(userId: String, frameId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val type = AvatarFrameType.entries.firstOrNull { it.name == frameId } ?: return
    if (!type.isFrameUnlocked(profile.unlockedFrames, profile.level)) return
    if (profile.selectedFrameId == frameId) return
    updateChildProfile(profile.copy(selectedFrameId = frameId))
}

/**
 * Покупка рамки аватара за монеты: проверяет баланс, списывает цену,
 * добавляет frameId в unlockedFrames и сразу выбирает её.
 * Запись в историю — тип FRAME_PURCHASE, arg = frameId.
 * Рамки за уровень не покупаются (открываются автоматически).
 * @return true при успешной покупке/выборе уже купленной рамки.
 */
fun MainViewModel.purchaseFrame(userId: String, frameId: String): Boolean {
    val profile = familyRepository.getChild(userId) ?: return false
    val type = AvatarFrameType.entries.firstOrNull { it.name == frameId } ?: return false
    val rule = type.unlockRule as? ThemeUnlockRule.Paid ?: return false
    if (frameId in profile.unlockedFrames) {
        // Уже куплена — просто применяем.
        updateChildProfile(profile.copy(selectedFrameId = frameId))
        return true
    }
    if (profile.balance < rule.price) return false
    val updated = profile.copy(
        balance = profile.balance - rule.price,
        unlockedFrames = profile.unlockedFrames + frameId,
        selectedFrameId = frameId,
        historyItems = listOf(
            HistoryData(
                title = "Покупка рамки: ${type.name}",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-${rule.price} ₽",
                isIncome = false,
                category = "Рамки",
                type = HistoryType.FRAME_PURCHASE,
                amountValue = -rule.price,
                arg = frameId
            )
        ) + profile.historyItems
    )
    updateChildProfile(updated)
    return true
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
        // Заявка достижения может открыть привязанную тему магазина.
        updateChildProfile(updated.withAchievementThemesUnlocked())
    }
}

/** Удаляет фото квестов профиля из Supabase Storage (best-effort, fire-and-forget). */
private fun MainViewModel.deleteQuestPhotos(urls: List<String?>) {
    urls.filterNotNull().filter { it.isNotBlank() }.forEach { url ->
        viewModelScope.launch { SupabaseManager.deleteQuestPhoto(url) }
    }
}

fun MainViewModel.resetActiveQuests(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    // Полный сброс в изначальное состояние: ВСЕ квесты → AVAILABLE,
    // включая ONCE, бонусные и «в полёте» (PENDING/READY_TO_CLAIM).
    // Даты сброса ставим на СЕГОДНЯ (а не ""): иначе DAILY/WEEKLY-квесты
    // с пустой датой при следующем checkResets снова «протухали» бы и
    // статус выполнения за текущий день/неделю не считался сброшенным.
    val today = LocalDate.now().toString()
    val updatedQuests = profile.customQuests.map { quest ->
        quest.copy(
            status = QuestStatus.AVAILABLE,
            photoUrl = null,
            rejectReason = null,
            lastResetDate = today
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
            closetResetDate = today,
            roomStatus = QuestStatus.AVAILABLE,
            roomRejectReason = null,
            roomPhotoUrl = null,
            roomComment = "",
            roomResetDate = today,
            diaryStatus = QuestStatus.AVAILABLE,
            diaryRejectReason = null,
            diaryPhotoUrl = null,
            diaryComment = "",
            diaryResetDate = today,
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
            unlockedThemes = DefaultUnlockedThemeIds
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
            baseClosetReward = 50,
            roomTitle = "Фото комнаты",
            roomDesc = "Заправь кровать без складок, собери игрушки и пришли фото",
            roomReward = 50,
            baseRoomReward = 50,
            baseDiaryFiveReward = 50,
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
