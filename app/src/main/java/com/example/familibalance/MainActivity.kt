package com.example.familibalance

import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*

import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.navigation.toRoute
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.push.PushTokenRegistrar
import com.example.familibalance.data.push.handlePushIntent
import com.example.familibalance.navigation.AdminRoute
import com.example.familibalance.navigation.ChildAchievementsRoute
import com.example.familibalance.navigation.ChildHistoryRoute
import com.example.familibalance.navigation.ChildMainRoute
import com.example.familibalance.navigation.ChildRoute
import com.example.familibalance.navigation.ChildTasksRoute
import com.example.familibalance.navigation.OnboardingRoute
import com.example.familibalance.navigation.PlayerLoginRoute
import com.example.familibalance.navigation.RegisterParentRoute
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.ChildSection
import com.example.familibalance.feature.child.MainChildScreen
import com.example.familibalance.feature.child.TasksScreen
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.ui.auth.ResetPasswordScreen
import com.example.familibalance.ui.onboarding.OnboardingScreen
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.feature.child.screens.*
import com.example.familibalance.ui.components.AppSplashScreen
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
    /** true — открыт сценарий сброса пароля по deep link familibalance://auth. */
    private val passwordResetPending = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
        handlePushIntent(intent)
    }

    /**
     * Ловит auth deep link (сброс пароля): помечает сценарий и передаёт
     * intent в Supabase — handleDeeplinks импортирует recovery-сессию.
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "familibalance" && data.host == "auth") {
            passwordResetPending.value = true
            SupabaseManager.handleAuthDeepLink(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Р—Р°С‰РёС‚Р° РѕС‚ СЃРєСЂРёРЅС€РѕС‚РѕРІ Рё Р·Р°РїРёСЃРё СЌРєСЂР°РЅР°
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        AppLocaleState.init(this)
        RewardSoundPlayer.init(this)
        handleAuthDeepLink(intent)
        handlePushIntent(intent)

        enableEdgeToEdge()
        setContent {
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }

            val context = androidx.compose.ui.platform.LocalContext.current
            val familyState by viewModel.familyState.collectAsStateWithLifecycle()
            val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
            val autoLoginChildId by viewModel.autoLoginChildId.collectAsStateWithLifecycle()
            val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
            val themeChild = familyState.children[familyState.selectedChildId]
                ?: familyState.children.values.firstOrNull()
            val currentThemeId = themeChild?.selectedThemeId ?: "COSMIC_BLUE"
            LocalizedAppContent {
            FamiliBalanceTheme(themeId = currentThemeId) {
                // РџРѕРєР° РіСЂСѓР·РёС‚СЃСЏ Р»РѕРєР°Р»СЊРЅС‹Р№ СЃС‚РµР№С‚ Рё Supabase РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµС‚ СЃРµСЃСЃРёСЋ вЂ”
                // РїРѕРєР°Р·С‹РІР°РµРј splash, С‡С‚РѕР±С‹ РЅРµ РјРёРіР°Р» СЌРєСЂР°РЅ РІС…РѕРґР° РїРµСЂРµРґ Р°РґРјРёРЅРєРѕР№.
                var splashMinTimeElapsed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2400)
                    splashMinTimeElapsed = true
                }
                // supabase-kt на Android может кратковременно эмитить
                // NotAuthenticated/RefreshFailure во время восстановления
                // сессии (onStart/onStop lifecycle). Ждём стабилизации статуса,
                // иначе NavHost стартует с WelcomeRoute и кабинет «мигает».
                var sessionReady by remember { mutableStateOf(false) }
                LaunchedEffect(sessionStatus) {
                    if (sessionReady) return@LaunchedEffect
                    val status = sessionStatus
                    when (status) {
                        SessionStatus.Initializing,
                        is SessionStatus.RefreshFailure -> Unit
                        is SessionStatus.Authenticated -> sessionReady = true
                        is SessionStatus.NotAuthenticated -> {
                            val settleMs = if (status.isSignOut) 300L else 1500L
                            kotlinx.coroutines.delay(settleMs)
                            sessionReady = true
                        }
                    }
                }
                if (!splashMinTimeElapsed || isInitializing || !sessionReady) {
                    AppSplashScreen()
                    return@FamiliBalanceTheme
                }

                // Сценарий сброса пароля по deep link: recovery-сессия уже
                // импортирована, показываем экран нового пароля вместо NavHost.
                if (passwordResetPending.value) {
                    ResetPasswordScreen(
                        viewModel = authViewModel,
                        onDone = { passwordResetPending.value = false },
                        onCancel = { passwordResetPending.value = false }
                    )
                    return@FamiliBalanceTheme
                }

                val navController = rememberNavController()

                // В«РќР°Р·Р°РґВ» РЅР° РіР»Р°РІРЅС‹С… СЂР°Р·РґРµР»Р°С… (ChildRoute / AdminRoute) вЂ”
                // РґРёР°Р»РѕРі РїРѕРґС‚РІРµСЂР¶РґРµРЅРёСЏ РІС‹С…РѕРґР° РІРјРµСЃС‚Рѕ РґРІРѕР№РЅРѕРіРѕ РЅР°Р¶Р°С‚РёСЏ.
                // РќР° СЌРєСЂР°РЅР°С… РІС…РѕРґР°/СЂРµРіРёСЃС‚СЂР°С†РёРё Рё РІРЅСѓС‚СЂРµРЅРЅРёС… РјР°СЂС€СЂСѓС‚Р°С…
                // СЂР°Р±РѕС‚Р°РµС‚ РѕР±С‹С‡РЅР°СЏ РЅР°РІРёРіР°С†РёСЏ (popBackStack).
                val topBackStackEntry by navController.currentBackStackEntryAsState()
                val isMainSection = topBackStackEntry?.destination?.let {
                    it.hasRoute<ChildRoute>() || it.hasRoute<AdminRoute>()
                } == true
                // Родительский предпросмотр детского интерфейса: «Назад»
                // возвращает в админку, а не показывает диалог выхода.
                val isChildPreview = topBackStackEntry?.let { entry ->
                    entry.destination.hasRoute<ChildRoute>() &&
                        runCatching { entry.toRoute<ChildRoute>().asParent }.getOrDefault(false)
                } == true
                var showExitDialog by remember { mutableStateOf(false) }
                val activity = LocalActivity.current
                BackHandler(enabled = isMainSection) {
                    if (isChildPreview) {
                        while (navController.popBackStack()) {
                            // pop до пустого стека, затем чистый корень
                        }
                        navController.navigate(AdminRoute)
                    } else {
                        showExitDialog = true
                    }
                }

                // Р–РёР·РЅРµРЅРЅС‹Р№ С†РёРєР» Realtime Рё СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё:
                //  - ON_START: РїРѕРґРїРёСЃРєР° Р°РєС‚РёРІРЅР° РІСЃРµРіРґР°, РІРєР»СЋС‡Р°СЏ С„РѕРЅ
                //    (startRealtimeSync РёРґРµРјРїРѕС‚РµРЅС‚РµРЅ вЂ” РїРѕРІС‚РѕСЂРЅС‹Р№ РІС‹Р·РѕРІ no-op);
                //  - ON_RESUME: РґРѕСЃС‹Р»Р°РµРј РЅРµСЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°РЅРЅС‹Рµ РґР°РЅРЅС‹Рµ Рё РѕР±РЅРѕРІР»РµРЅРёСЏ.
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
                                // Р•СЃР»Рё Р¶РґР°Р»Рё СЂР°Р·СЂРµС€РµРЅРёСЏ РЅР° СѓСЃС‚Р°РЅРѕРІРєСѓ вЂ” РїСЂРѕРґРѕР»Р¶Р°РµРј РѕР±РЅРѕРІР»РµРЅРёРµ
                                updateViewModel.onResume()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // РџРµСЂРµС…РѕРґ В«РІ РєРѕСЂРµРЅСЊВ»: startDestination РјРѕР¶РµС‚ Р±С‹С‚СЊ СѓР¶Рµ СѓРґР°Р»С‘РЅ
                // РёР· back stack РїРѕСЃР»Рµ РїРµСЂРІРѕРіРѕ popUpTo(inclusive = true),
                // РїРѕСЌС‚РѕРјСѓ popUpTo(graph.startDestinationId) РїРµСЂРµСЃС‚Р°С‘С‚ РЅР°С…РѕРґРёС‚СЊ
                // С†РµР»СЊ Рё back stack РЅРµ РѕС‡РёС‰Р°РµС‚СЃСЏ. РЎРЅРёРјР°РµРј РІСЃРµ destination'С‹
                // РґРѕ РїСѓСЃС‚РѕРіРѕ СЃС‚РµРєР° (popBackStack() РІРѕР·РІСЂР°С‰Р°РµС‚ false, РєРѕРіРґР°
                // СЃРЅРёРјР°С‚СЊ Р±РѕР»СЊС€Рµ РЅРµС‡РµРіРѕ), Р·Р°С‚РµРј navigate СЃРѕР·РґР°С‘С‚ С‡РёСЃС‚С‹Р№ РєРѕСЂРµРЅСЊ
                // Р±РµР· СЃС‚Р°СЂС‹С… СЌРєСЂР°РЅРѕРІ Р°РІС‚РѕСЂРёР·Р°С†РёРё Рё РґСѓР±Р»РµР№ РјР°СЂС€СЂСѓС‚РѕРІ.
                fun navigateToRoot(target: Any) {
                    while (navController.popBackStack()) {
                        // pop РґРѕ С‚РµС… РїРѕСЂ, РїРѕРєР° СЃС‚РµРє РЅРµ РѕРїСѓСЃС‚РµРµС‚
                    }
                    navController.navigate(target)
                }
                fun backToLogin() {
                    navigateToRoot(OnboardingRoute)
                }

                // РђРЅРѕРЅРёРјРЅР°СЏ СЃРµСЃСЃРёСЏ (СѓСЃС‚СЂРѕР№СЃС‚РІРѕ СЂРµР±С‘РЅРєР°) РЅРµ СЃС‡РёС‚Р°РµС‚СЃСЏ СЂРѕРґРёС‚РµР»СЊСЃРєРѕР№:
                // РІ AdminRoute РїСѓСЃРєР°РµРј С‚РѕР»СЊРєРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»РµР№ СЃ email.
                val isParentSession = (sessionStatus as? SessionStatus.Authenticated)
                    ?.session?.user?.email?.isNotBlank() == true

                // Р•СЃР»Рё СЃРµСЃСЃРёСЏ СЂРѕРґРёС‚РµР»СЏ СѓР¶Рµ Р°РєС‚РёРІРЅР° вЂ” СЃСЂР°Р·Сѓ РІ СЂРѕРґРёС‚РµР»СЊСЃРєРёР№ РєР°Р±РёРЅРµС‚
                var autoNavigatedToAdmin by remember { mutableStateOf(false) }
                LaunchedEffect(isParentSession) {
                    if (!autoNavigatedToAdmin && isParentSession) {
                        autoNavigatedToAdmin = true
                        // Регистрация FCM-токена родителя для пушей «Панель Родителя»
                        (sessionStatus as? SessionStatus.Authenticated)
                            ?.session?.user?.id
                            ?.let { PushTokenRegistrar.registerParent(context, it) }
                        // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј Р»РѕРєР°Р»СЊРЅС‹Р№ С„Р»Р°Рі Р°РІС‚РѕРІС…РѕРґР° СЃ Supabase-СЃРµСЃСЃРёРµР№
                        SecurePrefs.get(context, "app_prefs")
                            .edit {
                                putBoolean("is_admin_logged_in", true)
                                putBoolean("is_logged_in", false)
                                putBoolean("onboarding_done", true)
                                remove("logged_in_child_id")
                            }
                        // Переход в AdminRoute выполняет startDestination NavHost
                    }
                }

                // РђРІС‚РѕРІС…РѕРґ СЂРµР±С‘РЅРєР°: MainViewModel РІС‹СЃС‚Р°РІР»СЏРµС‚ autoLoginChildId
                // РїРѕСЃР»Рµ Р·Р°РіСЂСѓР·РєРё Р»РѕРєР°Р»СЊРЅРѕРіРѕ СЃС‚РµР№С‚Р°, РµСЃР»Рё СЃС‚РѕРёС‚ С„Р»Р°Рі is_logged_in.
                var autoChildNavigated by remember { mutableStateOf(false) }
                LaunchedEffect(autoLoginChildId) {
                    if (!autoChildNavigated && autoLoginChildId != null) {
                        autoChildNavigated = true
                        // Регистрация FCM-токена ребёнка для пушей «Игрок»
                        PushTokenRegistrar.registerChild(context, autoLoginChildId!!)
                        SecurePrefs.get(context, "app_prefs")
                            .edit {
                                putBoolean("onboarding_done", true)
                            }
                        // Переход в ChildRoute выполняет startDestination NavHost
                    }
                }

                fun completeOnboarding(navigate: () -> Unit) {
                    SecurePrefs.get(context, "app_prefs")
                        .edit {
                            putBoolean("onboarding_done", true)
                        }
                    navigate()
                }

                NavHost(
                    navController = navController,
                    // Стартовый destination выбирается после разрешения сессии:
                    // активная родительская сессия → кабинет, автовход ребёнка →
                    // ChildRoute, иначе онбординг (выбор роли → слайды → вход).
                    // Так OnboardingRoute не успевает отрисоваться перед
                    // автоматическим переходом.
                    startDestination = when {
                        isParentSession -> AdminRoute
                        autoLoginChildId != null -> ChildRoute()
                        else -> OnboardingRoute
                    }
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            onParentClick = {
                                completeOnboarding {
                                    navigateToRoot(RegisterParentRoute(parentLogin = true))
                                }
                            },
                            onChildClick = {
                                completeOnboarding {
                                    navigateToRoot(PlayerLoginRoute)
                                }
                            }
                        )
                    }
                    composable<PlayerLoginRoute> {
                        PlayerLoginScreen(
                            viewModel = viewModel,
                            authViewModel = authViewModel,
                            onBack = { navController.popBackStack() },
                            onLoginSuccess = {
                                navigateToRoot(ChildRoute())
                            }
                        )
                    }
                    composable<RegisterParentRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<RegisterParentRoute>()
                        RegisterParentScreen(
                            viewModel = authViewModel,
                            mainViewModel = viewModel,
                            initialParentLogin = route.parentLogin,
                            onPlayerSuccess = {
                                navigateToRoot(ChildRoute())
                            },
                            onParentSuccess = {
                                // Р¤Р»Р°Рі Р°РІС‚РѕРІС…РѕРґР° СЂРѕРґРёС‚РµР»СЏ: РїСЂРё РїРµСЂРµР·Р°РїСѓСЃРєРµ
                                // isParentSession (Supabase) + СЌС‚РѕС‚ С„Р»Р°Рі в†’ AdminRoute
                                SecurePrefs.get(context, "app_prefs")
                                    .edit {
                                        putBoolean("is_admin_logged_in", true)
                                        putBoolean("is_logged_in", false)
                                        remove("logged_in_child_id")
                                    }
                                navigateToRoot(AdminRoute)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<AdminRoute> {
                        AdminScreen(
                            viewModel = viewModel,
                            onEnterChildMode = { childId ->
                                viewModel.selectChild(childId)
                                navigateToRoot(ChildRoute(asParent = true))
                            },
                            onLogout = {
                                PushTokenRegistrar.unregister(context)
                                authViewModel.signOut()
                                viewModel.clearState()
                                SecurePrefs.get(context, "app_prefs")
                                    .edit {
                                        putBoolean("is_admin_logged_in", false)
                                        putBoolean("is_logged_in", false)
                                        remove("logged_in_child_id")
                                    }
                                navigateToRoot(OnboardingRoute)
                            }
                        )
                    }
                    composable<ChildRoute> { backStackEntry ->
                        val childRoute = backStackEntry.toRoute<ChildRoute>()
                        ChildSection(
                            viewModel = viewModel,
                            isParentPreview = childRoute.asParent,
                            onBackToAdmin = { navigateToRoot(AdminRoute) },
                            onSwitchProfile = {
                                PushTokenRegistrar.unregister(context)
                                backToLogin()
                            },
                            onLogout = {
                                if (childRoute.asParent) {
                                    navigateToRoot(AdminRoute)
                                } else {
                                    PushTokenRegistrar.unregister(context)
                                    viewModel.logoutChild()
                                    navigateToRoot(OnboardingRoute)
                                }
                            }
                        )
                    }
                }

                // РЎС‚СЂРѕРєРё РґРёР°Р»РѕРіР° РІС‹С…РѕРґР° РІС‹С‡РёСЃР»СЏРµРј Р·РґРµСЃСЊ, Р° РЅРµ РІРЅСѓС‚СЂРё AlertDialog:
                // Dialog СЃРѕР·РґР°С‘С‚ РѕС‚РґРµР»СЊРЅРѕРµ РѕРєРЅРѕ Рё РјРѕР¶РµС‚ РїРѕС‚РµСЂСЏС‚СЊ Р»РѕРєР°Р»РёР·РѕРІР°РЅРЅС‹Р№ РєРѕРЅС‚РµРєСЃС‚.
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
                            // Р“Р°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅС‹Р№ РІС‹С…РѕРґ, РµСЃР»Рё finishAffinity РЅРµ СЃСЂР°Р±РѕС‚Р°Р»
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        onDismiss = { showExitDialog = false }
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

