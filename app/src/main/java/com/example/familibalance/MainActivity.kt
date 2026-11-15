package com.example.familibalance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.annotation.StringRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.familibalance.navigation.AdminRoute
import com.example.familibalance.navigation.ChildAchievementsRoute
import com.example.familibalance.navigation.ChildHistoryRoute
import com.example.familibalance.navigation.ChildMainRoute
import com.example.familibalance.navigation.ChildRoute
import com.example.familibalance.navigation.ChildTasksRoute
import com.example.familibalance.navigation.PlayerLoginRoute
import com.example.familibalance.navigation.RegisterParentRoute
import com.example.familibalance.navigation.WelcomeRoute
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.MainChildScreen
import com.example.familibalance.feature.child.TasksScreen
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.ui.auth.WelcomeScreen
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.ui.screens.*
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.components.PulseBadge
import com.example.familibalance.ui.components.ThemeSelectionBottomSheet
import com.example.familibalance.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import com.example.familibalance.ui.update.UpdateDialog
import com.example.familibalance.viewmodel.UpdateViewModel

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var updateViewModel: UpdateViewModel
    private lateinit var authViewModel: AuthViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        AppLocaleState.init(this)

        enableEdgeToEdge()
        setContent {
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }

            // Запрос POST_NOTIFICATIONS (Android 13+) при первом запуске —
            // уведомления нужны для квестов и статуса скачивания обновления.
            val context = androidx.compose.ui.platform.LocalContext.current
            var showNotifRationale by remember { mutableStateOf(false) }
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* результат не критичен — уведомления просто не покажутся */ }
            val notifPrefs = remember {
                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            }
            LaunchedEffect(Unit) {
                val alreadyAsked = notifPrefs.getBoolean("notif_rationale_shown", false)
                if (!alreadyAsked &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPrefs.edit().putBoolean("notif_rationale_shown", true).apply()
                    showNotifRationale = true
                }
            }
            if (showNotifRationale) {
                AlertDialog(
                    onDismissRequest = { showNotifRationale = false },
                    title = { Text("Уведомления", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Разрешите уведомления, чтобы получать напоминания о квестах и статус скачивания обновлений.",
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            showNotifRationale = false
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text("Разрешить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNotifRationale = false }) { Text("Не сейчас") }
                    }
                )
            }
            val familyState by viewModel.familyState.collectAsStateWithLifecycle()
            val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
            val autoLoginChildId by viewModel.autoLoginChildId.collectAsStateWithLifecycle()
            val themeChild = familyState.children[familyState.selectedChildId]
                ?: familyState.children.values.firstOrNull()
            val currentThemeId = themeChild?.selectedThemeId ?: "COSMIC_BLUE"
            LocalizedAppContent {
            FamiliBalanceTheme(themeId = currentThemeId) {
                val navController = rememberNavController()

                // «Назад» на главных разделах (ChildRoute / AdminRoute) —
                // диалог подтверждения выхода вместо двойного нажатия.
                // На экранах входа/регистрации и внутренних маршрутах
                // работает обычная навигация (popBackStack).
                val topBackStackEntry by navController.currentBackStackEntryAsState()
                val isMainSection = topBackStackEntry?.destination?.let {
                    it.hasRoute<ChildRoute>() || it.hasRoute<AdminRoute>()
                } == true
                var showExitDialog by remember { mutableStateOf(false) }
                val activity = LocalActivity.current
                BackHandler(enabled = isMainSection) {
                    showExitDialog = true
                }

                // При возврате приложения из фона — досылаем несинхронизированные данные
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.forceSync()
                            // Если ждали разрешения на установку — продолжаем обновление
                            updateViewModel.onResume()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                fun backToLogin() {
                    navController.navigate(WelcomeRoute) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }

                // Анонимная сессия (устройство ребёнка) не считается родительской:
                // в AdminRoute пускаем только пользователей с email.
                var isParentSession by remember { mutableStateOf(false) }
                LaunchedEffect(sessionStatus) {
                    val user = runCatching {
                        SupabaseManager.client?.auth?.currentUserOrNull()
                    }.getOrNull()
                    isParentSession = sessionStatus is SessionStatus.Authenticated &&
                        !user?.email.isNullOrBlank()
                }

                // Если сессия родителя уже активна — сразу в родительский кабинет
                var autoNavigatedToAdmin by remember { mutableStateOf(false) }
                LaunchedEffect(isParentSession) {
                    if (!autoNavigatedToAdmin && isParentSession) {
                        autoNavigatedToAdmin = true
                        // Синхронизируем локальный флаг автовхода с Supabase-сессией
                        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("is_admin_logged_in", true)
                            .putBoolean("is_logged_in", false)
                            .remove("logged_in_child_id")
                            .apply()
                        navController.navigate(AdminRoute) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }

                // Автовход ребёнка: MainViewModel выставляет autoLoginChildId
                // после загрузки локального стейта, если стоит флаг is_logged_in.
                var autoChildNavigated by remember { mutableStateOf(false) }
                LaunchedEffect(autoLoginChildId) {
                    if (!autoChildNavigated && autoLoginChildId != null) {
                        autoChildNavigated = true
                        navController.navigate(ChildRoute) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = WelcomeRoute
                ) {
                    composable<WelcomeRoute> {
                        WelcomeScreen(
                            onPlayerClick = { navController.navigate(PlayerLoginRoute) },
                            onRegisterClick = { navController.navigate(RegisterParentRoute) }
                        )
                    }
                    composable<PlayerLoginRoute> {
                        PlayerLoginScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onLoginSuccess = {
                                navController.navigate(ChildRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            },
                            onCreateAccount = { navController.navigate(RegisterParentRoute) }
                        )
                    }
                    composable<RegisterParentRoute> {
                        RegisterParentScreen(
                            viewModel = authViewModel,
                            mainViewModel = viewModel,
                            onPlayerSuccess = {
                                navController.navigate(ChildRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            },
                            onParentSuccess = {
                                // Флаг автовхода родителя: при перезапуске
                                // isParentSession (Supabase) + этот флаг → AdminRoute
                                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_admin_logged_in", true)
                                    .putBoolean("is_logged_in", false)
                                    .remove("logged_in_child_id")
                                    .apply()
                                navController.navigate(AdminRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<AdminRoute> {
                        AdminScreen(
                            onSwitchProfile = { backToLogin() },
                            viewModel = viewModel,
                            onLogout = {
                                authViewModel.signOut()
                                viewModel.clearState()
                                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_admin_logged_in", false)
                                    .putBoolean("is_logged_in", false)
                                    .remove("logged_in_child_id")
                                    .apply()
                                navController.navigate(WelcomeRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<ChildRoute> {
                        ChildSection(
                            viewModel = viewModel,
                            onSwitchProfile = { backToLogin() },
                            onLogout = {
                                viewModel.logoutChild()
                                navController.navigate(WelcomeRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        )
                    }
                }

                // Строки диалога выхода вычисляем здесь, а не внутри AlertDialog:
                // Dialog создаёт отдельное окно и может потерять локализованный контекст.
                val exitDialogTitle = stringResource(R.string.exit_dialog_title)
                val exitDialogMessage = stringResource(R.string.exit_dialog_message)
                val exitDialogConfirm = stringResource(R.string.exit_dialog_confirm)
                val exitDialogCancel = stringResource(R.string.action_cancel)

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        containerColor = SpaceBgStart,
                        title = {
                            Text(
                                text = exitDialogTitle,
                                color = AccentColor,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = exitDialogMessage,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showExitDialog = false
                                activity?.finishAffinity()
                                // Гарантированный выход, если finishAffinity не сработал
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }) {
                                Text(
                                    exitDialogConfirm,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text(exitDialogCancel, color = TextSecondary)
                            }
                        }
                    )
                }

                UpdateDialog(
                    state = updateState,
                    onUpdate = { updateViewModel.startDownload() },
                    onDismiss = { updateViewModel.dismiss() }
                )
            }
            }
        }
    }
}

private data class ChildTab(
    val route: Any,
    @StringRes val titleRes: Int,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildSection(
    viewModel: MainViewModel,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val childNavController = rememberNavController()
    var showThemeSheet by remember { mutableStateOf(false) }
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isSynced by viewModel.isSynced.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
        ?: ChildProfileState(id = "user_1", name = "Игрок", avatar = "🧒")

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isSynced) {
        if (isSynced) isRefreshing = false
    }
    // Страховка от «вечного» индикатора: forceSync() асинхронный,
    // а isSynced (StateFlow) не эмитит, если значение не изменилось
    // (всё уже синхронизировано или офлайн). Сбрасываем по таймауту.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(15_000)
            isRefreshing = false
        }
    }

    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedTabRoute = when {
        currentDestination?.hasRoute<ChildTasksRoute>() == true -> ChildTasksRoute
        currentDestination?.hasRoute<ChildAchievementsRoute>() == true -> ChildAchievementsRoute
        currentDestination?.hasRoute<ChildHistoryRoute>() == true -> ChildHistoryRoute
        else -> ChildMainRoute
    }

    val tabs = listOf(
        ChildTab(ChildMainRoute, R.string.nav_home, "🏠"),
        ChildTab(ChildTasksRoute, R.string.nav_quests, "✨"),
        ChildTab(ChildAchievementsRoute, R.string.nav_achievements, "🏆"),
        ChildTab(ChildHistoryRoute, R.string.nav_history, "📜")
    )

    // Красная точка на «Достижениях», если есть награда, которую можно забрать
    val hasClaimableAchievements = remember(child.achievements, child.fivesCount, child.wardrobeCount, child.roomCount, child.balance, child.streakDays) {
        getAchievements(child).any { it.isReadyToClaim }
    }

    // Бейдж на «Заданиях» — только если есть квест, готовый к получению награды
    val hasClaimableTasks = remember(child) {
        child.closetStatus == QuestStatus.READY_TO_CLAIM ||
            child.roomStatus == QuestStatus.READY_TO_CLAIM ||
            child.diaryStatus == QuestStatus.READY_TO_CLAIM ||
            child.bonusQuests.any { it.status == QuestStatus.READY_TO_CLAIM } ||
            child.customQuests.any { it.status == QuestStatus.READY_TO_CLAIM }
    }

    fun navigateToTab(route: Any) {
        childNavController.navigate(route) {
            popUpTo<ChildMainRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.space_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceBgOverlay.copy(alpha = 0.55f))
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TopBarBg.copy(alpha = 0.85f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(child.avatar, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${child.name} (Игрок)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSynced) {
                            Text(
                                text = "📴",
                                fontSize = 16.sp,
                                color = StatusYellow
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        LanguageSelectorButton()
                        IconButton(onClick = { showThemeSheet = true }) {
                            Text("🎨", fontSize = 18.sp)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = TopBarBg.copy(alpha = 0.92f),
                    tonalElevation = 0.dp
                ) {
                    val items = tabs
                    items.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.route == selectedTabRoute,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                val showBadge = when (tab.route) {
                                    ChildTasksRoute -> hasClaimableTasks
                                    ChildAchievementsRoute -> hasClaimableAchievements
                                    else -> false
                                }
                                if (showBadge) {
                                    BadgedBox(badge = { PulseBadge() }) {
                                        Text(tab.icon, fontSize = 20.sp)
                                    }
                                } else {
                                    Text(tab.icon, fontSize = 20.sp)
                                }
                            },
                            label = {
                                Text(
                                    stringResource(tab.titleRes),
                                    color = if (tab.route == selectedTabRoute) LocalAppPalette.current.primaryNeon else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = LocalAppPalette.current.primaryNeon.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
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
                    NavHost(navController = childNavController, startDestination = ChildMainRoute) {
                        composable<ChildMainRoute> {
                            MainChildScreen(
                                onNavigateToTasks = { navigateToTab(ChildTasksRoute) },
                                onLogout = onLogout,
                                viewModel = viewModel
                            )
                        }
                        composable<ChildTasksRoute> { TasksScreen(viewModel = viewModel) }
                        composable<ChildAchievementsRoute> { AchievementsScreen(viewModel = viewModel) }
                        composable<ChildHistoryRoute> { HistoryScreen(viewModel = viewModel) }
                    }
                }
            }
            if (showThemeSheet) {
                ThemeSelectionBottomSheet(
                    currentThemeId = child.selectedThemeId,
                    onThemeSelected = { viewModel.updateTheme(child.id, it) },
                    onDismissRequest = { showThemeSheet = false }
                )
            }
        }
    }
}
