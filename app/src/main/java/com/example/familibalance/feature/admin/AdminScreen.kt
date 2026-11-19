package com.example.familibalance.feature.admin

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.feature.child.screens.*
import com.example.familibalance.feature.admin.verification.VerificationAdminContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.familibalance.navigation.AdminAchievementsEditorRoute
import com.example.familibalance.navigation.AdminAchievementsRoute
import com.example.familibalance.navigation.AdminHistoryRoute
import com.example.familibalance.navigation.AdminProgressRoute
import com.example.familibalance.navigation.AdminQuestsEditorRoute
import com.example.familibalance.navigation.AdminSettingsRoute
import com.example.familibalance.navigation.AdminVerificationRoute
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.familibalance.R
import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.push.PushNavBus
import com.example.familibalance.data.push.PushChannels
import com.example.familibalance.data.push.rememberPushPermissionRequester
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import com.example.familibalance.viewmodel.actions.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onSwitchProfile: () -> Unit,
    viewModel: MainViewModel,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val parentPanelDesc = stringResource(R.string.a11y_parent_panel)
    val adminNavController = rememberNavController()
    var showCashOutDialog by remember { mutableStateOf(false) }
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isSynced by viewModel.isSynced.collectAsStateWithLifecycle()
    val isProcessingAction by viewModel.isProcessingAction.collectAsStateWithLifecycle()

    // uid текущего родителя из сессии Supabase — для изоляции детей в админке
    val currentUid by produceState<String?>(initialValue = null) {
        val auth = SupabaseManager.client?.auth ?: return@produceState
        auth.sessionStatus.collect { status ->
            value = (status as? SessionStatus.Authenticated)?.session?.user?.id
        }
    }

    // Показываем только детей, привязанных к текущему родителю.
    // Гость (uid == null) видит всех — как раньше.
    val visibleChildren = if (currentUid == null) {
        familyState.children.values.toList()
    } else {
        familyState.children.values.filter { it.parentUserId == currentUid }
    }

    val child = visibleChildren.firstOrNull { it.id == familyState.selectedChildId }
        ?: visibleChildren.firstOrNull()
        ?: ChildProfileState(id = "user_1", name = stringResource(R.string.child_player_role), avatar = "🧒")

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isSynced) {
        if (isSynced) isRefreshing = false
    }
    // Страховка от «вечного» индикатора: forceSync() асинхронный,
    // а isSynced (StateFlow) не эмитит, если значение не изменилось
    // (всё уже синхронизировано или офлайн). Сбрасываем по таймауту.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(15_000)
            isRefreshing = false
        }
    }

    val navBackStackEntry by adminNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedTabRoute = when {
        currentDestination?.hasRoute<AdminProgressRoute>() == true -> AdminProgressRoute
        currentDestination?.hasRoute<AdminAchievementsRoute>() == true -> AdminAchievementsRoute
        currentDestination?.hasRoute<AdminHistoryRoute>() == true -> AdminHistoryRoute
        currentDestination?.hasRoute<AdminSettingsRoute>() == true ||
            currentDestination?.hasRoute<AdminQuestsEditorRoute>() == true ||
            currentDestination?.hasRoute<AdminAchievementsEditorRoute>() == true -> AdminSettingsRoute
        else -> AdminVerificationRoute
    }

    val tabs = listOf(
        AdminTab(AdminScreenType.Verification, AdminVerificationRoute),
        AdminTab(AdminScreenType.Progress, AdminProgressRoute),
        AdminTab(AdminScreenType.History, AdminHistoryRoute),
        AdminTab(AdminScreenType.Settings, AdminSettingsRoute)
    )

    fun navigateToTab(route: Any) {
        adminNavController.navigate(route) {
            popUpTo<AdminVerificationRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Deep-link из пуша: переключаем вкладку по роли и экрану.
    val pushNav by PushNavBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pushNav) {
        val nav = pushNav ?: return@LaunchedEffect
        PushNavBus.consume()
        if (nav.role != "parent") return@LaunchedEffect
        navigateToTab(
            when (nav.screen) {
                "progress" -> AdminProgressRoute
                "history" -> AdminHistoryRoute
                "settings" -> AdminSettingsRoute
                else -> AdminVerificationRoute
            }
        )
    }

    // Разрешение на уведомления — в момент появления первого ребёнка
    // (не при холодном старте, чтобы не спалить системный диалог).
    val requestNotifPermission = rememberPushPermissionRequester()
    var notifPermissionAsked by remember { mutableStateOf(false) }
    LaunchedEffect(visibleChildren.isNotEmpty()) {
        if (!notifPermissionAsked && visibleChildren.isNotEmpty()) {
            notifPermissionAsked = true
            requestNotifPermission()
        }
    }
    LaunchedEffect(Unit) { PushChannels.ensureChannels(context) }

    val hasPending = child.closetStatus == QuestStatus.PENDING
        || child.roomStatus == QuestStatus.PENDING
        || child.diaryStatus == QuestStatus.PENDING
        || child.bonusQuests.any { it.status == QuestStatus.PENDING }
        || child.cashoutRequests.isNotEmpty()
        || child.honestAdmissionStatus == QuestStatus.PENDING

    if (showCashOutDialog) {
        AlertDialog(
            onDismissRequest = { showCashOutDialog = false },
            containerColor = SpaceBgStart,
            title = { Text(stringResource(R.string.admin_cashout_title), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.admin_cashout_message, childDisplayName(child), child.balance),
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminCashoutAll(child.id)
                        showCashOutDialog = false
                    },
                    enabled = !isProcessingAction,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.admin_cashout_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCashOutDialog = false },
                    border = BorderStroke(1.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.space_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AdminOverlayBg.copy(alpha = 0.82f))
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                AdminTopBar(
                    onSwitchProfile = onSwitchProfile,
                    visibleChildren = visibleChildren,
                    selectedChildId = familyState.selectedChildId,
                    onSelectChild = { viewModel.selectChild(it, pushRemote = true) }
                )
            },
            bottomBar = {
                AdminBottomBar(
                    tabs = tabs,
                    selectedTabRoute = selectedTabRoute,
                    onTabClick = { navigateToTab(it) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.forceSync()
                        // Если после forceSync нечего синхронизировать,
                        // isSynced не изменится и LaunchedEffect не сработает —
                        // сбрасываем индикатор сразу.
                        if (viewModel.isSynced.value) isRefreshing = false
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(navController = adminNavController, startDestination = AdminVerificationRoute) {
                    composable<AdminVerificationRoute> {
                        VerificationAdminContent(
                            onCashOutClick = { showCashOutDialog = true },
                            viewModel = viewModel
                        )
                    }
                    composable<AdminProgressRoute> {
                        ProgressAdminContent(viewModel = viewModel)
                    }
                    composable<AdminAchievementsRoute> {
                        AdminAchievementsScreen(viewModel = viewModel)
                    }
                    composable<AdminHistoryRoute> {
                        HistoryScreen(viewModel = viewModel, showVerificationCard = false)
                    }
                    composable<AdminSettingsRoute> {
                        AdminSettingsScreen(
                            viewModel = viewModel,
                            onOpenQuestsEditor = { adminNavController.navigate(AdminQuestsEditorRoute) },
                            onOpenAchievementsEditor = { adminNavController.navigate(AdminAchievementsEditorRoute) },
                            onLogout = onLogout
                        )
                    }
                    composable<AdminQuestsEditorRoute> {
                        QuestsEditorSubScreen(
                            onBack = { adminNavController.popBackStack() },
                            viewModel = viewModel
                        )
                    }
                    composable<AdminAchievementsEditorRoute> {
                        AchievementsEditorSubScreen(
                            onBack = { adminNavController.popBackStack() },
                            viewModel = viewModel
                        )
                    }
                    }
                }
            }
        }
    }
}
