package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

/** Диалог вывода наличных: сумма + комментарий, валидация по доступному балансу. */
@Composable
fun CashOutDialog(
    balance: Int,
    pendingTotal: Int,
    isProcessing: Boolean,
    onSubmit: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }

    val availableBalance = balance - pendingTotal
    val amountInt = amountText.toIntOrNull()
    val isAmountExceeded = amountInt != null && amountInt > availableBalance
    val canSubmit = amountInt != null && amountInt > 0 && amountInt <= availableBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceBgStart,
        title = {
            Text(
                text = stringResource(R.string.child_cashout_dialog_title),
                color = LocalAppPalette.current.primaryNeon,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.child_cashout_amount_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = LocalAppPalette.current.primaryNeon,
                        focusedBorderColor = LocalAppPalette.current.primaryNeon,
                        unfocusedBorderColor = GlassStroke,
                        focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
                        unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                if (isAmountExceeded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.child_cashout_insufficient),
                        color = PenaltyRed,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text(stringResource(R.string.child_cashout_comment_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = LocalAppPalette.current.primaryNeon,
                        focusedBorderColor = LocalAppPalette.current.primaryNeon,
                        unfocusedBorderColor = GlassStroke,
                        focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
                        unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSubmit) {
                        onSubmit(amountInt, commentText)
                    }
                },
                enabled = canSubmit && !isProcessing
            ) {
                Text(stringResource(R.string.child_cashout_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
