package com.example.familibalance.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familibalance.AppLocaleState
import com.example.familibalance.LocalizedAppContent
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Базовый контракт AppDialog: слоты рендерятся, dismiss-кнопка
 * вызывает onDismissRequest, AppLocaleContent-обёртка не ломает строковые ресурсы.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setLocale() {
        // LocalizedAppContent резолвит строки через AppLocaleState (дефолт "ru") —
        // приводим к локали устройства (en), чтобы string() совпадал с UI.
        AppLocaleState.set(ApplicationProvider.getApplicationContext(), "en")
    }

    @Test
    fun `renders title text and buttons`() {
        composeRule.setContent {
            LocalizedAppContent {
            AppDialog(
                onDismissRequest = {},
                title = { Text("Dialog title") },
                text = { Text("Dialog body") },
                confirmButton = { Text("Confirm") },
                dismissButton = { Text("Dismiss") }
            )
            }
        }

        composeRule.onNodeWithText("Dialog title").assertIsDisplayed()
        composeRule.onNodeWithText("Dialog body").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun `dismiss button triggers onDismissRequest`() {
        var dismissed = false
        composeRule.setContent {
            LocalizedAppContent {
            AppDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("T") },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) { Text("Close") }
                }
            )
            }
        }

        composeRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
