package com.example.familibalance.viewmodel.actions

import android.app.Application
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.model.HistoryData
import com.example.familibalance.data.model.HistoryType
import com.example.familibalance.data.model.QuestStatus
import com.example.familibalance.util.GamificationEngine
import com.example.familibalance.util.getCurrentDateString
import com.example.familibalance.util.getCurrentTimeString
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Результат расчёта геймифицированной награды за квест. */
internal data class QuestRewardOutcome(
    /** Итоговые монеты с учётом множителя (идут в balance и history). */
    val coins: Int,
    /** Базовая сумма до множителя — нужна для диффа в истории дневника. */
    val baseCoins: Int,
    val xpGain: Int,
    val multiplier: Double,
    /** Множители по отдельности — для расшифровки «Уровень ×… + Серия ×…». */
    val levelMultiplier: Double,
    val streakMultiplier: Double,
    val levelUp: GamificationEngine.LevelUpResult
)

/**
 * Считает монеты/XP/level-up для базовой награды квеста.
 * Вызывать ДО изменения профиля — читает level/xp/streakDays.
 */
internal fun computeQuestReward(
    profile: ChildProfileState,
    baseCoins: Int,
    kind: GamificationEngine.QuestKind,
    baseXpOverride: Int? = null
): QuestRewardOutcome {
    // multipliersEnabled=false → множитель 1.0: без бонусов уровня,
    // серии и идеальных дней (XP тоже без надбавок).
    val effLevel = if (profile.multipliersEnabled) profile.level else 1
    val effStreak = if (profile.multipliersEnabled) profile.streakDays else 0
    val baseXp = baseXpOverride ?: GamificationEngine.baseXp(kind)
    val reward = GamificationEngine.computeReward(
        baseCoins = baseCoins,
        baseXp = baseXp,
        level = effLevel,
        streakDays = effStreak
    )
    // Серия идеальных дней усиливает только XP (рост уровня), монеты не растут.
    val perfectBonus = if (profile.multipliersEnabled) {
        GamificationEngine.perfectDayXpBonus(profile.perfectDaysStreak)
    } else {
        0.0
    }
    val xpGain = if (perfectBonus > 0.0) {
        (reward.xp * (1.0 + perfectBonus)).roundToInt()
    } else {
        reward.xp
    }
    val levelUp = GamificationEngine.applyXp(profile.xp, profile.level, xpGain)
    return QuestRewardOutcome(
        coins = reward.coins,
        baseCoins = baseCoins,
        xpGain = xpGain,
        multiplier = reward.multiplier,
        levelMultiplier = GamificationEngine.levelMultiplier(effLevel),
        streakMultiplier = GamificationEngine.streakMultiplier(effStreak),
        levelUp = levelUp
    )
}

/**
 * Новые поля профиля после начисления XP/уровня.
 * balance здесь НЕ трогаем — монеты добавляет вызывающий код вместе
 * с записями истории (инвариант «дельта баланса = сумма истории»).
 */
internal fun ChildProfileState.withGamification(outcome: QuestRewardOutcome): ChildProfileState =
    copy(xp = outcome.levelUp.newXp, level = outcome.levelUp.newLevel)

/**
 * Информационные записи геймификации для истории:
 *  - REWARD_MULTIPLIER: расшифровка «×1.10 • +28 XP»; amountValue =
 *    дельта монет от множителя (0/null — не денежная запись).
 *  - LEVEL_UP: бонус монет за повышение уровня (amountValue = coinBonus).
 *
 * @param coinDelta сколько монет добавил множитель сверх базовой суммы
 *                  (для дневника: coins - diaryApprovedReward; для
 *                  одиночных квестов 0 — сумма уже в основной записи).
 */
internal fun MainViewModel.gamificationHistoryEntries(
    outcome: QuestRewardOutcome,
    time: String,
    date: String,
    coinDelta: Int
): List<HistoryData> {
    val entries = mutableListOf<HistoryData>()
    // Расшифровка множителя: «×1.25 (Уровень ×1.14 + Серия ×1.10) • +28 XP».
    val breakdown = this.getApplication<Application>().getString(
        R.string.mult_breakdown,
        outcome.multiplier,
        outcome.levelMultiplier,
        outcome.streakMultiplier
    )
    val multText = "$breakdown • +${outcome.xpGain} XP"
    if (outcome.multiplier > 1.0 || outcome.xpGain > 0) {
        entries += HistoryData(
            title = "Бонус за уровень и серию",
            time = time,
            date = date,
            amount = if (coinDelta != 0) {
                if (coinDelta > 0) "+$coinDelta ₽" else "-$coinDelta ₽"
            } else "",
            isIncome = coinDelta >= 0,
            category = "Бонусы",
            type = HistoryType.REWARD_MULTIPLIER,
            amountValue = coinDelta.takeIf { it != 0 },
            arg = multText
        )
    }
    if (outcome.levelUp.levelsGained > 0) {
        entries += HistoryData(
            title = "Новый уровень!",
            time = time,
            date = date,
            amount = "+${outcome.levelUp.coinBonus} ₽",
            isIncome = true,
            category = "Достижения",
            type = HistoryType.LEVEL_UP,
            amountValue = outcome.levelUp.coinBonus,
            arg = outcome.levelUp.rank.name
        )
    }
    return entries
}

