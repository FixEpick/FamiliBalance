package com.example.familibalance.feature.child

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
    onNavigateToTasks: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dailyLimit = 90
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isProcessingAction by viewModel.isProcessingAction.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
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

    var showHonestCatScreen by remember { mutableStateOf(false) }
    var catAdmissionText by remember { mutableStateOf("") }

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

    var showRulesSheet by remember { mutableStateOf(false) }
    var showEconomySheet by remember { mutableStateOf(false) }
    var showBuyTimeSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Строки диалога выхода резолвим здесь, а не внутри AlertDialog:
    // Dialog создаёт отдельное окно и может потерять локализованный LocalContext.
    val logoutDialogTitle = stringResource(R.string.logout_title)
    val logoutDialogMessage = stringResource(R.string.logout_message)
    val logoutDialogConfirm = stringResource(R.string.logout_confirm)
    val logoutDialogCancel = stringResource(R.string.action_cancel)

    if (showHonestCatScreen) {
        HonestCatScreen(
            admissionText = catAdmissionText,
            onTextChange = { catAdmissionText = it },
            onSendClick = {
                viewModel.submitHonestAdmission(child.id, catAdmissionText)
                catAdmissionText = ""
                showHonestCatScreen = false
            },
            onBackClick = {
                showHonestCatScreen = false
            },
            modifier = modifier
        )
        return
    }

    if (showRulesSheet) {
        RulesBottomSheet(
            wardrobeReward = child.wardrobeReward,
            roomReward = child.roomReward,
            onDismissRequest = { showRulesSheet = false }
        )
    }

    if (showEconomySheet) {
        EconomyBottomSheet(
            child = child,
            onDismissRequest = { showEconomySheet = false }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            // ================= БЛОК 1: КАРТОЧКА БАЛАНСА =================
            item {
                BalanceCard(
                    avatar = child.avatar,
                    animatedBalance = animatedBalance,
                    onBadgePositioned = { balanceBadgePosition = it }
                )
            }

            // ================= БЛОК ВЫВОДА СРЕДСТВ И АКТИВНЫХ ЗАЯВОК (ПОД БАЛАНСОМ) =================
            item {
                CashoutSection(
                    cashoutRequests = child.cashoutRequests,
                    onCashOutClick = { showCashOutDialog = true }
                )
            }

            // ================= БАННЕР БОНУСА ЗА СТРИК =================
            if (child.streakBonusMessage.isNotEmpty()) {
                item {
                    StreakBonusBanner(
                        message = child.streakBonusMessage,
                        onDismiss = { viewModel.dismissStreakBonus(child.id) }
                    )
                }
            }

            // ================= БЛОК 2: ЦЕЛЬ-МЕЧТА (КОПИЛКА) =================
            if (child.dreamTarget > 0) {
                item {
                    DreamGoalCard(
                        dreamTitle = child.dreamTitle,
                        dreamTarget = child.dreamTarget,
                        balance = balance,
                        isProcessingAction = isProcessingAction,
                        onClaim = {
                            triggerCoinFlight(Offset.Zero) {
                                viewModel.claimDreamGoal(child.id)
                            }
                        }
                    )
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

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = LocalAppPalette.current.primaryNeon,
                            trackColor = SpaceBgStart
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val canBuyTime = (balance > 0) && (boughtMinutesToday < dailyLimit)

                        Button(
                            onClick = { showBuyTimeSheet = true },
                            enabled = canBuyTime,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalAppPalette.current.primaryNeon,
                                contentColor = SpaceBgStart,
                                disabledContainerColor = GlassBackground,
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
                            color = TextSecondary,
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
                    onCheckIn = { viewModel.checkInStreak(child.id) }
                )
            }

            // ================= БЛОК 5: КНОПКИ ДЕЙСТВИЙ (ПРИЗНАНИЕ И ПРАВИЛА) =================
            item {
                HonestCatEntry(
                    status = honestAdmissionStatus,
                    cooldownDaysLeft = honestCooldownDaysLeft,
                    onOpen = { showHonestCatScreen = true }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        showRulesSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppPalette.current.primaryNeon)
                ) {
                    Text(stringResource(R.string.child_rules_btn), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        showEconomySheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppPalette.current.primaryNeon)
                ) {
                    Text(stringResource(R.string.child_economy_btn), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ================= ВЫХОД / СМЕНА ПОЛЬЗОВАТЕЛЯ =================
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "🚪 " + stringResource(R.string.logout_button),
                        color = PenaltyRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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

    // Диалог вывода наличных
    if (showCashOutDialog) {
        CashOutDialog(
            balance = child.balance,
            pendingTotal = child.cashoutRequests.sumOf { it.amount },
            isProcessing = isProcessingAction,
            onSubmit = { amount, comment ->
                viewModel.createCashoutRequest(amount, comment)
                showCashOutDialog = false
            },
            onDismiss = { showCashOutDialog = false }
        )
    }

    // Диалог выхода / смены пользователя
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = SpaceBgStart,
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
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(logoutDialogCancel, color = TextSecondary)
                }
            }
        )
    }
}
