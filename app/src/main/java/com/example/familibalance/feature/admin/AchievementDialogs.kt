package com.example.familibalance.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.AchievementBranch
import com.example.familibalance.data.model.AchievementConfig
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.PenaltyRed
import com.example.familibalance.ui.theme.SpaceBgStart
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.util.currencyString
import com.example.familibalance.util.systemDesc
import com.example.familibalance.util.systemTitle

/** Диалог редактирования достижения (экран списка достижений родителя). */
@Composable
fun AchievementEditDialog(
    achievement: AchievementConfig,
    onSave: (title: String, description: String, target: Int, reward: Int, branch: AchievementBranch) -> Unit,
    onDismiss: () -> Unit
) {
    var editTitle by remember(achievement.id) { mutableStateOf(achievement.title) }
    var editDescription by remember(achievement.id) { mutableStateOf(achievement.description) }
    var editTarget by remember(achievement.id) { mutableStateOf(achievement.targetCount.toString()) }
    var editReward by remember(achievement.id) { mutableStateOf(achievement.reward.toString()) }
    var editBranch by remember(achievement.id) { mutableStateOf(achievement.branch) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.achadm_edit_title),
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.imePadding()) {
                AppOutlinedField(
                    value = systemTitle(editTitle),
                    onValueChange = { editTitle = it },
                    label = { Text(stringResource(R.string.achadm_field_name), color = TextPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedField(
                    value = systemDesc(editDescription),
                    onValueChange = { editDescription = it },
                    label = { Text(stringResource(R.string.achadm_field_desc), color = TextPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedField(
                    value = editTarget,
                    onValueChange = { editTarget = it },
                    label = { Text(stringResource(R.string.achadm_field_target), color = TextPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedField(
                    value = editReward,
                    onValueChange = { editReward = it },
                    label = { Text(currencyString(R.string.achadm_field_reward), color = TextPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.achadm_branch_label),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val branches = listOf(
                            AchievementBranch.DIARY to stringResource(R.string.achadm_branch_diary),
                            AchievementBranch.WARDROBE to stringResource(R.string.achadm_branch_wardrobe),
                            AchievementBranch.ROOM to stringResource(R.string.achadm_branch_room)
                        )
                        branches.forEach { (branch, label) ->
                            val isSelected = editBranch == branch
                            Button(
                                onClick = { editBranch = branch },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) AccentColor else GlassBackground,
                                    contentColor = if (isSelected) SpaceBgStart else TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
        },
        confirmButton = {
            Button(
                onClick = {
                    val title = editTitle.trim()
                    if (title.isBlank()) return@Button
                    onSave(
                        title,
                        editDescription.trim(),
                        editTarget.toIntOrNull() ?: 0,
                        editReward.toIntOrNull() ?: 0,
                        editBranch
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}

/** Диалог подтверждения удаления достижения (экран списка достижений родителя). */
@Composable
fun AchievementDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.achadm_delete_title),
                color = PenaltyRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.achadm_delete_message), color = TextPrimary)
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
                Text(stringResource(R.string.admin_set_delete), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}
