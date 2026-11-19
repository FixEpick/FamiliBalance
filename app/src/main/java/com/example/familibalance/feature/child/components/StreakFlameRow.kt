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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.theme.*

/** Карточка стрика: 7 огоньков дней без штрафов + кнопка чек-ина. */
@Composable
fun StreakFlameCard(
    streakDays: Int,
    lastStreakDate: String,
    onCheckIn: () -> Unit
) {
    val activeFlames = if (streakDays == 0) 0 else ((streakDays - 1) % 7) + 1

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
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.child_streak_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                        EmojiIcon(
                            emoji = "🔥",
                            size = 24.dp,
                            modifier = Modifier.alpha(if (isActive) 1f else 0.25f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.child_streak_day_label, day),
                            fontSize = 11.sp,
                            color = if (isActive) LocalAppPalette.current.primaryNeon else TextSecondary.copy(alpha = 0.4f),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (lastStreakDate.isBlank() || lastStreakDate != java.time.LocalDate.now().toString()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppPalette.current.primaryNeon, contentColor = SpaceBgStart),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.child_streak_checkin), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.child_streak_bonus),
                    color = LocalAppPalette.current.primaryNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stringResource(R.string.child_streak_checked_today),
                    color = StatusGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.child_streak_bonus),
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
