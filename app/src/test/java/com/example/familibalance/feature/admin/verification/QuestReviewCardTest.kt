package com.example.familibalance.feature.admin.verification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familibalance.R
import com.example.familibalance.AppLocaleState
import com.example.familibalance.LocalizedAppContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Критический путь «проверка квеста» (родитель): approve начисляет
 * награду, reject открывает диалог причины и передаёт её в onReject.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QuestReviewCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setLocale() {
        AppLocaleState.set(ApplicationProvider.getApplicationContext(), "en")
    }

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    @Test
    fun `approve button calls onApprove`() {
        var approved = false
        composeRule.setContent {
            LocalizedAppContent {
            QuestVerificationCard(
                title = "Clean room",
                reward = "50",
                description = "",
                photoUrl = null,
                onApprove = { approved = true },
                onReject = {}
            )
            }
        }

        composeRule.onNodeWithText(string(R.string.admin_approve)).performClick()
        assertTrue(approved)
    }

    @Test
    fun `reject opens reason dialog and submits reason`() {
        var rejectedReason: String? = null
        composeRule.setContent {
            LocalizedAppContent {
            QuestVerificationCard(
                title = "Clean room",
                reward = "50",
                description = "",
                photoUrl = null,
                onApprove = {},
                onReject = { rejectedReason = it }
            )
            }
        }

        // Открываем диалог отклонения.
        composeRule.onNodeWithText(string(R.string.admin_reject)).performClick()
        composeRule.onNodeWithText(string(R.string.admin_reject_reason_title)).assertIsDisplayed()

        // Вводим причину и подтверждаем — текст уходит в onReject.
        // «Reject» встречается дважды: кнопка карточки и confirm диалога —
        // диалоговое окно идёт позже в semantics-дереве.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Not done")
        val rejectNodes = composeRule.onAllNodesWithText(string(R.string.admin_reject))
        rejectNodes[rejectNodes.fetchSemanticsNodes().size - 1].performClick()

        assertEquals("Not done", rejectedReason)
    }
}
