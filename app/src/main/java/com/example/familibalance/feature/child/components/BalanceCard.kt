package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.CashoutRequest
import com.example.familibalance.data.model.CashoutStatus
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.theme.*

/**
 * Геройская карточка баланса: насыщенный тематический градиент,
 * крупный полупрозрачный арт-символ темы справа и контрастная
 * кнопка «Вывести наличные». onBadgePositioned — для CoinFlightOverlay.
 * onAvatarClick != null — аватар кликабелен (выход из аккаунта ребёнка).
 */
@Composable
fun BalanceCard(
    avatar: String,
    animatedBalance: Int,
    onBadgePositioned: (Offset) -> Unit,
    onAvatarClick: (() -> Unit)? = null,
    onCashOutClick: (() -> Unit)? = null,
    cashoutEnabled: Boolean = true,
    level: Int = 1,
    rankName: String = "",
    xpIntoLevel: Long = 0,
    xpForNext: Long = 1,
    multiplier: Double = 1.0,
    streakDays: Int = 0,
    frameId: String? = null
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contentColor = heroContentColor()
    val heroShape = RoundedCornerShape(24.dp)
    val heroBrush = Brush.linearGradient(
        colors = listOf(theme.accent, theme.backgroundGradientEnd),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = heroShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(heroBrush)
        ) {
            // Крупный тематический арт-символ справа, уходит за край карточки.
            ThemeArtIcon(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(170.dp)
                    .offset(x = 46.dp),
                alpha = 0.5f
            )
            // Мягкий световой блик сверху-слева.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(0f, 0f),
                            radius = 500f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        onBadgePositioned(Offset(pos.x + coordinates.size.width - 80f, pos.y + coordinates.size.height / 2f))
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = contentColor.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable(enabled = onAvatarClick != null) { onAvatarClick?.invoke() }
                        ) {
                            CloudAvatar(avatarId = avatar, size = 44.dp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                stringResource(R.string.child_balance),
                                color = contentColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            AnimatedAmountText(
                                text = "$animatedBalance ${rewardCurrencySymbol()}",
                                color = contentColor,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = contentColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CloudStyleIcon(type = CloudIconType.BANK, size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.child_bank_name),
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Геймификация: уровень, ранг, XP-прогресс и множитель наград.
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = contentColor.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.child_level_badge, level),
                            color = contentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (rankName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rankName,
                            color = contentColor.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (multiplier > 1.0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = StatusYellow.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, StatusYellow.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.child_reward_mult, multiplier),
                                color = StatusYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val xpFraction = if (xpForNext > 0) (xpIntoLevel.toFloat() / xpForNext).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(contentColor.copy(alpha = 0.25f))
                ) {
                    if (xpFraction > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(xpFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(contentColor)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.child_xp_progress, xpIntoLevel, xpForNext),
                    color = contentColor.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                // Расшифровка множителя: «×1.25 (Уровень ×1.14 + Серия ×1.10)».
                if (multiplier > 1.0) {
                    Text(
                        text = stringResource(
                            R.string.mult_breakdown,
                            multiplier,
                            GamificationEngine.levelMultiplier(level),
                            GamificationEngine.streakMultiplier(streakDays)
                        ),
                        color = contentColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onCashOutClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            FeedbackManager.playClick(context, haptic)
                            onCashOutClick()
                        },
                        enabled = cashoutEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(18.dp),
                                spotColor = theme.accent.copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = if (theme.isDark) theme.background else theme.primaryText,
                            disabledContainerColor = Color.White.copy(alpha = 0.6f),
                            disabledContentColor = (if (theme.isDark) theme.background else theme.primaryText).copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.child_cashout_btn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/** Список активных заявок на вывод под балансом. */
@Composable
fun CashoutSection(
    cashoutRequests: List<CashoutRequest>,
    onDismissRejected: (String) -> Unit = {}
) {
    var reasonDialogRequest by remember { mutableStateOf<CashoutRequest?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (cashoutRequests.isEmpty()) {
            Text(
                text = stringResource(R.string.child_no_cashout_requests),
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
                    text = stringResource(R.string.child_cashout_requests_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                cashoutRequests.forEach { request ->
                    val isRejected = request.status == CashoutStatus.REJECTED
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRejected) PenaltyRed.copy(alpha = 0.12f)
                            else CardBgDark.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRejected) PenaltyRed.copy(alpha = 0.5f) else GlassStroke
                        )
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
                                    text = currencyString(R.string.child_cashout_amount, request.amount),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.child_cashout_comment, cashoutCommentDisplay(request.comment)),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (isRejected) {
                                    Text(
                                        text = stringResource(R.string.child_cashout_rejected),
                                        color = PenaltyRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.child_cashout_why),
                                        color = AccentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { reasonDialogRequest = request }
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.child_cashout_waiting),
                                        color = StatusYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Окно с причиной отклонения: «Понятно» убирает плашку с главного экрана.
    reasonDialogRequest?.let { request ->
        AppDialog(
            onDismissRequest = { reasonDialogRequest = null },
            title = {
                Text(
                    stringResource(R.string.child_cashout_rejected),
                    color = PenaltyRed
                )
            },
            text = {
                Text(
                    text = request.rejectReason.orEmpty().ifBlank {
                        stringResource(R.string.admin_reject_default_reason)
                    },
                    color = TextPrimary,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissRejected(request.id)
                        reasonDialogRequest = null
                    }
                ) {
                    Text(stringResource(R.string.child_cashout_reason_ok))
                }
            }
        )
    }
}
