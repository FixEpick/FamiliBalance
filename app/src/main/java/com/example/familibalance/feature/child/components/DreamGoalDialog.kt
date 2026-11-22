package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.theme.*

/** Диалог выбора/смены цели-мечты: название + сумма, подсказка про кулдаун 7 дней. */
@Composable
fun DreamGoalDialog(
    initialTitle: String,
    initialTarget: Int,
    onConfirm: (title: String, target: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var amountText by remember { mutableStateOf(if (initialTarget > 0) initialTarget.toString() else "") }
    val target = amountText.toIntOrNull() ?: 0
    val canConfirm = target > 0

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = LocalAppPalette.current.primaryNeon,
        focusedBorderColor = LocalAppPalette.current.primaryNeon,
        unfocusedBorderColor = GlassStroke,
        focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
        unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceBgStart,
        title = {
            AppLocaleContent {
                Text(
                    text = stringResource(R.string.dream_dialog_title),
                    color = AccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            AppLocaleContent {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        label = { Text(stringResource(R.string.dream_name_label)) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(7) },
                        label = { Text(currencyString(R.string.dream_amount_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dream_change_hint),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.dream_deduct_hint),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {
            AppLocaleContent {
                Button(
                    onClick = { onConfirm(title.trim(), target) },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = SpaceBgStart,
                        disabledContainerColor = GlassBackground,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            AppLocaleContent {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
            }
        }
    )
}
