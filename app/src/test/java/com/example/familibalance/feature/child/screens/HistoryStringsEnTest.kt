package com.example.familibalance.feature.child.screens

import android.content.res.Configuration
import com.example.familibalance.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Проверяет, что все resId из [historyTitleSpec] резолвятся в EN-локали:
 * строка существует, не пустая и не содержит неэкранированных плейсхолдеров.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryStringsEnTest {

    private fun enString(resId: Int, vararg args: Any): String {
        val context = RuntimeEnvironment.getApplication()
        val config = Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        }
        return context.createConfigurationContext(config).resources.getString(resId, *args)
    }

    @Test
    fun `all history title resIds resolve in EN locale`() {
        // resId → пример аргументов для форматирования
        val cases = listOf(
            R.string.hist_screen_time_purchase to arrayOf<Any>(15),
            R.string.hist_streak_bonus to emptyArray(),
            R.string.hist_achievement_unlocked to arrayOf("X"),
            R.string.hist_cashout_given to arrayOf("X"),
            R.string.hist_cashout_request to arrayOf("X"),
            R.string.hist_custom_quest_done to arrayOf("X"),
            R.string.hist_admin_reward to arrayOf("X"),
            R.string.hist_admin_reward_plain to emptyArray(),
            R.string.hist_admin_withdraw to arrayOf("X"),
            R.string.hist_admin_withdraw_plain to emptyArray(),
            R.string.hist_fine to arrayOf("X"),
            R.string.hist_fine_plain to emptyArray(),
            R.string.hist_fine_discount to arrayOf("X"),
            R.string.hist_fine_plain_discount to emptyArray(),
            R.string.hist_bonus_quest_approved to arrayOf("X"),
            R.string.hist_quest_approved to arrayOf("X"),
            R.string.hist_honest_approved to emptyArray(),
            R.string.hist_diary_grade5 to emptyArray(),
            R.string.hist_diary_grade4 to emptyArray(),
            R.string.hist_quest_reward to arrayOf("X"),
            R.string.hist_bonus_quest_done to arrayOf("X"),
            R.string.hist_dream_set to arrayOf("X")
        )
        cases.forEach { (resId, args) ->
            val text = enString(resId, *args)
            assertTrue("resId=$resId resolved to blank", text.isNotBlank())
            assertFalse("resId=$resId has unformatted placeholder: $text", text.contains("%1"))
        }
    }

    @Test
    fun `sample EN outputs look correct`() {
        assertTrue(enString(R.string.hist_diary_grade5).startsWith("Grade"))
        assertTrue(enString(R.string.hist_quest_reward, "Photo").contains("Photo"))
        assertTrue(enString(R.string.hist_fine_discount, "Reason").contains("50%"))
    }
}
