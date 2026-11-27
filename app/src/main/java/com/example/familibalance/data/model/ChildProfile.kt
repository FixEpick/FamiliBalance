package com.example.familibalance.data.model

import com.example.familibalance.ui.theme.DefaultUnlockedAvatarIds
import com.example.familibalance.ui.theme.DefaultUnlockedFrameIds
import com.example.familibalance.ui.theme.DefaultUnlockedThemeIds
import kotlinx.serialization.Serializable

private const val MAX_HISTORY_ITEMS = 200

/**
 * Усечение истории для хранения/синхронизации.
 *
 * Денежные записи (amountValue != null) — это леджер баланса
 * (balance = balanceBase + Σ amountValue): они НЕ усекаются никогда,
 * иначе derived-баланс терял бы факты. Под лимит попадают только
 * неденежные записи (информационные: множитель, уровень без монет и т.п.).
 * Порядок записей сохраняется.
 */
internal fun trimHistoryItems(items: List<HistoryData>): List<HistoryData> {
    if (items.size <= MAX_HISTORY_ITEMS) return items
    val moneyCount = items.count { it.amountValue != null }
    val nonMoneyKeep = maxOf(0, MAX_HISTORY_ITEMS - moneyCount)
    // Последние nonMoneyKeep неденежных записей — «выжившие»
    val keptNonMoney = items
        .filter { it.amountValue == null }
        .takeLast(nonMoneyKeep)
        .toSet()
    return items.filter { it.amountValue != null || it in keptNonMoney }
}

