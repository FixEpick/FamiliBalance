package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.theme.*
import java.time.LocalDate

/**
 * Объединённая карточка «Серия без штрафов» + «Идеальные дни»:
 * одна стеклянная карточка с тонким разделителем вместо двух
 * отдельных. Логика отметок и расчётов не меняется.
 */
@Composable
fun CombinedStreaksCard(
    streakDays: Int,
    lastStreakDate: String,
    perfectDaysStreak: Int,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFlames = if (streakDays == 0) 0 else ((streakDays - 1) % 7) + 1
    val checkedToday = lastStreakDate.isNotBlank() &&
        lastStreakDate == LocalDate.now().toString()
    val xpBonus = GamificationEngine.perfectDayXpBonus(perfectDaysStreak)
    val bonusPercent = (xpBonus * 100).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column {
            // ===== Верхняя секция: серия без штрафов =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloudBadge(type = CloudIconType.STREAK, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_streak_compact, streakDays),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (day in 1..7) {
                            val isActive = day <= activeFlames
                            CloudStyleIcon(
                                type = CloudIconType.STREAK,
                                size = 18.dp,
                                modifier = Modifier.alpha(if (isActive) 1f else 0.25f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyString(R.string.child_streak_bonus),
                        color = LocalAppPalette.current.primaryNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (checkedToday) {
                        Text(
                            text = stringResource(R.string.child_streak_checked_short),
                            color = StatusGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Button(
                            onClick = onCheckIn,
                            modifier = Modifier
                                .height(32.dp)
                                .accentButtonChrome()
                                .bounceClick(),
                            shape = AccentButtonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = AccentOnColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CloudStyleIcon(type = CloudIconType.STREAK, size = 16.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.child_streak_checkin),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = GlassStroke,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // ===== Нижняя секция: идеальные дни =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameIcon(
                    icon = GameIconType.Star,
                    tint = LocalAppPalette.current.primaryNeon,
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_perfect_days_title),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (perfectDaysStreak > 0) {
                            stringResource(R.string.child_perfect_days_streak, perfectDaysStreak)
                        } else {
                            stringResource(R.string.child_perfect_days_hint)
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (bonusPercent > 0) {
                        stringResource(R.string.child_perfect_days_bonus, bonusPercent)
                    } else {
                        stringResource(R.string.child_perfect_days_reward)
                    },
                    color = LocalAppPalette.current.primaryNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
