package com.example.familibalance

import com.example.familibalance.util.GamificationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Unit-тесты чистой логики геймификации (без Android-зависимостей). */
class GamificationEngineTest {

    // ---------- XP-кривая ----------

    @Test
    fun `xpToNextLevel grows monotonically up to level 50`() {
        var prev = 0L
        for (level in 1..50) {
            val need = GamificationEngine.xpToNextLevel(level)
            assertTrue("level $level: need=$need must be > 0", need > 0)
            assertTrue("level $level: need=$need must grow", need > prev)
            prev = need
        }
    }

    @Test
    fun `xpToNextLevel matches formula`() {
        // round(80 + 28*1 + 1.15*1) = round(109.15) = 109
        assertEquals(109L, GamificationEngine.xpToNextLevel(1))
        // round(80 + 28*10 + 1.15*100) = round(475.0) = 475
        assertEquals(475L, GamificationEngine.xpToNextLevel(10))
    }

    // ---------- Ранги ----------

    @Test
    fun `rankForLevel boundaries`() {
        assertEquals(GamificationEngine.Rank.NOVICE, GamificationEngine.rankForLevel(1))
        assertEquals(GamificationEngine.Rank.NOVICE, GamificationEngine.rankForLevel(4))
        assertEquals(GamificationEngine.Rank.HOME_HELPER, GamificationEngine.rankForLevel(5))
        assertEquals(GamificationEngine.Rank.ORDER_KEEPER, GamificationEngine.rankForLevel(10))
        assertEquals(GamificationEngine.Rank.BALANCE_MASTER, GamificationEngine.rankForLevel(15))
        assertEquals(GamificationEngine.Rank.FAMILY_HERO, GamificationEngine.rankForLevel(25))
        assertEquals(GamificationEngine.Rank.HOUSE_LEGEND, GamificationEngine.rankForLevel(40))
        assertEquals(GamificationEngine.Rank.HOUSE_LEGEND, GamificationEngine.rankForLevel(99))
    }

    // ---------- Базовый XP ----------

    @Test
    fun `baseXp defaults per quest kind`() {
        assertEquals(25, GamificationEngine.baseXp(GamificationEngine.QuestKind.CLOSET))
        assertEquals(25, GamificationEngine.baseXp(GamificationEngine.QuestKind.ROOM))
        assertEquals(70, GamificationEngine.baseXp(GamificationEngine.QuestKind.DIARY))
        assertEquals(30, GamificationEngine.baseXp(GamificationEngine.QuestKind.BONUS_OR_CUSTOM))
    }

    // ---------- Множители ----------

    @Test
    fun `levelBonus tiers`() {
        assertEquals(0.0, GamificationEngine.levelBonus(1), 0.001)
        assertEquals(0.0, GamificationEngine.levelBonus(5), 0.001)
        assertEquals(0.1, GamificationEngine.levelBonus(6), 0.001)
        assertEquals(0.2, GamificationEngine.levelBonus(15), 0.001)
        assertEquals(0.3, GamificationEngine.levelBonus(20), 0.001)
        assertEquals(0.35, GamificationEngine.levelBonus(25), 0.001)
        assertEquals(0.5, GamificationEngine.levelBonus(40), 0.001)
        // Кап +0.5 даже на высоких уровнях
        assertEquals(0.5, GamificationEngine.levelBonus(100), 0.001)
    }

    @Test
    fun `streakBonus tiers`() {
        assertEquals(0.0, GamificationEngine.streakBonus(0), 0.001)
        assertEquals(0.1, GamificationEngine.streakBonus(3), 0.001)
        assertEquals(0.25, GamificationEngine.streakBonus(7), 0.001)
        assertEquals(0.4, GamificationEngine.streakBonus(14), 0.001)
        assertEquals(0.5, GamificationEngine.streakBonus(30), 0.001)
        assertEquals(0.5, GamificationEngine.streakBonus(365), 0.001)
    }

    @Test
    fun `rewardMultiplier caps at 1_75`() {
        // level 40 (+0.5) + streak 30+ (+0.5) = 2.0 → кап 1.75
        assertEquals(1.75, GamificationEngine.rewardMultiplier(40, 30), 0.001)
        assertEquals(1.75, GamificationEngine.rewardMultiplier(100, 100), 0.001)
        // level 1, streak 0 → 1.0
        assertEquals(1.0, GamificationEngine.rewardMultiplier(1, 0), 0.001)
        // level 10 (+0.1) + streak 7 (+0.25) = 1.35
        assertEquals(1.35, GamificationEngine.rewardMultiplier(10, 7), 0.001)
    }

