package com.example.familibalance.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.GlassCardShape
import com.example.familibalance.ui.theme.glassCard
import androidx.compose.ui.Modifier

/**
 * Единый стиль диалогов приложения: тёмный космический фон
 * ([SpaceBgStart]) и полноширинный layout (usePlatformDefaultWidth = false).
 *
 * Каждый переданный слот автоматически оборачивается в [AppLocaleContent],
 * поэтому stringResource() внутри корректно резолвится в локаль приложения
 * (диалоги живут в отдельном окне с системной локалью).
 *
 * Внутренние AppLocaleContent-обёртки в слотах допустимы и безвредны
 * (вложенный провайдер с тем же значением), но не нужны.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    containerColor: Color = GlassBackground,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = Modifier.glassCard(),
        shape = GlassCardShape,
        containerColor = containerColor,
        title = title?.let { slot -> { AppLocaleContent { slot() } } },
        text = text?.let { slot -> { AppLocaleContent { slot() } } },
        confirmButton = { AppLocaleContent { confirmButton() } },
        dismissButton = dismissButton?.let { slot -> { AppLocaleContent { slot() } } }
    )
}
