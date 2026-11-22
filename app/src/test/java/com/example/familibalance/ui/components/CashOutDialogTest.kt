package com.example.familibalance.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familibalance.R
import com.example.familibalance.AppLocaleState
import com.example.familibalance.LocalizedAppContent
import com.example.familibalance.feature.child.components.CashOutDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Критический путь «вывод средств» (ребёнок): валидация суммы
 * по доступному балансу и вызов onSubmit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CashOutDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setLocale() {
        AppLocaleState.set(ApplicationProvider.getApplicationContext(), "en")
    }

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    @Test
    fun `submit disabled until valid amount entered`() {
        var submitted = false
        composeRule.setContent {
            LocalizedAppContent {
            CashOutDialog(
                balance = 100,
                pendingTotal = 0,
                isProcessing = false,
                onSubmit = { _, _ -> submitted = true },
                onDismiss = {}
            )
            }
        }

        val submitText = string(R.string.child_cashout_submit)
        composeRule.onNodeWithText(submitText).assertIsDisplayed().assertIsNotEnabled()

        // Сумма больше баланса — предупреждение и кнопка остаётся выключенной.
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("150")
        composeRule.onNodeWithText(string(R.string.child_cashout_insufficient)).assertIsDisplayed()
        composeRule.onNodeWithText(submitText).assertIsNotEnabled()

        // Валидная сумма — кнопка активна, клик вызывает onSubmit.
        fields[0].performTextClearance()
        fields[0].performTextInput("50")
        composeRule.onNodeWithText(submitText).assertIsEnabled().performClick()
        assertTrue(submitted)
    }

    @Test
    fun `pending requests reduce available balance`() {
        composeRule.setContent {
            LocalizedAppContent {
            CashOutDialog(
                balance = 100,
                pendingTotal = 80,
                isProcessing = false,
                onSubmit = { _, _ -> },
                onDismiss = {}
            )
            }
        }

        // Доступно 20: сумма 50 превышает остаток.
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("50")
        composeRule.onNodeWithText(string(R.string.child_cashout_insufficient)).assertIsDisplayed()
    }
}
