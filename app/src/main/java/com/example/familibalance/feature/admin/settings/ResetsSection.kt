package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.theme.PenaltyRed
import com.example.familibalance.ui.theme.SpaceBgStart
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.ui.theme.bounceClick

/** Сворачиваемый блок «Сбросы»: выбор операции из списка и кнопка полного сброса. */
fun LazyListScope.resetsSection(
    onRequestReset: (kind: ResetKind) -> Unit
) {
    item {
        var showResets by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(
                onClick = { showResets = !showResets },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PenaltyRed.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = GlassBackground,
                    contentColor = PenaltyRed
                )
            ) {
                Text(
                    text = if (showResets) {
                        stringResource(R.string.admin_set_resets_open)
                    } else {
                        stringResource(R.string.admin_set_resets_closed)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            if (showResets) {
                ResetsContent(
                    onRequestReset = onRequestReset
                )
            }
        }
    }
}

@Composable
private fun ResetsContent(
    onRequestReset: (kind: ResetKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val resetOptions = listOf(
            ResetKind.QUESTS to stringResource(R.string.admin_set_reset_quests_label),
            ResetKind.ACHIEVEMENTS to stringResource(R.string.admin_set_reset_achievements_label),
            ResetKind.HONEST to stringResource(R.string.admin_set_reset_honest_label),
            ResetKind.HISTORY to stringResource(R.string.admin_set_reset_history_label),
            ResetKind.BALANCE to stringResource(R.string.admin_set_reset_balance_label),
            ResetKind.STREAK to stringResource(R.string.admin_set_reset_streak_label),
            ResetKind.DREAM to stringResource(R.string.admin_set_reset_dream_label),
            ResetKind.THEME to stringResource(R.string.admin_set_reset_theme_label)
        )
        var selectedResetIndex by rememberSaveable { mutableIntStateOf(-1) }
        var resetMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { resetMenuExpanded = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Text(
                    text = resetOptions.getOrNull(selectedResetIndex)?.second
                        ?: stringResource(R.string.admin_set_reset_select),
                    color = if (selectedResetIndex >= 0) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(
                expanded = resetMenuExpanded,
                onDismissRequest = { resetMenuExpanded = false },
                containerColor = SpaceBgStart,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GlassStroke)
            ) {
                resetOptions.forEachIndexed { index, (_, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = TextPrimary, fontSize = 14.sp) },
                        onClick = {
                            selectedResetIndex = index
                            resetMenuExpanded = false
                        }
                    )
                }
            }
        }
        Button(
            onClick = {
                resetOptions.getOrNull(selectedResetIndex)?.first?.let { kind ->
                    onRequestReset(kind)
                }
            },
            enabled = selectedResetIndex >= 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = PenaltyRed,
                disabledContainerColor = GlassStroke
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).bounceClick()
        ) {
            Text(
                stringResource(R.string.admin_set_reset_execute),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onRequestReset(ResetKind.ALL) },
            colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).bounceClick()
        ) {
            Text(
                stringResource(R.string.admin_set_reset_all),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
