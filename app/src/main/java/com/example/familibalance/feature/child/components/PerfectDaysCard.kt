package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.GamificationEngine
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.theme.*

/**
 * Компактная карточка «Идеальные дни»: серия подряд и текущий
 * бонус к XP за задания (0% / +10% / +20%). При нулевой серии —
 * подсказка, как получить первый идеальный день.
 */
@Composable
fun PerfectDaysCard(perfectDaysStreak: Int) {
    val xpBonus = GamificationEngine.perfectDayXpBonus(perfectDaysStreak)
    val bonusPercent = (xpBonus * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
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
