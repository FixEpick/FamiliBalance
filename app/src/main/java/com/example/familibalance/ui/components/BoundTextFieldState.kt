package com.example.familibalance.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * Мост между обычным String-состоянием (ViewModel / remember) и новым
 * state-based [TextFieldState], который требуют SecureTextField-компоненты.
 *
 * - Правки пользователя в поле → [onValueChange]
 * - Внешнее изменение [value] (сброс, синхронизация) → текст поля
 */
@Composable
fun rememberBoundTextFieldState(
    value: String,
    onValueChange: (String) -> Unit
): TextFieldState {
    val state = rememberTextFieldState()
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { onValueChange(it) }
    }
    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    return state
}
