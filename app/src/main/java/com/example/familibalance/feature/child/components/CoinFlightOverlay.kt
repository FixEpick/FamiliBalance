package com.example.familibalance.feature.child.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.example.familibalance.ui.theme.GoldBright

data class CoinFlightData(
    val startX: Float,
    val startY: Float,
    val onFinished: () -> Unit
)

@Composable
fun CoinFlightOverlay(
    flights: List<CoinFlightData>,
    badgePosition: Offset,
    onFlightFinished: (CoinFlightData) -> Unit
) {
    flights.forEach { flight ->
        key(flight) {
            SingleCoinFlight(
                flight = flight,
                target = badgePosition,
                onFinished = { onFlightFinished(flight) }
            )
        }
    }
}

@Composable
private fun SingleCoinFlight(
    flight: CoinFlightData,
    target: Offset,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val endX = if (target.x != 0f) target.x else 800f
    val endY = if (target.y != 0f) target.y else 100f

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val currentProgress = progress.value
    val currentX = flight.startX + (endX - flight.startX) * currentProgress
    val currentY = flight.startY + (endY - flight.startY) * currentProgress - (150f * kotlin.math.sin(currentProgress * Math.PI)).toFloat()
    val alpha = if (currentProgress > 0.8f) (1f - currentProgress) / 0.2f else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = GoldBright.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = 18f,
                center = Offset(currentX, currentY)
            )
        }
    }
}
