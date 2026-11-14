package com.example.familibalance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.familibalance.ui.theme.LocalAppPalette
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiParticle(
    val startX: Float,      // 0..1 по ширине
    val startY: Float,      // -0.15..0.1 по высоте
    val speed: Float,       // доля высоты экрана за анимацию
    val swayFreq: Float,
    val swayAmp: Float,     // доля ширины экрана
    val phase: Float,
    val delay: Float,       // 0..0.3 — stagger
    val rotation: Float,
    val widthDp: Float,
    val heightDp: Float,
    val color: Color
)

/**
 * Конфетти поверх контента. Не перехватывает касания — чисто визуальный оверлей.
 * Частицы падают сверху вниз с покачиванием и вращением, цвета — из текущей темы.
 */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    val particleColors = remember(palette) {
        listOf(
            palette.primaryNeon,
            palette.secondaryNeon,
            Color(0xFFFFD700),
            Color(0xFFFF4081),
            Color(0xFF69F0AE),
            Color.White
        )
    }
    val particles = remember(particleColors) {
        val rnd = Random(System.nanoTime())
        List(90) {
            ConfettiParticle(
                startX = rnd.nextFloat(),
                startY = -0.15f + rnd.nextFloat() * 0.25f,
                speed = 0.55f + rnd.nextFloat() * 0.5f,
                swayFreq = 2f + rnd.nextFloat() * 4f,
                swayAmp = 0.02f + rnd.nextFloat() * 0.05f,
                phase = rnd.nextFloat() * (2f * Math.PI.toFloat()),
                delay = rnd.nextFloat() * 0.3f,
                rotation = -1f + rnd.nextFloat() * 2f,
                widthDp = 5f + rnd.nextFloat() * 7f,
                heightDp = 8f + rnd.nextFloat() * 10f,
                color = particleColors[rnd.nextInt(particleColors.size)]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 2400, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val localT = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEach
            val x = (p.startX + sin(localT * p.swayFreq * Math.PI.toFloat() + p.phase) * p.swayAmp) * w
            val y = (p.startY + localT * p.speed) * h
            val alpha = if (localT > 0.85f) ((1f - localT) / 0.15f).coerceIn(0f, 1f) else 1f
            rotate(degrees = p.rotation * localT * 360f, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(p.widthDp.dp.toPx(), p.heightDp.dp.toPx())
                )
            }
        }
    }
}
