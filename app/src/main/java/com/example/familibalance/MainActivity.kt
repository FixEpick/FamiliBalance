package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.navigation.AdminRoute
import com.example.familibalance.navigation.ChildAchievementsRoute
import com.example.familibalance.navigation.ChildHistoryRoute
import com.example.familibalance.navigation.ChildMainRoute
import com.example.familibalance.navigation.ChildRoute
import com.example.familibalance.navigation.ChildTasksRoute
import com.example.familibalance.navigation.OnboardingRoute
import com.example.familibalance.navigation.PlayerLoginRoute
import com.example.familibalance.navigation.RegisterParentRoute
import com.example.familibalance.navigation.WelcomeRoute
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.ChildSection
import com.example.familibalance.feature.child.MainChildScreen
import com.example.familibalance.feature.child.TasksScreen
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.ui.auth.WelcomeScreen
import com.example.familibalance.ui.onboarding.OnboardingScreen
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.feature.child.screens.*
import com.example.familibalance.ui.components.ExitConfirmDialog
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
        // Защита от скриншотов и записи экрана
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
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
                SecurePrefs.get(context, "app_prefs")
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
            val familyState by viewModel.familyState.collectAsStateWithLifecycle()
            val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
            val autoLoginChildId by viewModel.autoLoginChildId.collectAsStateWithLifecycle()
            val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
            val themeChild = familyState.children[familyState.selectedChildId]
                ?: familyState.children.values.firstOrNull()
            val currentThemeId = themeChild?.selectedThemeId ?: "COSMIC_BLUE"
            LocalizedAppContent {
            FamiliBalanceTheme(themeId = currentThemeId) {
                // Пока грузится локальный стейт и Supabase восстанавливает сессию —
                // показываем splash, чтобы не мигал экран входа перед админкой.
                if (isInitializing || sessionStatus is SessionStatus.Initializing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    return@FamiliBalanceTheme
                }
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

                // Жизненный цикл Realtime и синхронизации:
                //  - ON_START: подписка активна всегда, включая фон
                //    (startRealtimeSync идемпотентен — повторный вызов no-op);
                //  - ON_RESUME: досылаем несинхронизированные данные и обновления.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> {
                                if (sessionStatus is SessionStatus.Authenticated) {
                                    SupabaseManager.startRealtimeSync(context)
                                }
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                viewModel.forceSync()
                                // Если ждали разрешения на установку — продолжаем обновление
                                updateViewModel.onResume()
                            }
                            else -> {}
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
                        SecurePrefs.get(context, "app_prefs")
                            .edit()
                            .putBoolean("is_admin_logged_in", true)
                            .putBoolean("is_logged_in", false)
                            .putBoolean("onboarding_done", true)
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
                        SecurePrefs.get(context, "app_prefs")
                            .edit()
                            .putBoolean("onboarding_done", true)
                            .apply()
                        navController.navigate(ChildRoute) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }

                // Onboarding is shown only to a new user; the flag lives
                // in app_prefs (SecurePrefs), same as other login flags.
                val onboardingDone = remember {
                    SecurePrefs.get(context, "app_prefs")
                        .getBoolean("onboarding_done", false)
                }

                fun completeOnboarding(navigate: () -> Unit) {
                    SecurePrefs.get(context, "app_prefs")
                        .edit()
                        .putBoolean("onboarding_done", true)
                        .apply()
                    navigate()
                }

                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone) WelcomeRoute else OnboardingRoute
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            onParentClick = {
                                completeOnboarding {
                                    navController.navigate(RegisterParentRoute) {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    }
                                }
                            },
                            onChildClick = {
                                completeOnboarding {
                                    navController.navigate(PlayerLoginRoute) {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    }
                                }
                            },
                            onSkip = {
                                completeOnboarding {
                                    navController.navigate(WelcomeRoute) {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
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
                                SecurePrefs.get(context, "app_prefs")
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
                                SecurePrefs.get(context, "app_prefs")
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
                    ExitConfirmDialog(
                        title = exitDialogTitle,
                        message = exitDialogMessage,
                        confirmText = exitDialogConfirm,
                        cancelText = exitDialogCancel,
                        onConfirm = {
                            showExitDialog = false
                            activity?.finishAffinity()
                            // Гарантированный выход, если finishAffinity не сработал
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        onDismiss = { showExitDialog = false }
                    )
                }

                // Диалог разрешения на уведомления — внутри LocalizedAppContent/FamiliBalanceTheme,
                // чтобы получить тёмную тему и локаль приложения.
                if (showNotifRationale) {
                    AlertDialog(
                        onDismissRequest = { showNotifRationale = false },
                        containerColor = SpaceBgStart,
                        title = {
                            Text(
                                stringResource(R.string.notif_rationale_title),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                stringResource(R.string.notif_rationale_message),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showNotifRationale = false
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart)
                            ) { Text(stringResource(R.string.notif_rationale_allow)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotifRationale = false }) {
                                Text(stringResource(R.string.notif_rationale_later), color = TextSecondary)
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

