package com.example.familibalance.feature.admin

import com.example.familibalance.AppLocaleContent
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.BuildConfig

import com.example.familibalance.data.model.*
import com.example.familibalance.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.example.familibalance.feature.admin.settings.*
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
    var showDeletePicker by rememberSaveable { mutableStateOf(false) }
    // Цель удаления: "account" — весь аккаунт, childId — профиль ребёнка.
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var isDeletingAccount by rememberSaveable { mutableStateOf(false) }
    var showPinPicker by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var pinTarget by rememberSaveable { mutableStateOf<String?>(null) } // "parent" или childId
    var pendingReset by rememberSaveable { mutableStateOf<ResetKind?>(null) }

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

    fun requestReset(kind: ResetKind) {
        pendingReset = kind
        showConfirmDialog = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp)
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

        pendingRequestsSection(
            requests = pendingRequests,
            onApprove = { adminVm.approveRequest(it) }
        )
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
        resetsSection(
            onRequestReset = { kind -> requestReset(kind) }
        )
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
            CurrencyCard(
                currentCurrency = child.rewardCurrency,
                onSelect = { viewModel.setRewardCurrency(it) }
            )
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
                onClick = { showDeletePicker = true },
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
    PinPickerDialog(
        children = familyState.children.values.sortedBy { it.name },
        onSelect = { target ->
            showPinPicker = false
            pinTarget = target
        },
        onDismiss = { showPinPicker = false }
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
    AddBonusQuestDialog(
        child = child,
        onConfirm = { title, desc, reward, requiresPhoto ->
            viewModel.addBonusQuest(child.id, title, reward, desc, requiresPhoto)
            viewModel.saveAndSync()
            showAddBonusDialog = false
        },
        onDismiss = { showAddBonusDialog = false }
    )
}
if (showConfirmDialog && pendingReset != null) {
    ResetConfirmDialog(
        onConfirm = {
            pendingReset?.execute(viewModel, child.id)
            showConfirmDialog = false
            pendingReset = null
        },
        onDismiss = {
            showConfirmDialog = false
            pendingReset = null
        }
    )
}
// Подтверждение выхода из аккаунта
if (showLogoutDialog) {
    LogoutConfirmDialog(
        onConfirm = {
            showLogoutDialog = false
            onLogout()
        },
        onDismiss = { showLogoutDialog = false }
    )
}
// Меню удаления: аккаунт целиком или профиль конкретного ребёнка
if (showDeletePicker) {
    val childrenList = familyState.children.values.sortedBy { it.name }
    DeleteTargetPickerDialog(
        children = childrenList,
        onSelectAccount = {
            showDeletePicker = false
            deleteTarget = "account"
        },
        onSelectChild = {
            showDeletePicker = false
            // Один ребёнок — сразу к подтверждению, несколько — список профилей.
            deleteTarget = if (childrenList.size == 1) {
                childrenList.first().id
            } else {
                "child_picker"
            }
        },
        onDismiss = { showDeletePicker = false }
    )
}

// Выбор профиля ребёнка для удаления (несколько детей)
if (deleteTarget == "child_picker") {
    ChildDeletePickerDialog(
        children = familyState.children.values.sortedBy { it.name },
        onSelect = { deleteTarget = it },
        onDismiss = { deleteTarget = null }
    )
}

// Подтверждение удаления: аккаунт родителя или профиль ребёнка
deleteTarget?.let { target ->
    if (target == "child_picker") return@let
    val targetChild = if (target == "account") null else familyState.children[target]
    if (target != "account" && targetChild == null) {
        deleteTarget = null
        return@let
    }
    DeleteConfirmDialog(
        target = target,
        targetChild = targetChild,
        isDeleting = isDeletingAccount,
        onConfirm = {
            isDeletingAccount = true
            if (target == "account") {
                viewModel.deleteAccount { success ->
                    isDeletingAccount = false
                    deleteTarget = null
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
            } else {
                viewModel.deleteChildProfile(target) { success ->
                    isDeletingAccount = false
                    if (success) {
                        deleteTarget = null
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            deleteErrorText,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        },
        onDismiss = { deleteTarget = null }
    )
}
}
