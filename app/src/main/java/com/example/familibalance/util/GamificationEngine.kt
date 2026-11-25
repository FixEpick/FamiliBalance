package com.example.familibalance.util

import androidx.annotation.StringRes
import com.example.familibalance.R
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Чистая логика геймификации: XP, уровни, ранги, множители наград.
 * Не зависит от Android/Compose — покрывается unit-тестами.
 *
 * Инвариант: все денежные начисления проходят через [computeReward]
 * и [applyXp], чтобы «дельта баланса = сумма истории» сохранялась.
 */
object GamificationEngine {

    /** Тип базового задания для дефолтного XP. */
    enum class QuestKind { CLOSET, ROOM, DIARY, BONUS_OR_CUSTOM }

    /** Ранги по уровню. Названия — в strings.xml (gamification_rank_*). */
    enum class Rank(val minLevel: Int) {
        NOVICE(1),
        HOME_HELPER(5),
        ORDER_KEEPER(10),
        BALANCE_MASTER(15),
        FAMILY_HERO(25),
        HOUSE_LEGEND(40)
    }

    /** XP, необходимый для перехода с [level] на [level]+1. */
    fun xpToNextLevel(level: Int): Long =
        round(80 + 28 * level + 1.15 * level * level).toLong()

    fun rankForLevel(level: Int): Rank =
        Rank.entries.last { level >= it.minLevel }

    /** String-res названия ранга (без эмодзи — чистый текст). */
    @StringRes
    fun rankTitleRes(rank: Rank): Int = when (rank) {
        Rank.NOVICE -> R.string.gamification_rank_novice
        Rank.HOME_HELPER -> R.string.gamification_rank_helper
        Rank.ORDER_KEEPER -> R.string.gamification_rank_keeper
        Rank.BALANCE_MASTER -> R.string.gamification_rank_master
        Rank.FAMILY_HERO -> R.string.gamification_rank_hero
        Rank.HOUSE_LEGEND -> R.string.gamification_rank_legend
    }

    /** Базовый XP задания (если не переопределено квестом). */
    fun baseXp(kind: QuestKind): Int = when (kind) {
        QuestKind.CLOSET -> 25
        QuestKind.ROOM -> 25
        QuestKind.DIARY -> 70
        QuestKind.BONUS_OR_CUSTOM -> 30
    }

    /** Аддитивная надбавка к множителю от уровня (ур. 1–5 → +0.0 … макс +0.5). */
    fun levelBonus(level: Int): Double = when {
        level <= 5 -> 0.0
        level <= 10 -> 0.1
        level <= 15 -> 0.2
        level <= 20 -> 0.3
        else -> min(0.3 + 0.05 * ((level - 20) / 5), 0.5)
    }

    /** Аддитивная надбавка от серии дней без штрафов. */
    fun streakBonus(streakDays: Int): Double = when {
        streakDays >= 30 -> 0.5
        streakDays >= 14 -> 0.4
        streakDays >= 7 -> 0.25
        streakDays >= 3 -> 0.1
        else -> 0.0
    }

    /** Итоговый множитель наград: 1.0 + levelBonus + streakBonus, кап 1.75. */
    fun rewardMultiplier(level: Int, streakDays: Int): Double =
        min(1.0 + levelBonus(level) + streakBonus(streakDays), 1.75)

    data class RewardResult(
        val coins: Int,
        val xp: Int,
        val multiplier: Double
    )

    /** Итоговые монеты и XP с учётом множителя (округление до целого). */
    fun computeReward(baseCoins: Int, baseXp: Int, level: Int, streakDays: Int): RewardResult {
        val mult = rewardMultiplier(level, streakDays)
        return RewardResult(
            coins = (baseCoins * mult).roundToInt(),
            xp = (baseXp * mult).roundToInt(),
            multiplier = mult
        )
    }

    data class LevelUpResult(
        val newXp: Long,
        val newLevel: Int,
        val levelsGained: Int,
        /** Суммарный бонус монет за все полученные уровни. */
        val coinBonus: Int,
        val rank: Rank
    )

    /**
     * Начисляет XP и проводит через возможные повышения уровня.
     * Бонус за каждый новый уровень: 50 + newLevel * 10 монет.
     */
    fun applyXp(currentXp: Long, currentLevel: Int, xpGain: Int): LevelUpResult {
        var xp = currentXp + xpGain.coerceAtLeast(0)
        var level = currentLevel.coerceAtLeast(1)
        var gained = 0
        var bonus = 0
        while (xp >= xpToNextLevel(level)) {
            xp -= xpToNextLevel(level)
            level++
            gained++
            bonus += 50 + level * 10
        }
        return LevelUpResult(
            newXp = xp,
            newLevel = level,
            levelsGained = gained,
            coinBonus = bonus,
            rank = rankForLevel(level)
        )
    }

    /** Награда за «Идеальный день» (шкаф + комната за один день). */
    const val PERFECT_DAY_COINS = 25
    const val PERFECT_DAY_XP = 15

    /**
     * Бонус к XP от серии идеальных дней: 0–2 дня — 0%, 3–6 — +10%,
     * 7+ — +20%. Усиливает только рост уровня/XP, монеты не растут.
     */
    fun perfectDayXpBonus(perfectDaysStreak: Int): Double = when {
        perfectDaysStreak >= 7 -> 0.20
        perfectDaysStreak >= 3 -> 0.10
        else -> 0.0
    }

    /** Награда бонусного сундука: монеты или дополнительный XP. */
    sealed class BonusChestReward {
        data class Coins(val amount: Int) : BonusChestReward()
        data class Xp(val amount: Int) : BonusChestReward()
    }

    /** Фиксированный набор монет сундука (кратны 5, 20–60 ₽). */
    private val BONUS_CHEST_COINS = listOf(20, 25, 30, 40, 50, 60)

    /**
     * Бросок бонусного сундука: с вероятностью [chance] (по умолчанию 15%)
     * возвращает награду — 50/50 монеты из [BONUS_CHEST_COINS] или XP 10..15.
     * Иначе возвращает null. [random] вынесен параметром для
     * детерминированных unit-тестов.
     */
    fun rollBonusChest(
        chance: Double = 0.15,
        random: Random = Random.Default
    ): BonusChestReward? {
        if (random.nextDouble() >= chance) return null
        return if (random.nextBoolean()) {
            BonusChestReward.Coins(BONUS_CHEST_COINS[random.nextInt(BONUS_CHEST_COINS.size)])
        } else {
            BonusChestReward.Xp(10 + random.nextInt(6))
        }
    }
}
