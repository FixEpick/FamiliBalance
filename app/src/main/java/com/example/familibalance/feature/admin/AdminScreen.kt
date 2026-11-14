package com.example.familibalance.feature.admin

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.ui.screens.*
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


import com.example.familibalance.R
import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.CashoutStatus

import com.example.familibalance.*
import com.example.familibalance.viewmodel.actions.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

sealed class AdminScreenType(val title: String, val icon: String) {
    object Verification : AdminScreenType("Проверка", "🛡️")
    object Progress : AdminScreenType("Прогресс", "📊")
    object History : AdminScreenType("История", "📜")
    object Settings : AdminScreenType("Настройки", "⚙️")
}

private data class AdminTab(
    val tab: AdminScreenType,
    val route: Any
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onSwitchProfile: () -> Unit,
    viewModel: MainViewModel,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adminNavController = rememberNavController()
    var showCashOutDialog by remember { mutableStateOf(false) }
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isSynced by viewModel.isSynced.collectAsStateWithLifecycle()

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
            title = { Text("💵 Выдача наличных", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Выдать ${child.name} ${child.balance} ₽ наличными и обнулить баланс в Космо-банке?",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminCashoutAll(child.id)
                        showCashOutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBgStart),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Выдать и обнулить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCashOutDialog = false },
                    border = BorderStroke(1.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Отмена")
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = StatusYellow.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, StatusYellow),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("👑", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Панель Папы", color = TextSecondary, fontSize = 11.sp)
                                Text("Администратор", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onSwitchProfile,
                            border = BorderStroke(1.dp, GlassStroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("🔄 Сменить", fontSize = 12.sp)
                        }
                    }

                    // Переключатель пользователей
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleChildren.forEach { profile ->
                            val isSelected = familyState.selectedChildId == profile.id
                            Surface(
                                onClick = {
                                    viewModel.selectChild(profile.id, pushRemote = true)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) NeonCyan.copy(alpha = 0.25f) else GlassBackground,
                                border = BorderStroke(1.dp, if (isSelected) NeonCyan else GlassStroke),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${profile.avatar} ${profile.name}",
                                        color = if (isSelected) NeonCyan else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = GlassBackground,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { adminTab ->
                        NavigationBarItem(
                            selected = adminTab.route == selectedTabRoute,
                            onClick = { navigateToTab(adminTab.route) },
                            icon = {
                                Text(adminTab.tab.icon, fontSize = 20.sp)
                            },
                            label = { Text(adminTab.tab.title, color = if (adminTab.route == selectedTabRoute) NeonCyan else TextSecondary) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = GlassStroke)
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
