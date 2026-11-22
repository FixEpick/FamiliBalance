package com.example.familibalance.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.CardBgDark
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.GlassStroke

/**
 * Цвета поля ввода в стиле админ-редакторов: стеклянный фон,
 * неоновая рамка при фокусе.
 */
@Composable
fun appOutlinedFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = GlassBackground,
    unfocusedContainerColor = GlassBackground,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = GlassStroke
)

/**
 * Цвета поля ввода в стиле детских диалогов: тёмная карточка,
 * рамка и курсор в цвете палитры приложения.
 */
@Composable
fun appOutlinedFieldColorsNeon(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = com.example.familibalance.ui.theme.LocalAppPalette.current.primaryNeon,
    focusedBorderColor = com.example.familibalance.ui.theme.LocalAppPalette.current.primaryNeon,
    unfocusedBorderColor = GlassStroke,
    focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
    unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
)

/**
 * Единое поле ввода приложения: скругление 10.dp по умолчанию,
 * цвета по умолчанию — [appOutlinedFieldColors] (стеклянный стиль).
 */
@Composable
fun AppOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    colors: TextFieldColors = appOutlinedFieldColors()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = shape,
        colors = colors
    )
}
