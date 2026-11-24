package com.example.familibalance.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты Cloud-аватаров: маппинг legacy-строк, правила
 * разблокировки и авто-открытие за достижения.
 */
class CloudAvatarsTest {

    // ---------- cloudAvatarTypeOf ----------

    @Test
    fun `cloudAvatarTypeOf maps enum names case-insensitively`() {
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf("CAT"))
        assertEquals(CloudAvatarType.PANDA, cloudAvatarTypeOf("panda"))
        assertEquals(CloudAvatarType.NINJA, cloudAvatarTypeOf("Ninja"))
    }

    @Test
    fun `cloudAvatarTypeOf maps legacy emojis`() {
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf("🐱"))
        assertEquals(CloudAvatarType.PANDA, cloudAvatarTypeOf("🐼"))
        assertEquals(CloudAvatarType.ALIEN, cloudAvatarTypeOf("👽"))
        assertEquals(CloudAvatarType.UNICORN, cloudAvatarTypeOf("🦄"))
        assertEquals(CloudAvatarType.ASTRONAUT, cloudAvatarTypeOf("🚀"))
        assertEquals(CloudAvatarType.ROBOT, cloudAvatarTypeOf("🤖"))
        assertEquals(CloudAvatarType.DRAGON, cloudAvatarTypeOf("🐉"))
        assertEquals(CloudAvatarType.NINJA, cloudAvatarTypeOf("🥷"))
    }

    @Test
    fun `cloudAvatarTypeOf falls back to CAT for unknown or blank`() {
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf(null))
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf(""))
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf("🦊"))
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf("🧒"))
        assertEquals(CloudAvatarType.CAT, cloudAvatarTypeOf("random-string"))
    }

    // ---------- Правила разблокировки ----------

    @Test
    fun `free avatars are exactly CAT PANDA ALIEN UNICORN`() {
        assertEquals(
            setOf("CAT", "PANDA", "ALIEN", "UNICORN"),
            DefaultUnlockedAvatarIds.toSet()
        )
        CloudAvatarType.entries.forEach { type ->
            val expectedFree = type.name in DefaultUnlockedAvatarIds
            assertEquals(
                "Правило Free должно совпадать с DefaultUnlockedAvatarIds для $type",
                expectedFree,
                type.unlockRule is ThemeUnlockRule.Free
            )
        }
    }

    @Test
    fun `paid avatars have correct prices`() {
        assertEquals(100, (CloudAvatarType.ROBOT.unlockRule as ThemeUnlockRule.Paid).price)
        assertEquals(150, (CloudAvatarType.NINJA.unlockRule as ThemeUnlockRule.Paid).price)
    }

    @Test
    fun `achievement avatars are bound to correct achievements`() {
        val astronaut = CloudAvatarType.ASTRONAUT.unlockRule as ThemeUnlockRule.Achievement
        assertEquals("Победитель Монстров", astronaut.achievementTitle)
        val dragon = CloudAvatarType.DRAGON.unlockRule as ThemeUnlockRule.Achievement
        assertEquals("Босс Первой Парты", dragon.achievementTitle)
    }

    // ---------- isAvatarUnlocked ----------

    @Test
    fun `free avatar is always unlocked`() {
        assertTrue(
            CloudAvatarType.CAT.isAvatarUnlocked(
                unlockedAvatars = emptyList(),
                claimedAchievementTitles = emptySet()
            )
        )
    }

    @Test
    fun `paid avatar unlocked only when in unlockedAvatars`() {
        assertFalse(
            CloudAvatarType.ROBOT.isAvatarUnlocked(emptyList(), emptySet())
        )
        assertTrue(
            CloudAvatarType.ROBOT.isAvatarUnlocked(listOf("ROBOT"), emptySet())
        )
    }

    @Test
    fun `achievement avatar unlocked by claimed achievement title`() {
        assertTrue(
            CloudAvatarType.ASTRONAUT.isAvatarUnlocked(
                unlockedAvatars = emptyList(),
                claimedAchievementTitles = setOf("Победитель Монстров")
            )
        )
        assertFalse(
            CloudAvatarType.ASTRONAUT.isAvatarUnlocked(
                unlockedAvatars = emptyList(),
                claimedAchievementTitles = setOf("Другая награда")
            )
        )
    }

    // ---------- achievementAvatarIds ----------

    @Test
    fun `achievementAvatarIds returns only achievement-bound avatars`() {
        assertEquals(
            setOf("ASTRONAUT", "DRAGON"),
            achievementAvatarIds(setOf("Победитель Монстров", "Босс Первой Парты"))
        )
        assertEquals(
            setOf("ASTRONAUT"),
            achievementAvatarIds(setOf("Победитель Монстров"))
        )
        assertTrue(achievementAvatarIds(emptySet()).isEmpty())
    }
}
