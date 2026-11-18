package com.example.familibalance.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Локализуемый текст для ViewModel → UI.
 * ViewModel не имеет доступа к ресурсам, поэтому вместо String
 * эмитит [Res] (resId + аргументы) или [RawString] (динамический текст).
 */
sealed interface UiText {
    data class Res(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class RawString(val value: String) : UiText
}

/** Резолв в строку внутри Composable (Text, Toast в composable-контексте). */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(resId, *args.toTypedArray())
    is UiText.RawString -> value
}

/** Резолв в строку вне Composable (Toast в LaunchedEffect, колбэки). */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Res -> context.getString(resId, *args.toTypedArray())
    is UiText.RawString -> value
}