@Serializable
data class ChildProfileState(
    val id: String,
    val name: String,
    val avatar: String,
    val balance: Int = 40,
    val boughtMinutesToday: Int = 0,
    val honestAdmissionStatus: QuestStatus = QuestStatus.AVAILABLE,
    val lastHonestAdmissionDate: String = "",
    val honestAdmissionText: String = "",
    val closetStatus: QuestStatus = QuestStatus.AVAILABLE,
    val closetRejectReason: String? = null,
    val closetPhotoUrl: String? = null,
    val closetComment: String = "",
    val roomStatus: QuestStatus = QuestStatus.AVAILABLE,
    val roomRejectReason: String? = null,
    val roomPhotoUrl: String? = null,
    val roomComment: String = "",
    val diaryStatus: QuestStatus = QuestStatus.AVAILABLE,
    val diaryRejectReason: String? = null,
    val diaryPhotoUrl: String? = null,
    val diaryComment: String = "",
    val diaryApprovedReward: Int = 100,
    /** Оценки дневника, утверждённые админом — применяются при «Забрать». */
    val diaryCount5: Int = 0,
    val diaryCount4: Int = 0,
    val diaryCount3: Int = 0,
    val diaryCount2: Int = 0,
    val wardrobeFrequency: QuestFrequency = QuestFrequency.DAILY,
    val roomFrequency: QuestFrequency = QuestFrequency.DAILY,
    val diaryFrequency: QuestFrequency = QuestFrequency.WEEKLY,
    val closetResetDate: String = "",
    val roomResetDate: String = "",
    val diaryResetDate: String = "",
    val lastDailyResetDate: String = "",
    val dreamTitle: String = "",
    val dreamTarget: Int = 0,
    /** Дата (dd.MM.yyyy) установки цели — для кулдауна смены цели (7 дней). */
    val dreamSetDate: String = "",
    val streakDays: Int = 0,
    val lastStreakDate: String = "",
    val streakBonusMessage: String = "",
    val fivesCount: Int = 0,
    val wardrobeCount: Int = 0,
    val roomCount: Int = 0,
    val achievements: List<AchievementConfig> = defaultAchievements(),
    val cashoutRequests: List<CashoutRequest> = emptyList(),
    val pendingQuests: List<PendingQuestData> = emptyList(),
    val bonusQuests: List<BonusQuest> = emptyList(),
    val customQuests: List<CustomQuest> = emptyList(),
    val historyItems: List<HistoryData> = emptyList(),
    val selectedThemeId: String = "COSMIC_BLUE",
    val unlockedThemes: List<String> = DefaultUnlockedThemeIds,
    val unlockedAvatars: List<String> = DefaultUnlockedAvatarIds,
    val isWardrobeVisible: Boolean = true,
    val isRoomVisible: Boolean = true,
    val isDiaryVisible: Boolean = true,
    val isWardrobeDeleted: Boolean = false,
    val isRoomDeleted: Boolean = false,
    val isDiaryDeleted: Boolean = false,
    val wardrobeTitle: String = "Фото шкафа",
    val wardrobeDesc: String = "Наведи идеальный порядок на полках и сфотографируй результат для родителя",
    val wardrobeReward: Int = 50,
    /** true — для квеста обязателен фотоотчёт; false — достаточно отправить на проверку. */
    val wardrobeRequiresPhoto: Boolean = true,
    val roomTitle: String = "Фото комнаты",
    val roomDesc: String = "Заправь кровать без складок, собери игрушки и пришли фото",
    val roomReward: Int = 50,
    val roomRequiresPhoto: Boolean = true,
    val diaryTitle: String = "📚 Дневник",
    val diaryDesc: String = "Квест выполняется раз в неделю. Родитель проверяет все оценки в дневнике за прошедшую неделю.",
    val diaryRequiresPhoto: Boolean = true,
    val parentUserId: String = "",
    val authUserId: String = "",
    /** Хэш PIN (salt:hex). Пустая строка — PIN ещё не установлен. */
    val pinCode: String = "",
    val parentEmail: String = "",
    /** "active" — полноценный член семьи; "pending" — ждёт подтверждения родителя. */
    val status: String = "active",
    /** ANDROID_ID устройства, с которого ребёнок последний раз входил (для будущих пушей). */
    val deviceId: String = "",
    /** Epoch ms последнего локального изменения состояния (last-writer-wins при синхронизации). */
    val stateUpdatedAt: Long = 0,
    /** Монотонный счётчик локальных изменений: +1 при каждом updateChildProfile.
     *  В отличие от stateUpdatedAt не зависит от расхождения часов устройств —
     *  основной критерий last-writer-wins при синхронизации state_json. */
    val stateVersion: Long = 0,
    /** Код валюты наград (RUB/USD/EUR/GBP/KZT/BYN/COIN) — только символ отображения, без конвертации. */
    val rewardCurrency: String = "RUB",
    /** XP текущего уровня (сброс в 0 при каждом level-up). См. GamificationEngine. */
    val xp: Long = 0L,
    /** Текущий уровень 1–50+. См. GamificationEngine. */
    val level: Int = 1,
    /** Серия «идеальных дней» подряд (шкаф + комната за один день). */
    val perfectDaysStreak: Int = 0,
    /** Дата (yyyy-MM-dd) последнего идеального дня; null — ещё не было. */
    val lastPerfectDayDate: String? = null,
    /** Выбранная рамка аватара (AvatarFrameType.name). */
    val selectedFrameId: String = "NONE",
    /** Купленные рамки аватара; рамки за уровень открываются динамически. */
    val unlockedFrames: List<String> = DefaultUnlockedFrameIds,
    /** Базовая награда за «Фото шкафа» (зеркалит wardrobeReward). */
    val baseClosetReward: Int = 50,
    /** Базовая награда за «Фото комнаты» (зеркалит roomReward). */
    val baseRoomReward: Int = 50,
    /** Базовая награда за оценку 5 в дневнике. */
    val baseDiaryFiveReward: Int = 50,
    /** false — награды без множителей уровня/серии/идеальных дней (×1.0). */
    val multipliersEnabled: Boolean = true,
    /** Шанс бонусного сундука после квеста, % (0/10/15/20). */
    val bonusChestChancePercent: Int = 15,
    /** Недельный лимит заработка монет за задания; 0 — без лимита. */
    val weeklyEarnLimit: Int = 0,
    /** Жетоны честности: +1 за каждое отправленное честное признание. */
    val honestyTokens: Int = 0,
    /** Активные (непогашенные) подарки-купоны, отправленные родителю. */
    val pendingParentGifts: List<ParentGiftItem> = emptyList(),
    /**
     * База derived-баланса: balance = balanceBase + Σ historyItems.amountValue.
     * null — база ещё не вычислена (legacy-профиль): derivedBalance откатывается
     * на хранимый balance. Вычисляется один раз в updateChildProfile и дальше
     * поддерживается инвариантом; при усечении истории сумма «выпавших»
     * денежных записей складывается в базу (см. toDto).
     */
    val balanceBase: Int? = null
) {
    constructor(
        id: String,
        name: String,
        avatar: String,
        initialBalance: Int = 40,
        initialMinutes: Int = 0
    ) : this(
        id = id,
        name = name,
        avatar = avatar,
        balance = initialBalance,
        boughtMinutesToday = initialMinutes
    )

    companion object {
        fun fromDto(dto: ChildProfileDto): ChildProfileState = ChildProfileState(
            id = dto.id,
            name = dto.name,
            avatar = dto.avatar.ifBlank { "🧒" },
            balance = dto.balance,
            boughtMinutesToday = dto.boughtMinutesToday,
            honestAdmissionStatus = dto.honestAdmissionStatus,
            lastHonestAdmissionDate = dto.lastHonestAdmissionDate,
            honestAdmissionText = dto.honestAdmissionText,
            closetStatus = dto.closetStatus,
            closetRejectReason = dto.closetRejectReason,
            closetPhotoUrl = dto.closetPhotoUrl,
            closetComment = dto.closetComment,
            roomStatus = dto.roomStatus,
            roomRejectReason = dto.roomRejectReason,
            roomPhotoUrl = dto.roomPhotoUrl,
            roomComment = dto.roomComment,
            diaryStatus = dto.diaryStatus,
            diaryRejectReason = dto.diaryRejectReason,
            diaryPhotoUrl = dto.diaryPhotoUrl,
            diaryComment = dto.diaryComment,
            diaryApprovedReward = dto.diaryApprovedReward,
            diaryCount5 = dto.diaryCount5,
            diaryCount4 = dto.diaryCount4,
            diaryCount3 = dto.diaryCount3,
            diaryCount2 = dto.diaryCount2,
            wardrobeFrequency = dto.wardrobeFrequency,
            roomFrequency = dto.roomFrequency,
            diaryFrequency = dto.diaryFrequency,
            closetResetDate = dto.closetResetDate,
            roomResetDate = dto.roomResetDate,
            diaryResetDate = dto.diaryResetDate,
            lastDailyResetDate = dto.lastDailyResetDate,
            dreamTitle = dto.dreamTitle,
            dreamTarget = dto.dreamTarget,
            dreamSetDate = dto.dreamSetDate,
            streakDays = dto.streakDays,
            lastStreakDate = dto.lastStreakDate,
            streakBonusMessage = dto.streakBonusMessage,
            fivesCount = dto.fivesCount,
            wardrobeCount = dto.wardrobeCount,
            roomCount = dto.roomCount,
            achievements = dto.achievements,
            cashoutRequests = dto.cashoutRequests,
            pendingQuests = dto.pendingQuests,
            bonusQuests = dto.bonusQuests,
            historyItems = trimHistoryItems(dto.historyItems),
            selectedThemeId = dto.selectedThemeId,
            unlockedThemes = dto.unlockedThemes,
            unlockedAvatars = dto.unlockedAvatars,
            customQuests = dto.customQuests,
            isWardrobeVisible = dto.isWardrobeVisible,
            isRoomVisible = dto.isRoomVisible,
            isDiaryVisible = dto.isDiaryVisible,
            isWardrobeDeleted = dto.isWardrobeDeleted,
            isRoomDeleted = dto.isRoomDeleted,
            isDiaryDeleted = dto.isDiaryDeleted,
            wardrobeTitle = dto.wardrobeTitle,
            wardrobeDesc = dto.wardrobeDesc,
            wardrobeReward = dto.wardrobeReward,
            wardrobeRequiresPhoto = dto.wardrobeRequiresPhoto,
            roomTitle = dto.roomTitle,
            roomDesc = dto.roomDesc,
            roomReward = dto.roomReward,
            roomRequiresPhoto = dto.roomRequiresPhoto,
            diaryTitle = dto.diaryTitle,
            diaryDesc = dto.diaryDesc,
            diaryRequiresPhoto = dto.diaryRequiresPhoto,
            parentUserId = dto.parentUserId,
            authUserId = dto.authUserId,
            pinCode = dto.pinCode,
            parentEmail = dto.parentEmail,
            status = dto.status,
            deviceId = dto.deviceId,
            stateUpdatedAt = dto.stateUpdatedAt,
            stateVersion = dto.stateVersion,
            rewardCurrency = dto.rewardCurrency,
            xp = dto.xp,
            level = dto.level,
            perfectDaysStreak = dto.perfectDaysStreak,
            lastPerfectDayDate = dto.lastPerfectDayDate,
            selectedFrameId = dto.selectedFrameId,
            unlockedFrames = dto.unlockedFrames,
            baseClosetReward = dto.baseClosetReward,
            baseRoomReward = dto.baseRoomReward,
            baseDiaryFiveReward = dto.baseDiaryFiveReward,
            multipliersEnabled = dto.multipliersEnabled,
            bonusChestChancePercent = dto.bonusChestChancePercent,
            weeklyEarnLimit = dto.weeklyEarnLimit,
            honestyTokens = dto.honestyTokens,
            pendingParentGifts = dto.pendingParentGifts,
            balanceBase = dto.balanceBase
        )
    }

    /**
     * Баланс, вычисленный из истории: balanceBase + сумма amountValue.
     * При balanceBase == null (legacy) возвращает хранимый balance —
     * reconcile в updateChildProfile проставит базу при первой правке.
     */
    val derivedBalance: Int
        get() {
            val base = balanceBase ?: (balance - historyItems.sumOf { it.amountValue ?: 0 })
            return base + historyItems.sumOf { it.amountValue ?: 0 }
        }
}

