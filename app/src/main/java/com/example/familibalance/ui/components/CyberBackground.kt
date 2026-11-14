package com.example.familibalance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CyberBgStart = Color(0xFF1E1B4B)
private val CyberBgEnd = Color(0xFF020617)
private val CyberGridColor = Color(0x123B82F6)

/**
 * Тёмный киберпанк-фон для auth-экранов: радиальный градиент
 * (#1E1B4B -> #020617) + тонкая сетка.
 */
@Composable
fun CyberBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberBgStart, CyberBgEnd),
                        radius = 1400f
                    )
                )
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(CyberGridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(CyberGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}
