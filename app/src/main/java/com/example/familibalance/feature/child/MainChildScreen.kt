package com.example.familibalance.feature.child

import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.components.ConfettiOverlay
import com.example.familibalance.viewmodel.actions.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChildScreen(
    modifier: Modifier = Modifier,
    onNavigateToTasks: () -> Unit = {},
    onAvatarClick: (() -> Unit)? = null,
    viewModel: MainViewModel
) {
    val dailyLimit = 90
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isProcessingAction by viewModel.isProcessingAction.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
    if (child == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.admin_no_data),
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }
    val balance = child.balance
    val boughtMinutesToday = child.boughtMinutesToday
    val honestAdmissionStatus = child.honestAdmissionStatus

    // Кулдаун честного признания: 1 раз в 7 дней
    val honestCooldownDaysLeft = remember(child.lastHonestAdmissionDate) {
        val last = runCatching { LocalDate.parse(child.lastHonestAdmissionDate) }.getOrNull()
        if (last == null) 0
        // coerceIn(0,7): при откате времени last может быть «в будущем»
        // и разница стать отрицательной — не показываем >7 дней кулдауна.
        else (7 - ChronoUnit.DAYS.between(last, LocalDate.now()).toInt()).coerceIn(0, 7)
    }

    // Кулдаун смены цели-мечты: 1 раз в 7 дней (или после выполнения цели)
    val dreamCooldownDaysLeft = remember(child.dreamSetDate, child.dreamTarget) {
        if (child.dreamTarget <= 0 || child.dreamSetDate.isBlank()) 0
        else {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val last = runCatching { LocalDate.parse(child.dreamSetDate, fmt) }.getOrNull()
            if (last == null) 0
            else (7 - ChronoUnit.DAYS.between(last, LocalDate.now()).toInt()).coerceIn(0, 7)
        }
    }
    val canChangeDream = dreamCooldownDaysLeft <= 0

    var showHonestCatScreen by remember { mutableStateOf(false) }
    var catAdmissionText by remember { mutableStateOf("") }
    var showDreamDialog by remember { mutableStateOf(false) }

    var showCashOutDialog by remember { mutableStateOf(false) }

    var lastRecordedDate by remember { mutableStateOf(getCurrentDateString()) }

    val currentDate = getCurrentDateString()
    LaunchedEffect(currentDate) {
        // Хронологическое сравнение: сбрасываем минуты только если текущая
        // дата строго позже последней записанной (защита от отката TZ).
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val lastParsed = runCatching { LocalDate.parse(lastRecordedDate, fmt) }.getOrNull()
        val currentParsed = runCatching { LocalDate.parse(currentDate, fmt) }.getOrNull()
        if (currentParsed != null && (lastParsed == null || currentParsed.isAfter(lastParsed))) {
            viewModel.resetDailyScreenTime(child.id)
            lastRecordedDate = currentDate
        }
    }

    var showBuyTimeSheet by remember { mutableStateOf(false) }

    // Строки диалога выхода больше не нужны: выход переехал
    // в шапку ChildSection (клик по профилю).

    if (showHonestCatScreen) {
        HonestCatSheet(
            admissionText = catAdmissionText,
            onTextChange = { catAdmissionText = it },
            onSendClick = {
                viewModel.submitHonestAdmission(child.id, catAdmissionText)
                catAdmissionText = ""
                showHonestCatScreen = false
            },
            onDismiss = {
                showHonestCatScreen = false
            }
        )
    }

    if (showDreamDialog) {
        DreamGoalDialog(
            initialTitle = child.dreamTitle,
            initialTarget = child.dreamTarget,
            onConfirm = { title, target ->
                viewModel.setDreamGoal(child.id, title, target)
                showDreamDialog = false
            },
            onDismiss = { showDreamDialog = false }
        )
    }

    if (showBuyTimeSheet) {
        BuyTimeBottomSheet(
            currentBalance = child.balance,
            boughtMinutesToday = child.boughtMinutesToday,
            dailyLimit = dailyLimit,
            isProcessing = isProcessingAction,
            onBuyTime = { cost: Int, minutes: Int ->
                viewModel.buyScreenTime(child.id, cost, minutes)
                showBuyTimeSheet = false
            },
            onDismissRequest = { showBuyTimeSheet = false }
        )
    }

    val animatedBalance by animateIntAsState(
        targetValue = balance,
        animationSpec = tween(500, easing = FastOutSlowInEasing)
    )

    val progressTarget = (boughtMinutesToday.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val haptic = LocalHapticFeedback.current
    var balanceBadgePosition by remember { mutableStateOf(Offset.Zero) }
    var activeCoinFlights by remember { mutableStateOf<List<CoinFlightData>>(emptyList()) }
    var confettiTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(confettiTrigger) {
        if (confettiTrigger > 0) {
            delay(2500)
            confettiTrigger = 0
        }
    }

    val triggerCoinFlight: (Offset, () -> Unit) -> Unit = { buttonOffset, onComplete ->
        confettiTrigger++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        RewardSoundPlayer.playCoin()

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            // ================= БЛОК 1: КАРТОЧКА БАЛАНСА =================
            item {
                BalanceCard(
                    avatar = child.avatar,
                    animatedBalance = animatedBalance,
                    onBadgePositioned = { balanceBadgePosition = it },
                    onAvatarClick = onAvatarClick
                )
            }

            // ================= БЛОК ВЫВОДА СРЕДСТВ И АКТИВНЫХ ЗАЯВОК (ПОД БАЛАНСОМ) =================
            item {
                CashoutSection(
                    cashoutRequests = child.cashoutRequests,
                    onCashOutClick = { showCashOutDialog = true },
                    onDismissRejected = { requestId ->
                        viewModel.dismissRejectedCashoutRequest(child.id, requestId)
                    }
                )
            }

            // ================= БАННЕР БОНУСА ЗА СТРИК =================
            if (child.streakBonusMessage.isNotEmpty()) {
                item {
                    StreakBonusBanner(
                        message = RewardCurrency.localize(child.streakBonusMessage, child.rewardCurrency),
                        onDismiss = { viewModel.dismissStreakBonus(child.id) }
                    )
                }
            }

            // ================= БЛОК 2: ЦЕЛЬ-МЕЧТА (ВЫБОР / ПРОГРЕСС) =================
            item {
                if (child.dreamTarget > 0) {
                    DreamGoalCard(
                        dreamTitle = child.dreamTitle,
                        dreamTarget = child.dreamTarget,
                        balance = balance,
                        isProcessingAction = isProcessingAction,
                        cooldownDaysLeft = dreamCooldownDaysLeft,
                        onClaim = {
                            triggerCoinFlight(Offset.Zero) {
                                viewModel.claimDreamGoal(child.id)
                            }
                        }
                    )
                    if (canChangeDream) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDreamDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassStroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                        ) {
                            Text(stringResource(R.string.child_dream_change_btn), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.child_dream_choose_title),
                                color = LocalAppPalette.current.primaryNeon,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.child_dream_choose_subtitle),
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showDreamDialog = true },
                                border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = LocalAppPalette.current.primaryNeon
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.child_dream_choose_btn), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // ================= БЛОК 3: ЭКРАННОЕ ВРЕМЯ =================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassStroke, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.child_screen_time_title),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.child_bought_minutes, boughtMinutesToday, dailyLimit),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Серая дорожка всегда; неоновая заливка — только при прогрессе > 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TextMuted.copy(alpha = 0.25f))
                        ) {
                            if (animatedProgress > 0.001f) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = LocalAppPalette.current.primaryNeon,
                                    trackColor = Color.Transparent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val canBuyTime = (balance > 0) && (boughtMinutesToday < dailyLimit)

                        OutlinedButton(
                            onClick = { showBuyTimeSheet = true },
                            enabled = canBuyTime,
                            border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = LocalAppPalette.current.primaryNeon,
                                disabledContentColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.child_buy_time_btn), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.child_daily_limit_note, dailyLimit),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ================= БЛОК 4: ДНИ БЕЗ ШТРАФОВ (7 ОГОНЬКОВ) =================
            item {
                StreakFlameCard(
                    streakDays = child.streakDays,
                    lastStreakDate = child.lastStreakDate,
                    onCheckIn = {
                        RewardSoundPlayer.playCoin()
                        viewModel.checkInStreak(child.id)
                    }
                )
            }

            // ================= БЛОК 5: ЧЕСТНОЕ ПРИЗНАНИЕ (только когда активно) =================
            // На кулдауне и после применения скидки кнопка не занимает место —
            // показываем её только когда функция доступна или есть что забрать.
            if (honestCooldownDaysLeft <= 0 || honestAdmissionStatus == QuestStatus.READY_TO_CLAIM) {
                item {
                    HonestCatEntry(
                        status = honestAdmissionStatus,
                        cooldownDaysLeft = honestCooldownDaysLeft,
                        onOpen = { showHonestCatScreen = true }
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
        if (confettiTrigger > 0) {
            ConfettiOverlay(trigger = confettiTrigger, modifier = Modifier.fillMaxSize())
        }
    }

    // Диалог вывода наличных
    if (showCashOutDialog) {
        CashOutDialog(
            balance = child.balance,
            pendingTotal = child.cashoutRequests
                .filter { it.status == CashoutStatus.PENDING }
                .sumOf { it.amount },
            isProcessing = isProcessingAction,
            onSubmit = { amount, comment ->
                viewModel.createCashoutRequest(amount, comment)
                showCashOutDialog = false
            },
            onDismiss = { showCashOutDialog = false }
        )
    }
}
