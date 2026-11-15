package com.example.familibalance.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.R
import com.example.familibalance.feature.child.QuestDeliveryScreen
import com.example.familibalance.feature.child.QuestDeliveryTarget
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyTimeBottomSheet(
    currentBalance: Int,
    boughtMinutesToday: Int,
    dailyLimit: Int,
    onBuyTime: (cost: Int, minutes: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val minutesLeftToday = (dailyLimit - boughtMinutesToday).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.child_buytime_title),
                color = AccentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.child_buytime_limit, dailyLimit, minutesLeftToday),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            val tariffs = listOf(
                TariffData(stringResource(R.string.child_tariff_title, 30), 100, 30),
                TariffData(stringResource(R.string.child_tariff_title, 60), 200, 60),
                TariffData(stringResource(R.string.child_tariff_title, 90), 300, 90)
            )

            tariffs.forEach { tariff ->
                val isAffordable = (currentBalance >= tariff.cost) && (boughtMinutesToday + tariff.minutes <= dailyLimit)
                Card(
                    onClick = {
                        if (isAffordable) {
                            onBuyTime(tariff.cost, tariff.minutes)
                        }
                    },
                    enabled = isAffordable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(
                            width = 1.dp,
                            color = if (isAffordable) GlassStroke else GlassStroke.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAffordable) GlassBackground else GlassBackground.copy(alpha = 0.15f),
                        disabledContainerColor = GlassBackground.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tariff.title,
                            color = if (isAffordable) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (isAffordable) AccentColor.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isAffordable) AccentColor else TextSecondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${tariff.cost} ₽",
                                color = if (isAffordable) AccentColor else TextSecondary.copy(alpha = 0.4f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TariffData(
    val title: String,
    val cost: Int,
    val minutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesBottomSheet(
    wardrobeReward: Int,
    roomReward: Int,
    onDismissRequest: () -> Unit
) {
    val offlineDesc = stringResource(R.string.a11y_offline_status)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.rules_title),
                color = AccentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentColor.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📴",
                        fontSize = 22.sp,
                        modifier = Modifier.semantics {
                            contentDescription = offlineDesc
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.rules_offline),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            RuleBlock(
                title = stringResource(R.string.rules_balance_title),
                text = stringResource(R.string.rules_balance_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_earn_title),
                text = stringResource(R.string.rules_earn_text, wardrobeReward, roomReward)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_penalty_title),
                text = stringResource(R.string.rules_penalty_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_streak_title),
                text = stringResource(R.string.rules_streak_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_screentime_title),
                text = stringResource(R.string.rules_screentime_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_honest_title),
                text = stringResource(R.string.rules_honest_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_diary_title),
                text = stringResource(R.string.rules_diary_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules_achievements_title),
                text = stringResource(R.string.rules_achievements_text)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.rules_understood), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RuleBlock(
    title: String,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(1) }
    val tabs = listOf(
        stringResource(R.string.ach_tab_all),
        stringResource(R.string.ach_tab_in_progress),
        stringResource(R.string.ach_tab_hall)
    )

    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val currentChild = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val context = LocalContext.current
    val giftDesc = stringResource(R.string.a11y_gift_icon)
    val allAchievements = getAchievements(currentChild)

    val displayList = remember(selectedTab, allAchievements) {
        when (selectedTab) {
            1 -> allAchievements.filter { !it.isCompleted && !it.isLocked }
            2 -> allAchievements.filter { it.isCompleted }
            else -> allAchievements
        }
    }

    val completedCount = remember(allAchievements) {
        allAchievements.count { it.isCompleted }
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var balanceBadgePosition by remember { mutableStateOf(Offset.Zero) }
    var activeCoinFlights by remember { mutableStateOf<List<CoinFlightData>>(emptyList()) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            delay(2500)
            showConfetti = false
        }
    }

    val triggerCoinFlight: (Offset, () -> Unit) -> Unit = { buttonOffset, onComplete ->
        showConfetti = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch(Dispatchers.IO) {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            try {
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                delay(400)
            } catch (_: Exception) {
            } finally {
                toneGen.release()
            }
        }

        lateinit var flight: CoinFlightData
        flight = CoinFlightData(
            startX = if (buttonOffset != Offset.Zero) buttonOffset.x else 200f,
            startY = if (buttonOffset != Offset.Zero) buttonOffset.y else 500f,
            onFinished = {
                onComplete()
                activeCoinFlights = activeCoinFlights.filter { it != flight }
            }
        )
        activeCoinFlights = activeCoinFlights + flight
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Закреплённый мини-баланс — не скроллится
        MiniBalanceBar(
            balance = currentChild.balance,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                balanceBadgePosition = Offset(pos.x + coordinates.size.width - 80f, pos.y + coordinates.size.height / 2f)
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.ach_title),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ach_progress, completedCount, allAchievements.size),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                val isAllCompleted = completedCount == allAchievements.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / allAchievements.size.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentColor,
                        trackColor = CardBgDark.copy(alpha = 0.85f)
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isAllCompleted) {
                                    Modifier
                                        .border(1.5.dp, GoldBright, CircleShape)
                                        .background(GoldBright.copy(alpha = 0.25f), CircleShape)
                                } else {
                                    Modifier.alpha(0.3f)
                                }
                            )
                            .clickable {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.ach_gift_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🎁",
                            fontSize = 20.sp,
                            modifier = Modifier.semantics {
                                contentDescription = giftDesc
                            }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tabName ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tabName, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FilterSelectedBg.copy(alpha = 0.92f),
                            selectedLabelColor = AccentColor,
                            containerColor = FilterBg.copy(alpha = 0.85f),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedTab == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = AccentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        items(displayList, contentType = { "achievement" }) { item ->
            AchievementCard(
                title = item.title,
                condition = item.condition,
                description = item.description,
                reward = item.reward,
                progress = item.progress,
                isLocked = item.isLocked,
                isCompleted = item.isCompleted,
                isReadyToClaim = item.isReadyToClaim,
                onClaimClick = {
                    if (item.id.isNotBlank()) {
                        triggerCoinFlight(Offset.Zero) {
                            viewModel.claimAchievementReward(currentChild.id, item.id)
                        }
                    }
                }
            )
        }
        }
    }

        CoinFlightOverlay(
            flights = activeCoinFlights,
            badgePosition = balanceBadgePosition,
            onFlightFinished = { flight ->
                flight.onFinished()
            }
        )
        if (showConfetti) {
            ConfettiOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    showVerificationCard: Boolean = true
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    var showPendingSheet by remember { mutableStateOf(false) }
    var editingQuest by remember { mutableStateOf<QuestDeliveryTarget?>(null) }
    val filterLabels = listOf(
        stringResource(R.string.history_filter_all),
        stringResource(R.string.history_filter_quests),
        stringResource(R.string.ach_title),
        stringResource(R.string.history_filter_screentime),
        stringResource(R.string.history_filter_fines)
    )
    // Категории в historyItems хранятся на русском — маппинг для фильтрации.
    val filterCategories = listOf<String?>(null, "Квесты", "Достижения", "Экранное время", "Штрафы")
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val currentChild = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val rawHistoryItems = currentChild.historyItems
    val historyItems = remember(rawHistoryItems.size) {
        rawHistoryItems.filterNot { it.title.startsWith("Квест на проверке:") || it.title.startsWith("Одобрен квест:") }
    }

    val filteredItems = remember(selectedFilter, historyItems.size) {
        if (selectedFilter == 0) {
            historyItems
        } else {
            val category = filterCategories[selectedFilter]
            historyItems.filter { it.category == category }
        }
    }

    // Количество квестов, ожидающих проверки папой
    val pendingCount = remember(currentChild) {
        listOf(currentChild.closetStatus, currentChild.roomStatus, currentChild.diaryStatus)
            .count { it == QuestStatus.PENDING } +
            currentChild.bonusQuests.count { it.status == QuestStatus.PENDING } +
            currentChild.customQuests.count { it.status == QuestStatus.PENDING }
    }

    // Полноэкранная отправка/пересъёмка квеста из BottomSheet
    val activeEdit = editingQuest
    if (activeEdit != null) {
        QuestDeliveryScreen(
            questName = activeEdit.displayName,
            rewardText = activeEdit.rewardText,
            onSendClick = { comment, photoUri ->
                val photoUrlStr = photoUri?.toString()
                when {
                    activeEdit.key == "closet" || activeEdit.key == "room" || activeEdit.key == "diary" -> {
                        viewModel.submitQuestForReview(currentChild.id, activeEdit.key, photoUrlStr, comment)
                    }
                    activeEdit.key.startsWith("bonus:") -> {
                        viewModel.submitBonusQuestByChild(currentChild.id, activeEdit.key.removePrefix("bonus:"), photoUri, comment)
                    }
                    activeEdit.key.startsWith("custom:") -> {
                        viewModel.submitCustomQuestPhoto(currentChild.id, activeEdit.key.removePrefix("custom:"), photoUrlStr.orEmpty(), comment)
                    }
                }
                editingQuest = null
            },
            onBackClick = { editingQuest = null },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showVerificationCard) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPendingSheet = true }
                        .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏳", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.child_pending_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (pendingCount > 0) {
                                Text(
                                    text = stringResource(R.string.history_pending_count, pendingCount),
                                    color = StatusYellow,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.child_pending_empty),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("›", color = TextSecondary, fontSize = 20.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterLabels.size) { index ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = { Text(filterLabels[index], fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentColor.copy(alpha = 0.2f),
                            selectedLabelColor = AccentColor,
                            containerColor = GlassBackground,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = AccentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        items(filteredItems) { item ->
            HistoryItem(
                title = item.title,
                time = item.time,
                date = item.date,
                amount = item.amount,
                isIncome = item.isIncome
            )
        }

    }

    Text(
        text = stringResource(R.string.history_version, com.example.familibalance.BuildConfig.VERSION_NAME),
        color = TextSecondary.copy(alpha = 0.5f),
        fontSize = 10.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
    )
    }

    if (showPendingSheet) {
        PendingQuestsBottomSheet(
            child = currentChild,
            onDismiss = { showPendingSheet = false },
            onEditQuest = { target ->
                showPendingSheet = false
                editingQuest = target
            },
            viewModel = viewModel
        )
    }
}

/** Элемент списка квестов на проверке внутри BottomSheet. */
private data class PendingQuestItem(
    val key: String,
    val title: String,
    val rewardText: String,
    val photoUrl: String?,
    val comment: String?
)

/** Собирает все квесты ребёнка в статусе PENDING из основных, бонусных и кастомных списков. */
private fun collectPendingQuests(child: ChildProfileState): List<PendingQuestItem> {
    val items = mutableListOf<PendingQuestItem>()

    if (child.closetStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("closet", child.wardrobeTitle, "+${child.wardrobeReward} ₽", child.closetPhotoUrl, null)
    }
    if (child.roomStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("room", child.roomTitle, "+${child.roomReward} ₽", child.roomPhotoUrl, null)
    }
    if (child.diaryStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("diary", child.diaryTitle, "от +30 ₽ до ∞", child.diaryPhotoUrl, null)
    }
    child.bonusQuests.filter { it.status == QuestStatus.PENDING }.forEach { q ->
        items += PendingQuestItem("bonus:${q.id}", q.title, "+${q.reward} ₽", q.photoUrl, q.comment.ifBlank { null })
    }
    child.customQuests.filter { it.isVisible && it.status == QuestStatus.PENDING }.forEach { q ->
        items += PendingQuestItem("custom:${q.id}", q.title, "+${q.reward} ₽", q.photoUrl, q.description.ifBlank { null })
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PendingQuestsBottomSheet(
    child: ChildProfileState,
    onDismiss: () -> Unit,
    onEditQuest: (QuestDeliveryTarget) -> Unit,
    viewModel: MainViewModel
) {
    val pendingItems = remember(child) { collectPendingQuests(child) }
    val editDesc = stringResource(R.string.a11y_edit_button)
    val deleteDesc = stringResource(R.string.a11y_delete_button)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpaceBgStart,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.child_pending_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.child_pending_subtitle),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (pendingItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.child_pending_empty),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                pendingItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassBackground),
                        border = BorderStroke(1.dp, GlassStroke)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Фото квеста
                            if (!item.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.rewardText,
                                    color = AccentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!item.comment.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "💬 ${item.comment}",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }

                            // Кнопка «Изменить» — открыть QuestDeliveryScreen для пересъёмки
                            IconButton(
                                onClick = {
                                    onEditQuest(
                                        QuestDeliveryTarget(
                                            displayName = item.title,
                                            rewardText = item.rewardText,
                                            key = item.key
                                        )
                                    )
                                }
                            ) {
                                Text(
                                    "✏️",
                                    fontSize = 18.sp,
                                    modifier = Modifier.semantics {
                                        contentDescription = editDesc
                                    }
                                )
                            }

                            // Кнопка «Удалить» — отменить отправку на проверку
                            IconButton(
                                onClick = { viewModel.cancelPendingQuest(child.id, item.key) }
                            ) {
                                Text(
                                    "🗑️",
                                    fontSize = 18.sp,
                                    modifier = Modifier.semantics {
                                        contentDescription = deleteDesc
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EconomyBottomSheet(
    child: ChildProfileState,
    onDismissRequest: () -> Unit
) {
    val achievements = remember(child) { getAchievements(child) }
    val branchTitles = mapOf(
        AchievementBranch.DIARY to stringResource(R.string.eco_branch_diary),
        AchievementBranch.WARDROBE to stringResource(R.string.eco_branch_wardrobe),
        AchievementBranch.ROOM to stringResource(R.string.eco_branch_room)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = GlassStroke)
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.economy_title),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ===== Секция 1: Доходы =====
            item {
                EconomySectionTitle(stringResource(R.string.economy_earn_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_wardrobe_photo), "+${child.wardrobeReward} ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_room_photo), "+${child.roomReward} ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade5), "+50 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade4), "+30 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_streak), stringResource(R.string.eco_streak_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_dream), stringResource(R.string.eco_set_by_dad))
            }

            // ===== Секция 2: Расходы =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_spend_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_time30), "100 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time60), "200 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time90), "300 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade3), "−20 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade2), "−50 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_honest), stringResource(R.string.eco_honest_discount))
            }
            item {
                EconomyRow(stringResource(R.string.eco_parent_fine), stringResource(R.string.eco_set_by_dad))
            }

            // ===== Секция 3: Достижения =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_achievements_section))
            }
            AchievementBranch.entries.forEach { branch ->
                val branchItems = child.achievements
                    .filter { it.isVisible && it.branch == branch }
                    .sortedBy { it.targetCount }
                if (branchItems.isNotEmpty()) {
                    item {
                        Text(
                            text = branchTitles[branch] ?: branch.name,
                            color = LocalAppPalette.current.primaryNeon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    branchItems.forEach { config ->
                        val display = achievements.firstOrNull { it.id == config.id }
                        val isLocked = display?.isLocked == true
                        item {
                            EconomyRow(
                                title = config.title,
                                value = if (isLocked) "??? ₽" else "+${config.reward} ₽",
                                valueColor = if (isLocked) TextSecondary else StatusGreen
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EconomySectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EconomyRow(
    title: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextSecondary, fontSize = 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
