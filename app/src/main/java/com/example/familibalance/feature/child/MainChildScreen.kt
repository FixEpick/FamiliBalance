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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.screens.*
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.components.ConfettiOverlay
import com.example.familibalance.viewmodel.actions.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChildScreen(
    onNavigateToTasks: () -> Unit = {},
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dailyLimit = 90
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val balance = child.balance
    val boughtMinutesToday = child.boughtMinutesToday
    val honestAdmissionStatus = child.honestAdmissionStatus

    // Кулдаун честного признания: 1 раз в 7 дней
    val honestCooldownDaysLeft = remember(child.lastHonestAdmissionDate) {
        val last = runCatching { LocalDate.parse(child.lastHonestAdmissionDate) }.getOrNull()
        if (last == null) 0
        else (7 - ChronoUnit.DAYS.between(last, LocalDate.now()).toInt()).coerceAtLeast(0)
    }

    var showHonestCatScreen by remember { mutableStateOf(false) }
    var catAdmissionText by remember { mutableStateOf("") }

    var showCashOutDialog by remember { mutableStateOf(false) }
    var cashOutAmountText by remember { mutableStateOf("") }
    var cashOutCommentText by remember { mutableStateOf("") }

    var lastRecordedDate by remember { mutableStateOf(getCurrentDateString()) }

    val currentDate = getCurrentDateString()
    if (currentDate != lastRecordedDate) {
        viewModel.resetDailyScreenTime(child.id)
        lastRecordedDate = currentDate
    }

    var showRulesSheet by remember { mutableStateOf(false) }
    var showEconomySheet by remember { mutableStateOf(false) }
    var showBuyTimeSheet by remember { mutableStateOf(false) }

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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassStroke, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                balanceBadgePosition = Offset(pos.x + coordinates.size.width - 80f, pos.y + coordinates.size.height / 2f)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = LocalAppPalette.current.primaryNeon.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(child.avatar, fontSize = 24.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Баланс", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = "$animatedBalance ₽",
                                    color = TextPrimary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = GlassBackground,
                            border = BorderStroke(1.dp, GlassStroke),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🪙", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Космо-банк",
                                    color = LocalAppPalette.current.primaryNeon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // ================= БЛОК ВЫВОДА СРЕДСТВ И АКТИВНЫХ ЗАЯВОК (ПОД БАЛАНСОМ) =================
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = { showCashOutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = LocalAppPalette.current.primaryNeon,
                            contentColor = SpaceBgStart
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Вывести / Купить",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "*работает, когда вы с папой",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeCashoutRequests = child.cashoutRequests
                    if (activeCashoutRequests.isEmpty()) {
                        Text(
                            text = "Нет активных заявок на вывод",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "💵 Активные заявки на вывод:",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            activeCashoutRequests.forEach { request ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardBgDark.copy(alpha = 0.6f)),
                                    border = BorderStroke(1.dp, GlassStroke)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Сумма: ${request.amount} ₽",
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Комментарий: ${request.comment}",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "⏳ Ждёт подтверждения папы",
                                                color = StatusYellow,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ================= БАННЕР БОНУСА ЗА СТРИК =================
            if (child.streakBonusMessage.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LocalAppPalette.current.primaryNeon.copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, LocalAppPalette.current.primaryNeon)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = child.streakBonusMessage,
                                color = LocalAppPalette.current.primaryNeon,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.dismissStreakBonus(child.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    color = LocalAppPalette.current.primaryNeon,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ================= БЛОК 2: ЦЕЛЬ-МЕЧТА (КОПИЛКА) =================
            if (child.dreamTarget > 0) {
                item {
                    val dreamTitle = child.dreamTitle.ifBlank { "Моя цель" }
                    val dreamTarget = child.dreamTarget
                    val progressFraction = (balance.toFloat() / dreamTarget).coerceIn(0f, 1f)
                    val isGoalReached = balance >= dreamTarget

                    val targetProgress = if (isGoalReached) 1f else progressFraction
                    val currentProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "dreamProgress"
                    )

                    val progressBarColor = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = isGoalReached,
                                onClick = {
                                    triggerCoinFlight(Offset.Zero) {
                                        viewModel.claimDreamGoal(child.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalAppPalette.current.primaryNeon.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(
                            width = if (isGoalReached) 3.dp else 1.5.dp,
                            color = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dreamTitle,
                                    color = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "💫",
                                    fontSize = 20.sp
                                )
                            }
                            Text(
                                text = "$balance / $dreamTarget ₽",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LinearProgressIndicator(
                                progress = { currentProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = progressBarColor,
                                trackColor = LocalAppPalette.current.primaryNeon.copy(alpha = 0.15f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            if (isGoalReached) {
                                Text(
                                    text = "Цель достигнута! Нажми, чтобы забрать ✨",
                                    color = StatusYellow,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
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
                            text = "⏳ Экранное время на сегодня",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Куплено сегодня: $boughtMinutesToday / $dailyLimit мин",
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
                            Text("Купить время", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Дневной лимит 90 мин • Обновится завтра",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ================= БЛОК 4: ДНИ БЕЗ ШТРАФОВ (7 ОГОНЬКОВ) =================
            item {
                val activeFlames = if (child.streakDays == 0) 0 else ((child.streakDays - 1) % 7) + 1

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
                            text = "Дни без штрафов",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (day in 1..7) {
                                val isActive = day <= activeFlames
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🔥",
                                        fontSize = 24.sp,
                                        modifier = Modifier.alpha(if (isActive) 1f else 0.25f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$day дн",
                                        fontSize = 11.sp,
                                        color = if (isActive) LocalAppPalette.current.primaryNeon else TextSecondary.copy(alpha = 0.4f),
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (child.lastStreakDate.isBlank() || child.lastStreakDate != java.time.LocalDate.now().toString()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.checkInStreak(child.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = LocalAppPalette.current.primaryNeon, contentColor = SpaceBgStart),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔥 Отметиться сегодня", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Бонус +50 ₽ на 7-й день",
                                color = LocalAppPalette.current.primaryNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Бонус +50 ₽ на 7-й день",
                                color = LocalAppPalette.current.primaryNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ================= БЛОК 5: КНОПКИ ДЕЙСТВИЙ (ПРИЗНАНИЕ И ПРАВИЛА) =================
            item {
                Spacer(modifier = Modifier.height(4.dp))
                when (honestAdmissionStatus) {
                    QuestStatus.AVAILABLE, QuestStatus.APPROVED -> {
                        if (honestCooldownDaysLeft > 0) {
                            OutlinedButton(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, GlassStroke),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    disabledContentColor = TextSecondary,
                                    disabledContainerColor = GlassBackground
                                )
                            ) {
                                Text(
                                    "🐱 Честное признание • Доступно через $honestCooldownDaysLeft дн.",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showHonestCatScreen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppPalette.current.primaryNeon)
                            ) {
                                Text("🐱 Честное признание", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (honestAdmissionStatus == QuestStatus.APPROVED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = StatusGreen.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "✓ Штраф уменьшится вдвое при следующем нарушении",
                                    color = StatusGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                    QuestStatus.PENDING -> {
                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GlassStroke),
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContentColor = StatusYellow,
                                disabledContainerColor = GlassBackground
                            )
                        ) {
                            Text("• Признание на проверке у папы", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    QuestStatus.CLAIMED -> {
                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GlassStroke),
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContentColor = TextSecondary,
                                disabledContainerColor = GlassBackground
                            )
                        ) {
                            Text(
                                "✓ Скидка 50% уже применена",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    QuestStatus.READY_TO_CLAIM -> {
                        OutlinedButton(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StatusGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContentColor = StatusGreen,
                                disabledContainerColor = GlassBackground
                            )
                        ) {
                            Text(
                                "✓ Честное признание одобрено папой (Штраф уменьшен в половину)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
                    Text("📜 Свиток правил", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("💰 Экономика", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

    // Диалог вывода наличных
    if (showCashOutDialog) {
        val pendingTotal = child.cashoutRequests.sumOf { it.amount }
        val availableBalance = child.balance - pendingTotal
        val amountInt = cashOutAmountText.toIntOrNull()
        val isAmountExceeded = amountInt != null && amountInt > availableBalance
        val canSubmit = amountInt != null && amountInt > 0 && amountInt <= availableBalance

        AlertDialog(
            onDismissRequest = { showCashOutDialog = false },
            containerColor = SpaceBgStart,
            title = {
                Text(
                    text = "💵 Вывести наличные",
                    color = LocalAppPalette.current.primaryNeon,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashOutAmountText,
                        onValueChange = { cashOutAmountText = it },
                        label = { Text("Сумма (₽)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = LocalAppPalette.current.primaryNeon,
                            focusedBorderColor = LocalAppPalette.current.primaryNeon,
                            unfocusedBorderColor = GlassStroke,
                            focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
                            unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (isAmountExceeded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Недостаточно средств на балансе",
                            color = PenaltyRed,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashOutCommentText,
                        onValueChange = { cashOutCommentText = it },
                        label = { Text("Комментарий") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = LocalAppPalette.current.primaryNeon,
                            focusedBorderColor = LocalAppPalette.current.primaryNeon,
                            unfocusedBorderColor = GlassStroke,
                            focusedContainerColor = CardBgDark.copy(alpha = 0.8f),
                            unfocusedContainerColor = CardBgDark.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (canSubmit) {
                            viewModel.createCashoutRequest(amountInt, cashOutCommentText)
                            cashOutAmountText = ""
                            cashOutCommentText = ""
                            showCashOutDialog = false
                        }
                    },
                    enabled = canSubmit
                ) {
                    Text("Отправить заявку")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashOutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
