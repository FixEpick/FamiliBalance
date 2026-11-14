package com.example.familibalance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.familibalance.ui.theme.PenaltyRed

/**
 * Яркий пульсирующий красный бейдж-индикатор.
 * В отличие от стандартного Material3 Badge — заметный кружок 12.dp
 * с мягкой пульсацией, привлекающий внимание к вкладке навигации.
 */
@Composable
fun PulseBadge(
    color: Color = PenaltyRed,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulseBadge")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBadgeAlpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBadgeScale"
    )

    Box(
        modifier = modifier
            .size(12.dp)
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}
