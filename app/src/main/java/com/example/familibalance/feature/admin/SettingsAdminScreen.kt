package com.example.familibalance.feature.admin

import com.example.familibalance.AppLocaleContent
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.BuildConfig

import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.familibalance.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.example.familibalance.ui.auth.ChangeParentPinDialog
import com.example.familibalance.ui.auth.SetChildPinDialog
import com.example.familibalance.ui.components.LanguageSelectorRow
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.viewmodel.AdminEvent
import com.example.familibalance.viewmodel.AdminViewModel
import com.example.familibalance.viewmodel.actions.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenQuestsEditor: () -> Unit = {},
    onOpenAchievementsEditor: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
        ?: ChildProfileState(id = "user_1", name = stringResource(R.string.child_player_role), avatar = "🧒")
    var showAddBonusDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }
    var isDeletingAccount by rememberSaveable { mutableStateOf(false) }
    var showResets by rememberSaveable { mutableStateOf(false) }
    var showPinPicker by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var selectedResetIndex by rememberSaveable { mutableIntStateOf(-1) }
    var pinTarget by rememberSaveable { mutableStateOf<String?>(null) } // "parent" или childId
    var pendingResetAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // --- Ручная синхронизация: индикатор + Toast по завершении ---
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    var syncRequested by rememberSaveable { mutableStateOf(false) }

    val syncSuccessText = stringResource(R.string.sync_success)
    // Тексты для Toast в не-composable лямбдах — вычисляем здесь,
    // чтобы не дёргать LocalContext.getString (lint: configuration-aware).
    val deleteErrorText = stringResource(R.string.admin_set_delete_error)
    // Ждём реального старта синхронизации (до 3 сек — на случай мгновенного
    // завершения/офлайна), затем её окончания (до 25 сек). В любом случае
    // показываем Toast, чтобы пользователь получил обратную связь.
    LaunchedEffect(syncRequested) {
        if (!syncRequested) return@LaunchedEffect
        withTimeoutOrNull(25_000) {
            withTimeoutOrNull(3_000) {
                snapshotFlow { isSyncing }.first { it }
            }
            snapshotFlow { isSyncing }.first { !it }
        }
        syncRequested = false
        android.widget.Toast.makeText(
            context,
            syncSuccessText,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

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
                    android.widget.Toast.makeText(context, event.text.asString(context), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestReset(action: () -> Unit) {
        pendingResetAction = action
        showConfirmDialog = true
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
                Text(stringResource(R.string.admin_set_quests_editor), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                Text(stringResource(R.string.admin_set_achievements_editor), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                Text(stringResource(R.string.admin_set_bonus_quest), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

item {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = stringResource(R.string.admin_set_requests_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
if (pendingRequests.isEmpty()) {
    item {
        Text(
            text = stringResource(R.string.admin_set_no_requests),
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
                        text = stringResource(R.string.admin_set_request_join, row.name),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.admin_set_request_email, row.email),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = { adminVm.approveRequest(row.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.admin_set_approve), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
    item { Spacer(modifier = Modifier.height(12.dp)) }
}
item {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = stringResource(R.string.admin_set_security), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
item {
    OutlinedButton(
        onClick = { showPinPicker = true },
        border = BorderStroke(1.dp, AccentColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text(stringResource(R.string.admin_set_pin_management), fontWeight = FontWeight.Bold)
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
                    text = if (showResets) stringResource(R.string.admin_set_resets_open) else stringResource(R.string.admin_set_resets_closed),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        if (showResets) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val resetOptions = listOf(
                        stringResource(R.string.admin_set_reset_quests_label) to { viewModel.resetActiveQuests(child.id) },
                        stringResource(R.string.admin_set_reset_visibility_label) to { viewModel.resetQuestVisibility(child.id) },
                        stringResource(R.string.admin_set_reset_achievements_label) to { viewModel.resetAchievements(child.id) },
                        stringResource(R.string.admin_set_reset_counters_label) to { viewModel.resetGradeCounters(child.id) },
                        stringResource(R.string.admin_set_reset_honest_label) to { viewModel.resetHonestAdmission(child.id) },
                        stringResource(R.string.admin_set_reset_history_label) to { viewModel.clearHistory(child.id) },
                        stringResource(R.string.admin_set_reset_balance_label) to { viewModel.resetBalance(child.id) },
                        stringResource(R.string.admin_set_reset_streak_label) to { viewModel.resetStreakAndTime(child.id) },
                        stringResource(R.string.admin_set_reset_dream_label) to { viewModel.resetDream(child.id) },
                        stringResource(R.string.admin_set_reset_theme_label) to { viewModel.resetTheme(child.id) }
                    )
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
                                text = resetOptions.getOrNull(selectedResetIndex)?.first
                                    ?: stringResource(R.string.admin_set_reset_select),
                                color = if (selectedResetIndex >= 0) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = resetMenuExpanded,
                            onDismissRequest = { resetMenuExpanded = false }
                        ) {
                            resetOptions.forEachIndexed { index, (label, _) ->
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
                            resetOptions.getOrNull(selectedResetIndex)?.second?.let { action ->
                                requestReset(action)
                            }
                        },
                        enabled = selectedResetIndex >= 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PenaltyRed,
                            disabledContainerColor = GlassStroke
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(stringResource(R.string.admin_set_reset_execute), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { requestReset { viewModel.resetAllChildData(child.id) } },
                        colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(stringResource(R.string.admin_set_reset_all), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    if (!isSyncing && !syncRequested) {
                        syncRequested = true
                        viewModel.forceSync()
                    }
                },
                enabled = !isSyncing && !syncRequested,
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                if (isSyncing || syncRequested) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AccentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.admin_set_sync), fontWeight = FontWeight.Bold)
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GlassBackground,
                border = BorderStroke(1.dp, GlassStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguageSelectorRow(
                    label = stringResource(R.string.admin_set_language),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                border = BorderStroke(1.dp, PenaltyRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.admin_set_logout), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { showDeleteAccountDialog = true },
                enabled = !isDeletingAccount,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.admin_set_delete_account), color = PenaltyRed, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.history_version,
                    BuildConfig.VERSION_NAME + " (" + stringResource(
                        if (BuildConfig.DEBUG) R.string.build_type_debug else R.string.build_type_release
                    ) + ")"
                ),
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
        title = { AppLocaleContent { Text(stringResource(R.string.admin_set_pin_management), color = AccentColor, fontWeight = FontWeight.Bold) } },
        text = {
            AppLocaleContent {
            val options = listOf("parent" to stringResource(R.string.admin_set_my_pin)) +
                childrenList.map { it.id to "👦 ${childDisplayName(it)}" }
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
            }
        },
        confirmButton = {},
        dismissButton = {
            AppLocaleContent {
            OutlinedButton(
                onClick = { showPinPicker = false },
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
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

// Детский ПИН (для "parent" диалог уже открыт выше — не сбрасываем pinTarget)
pinTarget?.let { targetId ->
    if (targetId == "parent") return@let
    val targetChild = familyState.children[targetId]
    if (targetChild != null) {
        val pinUpdatedText = stringResource(R.string.admin_set_pin_updated, childDisplayName(targetChild))
        SetChildPinDialog(
            childName = childDisplayName(targetChild),
            onPinEntered = { newPin ->
                viewModel.updateChildPin(targetId, newPin)
                android.widget.Toast.makeText(
                    context,
                    pinUpdatedText,
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
    var bonusTitle by rememberSaveable { mutableStateOf("") }
    var bonusDesc by rememberSaveable { mutableStateOf("") }
    var bonusReward by rememberSaveable { mutableStateOf("50") }
    var bonusRequiresPhoto by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { showAddBonusDialog = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SpaceBgStart,
        title = { AppLocaleContent { Text(stringResource(R.string.admin_set_bonus_title, childDisplayName(child)), color = AccentColor, fontWeight = FontWeight.Bold) } },
        text = {
            AppLocaleContent {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = bonusTitle, onValueChange = { bonusTitle = it }, label = { Text(stringResource(R.string.admin_set_bonus_name), color = TextPrimary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
                OutlinedTextField(value = bonusDesc, onValueChange = { bonusDesc = it }, label = { Text(stringResource(R.string.admin_set_bonus_desc), color = TextPrimary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
                OutlinedTextField(value = bonusReward, onValueChange = { bonusReward = it }, label = { Text(stringResource(R.string.admin_set_bonus_reward), color = TextPrimary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = editorFieldColors())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = bonusRequiresPhoto,
                        onCheckedChange = { bonusRequiresPhoto = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                    )
                    Text(
                        text = stringResource(R.string.admin_set_bonus_photo),
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
            }
        },
        confirmButton = {
            AppLocaleContent {
            Button(
                onClick = {
                    val rewardInt = bonusReward.toIntOrNull() ?: 50
                    if (bonusTitle.isNotBlank()) {
                        viewModel.addBonusQuest(child.id, bonusTitle.trim(), rewardInt, bonusDesc.trim(), bonusRequiresPhoto)
                        viewModel.saveAndSync()
                        showAddBonusDialog = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart),
                shape = RoundedCornerShape(10.dp)
            ) { Text(stringResource(R.string.admin_set_create), fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { AppLocaleContent { TextButton(onClick = { showAddBonusDialog = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } } }
    )
}
if (showConfirmDialog && pendingResetAction != null) {
    AlertDialog(
        onDismissRequest = { showConfirmDialog = false; pendingResetAction = null },
        containerColor = SpaceBgStart,
        title = { AppLocaleContent { Text(stringResource(R.string.admin_set_confirm_title), color = AccentColor, fontWeight = FontWeight.Bold) } },
        text = { AppLocaleContent { Text(stringResource(R.string.admin_set_confirm_message), color = TextPrimary) } },
        confirmButton = {
            AppLocaleContent {
            Button(onClick = { pendingResetAction?.invoke(); showConfirmDialog = false; pendingResetAction = null }, colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed), shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.admin_set_confirm), color = Color.White, fontWeight = FontWeight.Bold)
            }
            }
        },
        dismissButton = { AppLocaleContent { TextButton(onClick = { showConfirmDialog = false; pendingResetAction = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } } }
    )
}
// Подтверждение выхода из аккаунта
if (showLogoutDialog) {
    AlertDialog(
        onDismissRequest = { showLogoutDialog = false },
        containerColor = SpaceBgStart,
        title = { AppLocaleContent { Text(stringResource(R.string.admin_set_logout_title), color = AccentColor, fontWeight = FontWeight.Bold) } },
        text = { AppLocaleContent { Text(stringResource(R.string.admin_set_logout_message), color = TextPrimary) } },
        confirmButton = {
            AppLocaleContent {
            Button(
                onClick = {
                    showLogoutDialog = false
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.admin_set_logout_confirm), fontWeight = FontWeight.Bold)
            }
            }
        },
        dismissButton = {
            AppLocaleContent {
            OutlinedButton(
                onClick = { showLogoutDialog = false },
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
            }
        }
    )
}
// Удаление аккаунта (Google Play): необратимо, с подтверждением и спиннером
if (showDeleteAccountDialog) {
    AlertDialog(
        onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
        containerColor = SpaceBgStart,
        title = { AppLocaleContent { Text(stringResource(R.string.admin_set_delete_title), color = PenaltyRed, fontWeight = FontWeight.Bold) } },
        text = {
            AppLocaleContent {
            Text(
                stringResource(R.string.admin_set_delete_message),
                color = TextPrimary
            )
            }
        },
        confirmButton = {
            AppLocaleContent {
            Button(
                onClick = {
                    isDeletingAccount = true
                    viewModel.deleteAccount { success ->
                        isDeletingAccount = false
                        showDeleteAccountDialog = false
                        if (success) {
                            onLogout()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                deleteErrorText,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !isDeletingAccount,
                colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isDeletingAccount) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.admin_set_delete), fontWeight = FontWeight.Bold)
            }
            }
        },
        dismissButton = {
            AppLocaleContent {
            TextButton(
                onClick = { showDeleteAccountDialog = false },
                enabled = !isDeletingAccount
            ) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
            }
        }
    )
}
}
