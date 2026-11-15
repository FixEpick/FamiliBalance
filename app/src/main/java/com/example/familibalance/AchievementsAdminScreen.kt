package com.example.familibalance

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.ui.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.familibalance.ui.theme.*
import com.example.familibalance.viewmodel.actions.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()

    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newTarget by remember { mutableStateOf("") }
    var newReward by remember { mutableStateOf("") }
    var newBranch by remember { mutableStateOf(AchievementBranch.DIARY) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editAchievement by remember { mutableStateOf<AchievementConfig?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf("") }
    var editReward by remember { mutableStateOf("") }
    var editBranch by remember { mutableStateOf(AchievementBranch.DIARY) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    fun startEdit(ach: AchievementConfig) {
        editAchievement = ach
        editTitle = ach.title
        editDescription = ach.description
        editTarget = ach.targetCount.toString()
        editReward = ach.reward.toString()
        editBranch = ach.branch
        showEditDialog = true
    }

    fun cancelEdit() {
        editAchievement = null
        editTitle = ""
        editDescription = ""
        editTarget = ""
        editReward = ""
        editBranch = AchievementBranch.DIARY
        showEditDialog = false
    }

    fun saveEdit() {
        val title = editTitle.trim()
        if (title.isBlank()) return
        val description = editDescription.trim()
        val target = editTarget.toIntOrNull() ?: 0
        val reward = editReward.toIntOrNull() ?: 0
        editAchievement?.let { ach ->
            viewModel.updateAchievement(child.id, ach.id, title, description, target, reward, editBranch)
        }
        cancelEdit()
    }

    fun requestDelete(ach: AchievementConfig) {
        deleteTargetId = ach.id
        showDeleteConfirm = true
    }

    fun confirmDelete() {
        deleteTargetId?.let { id ->
            viewModel.deleteAchievement(child.id, id)
        }
        deleteTargetId = null
        showDeleteConfirm = false
    }

    fun createAchievement() {
        val title = newTitle.trim()
        if (title.isBlank()) return
        val description = newDescription.trim()
        val target = newTarget.toIntOrNull() ?: 0
        val reward = newReward.toIntOrNull() ?: 0
        viewModel.addAchievement(child.id, title, description, target, reward, newBranch)
        newTitle = ""
        newDescription = ""
        newTarget = ""
        newReward = ""
        newBranch = AchievementBranch.DIARY
    }

    val editDesc = stringResource(R.string.a11y_edit_button)
    val deleteDesc = stringResource(R.string.a11y_delete_button)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        item {
            Text(
                text = "✨ Создание нового достижения",
                color = AccentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

    item {
            Text(
                text = "📋 Все достижения (${child.achievements.size})",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(child.achievements) { achievement ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBackground),
            border = BorderStroke(1.dp, GlassStroke)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = achievement.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val branchLabel = when (achievement.branch) {
                        AchievementBranch.DIARY -> "📚 Дневник"
                        AchievementBranch.WARDROBE -> "🚪 Шкаф"
                        AchievementBranch.ROOM -> "🛏️ Комната"
                    }
                    Text(
                        text = "$branchLabel · Цель: ${achievement.targetCount} · Награда: ${achievement.reward} ₽",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.width(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = achievement.isVisible,
                        onCheckedChange = { viewModel.toggleAchievementVisibility(child.id, achievement.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentColor,
                            checkedTrackColor = GlassBackground,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassBackground.copy(alpha = 0.5f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.width(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { startEdit(achievement) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "✏️",
                            fontSize = 16.sp,
                            modifier = Modifier.semantics { contentDescription = editDesc }
                        )
                    }
                    TextButton(
                        onClick = { requestDelete(achievement) },
                        colors = ButtonDefaults.textButtonColors(contentColor = PenaltyRed),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "🗑️",
                            fontSize = 16.sp,
                            modifier = Modifier.semantics { contentDescription = deleteDesc }
                        )
                    }
                }
            }
        }
    }

    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { cancelEdit() },
            containerColor = SpaceBgStart,
            title = {
                Text("✏️ Редактирование достижения", color = AccentColor, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Название", color = TextPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = GlassBackground,
                            unfocusedContainerColor = GlassBackground,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = GlassStroke
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Описание", color = TextPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = GlassBackground,
                            unfocusedContainerColor = GlassBackground,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = GlassStroke
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editTarget,
                        onValueChange = { editTarget = it },
                        label = { Text("Цель (количество раз/пятёрок)", color = TextPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = GlassBackground,
                            unfocusedContainerColor = GlassBackground,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = GlassStroke
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editReward,
                        onValueChange = { editReward = it },
                        label = { Text("Награда ₽", color = TextPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = GlassBackground,
                            unfocusedContainerColor = GlassBackground,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = GlassStroke
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ветка достижения:", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val branches = listOf(
                            AchievementBranch.DIARY to "📚 Дневник",
                            AchievementBranch.WARDROBE to "🚪 Шкаф",
                            AchievementBranch.ROOM to "🛏️ Комната"
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
                    onClick = { saveEdit() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelEdit() }) {
                    Text("Отмена", color = TextSecondary)
                }
            }
        )
    }

    if (showDeleteConfirm && deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SpaceBgStart,
            title = {
                Text("Удаление достижения", color = PenaltyRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Вы уверены, что хотите удалить это достижение?", color = TextPrimary)
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            }
        )
    }

    }
@Composable
fun AchievementsEditorSubScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newTarget by remember { mutableStateOf("5") }
    var newReward by remember { mutableStateOf("50") }
    var newBranch by remember { mutableStateOf(AchievementBranch.DIARY) }
    var editingAch by remember { mutableStateOf<AchievementConfig?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf("") }
    var editReward by remember { mutableStateOf("") }
    var editBranch by remember { mutableStateOf(AchievementBranch.DIARY) }
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
                OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, GlassStroke)) { Text("← Назад", color = Color.White) }
                Spacer(Modifier.width(12.dp))
                Text("🏆 Редактор достижений", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
            ) { Text("➕ Создать новое достижение", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        items(child.achievements, key = { it.id }, contentType = { "achievement" }) { ach ->
            val branchLabel = when (ach.branch) {
                AchievementBranch.DIARY -> "📚 Дневник"
                AchievementBranch.WARDROBE -> "🚪 Шкаф"
                AchievementBranch.ROOM -> "🛏️ Комната"
            }
            EditorItemRow(
                title = ach.title, subtitle = "$branchLabel • Цель: ${ach.targetCount} • Награда: ${ach.reward} ₽",
                checked = ach.isVisible, switchColors = switchColors,
                onCheckedChange = { viewModel.toggleAchievementVisibility(child.id, ach.id, it) },
                onEdit = { editingAch = ach; editTitle = ach.title; editDesc = ach.description; editTarget = ach.targetCount.toString(); editReward = ach.reward.toString(); editBranch = ach.branch },
                onDelete = { deletingAch = ach }
            )
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SpaceBgStart,
            title = { Text("Новое достижение", color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    OutlinedTextField(value = newTarget, onValueChange = { newTarget = it }, label = { Text("Цель (кол-во)", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    OutlinedTextField(value = newReward, onValueChange = { newReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> "Дневник"; AchievementBranch.WARDROBE -> "Шкаф"; AchievementBranch.ROOM -> "Комната" }
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
                ) { Text("Создать", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Отмена", color = TextSecondary) } }
        )
    }
    editingAch?.let { ach ->
        AlertDialog(
            onDismissRequest = { editingAch = null },
            containerColor = SpaceBgStart,
            title = { Text("Редактировать достижение", color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    OutlinedTextField(value = editTarget, onValueChange = { editTarget = it }, label = { Text("Цель (кол-во)", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    OutlinedTextField(value = editReward, onValueChange = { editReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> "Дневник"; AchievementBranch.WARDROBE -> "Шкаф"; AchievementBranch.ROOM -> "Комната" }
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
                ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingAch = null }) { Text("Отмена", color = TextSecondary) } }
        )
    }
    deletingAch?.let { ach ->
        AlertDialog(
            onDismissRequest = { deletingAch = null },
            containerColor = SpaceBgStart,
            title = { Text("Удалить достижение «${ach.title}»?", color = PenaltyRed, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), onClick = { viewModel.deleteAchievement(child.id, ach.id); deletingAch = null }) {
                    Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingAch = null }) { Text("Отмена", color = TextSecondary) } }
        )
    }
}