/** Уведомляет UI о level-up для confetti/звука (вызывается после updateChildProfile). */
internal fun MainViewModel.notifyLevelUp(outcome: QuestRewardOutcome) {
    if (outcome.levelUp.levelsGained > 0) {
        _levelUpEvent.value = outcome.levelUp.newLevel
    }
}

// ---------- Недельный лимит заработка и сундук ----------

/** Типы записей истории, которые считаются «заработком за задания». */
private val WEEKLY_EARN_TYPES = setOf(
    HistoryType.QUEST_REWARD,
    HistoryType.BONUS_QUEST_DONE,
    HistoryType.CUSTOM_QUEST_DONE,
    HistoryType.DIARY_GRADE_5,
    HistoryType.DIARY_GRADE_4
)

private val HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Сумма монет, заработанных за задания в текущую календарную неделю
 * (пн–вс, по полю date записей истории в формате dd.MM.yyyy).
 * Учитываются только доходные записи квестов (QUEST_REWARD,
 * BONUS_QUEST_DONE, CUSTOM_QUEST_DONE, DIARY_GRADE_5/4) —
 * level-up бонусы, сундуки и ручные пополнения не входят.
 */
internal fun ChildProfileState.earnedThisWeek(): Int {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    return historyItems.sumOf { item ->
        val amount = item.amountValue ?: return@sumOf 0
        if (!item.isIncome || amount <= 0 || item.type !in WEEKLY_EARN_TYPES) return@sumOf 0
        val date = runCatching {
            LocalDate.parse(item.date, HISTORY_DATE_FORMAT)
        }.getOrNull() ?: return@sumOf 0
        if (date in weekStart..weekEnd) amount else 0
    }
}

/** Результат применения недельного лимита к начислению монет. */
internal data class WeeklyCapResult(
    /** Монеты к начислению после ограничения (≤ исходных). */
    val coins: Int,
    /** true — лимит сработал, часть награды обрезана. */
    val capped: Boolean
)

/**
 * Ограничивает начисление монет остатком недельного лимита.
 * XP и level-up не затрагиваются — лимит касается только монет.
 * limit <= 0 — без ограничений.
 */
internal fun applyWeeklyEarnLimit(profile: ChildProfileState, coins: Int): WeeklyCapResult {
    val limit = profile.weeklyEarnLimit
    if (limit <= 0 || coins <= 0) return WeeklyCapResult(coins, false)
    val remaining = (limit - profile.earnedThisWeek()).coerceAtLeast(0)
    val granted = coins.coerceAtMost(remaining)
    return WeeklyCapResult(granted, granted < coins)
}

/**
 * Бросок бонусного сундука с учётом настройки профиля:
 * bonusChestChancePercent <= 0 — сундук не выпадает вообще.
 */
internal fun MainViewModel.rollChest(profile: ChildProfileState): GamificationEngine.BonusChestReward? =
    if (profile.bonusChestChancePercent <= 0) {
        null
    } else {
        bonusChestRoller(profile.bonusChestChancePercent)
    }

/** Результат применения бонусного сундука к уже обновлённому профилю. */
internal data class BonusChestOutcome(
    val reward: GamificationEngine.BonusChestReward,
    /** Записи истории от сундука (BONUS_CHEST и/или LEVEL_UP за XP-часть). */
    val historyEntries: List<HistoryData>,
    /** Суммарная дельта баланса от сундука (монеты + coinBonus за level-up). */
    val balanceDelta: Int,
    /** Обновлённые xp/level после XP-награды (для монет — без изменений). */
    val xp: Long,
    val level: Int,
    /** Сколько уровней дал XP сундука (0 — нет level-up). */
    val levelsGained: Int
)

/**
 * Применяет выпавшую награду сундука к профилю ПОСЛЕ основной награды квеста.
 *
 * Монеты: отдельная запись BONUS_CHEST с amountValue — инвариант
 * «дельта баланса = сумма истории» сохраняется.
 * XP: прогоняется через [GamificationEngine.applyXp] — возможный level-up
 * даёт coinBonus, который тоже идёт в баланс и историю (LEVEL_UP).
 * XP-запись сундука не денежная (amountValue = null), детали — в arg.
 */
