package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.theme.*

/**
 * Компактная карточка стрика: заголовок «Серия без штрафов: X/7 дн»,
 * мини-шкала из 7 огоньков, бейдж бонуса и кнопка чек-ина —
 * всё в 1–2 строки вместо полноразмерного календаря.
 */
@Composable
fun StreakFlameCard(
    streakDays: Int,
    lastStreakDate: String,
    onCheckIn: () -> Unit
) {
    val activeFlames = if (streakDays == 0) 0 else ((streakDays - 1) % 7) + 1
    val checkedToday = lastStreakDate.isNotBlank() &&
        lastStreakDate == java.time.LocalDate.now().toString()

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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        EmojiIcon(
                            emoji = "🔥",
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalAppPalette.current.primaryNeon,
                            contentColor = SpaceBgStart
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
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
}

/** Баннер бонуса за стрик с кнопкой скрытия. */
@Composable
fun StreakBonusBanner(
    message: String,
    onDismiss: () -> Unit
) {
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
                text = message,
                color = LocalAppPalette.current.primaryNeon,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                val closeDesc = stringResource(R.string.a11y_close_button)
                EmojiIcon(
                    emoji = "✕",
                    size = 22.dp,
                    color = LocalAppPalette.current.primaryNeon,
                    fontWeight = FontWeight.Bold,
                    contentDescription = closeDesc
                )
            }
        }
    }
}
