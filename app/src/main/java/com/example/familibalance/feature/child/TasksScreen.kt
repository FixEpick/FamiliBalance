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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.screens.*
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*

@Composable
private fun builtInFreqBadge(frequency: QuestFrequency): String = when (frequency) {
    QuestFrequency.DAILY -> stringResource(R.string.freq_daily)
    QuestFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
    QuestFrequency.ONCE -> stringResource(R.string.freq_once)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onScrollToRules: () -> Unit = {},
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val isProcessingAction by viewModel.isProcessingAction.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val closetStatus = child.closetStatus
    val roomStatus = child.roomStatus
    val diaryStatus = child.diaryStatus
    val isWardrobeVisible = child.isWardrobeVisible
    val isRoomVisible = child.isRoomVisible
    val isDiaryVisible = child.isDiaryVisible

    var activeDeliveryQuest by remember { mutableStateOf<QuestDeliveryTarget?>(null) }

    if (activeDeliveryQuest != null) {
        val target = activeDeliveryQuest!!
        QuestDeliveryScreen(
            questName = target.displayName,
            rewardText = target.rewardText,
            onSendClick = { comment, photoUri ->
                val photoUrlStr = photoUri?.toString()
                when (target.key) {
                    "closet", "room", "diary" -> {
                        viewModel.submitQuestForReview(child.id, target.key, photoUrlStr, comment)
                    }
                    else -> {
                        when {
                            target.key.startsWith("bonus:") -> {
                                viewModel.submitBonusQuestByChild(child.id, target.key.removePrefix("bonus:"), photoUri, comment)
                            }
                            target.key.startsWith("custom:") -> {
                                viewModel.submitCustomQuestPhoto(child.id, target.key.removePrefix("custom:"), photoUrlStr.orEmpty(), comment)
                            }
                        }
                    }
                }
                activeDeliveryQuest = null
            },
            onBackClick = {
                activeDeliveryQuest = null
            },
            modifier = modifier
        )
        return
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

    Column(modifier = modifier.fillMaxSize()) {
        // Закреплённый мини-баланс — не скроллится
        MiniBalanceBar(balance = child.balance)

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tasks_title),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val visibleBonusQuests = child.bonusQuests.filter { it.status != QuestStatus.CLAIMED }
            if (visibleBonusQuests.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tasks_bonus_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(visibleBonusQuests, key = { it.id }, contentType = { "bonus_quest" }) { bQuest ->
                    TaskCard(
                        title = bQuest.title,
                        description = bQuest.description.ifBlank { stringResource(R.string.tasks_bonus_default_desc) },
                        reward = "+${bQuest.reward} ₽",
                        status = bQuest.status,
                        rejectReason = bQuest.rejectReason,
                        borderColor = StatusYellow,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryQuest = QuestDeliveryTarget(bQuest.title, "+${bQuest.reward} ₽", "bonus:${bQuest.id}")
                        },
                        onClaim = { buttonOffset ->
                            triggerCoinFlight(buttonOffset) {
                                viewModel.claimBonusQuestReward(child.id, bQuest.id)
                            }
                        }
                    )
                }
            }

            // Список основных квестов (с учётом видимости)
            if (isWardrobeVisible && !child.isWardrobeDeleted) {
                item {
                    TaskCard(
                        title = child.wardrobeTitle,
                        description = child.wardrobeDesc,
                        reward = "+${child.wardrobeReward} ₽",
                        frequencyBadge = builtInFreqBadge(child.wardrobeFrequency),
                        status = closetStatus,
                        rejectReason = child.closetRejectReason,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryQuest = QuestDeliveryTarget(child.wardrobeTitle, "+${child.wardrobeReward} ₽", "closet")
                        },
                        onClaim = { buttonOffset ->
                            triggerCoinFlight(buttonOffset) {
                                viewModel.claimQuestReward(child.id, "closet")
                            }
                        }
                    )
                }
            }
            if (isRoomVisible && !child.isRoomDeleted) {
                item {
                    TaskCard(
                        title = child.roomTitle,
                        description = child.roomDesc,
                        reward = "+${child.roomReward} ₽",
                        frequencyBadge = builtInFreqBadge(child.roomFrequency),
                        status = roomStatus,
                        rejectReason = child.roomRejectReason,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryQuest = QuestDeliveryTarget(child.roomTitle, "+${child.roomReward} ₽", "room")
                        },
                        onClaim = { buttonOffset ->
                            triggerCoinFlight(buttonOffset) {
                                viewModel.claimQuestReward(child.id, "room")
                            }
                        }
                    )
                }
            }
            if (isDiaryVisible && !child.isDiaryDeleted) {
                item {
                    val diaryClaimText = if (child.diaryApprovedReward >= 0) {
                        stringResource(R.string.tasks_claim_reward, child.diaryApprovedReward)
                    } else {
                        stringResource(R.string.tasks_claim_reward_neg, child.diaryApprovedReward)
                    }
                    val diaryRewardText = stringResource(R.string.tasks_diary_reward)
                    TaskCard(
                        title = child.diaryTitle,
                        description = child.diaryDesc,
                        reward = diaryRewardText,
                        frequencyBadge = builtInFreqBadge(child.diaryFrequency),
                        status = diaryStatus,
                        rejectReason = child.diaryRejectReason,
                        claimButtonText = diaryClaimText,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryQuest = QuestDeliveryTarget(child.diaryTitle, diaryRewardText, "diary")
                        },
                        onClaim = { buttonOffset ->
                            triggerCoinFlight(buttonOffset) {
                                viewModel.claimQuestReward(child.id, "diary")
                            }
                        }
                    )
                }
            }

            // Кастомные квесты (только видимые)
            val visibleCustomQuests = child.customQuests.filter { it.isVisible }
            if (visibleCustomQuests.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tasks_custom_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(visibleCustomQuests) { quest ->
                    val badgeText = when (quest.frequency) {
                        QuestFrequency.ONCE -> stringResource(R.string.freq_once)
                        QuestFrequency.DAILY -> stringResource(R.string.freq_daily)
                        QuestFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
                    }
                    val claimText = if (quest.status == QuestStatus.READY_TO_CLAIM) {
                        stringResource(R.string.tasks_claim_reward, quest.reward)
                    } else {
                        stringResource(R.string.tasks_under_review)
                    }
                    TaskCard(
                        title = quest.title,
                        description = quest.description.ifBlank { stringResource(R.string.tasks_custom_default_desc) },
                        reward = "+${quest.reward} ₽",
                        frequencyBadge = badgeText,
                        status = quest.status,
                        rejectReason = quest.rejectReason,
                        claimButtonText = claimText,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryQuest = QuestDeliveryTarget(quest.title, "+${quest.reward} ₽", "custom:${quest.id}")
                        },
                        onClaim = { buttonOffset ->
                            triggerCoinFlight(buttonOffset) {
                                viewModel.claimCustomQuestReward(child.id, quest.id)
                            }
                        }
                    )
                }
            }

            // Empty state
            val hasAnyVisibleQuest = (isWardrobeVisible && !child.isWardrobeDeleted) || (isRoomVisible && !child.isRoomDeleted) || (isDiaryVisible && !child.isDiaryDeleted) || visibleCustomQuests.isNotEmpty()
            if (!hasAnyVisibleQuest) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassBackground),
                        border = BorderStroke(1.dp, GlassStroke)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "📭", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.tasks_empty_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.tasks_empty_subtitle),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
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
}

/** Цель доставки фото квеста: отображаемое имя, награда и стабильный ключ для идентификации. */
internal data class QuestDeliveryTarget(
    val displayName: String,
    val rewardText: String,
    val key: String
)
