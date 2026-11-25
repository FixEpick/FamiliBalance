package com.example.familibalance.feature.child.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.theme.*

/** Карточка цели-мечты (копилка): прогресс-бар и клейм при достижении. */
@Composable
fun DreamGoalCard(
    dreamTitle: String,
    dreamTarget: Int,
    balance: Int,
    isProcessingAction: Boolean,
    cooldownDaysLeft: Int = 0,
    onEdit: () -> Unit = {},
    onClaim: () -> Unit
) {
    val title = if (dreamTitle.isBlank()) {
        stringResource(R.string.child_dream_default_title)
    } else dreamTitle
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
                enabled = isGoalReached && !isProcessingAction,
                onClick = onClaim
            )
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloudBadge(type = CloudIconType.GOAL, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DreamCooldownStar(cooldownDaysLeft = cooldownDaysLeft)
                    // Кнопка редактирования цели — всегда доступна
                    // (кулдаун контролируется на уровне setDreamGoal/UI).
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dream_edit_btn),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$balance / $dreamTarget ${rewardCurrencySymbol()}",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.dream_progress_percent, (progressFraction * 100).toInt()),
                    color = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Прогресс-бар с отметками этапов 25% / 50% / 75%.
            Box(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = progressBarColor,
                    trackColor = LocalAppPalette.current.primaryNeon.copy(alpha = 0.15f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                listOf(0.25f, 0.5f, 0.75f).forEach { milestone ->
                    // Тик на доле ширины: fillMaxWidth(milestone) + CenterEnd
                    // ставит отметку ровно на 25%/50%/75% прогресс-бара.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(milestone)
                            .height(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(2.dp)
                                .height(10.dp)
                                .background(
                                    if (currentProgress >= milestone) progressBarColor
                                    else LocalAppPalette.current.primaryNeon.copy(alpha = 0.35f),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
            if (isGoalReached) {
                Text(
                    text = stringResource(R.string.child_dream_reached),
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

/** Звёздочка с тултипом: показывает, через сколько дней можно сменить цель. */
@Composable
private fun DreamCooldownStar(cooldownDaysLeft: Int) {
    var showHint by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        Box(
            modifier = Modifier.clickable { showHint = !showHint }
        ) {
            EmojiIcon(emoji = "💫", size = 20.dp)
        }
        if (showHint && cooldownDaysLeft > 0) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 26.dp.roundToPx() }),
                onDismissRequest = { showHint = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SpaceBgStart,
                    border = BorderStroke(1.dp, GlassStroke),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = stringResource(R.string.child_dream_cooldown, cooldownDaysLeft),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
