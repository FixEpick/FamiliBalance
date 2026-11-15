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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.auth.ChangeParentPinDialog
import com.example.familibalance.ui.auth.SetChildPinDialog
import com.example.familibalance.viewmodel.AdminEvent
import com.example.familibalance.viewmodel.AdminViewModel
import com.example.familibalance.viewmodel.actions.*

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
    var showResets by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<String?>(null) } // "parent" или childId
    var pendingResetAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // --- Запросы на добавление детей (status = "pending") — MVI через AdminViewModel ---
    val adminVm: AdminViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        AdminViewModel(viewModel)
    }
    val pendingRequests by adminVm.pendingRequests.collectAsStateWithLifecycle()

    // Polling только пока экран настроек видим (RESUMED).
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            adminVm.onResume()
        } else {
            adminVm.onPause()
        }
    }

    // Одноразовые события (Toast) из ViewModel
    LaunchedEffect(Unit) {
        adminVm.events.collect { event ->
            when (event) {
                is AdminEvent.ShowMessage ->
                    android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestReset(action: () -> Unit) {
        pendingResetAction = action
        showConfirmDialog = true
    }

    @Composable
    fun ResetSectionHeader(title: String) {
        Text(
            text = title,
            color = AccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
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
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = AccentColor)
            ) {
                Text("📋 Редактор заданий", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            OutlinedButton(
                onClick = onOpenAchievementsEditor,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = AccentColor)
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
    items(pendingRequests, key = { it.id }) { row ->
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
                Text(row.avatar, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${row.name} хочет присоединиться к семье",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Email: ${row.email}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = { adminVm.approveRequest(row.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
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
        border = BorderStroke(1.dp, AccentColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("🔐 Управление ПИН-кодами", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(20.dp))
}
        item {
            OutlinedButton(
                onClick = { showResets = !showResets },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PenaltyRed.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = PenaltyRed)
            ) {
                Text(
                    text = if (showResets) "🗑 Очистка и сбросы ▲" else "🗑 Очистка и сбросы ▼",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        if (showResets) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResetSectionHeader("📈 Прогресс и задания")
                    ResetRow("Сбросить прогресс всех заданий", "Сбросить") { viewModel.resetActiveQuests(child.id) }
                    ResetRow("Вернуть скрытые и удалённые задания", "Вернуть") { viewModel.resetQuestVisibility(child.id) }
                    ResetRow("Сбросить достижения и статистику", "Сбросить") { viewModel.resetAchievements(child.id) }
                    ResetRow("Обнулить счётчики оценок и уборок", "Обнулить") { viewModel.resetGradeCounters(child.id) }
                    ResetRow("Сбросить честное признание", "Сбросить") { viewModel.resetHonestAdmission(child.id) }
                    ResetRow("Очистить историю операций", "Очистить") { viewModel.clearHistory(child.id) }

                    ResetSectionHeader("💰 Баланс и время")
                    ResetRow("Обнулить баланс", "Обнулить") { viewModel.resetBalance(child.id) }
                    ResetRow("Сбросить стрик и экранное время", "Сбросить") { viewModel.resetStreakAndTime(child.id) }
                    ResetRow("Удалить цель-мечту", "Удалить") { viewModel.resetDream(child.id) }

                    ResetSectionHeader("🎨 Оформление")
                    ResetRow("Сбросить тему оформления", "Сбросить") { viewModel.resetTheme(child.id) }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { requestReset { viewModel.resetAllChildData(child.id) } },
                        colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Обнулить всё", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.forceSync() },
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("🔄 Синхронизировать с сервером", fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick = onLogout,
                border = BorderStroke(1.dp, PenaltyRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("🚪 Выйти из аккаунта", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Версия приложения: ${BuildConfig.VERSION_NAME}",
                color = TextSecondary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
}
// Выбор, чей ПИН-код менять
if (showPinPicker) {
    val childrenList = familyState.children.values.sortedBy { it.name }
    AlertDialog(
        onDismissRequest = { showPinPicker = false },
        containerColor = SpaceBgStart,
        title = { Text("🔐 Управление ПИН-кодами", color = AccentColor, fontWeight = FontWeight.Bold) },
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
        title = { Text("🎁 Новый бонус-квест для ${child.name}", color = AccentColor, fontWeight = FontWeight.Bold) },
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart),
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
        title = { Text("Подтверждение", color = AccentColor, fontWeight = FontWeight.Bold) },
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