    @Test
    fun `computeReward rounds coins and xp`() {
        // base 50 монет × 1.35 = 67.5 → 68; XP 25 × 1.35 = 33.75 → 34
        val r = GamificationEngine.computeReward(50, 25, level = 10, streakDays = 7)
        assertEquals(68, r.coins)
        assertEquals(34, r.xp)
        assertEquals(1.35, r.multiplier, 0.001)
    }

    // ---------- Level-up ----------

    @Test
    fun `applyXp single level up grants bonus`() {
        // level 1: need 109 XP. Даём 109 → level 2, bonus = 50 + 2*10 = 70.
        val res = GamificationEngine.applyXp(currentXp = 0, currentLevel = 1, xpGain = 109)
        assertEquals(2, res.newLevel)
        assertEquals(0, res.newXp)
        assertEquals(1, res.levelsGained)
        assertEquals(70, res.coinBonus)
        assertEquals(GamificationEngine.Rank.NOVICE, res.rank)
    }

    @Test
    fun `applyXp skips multiple levels`() {
        // level 1 need 109, level 2 need round(80+56+4.6)=141 → суммарно 250 XP.
        val res = GamificationEngine.applyXp(currentXp = 0, currentLevel = 1, xpGain = 250)
        assertEquals(2, res.levelsGained)
        assertEquals(3, res.newLevel)
        // bonus = (50+2*10) + (50+3*10) = 70 + 80 = 150
        assertEquals(150, res.coinBonus)
        assertEquals(GamificationEngine.Rank.NOVICE, res.rank)
    }

    @Test
    fun `applyXp keeps remainder xp`() {
        val res = GamificationEngine.applyXp(currentXp = 100, currentLevel = 1, xpGain = 20)
        // 100+20=120 ≥ 109 → level 2, остаток 11
        assertEquals(2, res.newLevel)
        assertEquals(11, res.newXp)
    }

    @Test
    fun `applyXp no level up when xp insufficient`() {
        val res = GamificationEngine.applyXp(currentXp = 50, currentLevel = 1, xpGain = 10)
        assertEquals(1, res.newLevel)
        assertEquals(60, res.newXp)
        assertEquals(0, res.levelsGained)
        assertEquals(0, res.coinBonus)
    }

    // ---------- Бонусный сундук ----------

    @Test
    fun `rollBonusChest returns null when chance is zero`() {
        repeat(100) { seed ->
            assertNull(GamificationEngine.rollBonusChest(chance = 0.0, random = Random(seed)))
        }
    }

    @Test
    fun `rollBonusChest always drops with chance 1_0 and rewards stay in range`() {
        val coinsAmounts = mutableSetOf<Int>()
        val xpAmounts = mutableSetOf<Int>()
        for (seed in 0 until 300) {
            val reward = GamificationEngine.rollBonusChest(chance = 1.0, random = Random(seed))
            assertNotNull(reward)
            when (reward) {
                is GamificationEngine.BonusChestReward.Coins -> {
                    assertTrue("coins=${reward.amount}", reward.amount in listOf(20, 25, 30, 40, 50, 60))
                    coinsAmounts += reward.amount
                }
                is GamificationEngine.BonusChestReward.Xp -> {
                    assertTrue("xp=${reward.amount}", reward.amount in 10..15)
                    xpAmounts += reward.amount
                }
                null -> error("chance=1.0 must always drop a reward")
            }
        }
        // 50/50 монеты/XP: за 300 бросков оба типа обязаны встретиться.
        assertTrue(coinsAmounts.isNotEmpty())
        assertTrue(xpAmounts.isNotEmpty())
    }

    @Test
    fun `rollBonusChest default chance hits roughly 15 percent`() {
        // Фиксированные seed → детерминированный результат, не flaky.
        var hits = 0
        for (seed in 0 until 2000) {
            if (GamificationEngine.rollBonusChest(random = Random(seed)) != null) hits++
        }
        assertTrue("hits=$hits (ожидалось ~300 из 2000)", hits in 200..400)
    }

    // ---------- Идеальный день ----------

    @Test
    fun `perfectDayXpBonus tiers`() {
        assertEquals(0.0, GamificationEngine.perfectDayXpBonus(0), 0.0001)
        assertEquals(0.0, GamificationEngine.perfectDayXpBonus(2), 0.0001)
        assertEquals(0.10, GamificationEngine.perfectDayXpBonus(3), 0.0001)
        assertEquals(0.10, GamificationEngine.perfectDayXpBonus(6), 0.0001)
        assertEquals(0.20, GamificationEngine.perfectDayXpBonus(7), 0.0001)
        assertEquals(0.20, GamificationEngine.perfectDayXpBonus(10), 0.0001)
    }
}
