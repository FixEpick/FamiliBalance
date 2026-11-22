package com.example.familibalance.feature.admin

import com.example.familibalance.MainViewModel
import com.example.familibalance.R

import com.example.familibalance.data.model.*
import com.example.familibalance.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.theme.*
import com.example.familibalance.viewmodel.actions.*

@Composable
fun AchievementsEditorSubScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
    if (child == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.admin_no_data),
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newDesc by rememberSaveable { mutableStateOf("") }
    var newTarget by rememberSaveable { mutableStateOf("5") }
    var newReward by rememberSaveable { mutableStateOf("50") }
    var newBranch by rememberSaveable { mutableStateOf(AchievementBranch.DIARY) }
    var editingAch by remember { mutableStateOf<AchievementConfig?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editDesc by rememberSaveable { mutableStateOf("") }
    var editTarget by rememberSaveable { mutableStateOf("") }
    var editReward by rememberSaveable { mutableStateOf("") }
    var editBranch by rememberSaveable { mutableStateOf(AchievementBranch.DIARY) }
    var deletingAch by remember { mutableStateOf<AchievementConfig?>(null) }
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = AccentColor,
        checkedTrackColor = AccentColor.copy(alpha = 0.35f),
        checkedBorderColor = AccentColor,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = GlassBackground.copy(alpha = 0.5f),
        uncheckedBorderColor = GlassStroke
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, GlassStroke)) { Text(stringResource(R.string.common_back), color = Color.White) }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.admin_set_achievements_editor), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
            ) { Text(stringResource(R.string.achadm_create_btn), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        items(child.achievements, key = { it.id }, contentType = { "achievement" }) { ach ->
            val branchLabel = when (ach.branch) {
                AchievementBranch.DIARY -> stringResource(R.string.achadm_branch_diary)
                AchievementBranch.WARDROBE -> stringResource(R.string.achadm_branch_wardrobe)
                AchievementBranch.ROOM -> stringResource(R.string.achadm_branch_room)
            }
            EditorItemRow(
                title = systemTitle(ach.title), subtitle = currencyString(R.string.achadm_meta, branchLabel, ach.targetCount, ach.reward),
                checked = ach.isVisible, switchColors = switchColors,
                onCheckedChange = { viewModel.toggleAchievementVisibility(child.id, ach.id, it) },
                onEdit = { editingAch = ach; editTitle = ach.title; editDesc = ach.description; editTarget = ach.targetCount.toString(); editReward = ach.reward.toString(); editBranch = ach.branch },
                onDelete = { deletingAch = ach }
            )
        }
    }
    if (showCreateDialog) {
        AppDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.achadm_new_title), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppOutlinedField(value = newTitle, onValueChange = { newTitle = it }, label = { Text(stringResource(R.string.achadm_field_name), color = TextPrimary) }, singleLine = true)
                    AppOutlinedField(value = newDesc, onValueChange = { newDesc = it }, label = { Text(stringResource(R.string.achadm_field_desc), color = TextPrimary) })
                    AppOutlinedField(value = newTarget, onValueChange = { newTarget = it }, label = { Text(stringResource(R.string.achadm_field_target_short), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    AppOutlinedField(value = newReward, onValueChange = { newReward = it }, label = { Text(currencyString(R.string.achadm_field_reward), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> stringResource(R.string.achadm_b_diary); AchievementBranch.WARDROBE -> stringResource(R.string.achadm_b_wardrobe); AchievementBranch.ROOM -> stringResource(R.string.achadm_b_room) }
                            OutlinedButton(onClick = { newBranch = b }, border = BorderStroke(1.dp, if (newBranch == b) AccentColor else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(label, fontSize = 12.sp, color = if (newBranch == b) AccentColor else Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = AccentColor.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    onClick = {
                        viewModel.addAchievement(child.id, newTitle.trim(), newDesc.trim(), newTarget.trim().toIntOrNull() ?: 1, newReward.trim().toIntOrNull() ?: 20, newBranch)
                        newTitle = ""; newDesc = ""; newTarget = "5"; newReward = "50"; showCreateDialog = false
                    }
                ) { Text(stringResource(R.string.admin_set_create), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
    editingAch?.let { ach ->
        AppDialog(
            onDismissRequest = { editingAch = null },
            title = { Text(stringResource(R.string.achadm_edit_dialog_title), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppOutlinedField(value = systemTitle(editTitle), onValueChange = { editTitle = it }, label = { Text(stringResource(R.string.achadm_field_name), color = TextPrimary) }, singleLine = true)
                    AppOutlinedField(value = systemDesc(editDesc), onValueChange = { editDesc = it }, label = { Text(stringResource(R.string.achadm_field_desc), color = TextPrimary) })
                    AppOutlinedField(value = editTarget, onValueChange = { editTarget = it }, label = { Text(stringResource(R.string.achadm_field_target_short), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    AppOutlinedField(value = editReward, onValueChange = { editReward = it }, label = { Text(currencyString(R.string.achadm_field_reward), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> stringResource(R.string.achadm_b_diary); AchievementBranch.WARDROBE -> stringResource(R.string.achadm_b_wardrobe); AchievementBranch.ROOM -> stringResource(R.string.achadm_b_room) }
                            OutlinedButton(onClick = { editBranch = b }, border = BorderStroke(1.dp, if (editBranch == b) AccentColor else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(label, fontSize = 12.sp, color = if (editBranch == b) AccentColor else Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = AccentColor.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    onClick = {
                        viewModel.updateAchievement(child.id, ach.id, editTitle.trim(), editDesc.trim(), editTarget.trim().toIntOrNull() ?: ach.targetCount, editReward.trim().toIntOrNull() ?: ach.reward, editBranch)
                        editingAch = null
                    }
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingAch = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
    deletingAch?.let { ach ->
        AppDialog(
            onDismissRequest = { deletingAch = null },
            title = { Text(stringResource(R.string.achadm_delete_confirm, systemTitle(ach.title)), color = PenaltyRed, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), onClick = { viewModel.deleteAchievement(child.id, ach.id); deletingAch = null }) {
                    Text(stringResource(R.string.admin_set_delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingAch = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
}
