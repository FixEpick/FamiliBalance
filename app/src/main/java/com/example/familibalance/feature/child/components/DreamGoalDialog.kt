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
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.components.appOutlinedFieldColorsNeon
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

    val fieldColors = appOutlinedFieldColorsNeon()

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CloudBadge(type = CloudIconType.GOAL, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dream_dialog_title),
                    color = AccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.imePadding()) {
                AppOutlinedField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text(stringResource(R.string.dream_name_label)) },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppOutlinedField(
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
        },
        confirmButton = {
            // Кнопки стопкой на всю ширину: «Сохранить» сверху,
            // «Отмена» по центру снизу — симметрично и без прилипания к краю.
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onConfirm(title.trim(), target) },
                    enabled = canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .accentButtonChrome()
                        .bounceClick(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = AccentOnColor,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = TextSecondary
                    ),
                    shape = AccentButtonShape
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
