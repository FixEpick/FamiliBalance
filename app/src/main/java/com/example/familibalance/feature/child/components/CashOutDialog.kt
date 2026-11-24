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
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.components.appOutlinedFieldColorsNeon
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CloudBadge(type = CloudIconType.BANK, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.child_cashout_dialog_title),
                    color = LocalAppPalette.current.primaryNeon,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(currencyString(R.string.child_cashout_amount_label)) },
                    singleLine = true,
                    colors = appOutlinedFieldColorsNeon(),
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
                AppOutlinedField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text(stringResource(R.string.child_cashout_comment_label)) },
                    singleLine = true,
                    colors = appOutlinedFieldColorsNeon(),
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
                enabled = canSubmit && !isProcessing,
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
                Text(stringResource(R.string.child_cashout_submit), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
