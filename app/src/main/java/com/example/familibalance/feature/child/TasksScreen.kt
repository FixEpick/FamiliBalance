package com.example.familibalance.feature.child

import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    modifier: Modifier = Modifier,
    onScrollToRules: () -> Unit = {},
    viewModel: MainViewModel
) {
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
    val closetStatus = child.closetStatus
    val roomStatus = child.roomStatus
    val diaryStatus = child.diaryStatus
    val isWardrobeVisible = child.isWardrobeVisible
    val isRoomVisible = child.isRoomVisible
    val isDiaryVisible = child.isDiaryVisible

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var balanceBadgePosition by remember { mutableStateOf(Offset.Zero) }
    var activeCoinFlights by remember { mutableStateOf<List<CoinFlightData>>(emptyList()) }
    var confettiTrigger by remember { mutableIntStateOf(0) }

    // Level-up из ViewModel (клейм наград происходит на этом экране):
    // конфетти + фанфара level_up.
    val levelUpEvent by viewModel.levelUpEvent.collectAsStateWithLifecycle()
    LaunchedEffect(levelUpEvent) {
        if (levelUpEvent != null) {
            confettiTrigger++
            FeedbackManager.playLevelUp(context, haptic)
            viewModel.consumeLevelUpEvent()
        }
    }

    // Бонусный сундук: диалог с наградой + конфетти + звук монетки.
    val bonusChestEvent by viewModel.bonusChestEvent.collectAsStateWithLifecycle()
    var bonusChestReward by remember { mutableStateOf<GamificationEngine.BonusChestReward?>(null) }
    LaunchedEffect(bonusChestEvent) {
        val reward = bonusChestEvent ?: return@LaunchedEffect
        bonusChestReward = reward
        confettiTrigger++
        FeedbackManager.playCoin(context, haptic)
        viewModel.consumeBonusChestEvent()
    }

    LaunchedEffect(confettiTrigger) {
        if (confettiTrigger > 0) {
            delay(2500)
            confettiTrigger = 0
        }
    }

    val triggerCoinFlight: (Offset, () -> Unit) -> Unit = { buttonOffset, onComplete ->
        confettiTrigger++
        FeedbackManager.playCoin(context, haptic)

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

    // rememberSaveable хранит только String-ключ: Bundle с Serializable-
    // объектом мог не восстановиться после process death (камера —
    // отдельная Activity). Цель резолвится из актуального child по ключу —
    // если квест удалён/изменён, экран просто не откроется.
    var activeDeliveryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeDeliveryQuest = rememberQuestDeliveryTarget(child, activeDeliveryKey)

    if (activeDeliveryQuest != null) {
        val target = activeDeliveryQuest
        QuestDeliveryScreen(
            questName = target.displayName,
            rewardText = target.rewardText,
            photoRequired = target.requiresPhoto,
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
                // Конфетти при отправке квеста на проверку — сработает
                // после возврата на список заданий (оверлеи живут там).
                confettiTrigger++
                activeDeliveryKey = null
            },
            onBackClick = {
                activeDeliveryKey = null
            },
            modifier = modifier
        )
        return
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
                        reward = "+${bQuest.reward} ${rewardCurrencySymbol()}",
                        status = bQuest.status,
                        rejectReason = bQuest.rejectReason,
                        borderColor = StatusYellow,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            if (bQuest.requiresPhoto) {
                                activeDeliveryKey = "bonus:${bQuest.id}"
                            } else {
                                triggerCoinFlight(Offset.Zero) {
                                    viewModel.submitBonusQuestByChild(child.id, bQuest.id, null, "")
                                }
                            }
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
                        title = systemTitle(child.wardrobeTitle),
                        description = systemDesc(child.wardrobeDesc),
                        reward = "+${child.wardrobeReward} ${rewardCurrencySymbol()}",
                        frequencyBadge = builtInFreqBadge(child.wardrobeFrequency),
                        status = closetStatus,
                        rejectReason = child.closetRejectReason,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryKey = "closet"
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
                        title = systemTitle(child.roomTitle),
                        description = systemDesc(child.roomDesc),
                        reward = "+${child.roomReward} ${rewardCurrencySymbol()}",
                        frequencyBadge = builtInFreqBadge(child.roomFrequency),
                        status = roomStatus,
                        rejectReason = child.roomRejectReason,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryKey = "room"
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
                    // Штрафные оценки (3 → -20 ₽, 2 → -50 ₽) вычитаются из
                    // diaryApprovedReward — та же формула, что в отчёте
                    // «Итого к начислению», чтобы кнопка совпадала с отчётом.
                    val diaryFines = 20 * child.diaryCount3 + 50 * child.diaryCount2
                    val diaryNetReward = child.diaryApprovedReward - diaryFines
                    val diaryClaimText = if (diaryNetReward >= 0) {
                        currencyString(R.string.tasks_claim_reward, diaryNetReward)
                    } else {
                        currencyString(R.string.tasks_claim_reward_neg, diaryNetReward)
                    }
                    val diaryRewardText = currencyString(R.string.tasks_diary_reward)
                    // Отчёт по оценкам — виден, когда админ утвердил дневник
                    // (READY_TO_CLAIM) и сохранил разбивку в diaryCount*.
                    val diaryReportText = if (
                        diaryStatus == QuestStatus.READY_TO_CLAIM &&
                        child.diaryCount5 + child.diaryCount4 +
                        child.diaryCount3 + child.diaryCount2 > 0
                    ) {
                        buildString {
                            appendLine(stringResource(R.string.tasks_diary_report_header))
                            appendLine(
                                if (child.diaryCount5 == 0) currencyString(R.string.tasks_diary_grade_zero, 5)
                                else currencyString(R.string.tasks_diary_grade5, child.diaryCount5, child.baseDiaryFiveReward * child.diaryCount5)
                            )
                            appendLine(
                                if (child.diaryCount4 == 0) currencyString(R.string.tasks_diary_grade_zero, 4)
                                else currencyString(R.string.tasks_diary_grade4, child.diaryCount4, 30 * child.diaryCount4)
                            )
                            appendLine(
                                if (child.diaryCount3 == 0) currencyString(R.string.tasks_diary_grade_zero, 3)
                                else currencyString(R.string.tasks_diary_grade3, child.diaryCount3, 20 * child.diaryCount3)
                            )
                            appendLine(
                                if (child.diaryCount2 == 0) currencyString(R.string.tasks_diary_grade_zero, 2)
                                else currencyString(R.string.tasks_diary_grade2, child.diaryCount2, 50 * child.diaryCount2)
                            )
                            appendLine(
                                if (diaryNetReward > 0) currencyString(R.string.tasks_diary_total, diaryNetReward)
                                else currencyString(R.string.tasks_diary_total_plain, diaryNetReward)
                            )
                        }.trimEnd()
                    } else null
                    TaskCard(
                        title = systemTitle(child.diaryTitle),
                        description = systemDesc(child.diaryDesc),
                        reward = diaryRewardText,
                        frequencyBadge = builtInFreqBadge(child.diaryFrequency),
                        status = diaryStatus,
                        rejectReason = child.diaryRejectReason,
                        claimButtonText = diaryClaimText,
                        reportText = diaryReportText,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            activeDeliveryKey = "diary"
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
                items(visibleCustomQuests, key = { it.id }, contentType = { "custom_quest" }) { quest ->
                    val badgeText = when (quest.frequency) {
                        QuestFrequency.ONCE -> stringResource(R.string.freq_once)
                        QuestFrequency.DAILY -> stringResource(R.string.freq_daily)
                        QuestFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
                    }
                    val claimText = if (quest.status == QuestStatus.READY_TO_CLAIM) {
                        currencyString(R.string.tasks_claim_reward, quest.reward)
                    } else {
                        stringResource(R.string.tasks_under_review)
                    }
                    TaskCard(
                        title = quest.title,
                        description = quest.description.ifBlank { stringResource(R.string.tasks_custom_default_desc) },
                        reward = "+${quest.reward} ${rewardCurrencySymbol()}",
                        frequencyBadge = badgeText,
                        status = quest.status,
                        rejectReason = quest.rejectReason,
                        claimButtonText = claimText,
                        actionEnabled = !isProcessingAction,
                        onExecute = {
                            if (quest.requiresPhoto) {
                                activeDeliveryKey = "custom:${quest.id}"
                            } else {
                                triggerCoinFlight(Offset.Zero) {
                                    viewModel.submitCustomQuestPhoto(child.id, quest.id, "", "")
                                }
                            }
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).glassCard(),
                        shape = GlassCardShape,
                        colors = CardDefaults.cardColors(containerColor = GlassBackground)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmojiIcon(emoji = "📭", size = 48.dp)
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
        if (confettiTrigger > 0) {
            ConfettiOverlay(trigger = confettiTrigger, modifier = Modifier.fillMaxSize())
        }
        }
    }

    // Диалог бонусного сундука — поверх всего экрана заданий.
    bonusChestReward?.let { reward ->
        BonusChestDialog(
            reward = reward,
            onDismiss = { bonusChestReward = null }
        )
    }
}

/** Цель доставки фото квеста: отображаемое имя, награда и стабильный ключ для идентификации. */
internal data class QuestDeliveryTarget(
    val displayName: String,
    val rewardText: String,
    val key: String,
    /** true — фото обязательно; false — квест отправляется на проверку без фото. */
    val requiresPhoto: Boolean = true
)

/**
 * Резолвит ключ квеста ("closet"/"room"/"diary"/"bonus:<id>"/"custom:<id>")
 * в [QuestDeliveryTarget] по актуальному состоянию ребёнка.
 * Используется TasksScreen и HistoryScreen: в rememberSaveable хранится
 * только String-ключ — он гарантированно переживает process death,
 * в отличие от Serializable-объекта в Bundle.
 */
@Composable
internal fun rememberQuestDeliveryTarget(
    child: ChildProfileState,
    key: String?
): QuestDeliveryTarget? {
    if (key == null) return null
    return when {
        key == "closet" -> QuestDeliveryTarget(systemTitle(child.wardrobeTitle), "+${child.wardrobeReward} ${rewardCurrencySymbol()}", key, child.wardrobeRequiresPhoto)
        key == "room" -> QuestDeliveryTarget(systemTitle(child.roomTitle), "+${child.roomReward} ${rewardCurrencySymbol()}", key, child.roomRequiresPhoto)
        key == "diary" -> QuestDeliveryTarget(systemTitle(child.diaryTitle), currencyString(R.string.tasks_diary_reward), key, child.diaryRequiresPhoto)
        key.startsWith("bonus:") -> child.bonusQuests
            .firstOrNull { "bonus:${it.id}" == key }
            ?.let { QuestDeliveryTarget(it.title, "+${it.reward} ${rewardCurrencySymbol()}", key, it.requiresPhoto) }
        key.startsWith("custom:") -> child.customQuests
            .firstOrNull { "custom:${it.id}" == key }
            ?.let { QuestDeliveryTarget(it.title, "+${it.reward} ${rewardCurrencySymbol()}", key, it.requiresPhoto) }
        else -> null
    }
}

