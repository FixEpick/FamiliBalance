package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.theme.PenaltyRed
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary

/** Подтверждение сброса данных ребёнка. */
@Composable
fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_set_confirm_title),
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.admin_set_confirm_message), color = TextPrimary)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    stringResource(R.string.admin_set_confirm),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}

/** Подтверждение выхода из аккаунта. */
@Composable
fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_set_logout_title),
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.admin_set_logout_message), color = TextPrimary)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PenaltyRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.admin_set_logout_confirm), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}
