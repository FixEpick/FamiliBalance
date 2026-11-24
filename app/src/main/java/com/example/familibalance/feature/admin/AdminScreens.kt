package com.example.familibalance.feature.admin

import com.example.familibalance.MainViewModel
import com.example.familibalance.R

import com.example.familibalance.data.model.*
import com.example.familibalance.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
private fun builtInFreqLabel(frequency: QuestFrequency): String = when (frequency) {
    QuestFrequency.DAILY -> stringResource(R.string.quested_freq_daily)
    QuestFrequency.WEEKLY -> stringResource(R.string.quested_freq_weekly)
    QuestFrequency.ONCE -> stringResource(R.string.quested_freq_once)
}

@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = GlassBackground,
    unfocusedContainerColor = GlassBackground,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = GlassStroke
)

@Composable
internal fun EditorItemRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchColors: SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().glassCard().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val editDesc = stringResource(R.string.a11y_edit_button)
        val deleteDesc = stringResource(R.string.a11y_delete_button)
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Text("✏️", fontSize = 15.sp, modifier = Modifier.semantics { contentDescription = editDesc })
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Text("🗑️", fontSize = 15.sp, modifier = Modifier.semantics { contentDescription = deleteDesc })
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = AccentColor, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = switchColors)
    }
}
@Composable
fun QuestsEditorSubScreen(viewModel: MainViewModel, onBack: () -> Unit) {
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
    var newReward by rememberSaveable { mutableStateOf("50") }
    var newFreq by rememberSaveable { mutableStateOf(QuestFrequency.DAILY) }
    var newRequiresPhoto by rememberSaveable { mutableStateOf(true) }
    var editKind by rememberSaveable { mutableStateOf<String?>(null) }
    var editQuestId by rememberSaveable { mutableStateOf("") }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editDesc by rememberSaveable { mutableStateOf("") }
    var editReward by rememberSaveable { mutableStateOf("") }
    var editFreq by rememberSaveable { mutableStateOf(QuestFrequency.DAILY) }
    var editRequiresPhoto by rememberSaveable { mutableStateOf(true) }
    var deleteConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deleteConfirmName by remember { mutableStateOf("") }
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
                Text(stringResource(R.string.admin_set_quests_editor), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp).bounceClick(),
                shape = AccentButtonShape,
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
            ) { Text(stringResource(R.string.quested_create), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        if (!child.isWardrobeDeleted) {
            item {
                EditorItemRow(
                    title = systemTitle(child.wardrobeTitle), subtitle = "${builtInFreqLabel(child.wardrobeFrequency)} • ${child.wardrobeReward} ${rewardCurrencySymbol()}",
                    checked = child.isWardrobeVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "closet", it) },
                    onEdit = { editKind = "wardrobe"; editTitle = child.wardrobeTitle; editDesc = child.wardrobeDesc; editReward = child.wardrobeReward.toString(); editFreq = child.wardrobeFrequency; editRequiresPhoto = child.wardrobeRequiresPhoto },
                    onDelete = { deleteConfirmName = child.wardrobeTitle; deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "wardrobe") } }
                )
            }
        }
        if (!child.isRoomDeleted) {
            item {
                EditorItemRow(
                    title = systemTitle(child.roomTitle), subtitle = "${builtInFreqLabel(child.roomFrequency)} • ${child.roomReward} ${rewardCurrencySymbol()}",
                    checked = child.isRoomVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "room", it) },
                    onEdit = { editKind = "room"; editTitle = child.roomTitle; editDesc = child.roomDesc; editReward = child.roomReward.toString(); editFreq = child.roomFrequency; editRequiresPhoto = child.roomRequiresPhoto },
                    onDelete = { deleteConfirmName = child.roomTitle; deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "room") } }
                )
            }
        }
        if (!child.isDiaryDeleted) {
            item {
                EditorItemRow(
                    title = systemTitle(child.diaryTitle), subtitle = "${builtInFreqLabel(child.diaryFrequency)} • ${stringResource(R.string.quested_by_grades)}",
                    checked = child.isDiaryVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "diary", it) },
                    onEdit = { editKind = "diary"; editTitle = child.diaryTitle; editDesc = child.diaryDesc; editReward = ""; editFreq = child.diaryFrequency; editRequiresPhoto = child.diaryRequiresPhoto },
                    onDelete = { deleteConfirmName = child.diaryTitle; deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "diary") } }
                )
            }
        }
        items(child.customQuests, key = { it.id }) { q ->
            val badge = when (q.frequency) {
                QuestFrequency.ONCE -> stringResource(R.string.quested_freq_once)
                QuestFrequency.DAILY -> stringResource(R.string.quested_freq_daily)
                QuestFrequency.WEEKLY -> stringResource(R.string.quested_freq_weekly)
            }
            EditorItemRow(
                title = q.title, subtitle = "$badge • ${q.reward} ${rewardCurrencySymbol()}",
                checked = q.isVisible, switchColors = switchColors,
                onCheckedChange = { viewModel.toggleQuestVisibility(child.id, q.id, it) },
                onEdit = { editKind = "custom"; editQuestId = q.id; editTitle = q.title; editDesc = q.description; editReward = q.reward.toString(); editFreq = q.frequency; editRequiresPhoto = q.requiresPhoto },
                onDelete = { deleteConfirmName = q.title; deleteConfirmAction = { viewModel.deleteCustomQuest(child.id, q.id) } }
            )
        }
    }
    if (showCreateDialog) {
        AppDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.quested_new_title), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppOutlinedField(value = newTitle, onValueChange = { newTitle = it }, label = { Text(stringResource(R.string.achadm_field_name), color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    AppOutlinedField(value = newDesc, onValueChange = { newDesc = it }, label = { Text(stringResource(R.string.achadm_field_desc), color = TextPrimary) }, colors = editorFieldColors())
                    AppOutlinedField(value = newReward, onValueChange = { newReward = it }, label = { Text(currencyString(R.string.achadm_field_reward), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Text(stringResource(R.string.quested_frequency), color = TextPrimary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { newFreq = QuestFrequency.ONCE }, border = BorderStroke(1.dp, if (newFreq == QuestFrequency.ONCE) AccentColor else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.quested_freq_once), color = if (newFreq == QuestFrequency.ONCE) AccentColor else Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { newFreq = QuestFrequency.DAILY }, border = BorderStroke(1.dp, if (newFreq == QuestFrequency.DAILY) AccentColor else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.quested_freq_daily), color = if (newFreq == QuestFrequency.DAILY) AccentColor else Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { newFreq = QuestFrequency.WEEKLY }, border = BorderStroke(1.dp, if (newFreq == QuestFrequency.WEEKLY) AccentColor else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.quested_freq_weekly), color = if (newFreq == QuestFrequency.WEEKLY) AccentColor else Color.White, fontSize = 13.sp)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = newRequiresPhoto,
                            onCheckedChange = { newRequiresPhoto = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Text(
                            text = stringResource(R.string.admin_set_bonus_photo),
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = AccentColor.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    shape = AccentButtonShape,
                    modifier = Modifier.bounceClick(),
                    onClick = {
                        viewModel.addCustomQuest(child.id, newTitle.trim(), newReward.trim().toIntOrNull() ?: 50, newFreq, newDesc.trim(), newRequiresPhoto)
                        newTitle = ""; newDesc = ""; newReward = "50"; newRequiresPhoto = true; showCreateDialog = false
                    }
                ) { Text(stringResource(R.string.admin_set_create), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
    if (editKind != null) {
        AppDialog(
            onDismissRequest = { editKind = null },
            title = { Text(stringResource(R.string.quested_edit_title), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Для системных (не изменённых родителем) названий/описаний
                    // показываем перевод; state хранит исходный русский текст,
                    // поэтому сохранение без правок не ломает data-контракт.
                    AppOutlinedField(value = systemTitle(editTitle), onValueChange = { editTitle = it }, label = { Text(stringResource(R.string.achadm_field_name), color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    AppOutlinedField(value = systemDesc(editDesc), onValueChange = { editDesc = it }, label = { Text(stringResource(R.string.achadm_field_desc), color = TextPrimary) }, colors = editorFieldColors())
                    if (editKind != "diary") {
                        AppOutlinedField(value = editReward, onValueChange = { editReward = it }, label = { Text(currencyString(R.string.achadm_field_reward), color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    }
                    Text(stringResource(R.string.quested_frequency), color = TextPrimary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editFreq = QuestFrequency.ONCE },
                            border = BorderStroke(1.dp, if (editFreq == QuestFrequency.ONCE) AccentColor else GlassStroke),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.quested_freq_once), color = if (editFreq == QuestFrequency.ONCE) AccentColor else Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { editFreq = QuestFrequency.DAILY },
                            border = BorderStroke(1.dp, if (editFreq == QuestFrequency.DAILY) AccentColor else GlassStroke),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.quested_freq_daily), color = if (editFreq == QuestFrequency.DAILY) AccentColor else Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { editFreq = QuestFrequency.WEEKLY },
                            border = BorderStroke(1.dp, if (editFreq == QuestFrequency.WEEKLY) AccentColor else GlassStroke),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.quested_freq_weekly), color = if (editFreq == QuestFrequency.WEEKLY) AccentColor else Color.White, fontSize = 13.sp)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = editRequiresPhoto,
                            onCheckedChange = { editRequiresPhoto = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Text(
                            text = stringResource(R.string.admin_set_bonus_photo),
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = AccentColor.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    shape = AccentButtonShape,
                    modifier = Modifier.bounceClick(),
                    onClick = {
                        val r = editReward.trim().toIntOrNull() ?: 50
                        when (editKind) {
                            "wardrobe" -> {
                                viewModel.updateBuiltInQuest(child.id, "wardrobe", editTitle.trim(), editDesc.trim(), r)
                                viewModel.updateBuiltInQuestFrequency(child.id, "wardrobe", editFreq)
                                viewModel.updateBuiltInQuestPhotoRequirement(child.id, "wardrobe", editRequiresPhoto)
                            }
                            "room" -> {
                                viewModel.updateBuiltInQuest(child.id, "room", editTitle.trim(), editDesc.trim(), r)
                                viewModel.updateBuiltInQuestFrequency(child.id, "room", editFreq)
                                viewModel.updateBuiltInQuestPhotoRequirement(child.id, "room", editRequiresPhoto)
                            }
                            "diary" -> {
                                viewModel.updateBuiltInQuest(child.id, "diary", editTitle.trim(), editDesc.trim(), r)
                                viewModel.updateBuiltInQuestFrequency(child.id, "diary", editFreq)
                                viewModel.updateBuiltInQuestPhotoRequirement(child.id, "diary", editRequiresPhoto)
                            }
                            "custom" -> viewModel.updateCustomQuest(child.id, editQuestId, editTitle.trim(), editDesc.trim(), r, editFreq, editRequiresPhoto)
                        }
                        editKind = null
                    }
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editKind = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
    deleteConfirmAction?.let { action ->
        AppDialog(
            onDismissRequest = { deleteConfirmAction = null },
            title = { Text(stringResource(R.string.quested_delete_title, systemTitle(deleteConfirmName)), color = PenaltyRed, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), onClick = { action(); deleteConfirmAction = null }) {
                    Text(stringResource(R.string.admin_set_delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmAction = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
}
