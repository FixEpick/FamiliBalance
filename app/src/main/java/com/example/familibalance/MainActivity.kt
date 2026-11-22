package com.example.familibalance

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.familibalance.navigation.AdminRoute
import com.example.familibalance.navigation.AppNavGraph
import com.example.familibalance.navigation.ChildRoute
import com.example.familibalance.ui.AppSessionEffects
import com.example.familibalance.ui.auth.ResetPasswordScreen
import com.example.familibalance.ui.components.AppSplashScreen
import com.example.familibalance.ui.components.ExitConfirmDialog
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.update.UpdateDialog
import com.example.familibalance.util.finishApp
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.UpdateViewModel
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var updateViewModel: UpdateViewModel
    private lateinit var authViewModel: AuthViewModel
    /** true — открыт сценарий сброса пароля по deep link familibalance://auth. */
    private val passwordResetPending = mutableStateOf(false)
    private val intents by lazy {
        MainActivityIntents { passwordResetPending.value = true }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intents.handle(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Защита от скриншотов и записи экрана
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        // Baseline Profile: генератор передаёт extra — пропускаем
        // онбординг/вход. Значение "admin" — кабинет родителя с тестовыми
        // детьми, "child" или boolean true — детский раздел.
        val benchMode = intent.getStringExtra(
            com.example.familibalance.util.BenchmarkSession.EXTRA_SKIP_ONBOARDING
        )
        com.example.familibalance.util.BenchmarkSession.adminRequested = benchMode == "admin"
        com.example.familibalance.util.BenchmarkSession.requested =
            benchMode == "child" ||
                (benchMode == null && intent.getBooleanExtra(
                    com.example.familibalance.util.BenchmarkSession.EXTRA_SKIP_ONBOARDING,
                    false
                ))
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        AppLocaleState.init(this)
        intents.handle(intent)

        // Debug-сборка: одноразовый Toast при каждом запуске,
        // чтобы сразу было видно, какая сборка установлена.
        if (BuildConfig.DEBUG) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.debug_build_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        // Тёмный UI → светлые иконки системных баров (иначе часы/батарея
        // сливаются с тёмным фоном статус-бара).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }

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

                // «Назад» на главных разделах (ChildRoute / AdminRoute) —
                // диалог подтверждения выхода вместо двойного нажатия.
                // На экранах входа/регистрации и внутренних маршрутах
                // работает обычная навигация (popBackStack).
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

                // Жизненный цикл синхронизации (Realtime + forceSync +
                // проверка обновлений) — в AppSessionEffects.
                AppSessionEffects(
                    viewModel = viewModel,
                    updateViewModel = updateViewModel,
                    sessionStatus = sessionStatus
                )

                // Анонимная сессия (устройство ребёнка) не считается родительской:
                // в AdminRoute пускаем только пользователей с email.
                val isParentSession = (sessionStatus as? SessionStatus.Authenticated)
                    ?.session?.user?.email?.isNotBlank() == true

                AppNavGraph(
                    navController = navController,
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    isParentSession = isParentSession,
                    autoLoginChildId = autoLoginChildId,
                    sessionStatus = sessionStatus
                )

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
                            activity?.finishApp()
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
