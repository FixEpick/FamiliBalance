package com.example.familibalance

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.ui.screens.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.CashoutStatus
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.ui.auth.ChangeParentPinDialog
import com.example.familibalance.ui.auth.SetChildPinDialog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        item {
            Text(
                text = "✨ Создание нового достижения",
                color = NeonCyan,
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
                            checkedThumbColor = NeonCyan,
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
                        Text("✏️", fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = { requestDelete(achievement) },
                        colors = ButtonDefaults.textButtonColors(contentColor = PenaltyRed),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🗑️", fontSize = 16.sp)
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
                Text("✏️ Редактирование достижения", color = NeonCyan, fontWeight = FontWeight.Bold)
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
                            focusedBorderColor = NeonCyan,
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
                            focusedBorderColor = NeonCyan,
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
                            focusedBorderColor = NeonCyan,
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
                            focusedBorderColor = NeonCyan,
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
                                    containerColor = if (isSelected) NeonCyan else GlassBackground,
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
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart),
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    viewModel: MainViewModel,
    onOpenQuestsEditor: () -> Unit = {},
    onOpenAchievementsEditor: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
        ?: ChildProfileState(id = "user_1", name = "Игрок", avatar = "🧒")
    var showAddBonusDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<String?>(null) } // "parent" или childId
    var pendingResetAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // --- Запросы на добавление детей (status = "pending") ---
    var pendingRequests by remember { mutableStateOf<List<FamilyProfileRow>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun loadPendingRequests() {
        val supabase = SupabaseManager.client ?: return
        val me = runCatching { supabase.auth.currentUserOrNull() }.getOrNull() ?: return
        val myEmail = me.email ?: return
        val rows = runCatching {
            supabase.from("family_profiles").select().decodeList<FamilyProfileRow>()
        }.getOrNull() ?: return
        pendingRequests = rows.filter {
            it.status == "pending" && it.parentEmail.equals(myEmail, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        loadPendingRequests()
        // Периодическое обновление списка запросов
        while (true) {
            delay(10_000)
            loadPendingRequests()
        }
    }

    fun approveRequest(row: FamilyProfileRow) {
        val supabase = SupabaseManager.client ?: return
        scope.launch {
            val me = runCatching { supabase.auth.currentUserOrNull() }.getOrNull() ?: return@launch
            runCatching {
                supabase.from("family_profiles").update({
                    set("parent_user_id", me.id)
                    set("parent_email", me.email)
                    set("status", "active")
                }) {
                    filter { eq("user_id", row.user_id) }
                }
            }
            // Если профиль уже есть локально — сразу помечаем активным и привязываем
            AppSharedState.children[row.user_id]?.let { child ->
                viewModel.updateChildProfile(
                    child.copy(parentUserId = me.id, status = "active")
                )
            }
            pendingRequests = pendingRequests.filterNot { it.user_id == row.user_id }
            android.widget.Toast.makeText(
                context,
                "Запрос одобрен — ребёнок добавлен в семью",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestChildName(row: FamilyProfileRow): String {
        return runCatching {
            Json.parseToJsonElement(row.state_json)
                .jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "Игрок"
    }

    fun requestChildAvatar(row: FamilyProfileRow): String {
        return runCatching {
            Json.parseToJsonElement(row.state_json)
                .jsonObject["avatar"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "🧒"
    }

    fun requestReset(action: () -> Unit) {
        pendingResetAction = action
        showConfirmDialog = true
    }

    @Composable
    fun ResetRow(label: String, buttonText: String, onConfirm: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
            Button(
                onClick = { requestReset(onConfirm) },
                colors = ButtonDefaults.buttonColors(containerColor = GlassBackground),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GlassStroke)
            ) {
                Text(buttonText, color = TextPrimary, fontSize = 12.sp)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp)
    ) {
        item {
            OutlinedButton(
                onClick = onOpenQuestsEditor,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = NeonCyan)
            ) {
                Text("📋 Редактор заданий", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            OutlinedButton(
                onClick = onOpenAchievementsEditor,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = NeonCyan)
            ) {
                Text("🏆 Редактор достижений", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            OutlinedButton(
                onClick = { showAddBonusDialog = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StatusYellow),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = StatusYellow)
            ) {
                Text("🎁 Выдать разовый бонус-квест", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

item {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = "📨 Запросы на добавление", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
if (pendingRequests.isEmpty()) {
    item {
        Text(
            text = "Нет новых запросов",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
} else {
    items(pendingRequests, key = { it.user_id }) { row ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = GlassBackground,
            border = BorderStroke(1.dp, StatusYellow.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(requestChildAvatar(row), fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${requestChildName(row)} хочет присоединиться к семье",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Email: ${row.parentEmail}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = { approveRequest(row) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Одобрить", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
    item { Spacer(modifier = Modifier.height(12.dp)) }
}
item {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = "🔐 Безопасность", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
item {
    OutlinedButton(
        onClick = { showPinPicker = true },
        border = BorderStroke(1.dp, NeonCyan),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("🔐 Управление ПИН-кодами", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(20.dp))
}
item {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = "Очистка и сброс данных", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
item {
    ResetRow("Очистка истории", "Очистить") { viewModel.clearHistory(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс достижений", "Сбросить") { viewModel.resetAchievements(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Обнуление заданий", "Обнулить") { viewModel.resetActiveQuests(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Обнуление баланса", "Обнулить") { viewModel.resetBalance(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс стрика и времени", "Сбросить") { viewModel.resetStreakAndTime(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс счётчиков оценок", "Сбросить") { viewModel.resetGradeCounters(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс цели-мечты", "Сбросить") { viewModel.resetDream(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс купленного времени", "Сбросить") { viewModel.resetBoughtTime(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс честного признания", "Сбросить") { viewModel.resetHonestAdmission(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс фото квестов", "Сбросить") { viewModel.resetQuestPhotos(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс темы", "Сбросить") { viewModel.resetTheme(child.id) }
    Spacer(modifier = Modifier.height(12.dp))
    ResetRow("Сброс видимости квестов", "Сбросить") { viewModel.resetQuestVisibility(child.id) }
    Spacer(modifier = Modifier.height(20.dp))
    OutlinedButton(
        onClick = { viewModel.forceSync() },
        border = BorderStroke(1.dp, NeonCyan),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text("🔄 Синхронизировать с сервером", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = { requestReset { viewModel.resetAllChildData(child.id) } },
        colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("Обнулить всё", color = Color.White, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(20.dp))
    OutlinedButton(
        onClick = onLogout,
        border = BorderStroke(1.dp, PenaltyRed),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("🚪 Выйти из аккаунта", fontWeight = FontWeight.Bold)
    }
}
}
// Выбор, чей ПИН-код менять
if (showPinPicker) {
    val childrenList = familyState.children.values.sortedBy { it.name }
    AlertDialog(
        onDismissRequest = { showPinPicker = false },
        containerColor = SpaceBgStart,
        title = { Text("🔐 Управление ПИН-кодами", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            val options = listOf("parent" to "🛡️ Мой ПИН (Родитель)") +
                childrenList.map { it.id to "👦 ${it.name}" }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (target, label) ->
                OutlinedButton(
                    onClick = {
                        showPinPicker = false
                        pinTarget = target
                    },
                    border = BorderStroke(1.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showPinPicker = false }) {
                Text("Отмена", color = TextSecondary)
            }
        }
    )
}

// Родительский ПИН
if (pinTarget == "parent") {
    ChangeParentPinDialog(
        onDismiss = { pinTarget = null },
        onSaved = { pinTarget = null }
    )
}

// Детский ПИН
pinTarget?.let { targetId ->
    val targetChild = familyState.children[targetId]
    if (targetChild != null) {
        SetChildPinDialog(
            childName = targetChild.name,
            onPinEntered = { newPin ->
                viewModel.updateChildPin(targetId, newPin)
                android.widget.Toast.makeText(
                    context,
                    "ПИН-код для ${targetChild.name} обновлён",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { pinTarget = null }
        )
    } else {
        pinTarget = null
    }
}
if (showAddBonusDialog) {
    var bonusTitle by remember { mutableStateOf("") }
    var bonusDesc by remember { mutableStateOf("") }
    var bonusReward by remember { mutableStateOf("50") }
    AlertDialog(
        onDismissRequest = { showAddBonusDialog = false },
        containerColor = SpaceBgStart,
        title = { Text("🎁 Новый бонус-квест для ${child.name}", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = bonusTitle, onValueChange = { bonusTitle = it }, label = { Text("Название квеста", color = TextPrimary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
                OutlinedTextField(value = bonusDesc, onValueChange = { bonusDesc = it }, label = { Text("Описание квеста", color = TextPrimary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
                OutlinedTextField(value = bonusReward, onValueChange = { bonusReward = it }, label = { Text("Награда (₽)", color = TextPrimary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rewardInt = bonusReward.toIntOrNull() ?: 50
                    if (bonusTitle.isNotBlank()) {
                        viewModel.addBonusQuest(child.id, bonusTitle.trim(), rewardInt, bonusDesc.trim())
                        viewModel.saveAndSync()
                        showAddBonusDialog = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Создать", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { showAddBonusDialog = false }) { Text("Отмена", color = TextSecondary) } }
    )
}
if (showConfirmDialog && pendingResetAction != null) {
    AlertDialog(
        onDismissRequest = { showConfirmDialog = false; pendingResetAction = null },
        containerColor = SpaceBgStart,
        title = { Text("Подтверждение", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = { Text("Вы уверены? Это действие нельзя отменить", color = TextPrimary) },
        confirmButton = {
            Button(onClick = { pendingResetAction?.invoke(); showConfirmDialog = false; pendingResetAction = null }, colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), shape = RoundedCornerShape(10.dp)) {
                Text("Подтвердить", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { showConfirmDialog = false; pendingResetAction = null }) { Text("Отмена", color = TextSecondary) } }
    )
}
}

@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = GlassBackground,
    unfocusedContainerColor = GlassBackground,
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = GlassStroke
)

@Composable
private fun EditorItemRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchColors: SwitchColors,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(GlassBackground, RoundedCornerShape(16.dp)).border(1.dp, GlassStroke, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Text("✏️", fontSize = 15.sp) }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Text("🗑️", fontSize = 15.sp) }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = NeonCyan, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = switchColors)
    }
}
@Composable
fun QuestsEditorSubScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newReward by remember { mutableStateOf("50") }
    var newFreq by remember { mutableStateOf(QuestFrequency.DAILY) }
    var editKind by remember { mutableStateOf<String?>(null) }
    var editQuestId by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editReward by remember { mutableStateOf("") }
    var deleteConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = NeonCyan,
        checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
        checkedBorderColor = NeonCyan,
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
                Text("📋 Редактор заданий", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
            ) { Text("➕ Создать новое задание", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        if (!child.isWardrobeDeleted) {
            item {
                EditorItemRow(
                    title = child.wardrobeTitle, subtitle = "Раз в день • ${child.wardrobeReward} ₽",
                    checked = child.isWardrobeVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "closet", it) },
                    onEdit = { editKind = "wardrobe"; editTitle = child.wardrobeTitle; editDesc = child.wardrobeDesc; editReward = child.wardrobeReward.toString() },
                    onDelete = { deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "wardrobe") } }
                )
            }
        }
        if (!child.isRoomDeleted) {
            item {
                EditorItemRow(
                    title = child.roomTitle, subtitle = "Раз в день • ${child.roomReward} ₽",
                    checked = child.isRoomVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "room", it) },
                    onEdit = { editKind = "room"; editTitle = child.roomTitle; editDesc = child.roomDesc; editReward = child.roomReward.toString() },
                    onDelete = { deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "room") } }
                )
            }
        }
        if (!child.isDiaryDeleted) {
            item {
                EditorItemRow(
                    title = child.diaryTitle, subtitle = "Раз в неделю • по оценкам",
                    checked = child.isDiaryVisible, switchColors = switchColors,
                    onCheckedChange = { viewModel.toggleQuestVisibility(child.id, "diary", it) },
                    onEdit = { editKind = "diary"; editTitle = child.diaryTitle; editDesc = child.diaryDesc; editReward = "" },
                    onDelete = { deleteConfirmAction = { viewModel.deleteBuiltInQuest(child.id, "diary") } }
                )
            }
        }
        items(child.customQuests, key = { it.id }) { q ->
            val badge = when (q.frequency) {
                QuestFrequency.ONCE -> "Разовое"
                QuestFrequency.DAILY -> "Раз в день"
                QuestFrequency.WEEKLY -> "Раз в неделю"
            }
            EditorItemRow(
                title = q.title, subtitle = "$badge • ${q.reward} ₽",
                checked = q.isVisible, switchColors = switchColors,
                onCheckedChange = { viewModel.toggleQuestVisibility(child.id, q.id, it) },
                onEdit = { editKind = "custom"; editQuestId = q.id; editTitle = q.title; editDesc = q.description; editReward = q.reward.toString() },
                onDelete = { deleteConfirmAction = { viewModel.deleteCustomQuest(child.id, q.id) } }
            )
        }
    }
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SpaceBgStart,
            title = { Text("Новое задание", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    OutlinedTextField(value = newReward, onValueChange = { newReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { newFreq = QuestFrequency.DAILY }, border = BorderStroke(1.dp, if (newFreq == QuestFrequency.DAILY) NeonCyan else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("Ежедневный", color = if (newFreq == QuestFrequency.DAILY) NeonCyan else Color.White, fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { newFreq = QuestFrequency.WEEKLY }, border = BorderStroke(1.dp, if (newFreq == QuestFrequency.WEEKLY) NeonCyan else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("Еженедельный", color = if (newFreq == QuestFrequency.WEEKLY) NeonCyan else Color.White, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart, disabledContainerColor = NeonCyan.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    onClick = {
                        viewModel.addCustomQuest(child.id, newTitle.trim(), newReward.trim().toIntOrNull() ?: 50, newFreq, newDesc.trim())
                        newTitle = ""; newDesc = ""; newReward = "50"; showCreateDialog = false
                    }
                ) { Text("Создать", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Отмена", color = TextSecondary) } }
        )
    }
    if (editKind != null) {
        AlertDialog(
            onDismissRequest = { editKind = null },
            containerColor = SpaceBgStart,
            title = { Text("Редактировать задание", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    if (editKind != "diary") {
                        OutlinedTextField(value = editReward, onValueChange = { editReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart, disabledContainerColor = NeonCyan.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    onClick = {
                        val r = editReward.trim().toIntOrNull() ?: 50
                        when (editKind) {
                            "wardrobe" -> viewModel.updateBuiltInQuest(child.id, "wardrobe", editTitle.trim(), editDesc.trim(), r)
                            "room" -> viewModel.updateBuiltInQuest(child.id, "room", editTitle.trim(), editDesc.trim(), r)
                            "diary" -> viewModel.updateBuiltInQuest(child.id, "diary", editTitle.trim(), editDesc.trim(), r)
                            "custom" -> viewModel.updateCustomQuest(child.id, editQuestId, editTitle.trim(), editDesc.trim(), r)
                        }
                        editKind = null
                    }
                ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editKind = null }) { Text("Отмена", color = TextSecondary) } }
        )
    }
    deleteConfirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { deleteConfirmAction = null },
            containerColor = SpaceBgStart,
            title = { Text("Удалить задание?", color = PenaltyRed, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), onClick = { action(); deleteConfirmAction = null }) {
                    Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmAction = null }) { Text("Отмена", color = TextSecondary) } }
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
        checkedThumbColor = NeonCyan,
        checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
        checkedBorderColor = NeonCyan,
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
                border = BorderStroke(1.dp, NeonCyan),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
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
            title = { Text("Новое достижение", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    OutlinedTextField(value = newTarget, onValueChange = { newTarget = it }, label = { Text("Цель (кол-во)", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    OutlinedTextField(value = newReward, onValueChange = { newReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> "Дневник"; AchievementBranch.WARDROBE -> "Шкаф"; AchievementBranch.ROOM -> "Комната" }
                            OutlinedButton(onClick = { newBranch = b }, border = BorderStroke(1.dp, if (newBranch == b) NeonCyan else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(label, fontSize = 12.sp, color = if (newBranch == b) NeonCyan else Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart, disabledContainerColor = NeonCyan.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
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
            title = { Text("Редактировать достижение", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Название", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Описание", color = TextPrimary) }, colors = editorFieldColors())
                    OutlinedTextField(value = editTarget, onValueChange = { editTarget = it }, label = { Text("Цель (кол-во)", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    OutlinedTextField(value = editReward, onValueChange = { editReward = it }, label = { Text("Награда ₽", color = TextPrimary) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = editorFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AchievementBranch.entries.forEach { b ->
                            val label = when (b) { AchievementBranch.DIARY -> "Дневник"; AchievementBranch.WARDROBE -> "Шкаф"; AchievementBranch.ROOM -> "Комната" }
                            OutlinedButton(onClick = { editBranch = b }, border = BorderStroke(1.dp, if (editBranch == b) NeonCyan else GlassStroke), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(label, fontSize = 12.sp, color = if (editBranch == b) NeonCyan else Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart, disabledContainerColor = NeonCyan.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
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
