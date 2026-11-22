package com.example.familibalance.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.push.PushTokenRegistrar
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.ChildSection
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.ui.onboarding.OnboardingScreen
import com.example.familibalance.util.BenchmarkSession
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Корневой граф навигации приложения: онбординг → вход (ребёнок/родитель)
 * → ChildRoute / AdminRoute.
 *
 * Вынесен из MainActivity: activity отвечает за окно, splash и системные
 * диалоги, а маршруты и переходы живут здесь.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    isParentSession: Boolean,
    autoLoginChildId: String?,
    sessionStatus: SessionStatus
) {
    val context = LocalContext.current

    // «Назад» на главных разделах (ChildRoute / AdminRoute) —
    // диалог подтверждения выхода вместо двойного нажатия.
    // На экранах входа/регистрации и внутренних маршрутах
    // работает обычная навигация (popBackStack).
    //
    // Переход «в корень»: startDestination может быть уже удалён
    // из back stack после первого popUpTo(inclusive = true),
    // поэтому popUpTo(graph.startDestinationId) перестаёт находить
    // цель и back stack не очищается. Снимаем все destination'ы
    // до пустого стека (popBackStack() возвращает false, когда
    // снимать больше нечего), затем navigate создаёт чистый корень
    // без старых экранов авторизации и дублей маршрутов.
    fun navigateToRoot(target: Any) {
        while (navController.popBackStack()) {
            // pop до тех пор, пока стек не опустеет
        }
        navController.navigate(target)
    }
    fun backToLogin() {
        navigateToRoot(OnboardingRoute)
    }

    fun completeOnboarding(navigate: () -> Unit) {
        SecurePrefs.get(context, "app_prefs")
            .edit {
                putBoolean("onboarding_done", true)
            }
        navigate()
    }

    // Анонимная сессия (устройство ребёнка) не считается родительской:
    // в AdminRoute пускаем только пользователей с email.
    // (isParentSession вычислен в MainActivity — здесь не дублируем.)

    // Если сессия родителя уже активна — сразу в родительский кабинет
    var autoNavigatedToAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(isParentSession) {
        if (!autoNavigatedToAdmin && isParentSession) {
            autoNavigatedToAdmin = true
            // Регистрация FCM-токена родителя для пушей «Панель Родителя»
            (sessionStatus as? SessionStatus.Authenticated)
                ?.session?.user?.id
                ?.let { PushTokenRegistrar.registerParent(context, it) }
            // Синхронизируем локальный флаг автовхода с Supabase-сессией
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

    // Автовход ребёнка: MainViewModel выставляет autoLoginChildId
    // после загрузки локального стейта, если стоит флаг is_logged_in.
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

    NavHost(
        navController = navController,
        // Стартовый destination выбирается после разрешения сессии:
        // активная родительская сессия → кабинет, автовход ребёнка →
        // ChildRoute, иначе онбординг (выбор роли → слайды → вход).
        // Так OnboardingRoute не успевает отрисоваться перед
        // автоматическим переходом.
        startDestination = when {
            // Режим бенчмарка админки: Supabase-сессии родителя
            // нет, открываем AdminRoute напрямую с тестовыми детьми.
            BenchmarkSession.adminRequested -> AdminRoute
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
                    // Флаг автовхода родителя: при перезапуске
                    // isParentSession (Supabase) + этот флаг → AdminRoute
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
}
