package com.example.familibalance.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familibalance.R
import com.example.familibalance.data.model.QuestStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Критический путь «квест ребёнка»: кнопка «Выполнить» для AVAILABLE
 * и «Забрать награду» для READY_TO_CLAIM вызывают свои колбэки.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    @Test
    fun `available quest shows execute button and calls onExecute`() {
        var executed = false
        composeRule.setContent {
            TaskCard(
                title = "Make bed",
                description = "",
                reward = "10",
                status = QuestStatus.AVAILABLE,
                onExecute = { executed = true },
                onClaim = {}
            )
        }

        composeRule.onNodeWithText(string(R.string.task_execute)).performClick()
        assertTrue(executed)
    }

    @Test
    fun `ready quest shows claim button and calls onClaim`() {
        var claimed = false
        composeRule.setContent {
            TaskCard(
                title = "Make bed",
                description = "",
                reward = "10",
                status = QuestStatus.READY_TO_CLAIM,
                onExecute = {},
                onClaim = { claimed = true }
            )
        }

        composeRule.onNodeWithText(string(R.string.task_claim_reward)).performClick()
        assertTrue(claimed)
    }
}