@Serializable
data class ChildProfileDto(
    val id: String,
    val name: String,
    val avatar: String,
    val balance: Int = 40,
    val boughtMinutesToday: Int = 0,
    val honestAdmissionStatus: QuestStatus = QuestStatus.AVAILABLE,
    val lastHonestAdmissionDate: String = "",
    val honestAdmissionText: String = "",
    val closetStatus: QuestStatus = QuestStatus.AVAILABLE,
    val closetRejectReason: String? = null,
    val closetPhotoUrl: String? = null,
    val closetComment: String = "",
    val roomStatus: QuestStatus = QuestStatus.AVAILABLE,
    val roomRejectReason: String? = null,
    val roomPhotoUrl: String? = null,
    val roomComment: String = "",
    val diaryStatus: QuestStatus = QuestStatus.AVAILABLE,
    val diaryRejectReason: String? = null,
    val diaryPhotoUrl: String? = null,
    val diaryComment: String = "",
    val diaryApprovedReward: Int = 100,
    /** Оценки дневника, утверждённые админом — применяются при «Забрать». */
    val diaryCount5: Int = 0,
    val diaryCount4: Int = 0,
    val diaryCount3: Int = 0,
    val diaryCount2: Int = 0,
    val wardrobeFrequency: QuestFrequency = QuestFrequency.DAILY,
    val roomFrequency: QuestFrequency = QuestFrequency.DAILY,
    val diaryFrequency: QuestFrequency = QuestFrequency.WEEKLY,
    val closetResetDate: String = "",
    val roomResetDate: String = "",
    val diaryResetDate: String = "",
    val lastDailyResetDate: String = "",
    val dreamTitle: String = "",
    val dreamTarget: Int = 0,
    /** Дата (dd.MM.yyyy) установки цели — для кулдауна смены цели (7 дней). */
    val dreamSetDate: String = "",
    val streakDays: Int = 0,
    val lastStreakDate: String = "",
    val streakBonusMessage: String = "",
    val fivesCount: Int = 0,
    val wardrobeCount: Int = 0,
    val roomCount: Int = 0,
    val achievements: List<AchievementConfig> = defaultAchievements(),
    val cashoutRequests: List<CashoutRequest> = emptyList(),
    val pendingQuests: List<PendingQuestData> = emptyList(),
    val bonusQuests: List<BonusQuest> = emptyList(),
    val historyItems: List<HistoryData> = emptyList(),
    val selectedThemeId: String = "COSMIC_BLUE",
    val unlockedThemes: List<String> = DefaultUnlockedThemeIds,
    val unlockedAvatars: List<String> = DefaultUnlockedAvatarIds,
    val customQuests: List<CustomQuest> = emptyList(),
    val isWardrobeVisible: Boolean = true,
    val isRoomVisible: Boolean = true,
    val isDiaryVisible: Boolean = true,
    val isWardrobeDeleted: Boolean = false,
    val isRoomDeleted: Boolean = false,
    val isDiaryDeleted: Boolean = false,
    val wardrobeTitle: String = "Фото шкафа",
    val wardrobeDesc: String = "Наведи идеальный порядок на полках и сфотографируй результат для родителя",
    val wardrobeReward: Int = 50,
    val wardrobeRequiresPhoto: Boolean = true,
    val roomTitle: String = "Фото комнаты",
    val roomDesc: String = "Заправь кровать без складок, собери игрушки и пришли фото",
    val roomReward: Int = 50,
    val roomRequiresPhoto: Boolean = true,
    val diaryTitle: String = "📚 Дневник",
    val diaryDesc: String = "Квест выполняется раз в неделю. Родитель проверяет все оценки в дневнике за прошедшую неделю.",
    val diaryRequiresPhoto: Boolean = true,
    val parentUserId: String = "",
    val authUserId: String = "",
    val pinCode: String = "",
    val parentEmail: String = "",
    val status: String = "active",
    val deviceId: String = "",
    val stateUpdatedAt: Long = 0,
    /** Монотонный счётчик локальных изменений (см. ChildProfileState.stateVersion). */
    val stateVersion: Long = 0,
    val rewardCurrency: String = "RUB",
    /** XP текущего уровня (сброс в 0 при каждом level-up). См. GamificationEngine. */
    val xp: Long = 0L,
    /** Текущий уровень 1–50+. См. GamificationEngine. */
    val level: Int = 1,
    /** Серия «идеальных дней» подряд (шкаф + комната за один день). */
    val perfectDaysStreak: Int = 0,
    /** Дата (yyyy-MM-dd) последнего идеального дня; null — ещё не было. */
    val lastPerfectDayDate: String? = null,
    /** Выбранная рамка аватара (AvatarFrameType.name). */
    val selectedFrameId: String = "NONE",
    /** Купленные рамки аватара; рамки за уровень открываются динамически. */
    val unlockedFrames: List<String> = DefaultUnlockedFrameIds,
    /** Базовая награда за «Фото шкафа» (зеркалит wardrobeReward). */
    val baseClosetReward: Int = 50,
    /** Базовая награда за «Фото комнаты» (зеркалит roomReward). */
    val baseRoomReward: Int = 50,
    /** Базовая награда за оценку 5 в дневнике. */
    val baseDiaryFiveReward: Int = 50,
    /** false — награды без множителей уровня/серии/идеальных дней (×1.0). */
    val multipliersEnabled: Boolean = true,
    /** Шанс бонусного сундука после квеста, % (0/10/15/20). */
    val bonusChestChancePercent: Int = 15,
    /** Недельный лимит заработка монет за задания; 0 — без лимита. */
    val weeklyEarnLimit: Int = 0,
    /** Жетоны честности: +1 за каждое отправленное честное признание. */
    val honestyTokens: Int = 0,
    /** Активные (непогашенные) подарки-купоны, отправленные родителю. */
    val pendingParentGifts: List<ParentGiftItem> = emptyList(),
    /** База derived-баланса (см. ChildProfileState.balanceBase). */
    val balanceBase: Int? = null
)

