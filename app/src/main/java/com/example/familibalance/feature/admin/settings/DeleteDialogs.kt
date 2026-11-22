package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.theme.PenaltyRed
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary

/** Меню удаления: аккаунт целиком или профиль конкретного ребёнка. */
@Composable
fun DeleteTargetPickerDialog(
    children: List<ChildProfileState>,
    onSelectAccount: () -> Unit,
    onSelectChild: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_set_delete_pick_title),
                color = PenaltyRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectAccount,
                    border = BorderStroke(1.dp, PenaltyRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.admin_set_delete_all), fontWeight = FontWeight.Bold)
                }
                if (children.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onSelectChild,
                        border = BorderStroke(1.dp, GlassStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.admin_set_delete_child_profile),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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

/** Выбор профиля ребёнка для удаления (несколько детей). */
@Composable
fun ChildDeletePickerDialog(
    children: List<ChildProfileState>,
    onSelect: (childId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_set_delete_pick_title),
                color = PenaltyRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { childProfile ->
                    OutlinedButton(
                        onClick = { onSelect(childProfile.id) },
                        border = BorderStroke(1.dp, GlassStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("👦 ${childDisplayName(childProfile)}", fontWeight = FontWeight.Bold)
                    }
                }
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

/** Подтверждение удаления: аккаунт родителя или профиль ребёнка. */
@Composable
fun DeleteConfirmDialog(
    target: String,
    targetChild: ChildProfileState?,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAccountDelete = target == "account"
    val targetName = targetChild?.let { childDisplayName(it) }.orEmpty()
    AppDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Text(
                if (isAccountDelete) stringResource(R.string.admin_set_delete_account_confirm_title)
                else stringResource(R.string.admin_set_delete_child_title, targetName),
                color = PenaltyRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (isAccountDelete) stringResource(R.string.admin_set_delete_account_confirm_msg)
                else stringResource(R.string.admin_set_delete_child_msg, targetName),
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PenaltyRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isAccountDelete) stringResource(R.string.admin_set_delete_forever)
                    else stringResource(R.string.admin_set_delete_child_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}
