package com.example.familibalance.feature.child

import com.example.familibalance.data.model.*
import com.example.familibalance.data.push.PushNavBus
import com.example.familibalance.util.*

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.familibalance.AppLocaleState
import com.example.familibalance.R
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.navigation.ChildAchievementsRoute
import com.example.familibalance.navigation.ChildHistoryRoute
import com.example.familibalance.navigation.ChildMainRoute
import com.example.familibalance.navigation.ChildTasksRoute
import com.example.familibalance.feature.child.components.CameraCaptureVisibility
import com.example.familibalance.feature.child.components.ChildSettingsBottomSheet
import com.example.familibalance.feature.child.components.InfoBottomSheet
import com.example.familibalance.feature.child.screens.*
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.components.StyleShopTab
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.ui.components.PulseBadge
import com.example.familibalance.ui.components.ThemeSelectionBottomSheet
import com.example.familibalance.ui.theme.*
import com.example.familibalance.viewmodel.actions.*


private data class ChildTab(
    val route: Any,
    @StringRes val titleRes: Int,
    @StringRes val a11yRes: Int,
    val icon: CloudIconType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSection(
    viewModel: MainViewModel,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit = {},
    isParentPreview: Boolean = false,
    onBackToAdmin: () -> Unit = {}
) {
    val childNavController = rememberNavController()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var styleSheetTab by remember { mutableStateOf(StyleShopTab.THEMES) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isSynced by viewModel.isSynced.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
        ?: ChildProfileState(id = "user_1", name = stringResource(R.string.child_player_role), avatar = "🧒")

    // Live-preview темы живёт только внутри детского раздела:
    // при выходе из раздела или смене ребёнка сбрасываем его,
    // чтобы вернуться к сохранённой теме профиля.
    DisposableEffect(child.id) {
        onDispose { ThemePreviewState.previewThemeId = null }
    }

    // Сброс live-preview при сворачивании приложения / блокировке
    // экрана (ON_STOP), чтобы временная тема не «зависала».
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                ThemePreviewState.previewThemeId = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Автосброс preview через 30 с «осмотра экрана»: работает только
    // когда шторка тем закрыта. Открытие шторки или смена темы
    // перезапускают эффект (новый ключ previewThemeId).
    var previewSecondsLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(ThemePreviewState.previewThemeId, showThemeSheet) {
        if (ThemePreviewState.previewThemeId != null && !showThemeSheet) {
            for (remaining in 30 downTo 1) {
                previewSecondsLeft = remaining
                kotlinx.coroutines.delay(1000L)
            }
            ThemePreviewState.previewThemeId = null
            previewSecondsLeft = 0
        } else {
            previewSecondsLeft = 0
        }
    }

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
        ChildTab(ChildMainRoute, R.string.nav_home, R.string.a11y_nav_home, CloudIconType.HOME),
        ChildTab(ChildTasksRoute, R.string.nav_quests, R.string.a11y_nav_quests, CloudIconType.TASKS),
        ChildTab(ChildAchievementsRoute, R.string.nav_achievements, R.string.a11y_nav_achievements, CloudIconType.ACHIEVEMENTS),
        ChildTab(ChildHistoryRoute, R.string.nav_history, R.string.a11y_nav_history, CloudIconType.HISTORY)
    )

    val offlineDesc = stringResource(R.string.a11y_offline_status)
    val themeDesc = stringResource(R.string.a11y_theme_button)
    val infoDesc = stringResource(R.string.a11y_info_button)
    val logoutDialogTitle = stringResource(R.string.logout_title)
    val logoutDialogMessage = stringResource(R.string.logout_message)
    val logoutDialogConfirm = stringResource(R.string.logout_confirm)
    val logoutDialogCancel = stringResource(R.string.action_cancel)
    val settingsDesc = stringResource(R.string.a11y_nav_settings)

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

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Флаги звука/вибрации — в SecurePrefs (app_prefs), дефолт true.
    // FeedbackManager читает их при каждом вызове.
    val appPrefs = remember { SecurePrefs.get(context, "app_prefs") }
    var soundEnabled by remember { mutableStateOf(appPrefs.getBoolean("sound_enabled", true)) }
    var vibrationEnabled by remember { mutableStateOf(appPrefs.getBoolean("vibration_enabled", true)) }

    fun navigateToTab(route: Any) {
        FeedbackManager.playClick(context, haptic)
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

    // Валюта наград ребёнка — для всех экранов раздела (баланс, квесты,
    // история, достижения). Символ подменяется на рендере, без конвертации.
    CompositionLocalProvider(LocalRewardCurrency provides child.rewardCurrency) {
    ThemedBackground(modifier = Modifier.fillMaxSize()) {
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
                        if (!isSynced) {
                            EmojiIcon(
                                emoji = "📴",
                                size = 16.dp,
                                color = StatusYellow,
                                contentDescription = offlineDesc
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            EmojiIcon(
                                emoji = "❓",
                                size = 18.dp,
                                contentDescription = infoDesc
                            )
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            CloudStyleIcon(
                                type = CloudIconType.SETTINGS,
                                size = 26.dp,
                                contentDescription = settingsDesc
                            )
                        }
                        IconButton(onClick = {
                            styleSheetTab = StyleShopTab.THEMES
                            showThemeSheet = true
                        }) {
                            CloudStyleIcon(
                                type = CloudIconType.SHOP_THEMES,
                                size = 26.dp,
                                contentDescription = themeDesc
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CloudAvatar(
                            avatarId = child.avatar,
                            size = 34.dp,
                            frameId = child.selectedFrameId,
                            modifier = Modifier
                                .bounceClick()
                                .clickable {
                                    FeedbackManager.playClick(context, haptic)
                                    styleSheetTab = StyleShopTab.AVATARS
                                    showThemeSheet = true
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Геймификация в шапке: уровень, ранг, множитель
                        // и тонкая полоска XP-прогресса.
                        val headerRank = GamificationEngine.rankForLevel(child.level)
                        val headerXpForNext = GamificationEngine.xpToNextLevel(child.level)
                        val headerMult = GamificationEngine.totalMultiplier(child.level, child.streakDays)
                        val headerXpFraction = if (headerXpForNext > 0) {
                            (child.xp.toFloat() / headerXpForNext).coerceIn(0f, 1f)
                        } else 0f
                        Column {
                            Text(
                                text = "${childDisplayName(child)} (${stringResource(R.string.child_player_role)})",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.child_level_badge, child.level) +
                                        " • " + stringResource(GamificationEngine.rankTitleRes(headerRank)),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                // На «Главной» — только «Ур. N • Ранг»:
                                // множитель и XP уже в BalanceCard.
                                if (headerMult > 1.0 && selectedTabRoute != ChildMainRoute) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.child_reward_mult, headerMult),
                                        color = StatusYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // XP-полоска в шапке — только на вкладках
                            // Задания/Достижения/История: на «Главной»
                            // она дублирует прогресс в BalanceCard.
                            if (selectedTabRoute != ChildMainRoute) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(110.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TextMuted.copy(alpha = 0.25f))
                                ) {
                                    if (headerXpFraction > 0.001f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(headerXpFraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(LocalAppPalette.current.primaryNeon)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                // Плавающая плашка предпросмотра: шторка закрыта,
                // но ребёнок смотрит тему на всём экране.
                val previewTheme = AllAppThemes.firstOrNull {
                    it.id == ThemePreviewState.previewThemeId
                }
                if (previewTheme != null && !showThemeSheet) {
                    ThemePreviewBar(
                        theme = previewTheme,
                        balance = child.balance,
                        secondsLeft = previewSecondsLeft,
                        onBuy = {
                            if (viewModel.purchaseTheme(child.id, previewTheme.id)) {
                                FeedbackManager.playCoin(context, haptic)
                                ThemePreviewState.previewThemeId = null
                            }
                        },
                        onClose = { ThemePreviewState.previewThemeId = null }
                    )
                }
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
                                        CloudStyleIcon(
                                            type = tab.icon,
                                            size = 28.dp,
                                            contentDescription = tabA11y
                                        )
                                    }
                                } else {
                                    CloudStyleIcon(
                                        type = tab.icon,
                                        size = 28.dp,
                                        contentDescription = tabA11y
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
                    // Быстрый fade 120 мс между вкладками: дефолтный
                // slide+fade даёт заметное наложение текстов при
                // быстром переключении нижней навигации.
                val tabFadeIn: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                    { fadeIn(tween(120)) }
                val tabFadeOut: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                    { fadeOut(tween(120)) }
                NavHost(navController = childNavController, startDestination = ChildMainRoute) {
                        composable<ChildMainRoute>(
                            enterTransition = tabFadeIn,
                            exitTransition = tabFadeOut,
                            popEnterTransition = tabFadeIn,
                            popExitTransition = tabFadeOut
                        ) {
                            MainChildScreen(
                                onNavigateToTasks = { navigateToTab(ChildTasksRoute) },
                                onAvatarClick = null,
                                viewModel = viewModel
                            )
                        }
                        composable<ChildTasksRoute>(
                            enterTransition = tabFadeIn,
                            exitTransition = tabFadeOut,
                            popEnterTransition = tabFadeIn,
                            popExitTransition = tabFadeOut
                        ) { TasksScreen(viewModel = viewModel) }
                        composable<ChildAchievementsRoute>(
                            enterTransition = tabFadeIn,
                            exitTransition = tabFadeOut,
                            popEnterTransition = tabFadeIn,
                            popExitTransition = tabFadeOut
                        ) { AchievementsScreen(viewModel = viewModel) }
                        composable<ChildHistoryRoute>(
                            enterTransition = tabFadeIn,
                            exitTransition = tabFadeOut,
                            popEnterTransition = tabFadeIn,
                            popExitTransition = tabFadeOut
                        ) { HistoryScreen(viewModel = viewModel) }
                    }
                }
            }
            if (showThemeSheet) {
                ThemeSelectionBottomSheet(
                    currentThemeId = child.selectedThemeId,
                    unlockedThemes = child.unlockedThemes,
                    claimedAchievementTitles = child.achievements
                        .filter { it.isClaimed }
                        .map { it.title }
                        .toSet(),
                    balance = child.balance,
                    previewThemeId = ThemePreviewState.previewThemeId,
                    onPreviewTheme = { ThemePreviewState.previewThemeId = it },
                    onThemeSelected = { viewModel.updateTheme(child.id, it) },
                    onPurchaseTheme = { viewModel.purchaseTheme(child.id, it) },
                    currentAvatar = child.avatar,
                    unlockedAvatars = child.unlockedAvatars,
                    initialTab = styleSheetTab,
                    onAvatarSelected = {
                        FeedbackManager.playLaser(context, haptic)
                        viewModel.updateAvatar(child.id, it)
                    },
                    onPurchaseAvatar = { viewModel.purchaseAvatar(child.id, it) },
                    onDismissRequest = { showThemeSheet = false }
                )
            }
            if (showSettingsSheet) {
                ChildSettingsBottomSheet(
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    onSoundToggle = { enabled ->
                        soundEnabled = enabled
                        appPrefs.edit { putBoolean("sound_enabled", enabled) }
                        if (enabled) FeedbackManager.playCoin(context, haptic)
                    },
                    onVibrationToggle = { enabled ->
                        vibrationEnabled = enabled
                        appPrefs.edit { putBoolean("vibration_enabled", enabled) }
                        if (enabled) FeedbackManager.playClick(context, haptic)
                    },
                    onLanguageSelect = { tag -> AppLocaleState.set(context, tag) },
                    onLogoutClick = {
                        showSettingsSheet = false
                        showLogoutDialog = true
                    },
                    onDismissRequest = { showSettingsSheet = false }
                )
            }
            if (showInfoSheet) {
                InfoBottomSheet(
                    child = child,
                    onDismissRequest = { showInfoSheet = false }
                )
            }
            if (showLogoutDialog) {
                AppDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = {
                        Text(
                            text = logoutDialogTitle,
                            color = PenaltyRed,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = logoutDialogMessage,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PenaltyRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(logoutDialogConfirm, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showLogoutDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                        ) {
                            Text(logoutDialogCancel)
                        }
                    }
                )
            }
        }
    }
    }
}

/**
 * Компактная стеклянная плашка live-preview над нижней навигацией:
 * показывается, когда шторка тем закрыта, а previewThemeId активен.
 * Для платной темы с достаточным балансом — кнопка покупки,
 * иначе иконка замка/кубка. ✕ возвращает сохранённую тему.
 */
@Composable
private fun ThemePreviewBar(
    theme: AppThemeConfig,
    balance: Int,
    secondsLeft: Int = 0,
    onBuy: () -> Unit,
    onClose: () -> Unit
) {
    val title = if (theme.titleRes != 0) stringResource(theme.titleRes) else theme.title
    val previewTitle = if (secondsLeft > 0) {
        stringResource(R.string.theme_preview_title_timer, title, secondsLeft)
    } else {
        stringResource(R.string.theme_preview_title, title)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .glassCard()
            .background(GlassBackground.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("👁", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = previewTitle,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        when (val rule = theme.unlockRule) {
            is ThemeUnlockRule.Paid -> {
                if (balance >= rule.price) {
                    val priceLabel = currencyString(R.string.theme_price, rule.price)
                    TextButton(
                        onClick = onBuy,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.theme_buy_for, priceLabel),
                            color = AccentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text("🔒", fontSize = 14.sp)
                }
            }
            is ThemeUnlockRule.Achievement -> Text("🏆", fontSize = 14.sp)
            ThemeUnlockRule.Free, is ThemeUnlockRule.Level -> Unit
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp)
        ) {
            Text("✕", color = TextSecondary, fontSize = 14.sp)
        }
    }
}