fun ChildProfileState.toDto(): ChildProfileDto {
    // Денежные записи не усекаются (trimHistoryItems) — «выпасть» может
    // только неденежная запись с amountValue=null, поэтому droppedSum
    // практически всегда 0; формула оставлена для страховки.
    val keptHistory = trimHistoryItems(historyItems)
    val effectiveBase = balanceBase
        ?: (balance - historyItems.sumOf { it.amountValue ?: 0 })
    val droppedSum = historyItems.sumOf { it.amountValue ?: 0 } -
        keptHistory.sumOf { it.amountValue ?: 0 }
    return ChildProfileDto(
    id = id,
    name = name,
    avatar = avatar,
    balance = balance,
    boughtMinutesToday = boughtMinutesToday,
    honestAdmissionStatus = honestAdmissionStatus,
    lastHonestAdmissionDate = lastHonestAdmissionDate,
    honestAdmissionText = honestAdmissionText,
    closetStatus = closetStatus,
    closetRejectReason = closetRejectReason,
    closetPhotoUrl = closetPhotoUrl,
    closetComment = closetComment,
    roomStatus = roomStatus,
    roomRejectReason = roomRejectReason,
    roomPhotoUrl = roomPhotoUrl,
    roomComment = roomComment,
    diaryStatus = diaryStatus,
    diaryRejectReason = diaryRejectReason,
    diaryPhotoUrl = diaryPhotoUrl,
    diaryComment = diaryComment,
    diaryApprovedReward = diaryApprovedReward,
    diaryCount5 = diaryCount5,
    diaryCount4 = diaryCount4,
    diaryCount3 = diaryCount3,
    diaryCount2 = diaryCount2,
    wardrobeFrequency = wardrobeFrequency,
    roomFrequency = roomFrequency,
    diaryFrequency = diaryFrequency,
    closetResetDate = closetResetDate,
    roomResetDate = roomResetDate,
    diaryResetDate = diaryResetDate,
    lastDailyResetDate = lastDailyResetDate,
    dreamTitle = dreamTitle,
    dreamTarget = dreamTarget,
    dreamSetDate = dreamSetDate,
    streakDays = streakDays,
    lastStreakDate = lastStreakDate,
    streakBonusMessage = streakBonusMessage,
    fivesCount = fivesCount,
    wardrobeCount = wardrobeCount,
    roomCount = roomCount,
    achievements = achievements.toList(),
    cashoutRequests = cashoutRequests,
    pendingQuests = pendingQuests.toList(),
    bonusQuests = bonusQuests.toList(),
    historyItems = keptHistory,
    selectedThemeId = selectedThemeId,
    unlockedThemes = unlockedThemes,
    unlockedAvatars = unlockedAvatars,
    customQuests = customQuests.toList(),
    isWardrobeVisible = isWardrobeVisible,
    isRoomVisible = isRoomVisible,
    isDiaryVisible = isDiaryVisible,
    isWardrobeDeleted = isWardrobeDeleted,
    isRoomDeleted = isRoomDeleted,
    isDiaryDeleted = isDiaryDeleted,
    wardrobeTitle = wardrobeTitle,
    wardrobeDesc = wardrobeDesc,
    wardrobeReward = wardrobeReward,
    wardrobeRequiresPhoto = wardrobeRequiresPhoto,
    roomTitle = roomTitle,
    roomDesc = roomDesc,
    roomReward = roomReward,
    roomRequiresPhoto = roomRequiresPhoto,
    diaryTitle = diaryTitle,
    diaryDesc = diaryDesc,
    diaryRequiresPhoto = diaryRequiresPhoto,
    parentUserId = parentUserId,
    authUserId = authUserId,
    pinCode = pinCode,
    parentEmail = parentEmail,
    status = status,
    deviceId = deviceId,
    stateUpdatedAt = stateUpdatedAt,
    stateVersion = stateVersion,
    rewardCurrency = rewardCurrency,
    xp = xp,
    level = level,
    perfectDaysStreak = perfectDaysStreak,
    lastPerfectDayDate = lastPerfectDayDate,
    selectedFrameId = selectedFrameId,
    unlockedFrames = unlockedFrames,
    baseClosetReward = baseClosetReward,
    baseRoomReward = baseRoomReward,
    baseDiaryFiveReward = baseDiaryFiveReward,
    multipliersEnabled = multipliersEnabled,
    bonusChestChancePercent = bonusChestChancePercent,
    weeklyEarnLimit = weeklyEarnLimit,
    honestyTokens = honestyTokens,
    pendingParentGifts = pendingParentGifts,
    balanceBase = effectiveBase + droppedSum
    )
}
