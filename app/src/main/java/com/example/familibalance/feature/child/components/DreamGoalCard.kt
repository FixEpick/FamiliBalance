package com.example.familibalance.feature.child.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.components.EmojiIcon
import com.example.familibalance.ui.theme.*

/** Карточка цели-мечты (копилка): прогресс-бар и клейм при достижении. */
@Composable
fun DreamGoalCard(
    dreamTitle: String,
    dreamTarget: Int,
    balance: Int,
    isProcessingAction: Boolean,
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
                    text = title,
                    color = if (isGoalReached) StatusYellow else LocalAppPalette.current.primaryNeon,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                EmojiIcon(emoji = "💫", size = 20.dp)
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