internal fun applyBonusChest(
    profile: ChildProfileState,
    reward: GamificationEngine.BonusChestReward,
    time: String,
    date: String
): BonusChestOutcome = when (reward) {
    is GamificationEngine.BonusChestReward.Coins -> {
        val entry = HistoryData(
            title = "Бонусный сундук",
            time = time,
            date = date,
            amount = "+${reward.amount} ₽",
            isIncome = true,
            category = "Бонусы",
            type = HistoryType.BONUS_CHEST,
            amountValue = reward.amount
        )
        BonusChestOutcome(
            reward = reward,
            historyEntries = listOf(entry),
            balanceDelta = reward.amount,
            xp = profile.xp,
            level = profile.level,
            levelsGained = 0
        )
    }
    is GamificationEngine.BonusChestReward.Xp -> {
        val levelUp = GamificationEngine.applyXp(profile.xp, profile.level, reward.amount)
        val entries = mutableListOf(
            HistoryData(
                title = "Бонусный сундук",
                time = time,
                date = date,
                amount = "",
                isIncome = true,
                category = "Бонусы",
                type = HistoryType.BONUS_CHEST,
                amountValue = null,
                arg = reward.amount.toString()
            )
        )
        if (levelUp.levelsGained > 0) {
            entries += HistoryData(
                title = "Новый уровень!",
                time = time,
                date = date,
                amount = "+${levelUp.coinBonus} ₽",
                isIncome = true,
                category = "Достижения",
                type = HistoryType.LEVEL_UP,
                amountValue = levelUp.coinBonus,
                arg = levelUp.rank.name
            )
        }
        BonusChestOutcome(
            reward = reward,
            historyEntries = entries,
            balanceDelta = levelUp.coinBonus,
            xp = levelUp.newXp,
            level = levelUp.newLevel,
            levelsGained = levelUp.levelsGained
        )
    }
}

/** Уведомляет UI о сундуке (диалог + confetti) и о level-up от его XP. */
internal fun MainViewModel.notifyBonusChest(outcome: BonusChestOutcome) {
    _bonusChestEvent.value = outcome.reward
    if (outcome.levelsGained > 0) {
        _levelUpEvent.value = outcome.level
    }
}

/**
 * Начисляет «Идеальный день», если оба ежедневных квеста (шкаф + комната)
 * заклеймлены сегодня впервые: +25 монет и +15 XP (через applyXp,
 * с обработкой level-up и его coinBonus). Инвариант
 * «дельта баланса = сумма истории» сохраняется: PERFECT_DAY несёт
 * 25 ₽, LEVEL_UP — coinBonus.
 *
 * Условия: оба статуса CLAIMED, обе даты сброса == сегодня
 * (квест был доступен сегодня и заклеймлен сегодня),
 * lastPerfectDayDate != сегодня (защита от повторного начисления).
 * Серия: lastPerfectDayDate == вчера → +1, иначе → 1.
 */
internal fun MainViewModel.maybeAwardPerfectDay(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val today = LocalDate.now()
    val todayStr = today.toString()
    if (profile.lastPerfectDayDate == todayStr) return
    if (profile.closetStatus != QuestStatus.CLAIMED || profile.roomStatus != QuestStatus.CLAIMED) return
    if (profile.closetResetDate != todayStr || profile.roomResetDate != todayStr) return

    val yesterdayStr = today.minusDays(1).toString()
    val newStreak = if (profile.lastPerfectDayDate == yesterdayStr) {
        profile.perfectDaysStreak + 1
    } else {
        1
    }

    val time = getCurrentTimeString()
    val date = getCurrentDateString()
    val levelUp = GamificationEngine.applyXp(
        profile.xp, profile.level, GamificationEngine.PERFECT_DAY_XP
    )

    val entries = mutableListOf(
        HistoryData(
            title = "Идеальный день",
            time = time,
            date = date,
            amount = "+${GamificationEngine.PERFECT_DAY_COINS} ₽",
            isIncome = true,
            category = "Достижения",
            type = HistoryType.PERFECT_DAY,
            amountValue = GamificationEngine.PERFECT_DAY_COINS
        )
    )
    if (levelUp.levelsGained > 0) {
        entries += HistoryData(
            title = "Новый уровень!",
            time = time,
            date = date,
            amount = "+${levelUp.coinBonus} ₽",
            isIncome = true,
            category = "Достижения",
            type = HistoryType.LEVEL_UP,
            amountValue = levelUp.coinBonus,
            arg = levelUp.rank.name
        )
    }

    updateChildProfile(
        profile.copy(
            perfectDaysStreak = newStreak,
            lastPerfectDayDate = todayStr,
            balance = profile.balance + GamificationEngine.PERFECT_DAY_COINS + levelUp.coinBonus,
            xp = levelUp.newXp,
            level = levelUp.newLevel,
            historyItems = entries + profile.historyItems
        )
    )
    if (levelUp.levelsGained > 0) {
        _levelUpEvent.value = levelUp.newLevel
    }
}
