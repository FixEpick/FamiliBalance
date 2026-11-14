package com.example.familibalance

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.res.painterResource
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
import com.example.familibalance.navigation.LoginRoute
import com.example.familibalance.feature.admin.AdminScreen
import com.example.familibalance.feature.child.MainChildScreen
import com.example.familibalance.feature.child.TasksScreen
import com.example.familibalance.ui.auth.PlayerLoginScreen
import com.example.familibalance.ui.auth.RegisterParentScreen
import com.example.familibalance.ui.auth.WelcomeScreen
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.*
import com.example.familibalance.ui.screens.*
import com.example.familibalance.ui.components.PulseBadge
import com.example.familibalance.ui.components.ThemeSelectionBottomSheet
import com.example.familibalance.ui.theme.*
import io.github.jan.supabase.auth.status.SessionStatus
import com.example.familibalance.ui.update.UpdateDialog
import com.example.familibalance.viewmodel.UpdateViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var updateViewModel: UpdateViewModel
    private lateinit var authViewModel: AuthViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        updateViewModel = ViewModelProvider(this)[UpdateViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }
            val familyState by viewModel.familyState.collectAsStateWithLifecycle()
            val sessionStatus by authViewModel.sessionStatus.collectAsStateWithLifecycle()
            val themeChild = familyState.children[familyState.selectedChildId]
                ?: familyState.children.values.firstOrNull()
            val currentThemeId = themeChild?.selectedThemeId ?: "COSMIC_BLUE"
            FamiliBalanceTheme(themeId = currentThemeId) {
                val navController = rememberNavController()

                // При возврате приложения из фона — досылаем несинхронизированные данные
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.forceSync()
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

                // Если сессия Supabase уже активна — сразу в родительский кабинет
                var autoNavigatedToAdmin by remember { mutableStateOf(false) }
                LaunchedEffect(sessionStatus) {
                    if (!autoNavigatedToAdmin && sessionStatus is SessionStatus.Authenticated) {
                        autoNavigatedToAdmin = true
                        navController.navigate(AdminRoute) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = if (sessionStatus is SessionStatus.NotAuthenticated) WelcomeRoute else AdminRoute
                ) {
                    composable<WelcomeRoute> {
                        WelcomeScreen(
                            onPlayerClick = { navController.navigate(PlayerLoginRoute) },
                            onRegisterClick = { navController.navigate(RegisterParentRoute) },
                            onParentGatePassed = { navController.navigate(RegisterParentRoute) }
                        )
                    }
                    composable<PlayerLoginRoute> {
                        PlayerLoginScreen(
                            profiles = familyState.children.values.sortedBy { it.name },
                            onBack = { navController.popBackStack() },
                            onLoginSuccess = { childId ->
                                viewModel.selectChild(childId)
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
                                navController.navigate(AdminRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<LoginRoute> {
                        LoginScreen(
                            onSelectRole = { role, childId ->
                                if (childId.isNotEmpty()) {
                                    viewModel.selectChild(childId)
                                }
                                navController.navigate(
                                    if (role == AppRole.DAD) AdminRoute else ChildRoute
                                )
                            }
                        )
                    }
                    composable<AdminRoute> {
                        AdminScreen(
                            onSwitchProfile = { backToLogin() },
                            viewModel = viewModel,
                            onLogout = {
                                authViewModel.signOut()
                                viewModel.clearState()
                                navController.navigate(WelcomeRoute) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<ChildRoute> {
                        ChildSection(
                            viewModel = viewModel,
                            onSwitchProfile = { backToLogin() }
                        )
                    }
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

private data class ChildTab(
    val route: Any,
    val title: String,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildSection(
    viewModel: MainViewModel,
    onSwitchProfile: () -> Unit
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

    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedTabRoute = when {
        currentDestination?.hasRoute<ChildTasksRoute>() == true -> ChildTasksRoute
        currentDestination?.hasRoute<ChildAchievementsRoute>() == true -> ChildAchievementsRoute
        currentDestination?.hasRoute<ChildHistoryRoute>() == true -> ChildHistoryRoute
        else -> ChildMainRoute
    }

    val tabs = listOf(
        ChildTab(ChildMainRoute, "Главная", "🏠"),
        ChildTab(ChildTasksRoute, "Задания", "✨"),
        ChildTab(ChildAchievementsRoute, "Достижения", "🏆"),
        ChildTab(ChildHistoryRoute, "История", "📜")
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
                    if (!isSynced) {
                        Text(
                            text = "📴",
                            fontSize = 16.sp,
                            color = StatusYellow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showThemeSheet = true }) {
                        Text("🎨", fontSize = 18.sp)
                    }
                    OutlinedButton(
                        onClick = onSwitchProfile,
                        border = BorderStroke(1.dp, GlassStroke),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardBgDark.copy(alpha = 0.8f),
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🔄 Сменить", fontSize = 12.sp)
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
                                    tab.title,
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
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(navController = childNavController, startDestination = ChildMainRoute) {
                        composable<ChildMainRoute> {
                            MainChildScreen(
                                onNavigateToTasks = { navigateToTab(ChildTasksRoute) },
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
