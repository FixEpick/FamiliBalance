package com.example.familibalance.feature.child

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
import com.example.familibalance.R
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.push.PushNavBus
import com.example.familibalance.navigation.AdminRoute
import com.example.familibalance.navigation.ChildAchievementsRoute
import com.example.familibalance.navigation.ChildHistoryRoute
import com.example.familibalance.navigation.ChildMainRoute
import com.example.familibalance.navigation.ChildRoute
import com.example.familibalance.navigation.ChildTasksRoute
import com.example.familibalance.navigation.PlayerLoginRoute
import com.example.familibalance.navigation.RegisterParentRoute
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.MainChildScreen
import com.example.familibalance.feature.child.TasksScreen
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.feature.child.components.CameraCaptureVisibility
import com.example.familibalance.feature.child.screens.*
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.ui.components.PulseBadge
import com.example.familibalance.ui.components.ThemeSelectionBottomSheet
import com.example.familibalance.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import com.example.familibalance.ui.update.UpdateDialog
import com.example.familibalance.viewmodel.UpdateViewModel


private data class ChildTab(
    val route: Any,
    @StringRes val titleRes: Int,
    @StringRes val a11yRes: Int,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSection(
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
        ChildTab(ChildMainRoute, R.string.nav_home, R.string.a11y_nav_home, "🏠"),
        ChildTab(ChildTasksRoute, R.string.nav_quests, R.string.a11y_nav_quests, "✨"),
        ChildTab(ChildAchievementsRoute, R.string.nav_achievements, R.string.a11y_nav_achievements, "🏆"),
        ChildTab(ChildHistoryRoute, R.string.nav_history, R.string.a11y_nav_history, "📜")
    )

    val offlineDesc = stringResource(R.string.a11y_offline_status)
    val themeDesc = stringResource(R.string.a11y_theme_button)

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

    // Deep-link из пуша: переключаем вкладку по роли и экрану.
    // Чужая роль (parent-пуш на детском устройстве) просто сбрасывается.
    val pushNav by PushNavBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pushNav) {
        val nav = pushNav ?: return@LaunchedEffect
        PushNavBus.consume()
        if (nav.role != "child") return@LaunchedEffect
        navigateToTab(
            when (nav.screen) {
                "tasks" -> ChildTasksRoute
                "history" -> ChildHistoryRoute
                "achievements" -> ChildAchievementsRoute
                else -> ChildMainRoute
            }
        )
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
                            text = "${childDisplayName(child)} (${stringResource(R.string.child_player_role)})",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSynced) {
                            EmojiIcon(
                                emoji = "📴",
                                size = 16.dp,
                                color = StatusYellow,
                                contentDescription = offlineDesc
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        LanguageSelectorButton()
                        IconButton(onClick = { showThemeSheet = true }) {
                            EmojiIcon(
                                emoji = "🎨",
                                size = 18.dp,
                                contentDescription = themeDesc
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!CameraCaptureVisibility.isCameraOpen) {
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
                                val tabA11y = stringResource(tab.a11yRes)
                                val showBadge = when (tab.route) {
                                    ChildTasksRoute -> hasClaimableTasks
                                    ChildAchievementsRoute -> hasClaimableAchievements
                                    else -> false
                                }
                                if (showBadge) {
                                    BadgedBox(badge = { PulseBadge() }) {
                                        Text(
                                            tab.icon,
                                            fontSize = 20.sp,
                                            modifier = Modifier.semantics {
                                                contentDescription = tabA11y
                                            }
                                        )
                                    }
                                } else {
                                    Text(
                                        tab.icon,
                                        fontSize = 20.sp,
                                        modifier = Modifier.semantics {
                                            contentDescription = tabA11y
                                        }
                                    )
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
