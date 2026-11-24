package com.example.familibalance.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фон детского раздела: объёмная многослойная композиция —
 * диагональный 3-ступенчатый градиент темы, крупные размытые
 * световые пятна, плавная волна в верхней трети и крупные
 * тематические силуэты на заднем плане.
 */
@Composable
fun ThemedBackground(
    theme: AppThemeConfig = LocalAppTheme.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bgStart by animateColorAsState(theme.background, tween(300), label = "bgStart")
    val bgEnd by animateColorAsState(theme.backgroundGradientEnd, tween(300), label = "bgEnd")
    val accent by animateColorAsState(theme.accent, tween(300), label = "bgAccent")
    val patternColor = accent.copy(alpha = if (theme.isDark) 0.06f else 0.08f)

    // Мягкая средняя точка диагонального градиента.
    val mid = Color(
        red = (bgStart.red + bgEnd.red) / 2f,
        green = (bgStart.green + bgEnd.green) / 2f,
        blue = (bgStart.blue + bgEnd.blue) / 2f,
        alpha = 1f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(bgStart, mid, bgEnd)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientGlows(accent, theme.isDark)
            drawTopWave(accent)
            drawSilhouette(theme.pattern, accent)
            drawPattern(theme.pattern, patternColor)
        }
        content()
    }
}

/** 2–3 крупных размытых световых пятна в верхней и нижней частях экрана. */
private fun DrawScope.drawAmbientGlows(accent: Color, isDark: Boolean) {
    val baseAlpha = if (isDark) 0.20f else 0.16f
    val spots = listOf(
        Triple(Offset(size.width * 0.15f, size.height * 0.08f), size.width * 0.85f, baseAlpha),
        Triple(Offset(size.width * 0.9f, size.height * 0.32f), size.width * 0.7f, baseAlpha * 0.8f),
        Triple(Offset(size.width * 0.5f, size.height * 0.95f), size.width * 0.95f, baseAlpha * 0.9f)
    )
    for ((center, radius, alpha) in spots) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius
            ),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )
    }
}

/** Плавная декоративная волна в верхней трети экрана (за блоком баланса). */
private fun DrawScope.drawTopWave(accent: Color) {
    val w = size.width
    val h = size.height
    val waveY = h * 0.30f
    val path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, waveY - h * 0.015f)
        cubicTo(
            w * 0.82f, waveY - h * 0.055f,
            w * 0.62f, waveY + h * 0.045f,
            w * 0.45f, waveY - h * 0.01f
        )
        cubicTo(
            w * 0.28f, waveY - h * 0.06f,
            w * 0.12f, waveY + h * 0.03f,
            0f, waveY - h * 0.005f
        )
        close()
    }
    drawPath(path, accent.copy(alpha = 0.07f))
    // Тонкая светлая кромка волны.
    val edge = Path().apply {
        moveTo(0f, waveY - h * 0.005f)
        cubicTo(
            w * 0.12f, waveY + h * 0.03f,
            w * 0.28f, waveY - h * 0.06f,
            w * 0.45f, waveY - h * 0.01f
        )
        cubicTo(
            w * 0.62f, waveY + h * 0.045f,
            w * 0.82f, waveY - h * 0.055f,
            w, waveY - h * 0.015f
        )
    }
    drawPath(edge, Color.White.copy(alpha = 0.10f), style = Stroke(width = 2f))
}

/** Крупный тематический силуэт на заднем плане (частично за краем экрана). */
private fun DrawScope.drawSilhouette(pattern: BackgroundPattern, accent: Color) {
    val w = size.width
    val h = size.height
    when (pattern) {
        BackgroundPattern.STARS, BackgroundPattern.PLANETS -> {
            // Большая планета с кольцом в правом нижнем углу.
            val cx = w * 1.02f
            val cy = h * 0.88f
            val r = size.minDimension * 0.34f
            drawCircle(accent.copy(alpha = 0.10f), r, Offset(cx, cy))
            drawOval(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(cx - r * 1.5f, cy - r * 0.42f),
                size = Size(r * 3f, r * 0.84f),
                style = Stroke(width = 3f)
            )
            drawCircle(Color.White.copy(alpha = 0.10f), r * 0.3f, Offset(cx - r * 0.3f, cy - r * 0.35f))
        }
        BackgroundPattern.CLOUDS, BackgroundPattern.PIXELS, BackgroundPattern.CANDY -> {
            // Большое мягкое облако в левом верхнем углу.
            val cx = -w * 0.08f
            val cy = h * 0.16f
            val r = size.minDimension * 0.30f
            val c = accent.copy(alpha = 0.10f)
            drawCircle(c, r, Offset(cx, cy))
            drawCircle(c, r * 0.75f, Offset(cx + r * 0.7f, cy + r * 0.1f))
            drawCircle(c, r * 0.65f, Offset(cx + r * 0.15f, cy - r * 0.55f))
            drawCircle(c, r * 0.55f, Offset(cx - r * 0.6f, cy + r * 0.15f))
        }
        BackgroundPattern.CYBER_GRID -> {
            // Неоновый кристалл в правом верхнем углу.
            val cx = w * 0.92f
            val cy = h * 0.12f
            val r = size.minDimension * 0.22f
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.62f, cy - r * 0.3f)
                lineTo(cx + r * 0.45f, cy + r * 0.9f)
                lineTo(cx - r * 0.45f, cy + r * 0.9f)
                lineTo(cx - r * 0.62f, cy - r * 0.3f)
                close()
            }
            drawPath(path, accent.copy(alpha = 0.08f))
            drawPath(path, accent.copy(alpha = 0.20f), style = Stroke(width = 3f))
            drawLine(
                accent.copy(alpha = 0.14f),
                Offset(cx, cy - r), Offset(cx, cy + r * 0.9f),
                strokeWidth = 2f
            )
        }
        BackgroundPattern.DRAGON_SCALES -> {
            // Крупная спираль свитка в левом нижнем углу.
            val cx = -w * 0.05f
            val cy = h * 0.92f
            val r = size.minDimension * 0.30f
            val path = Path()
            val turns = 2.4f
            val steps = 70
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val angle = t * turns * (Math.PI * 2).toFloat()
                val rad = r * (0.12f + 0.8f * t)
                val x = cx + rad * cos(angle)
                val y = cy + rad * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, accent.copy(alpha = 0.14f), style = Stroke(width = 5f))
            drawCircle(accent.copy(alpha = 0.16f), r * 0.1f, Offset(cx, cy))
        }
        BackgroundPattern.SUN_RAYS -> {
            // Большое солнце в правом верхнем углу.
            val cx = w * 0.95f
            val cy = h * 0.10f
            val r = size.minDimension * 0.20f
            for (i in 0 until 12) {
                val angle = i * (Math.PI * 2 / 12).toFloat()
                drawLine(
                    color = accent.copy(alpha = 0.14f),
                    start = Offset(cx + r * 0.8f * cos(angle), cy + r * 0.8f * sin(angle)),
                    end = Offset(cx + r * 1.25f * cos(angle), cy + r * 1.25f * sin(angle)),
                    strokeWidth = 4f
                )
            }
            drawCircle(accent.copy(alpha = 0.16f), r * 0.7f, Offset(cx, cy))
        }
    }
}

private fun DrawScope.drawPattern(pattern: BackgroundPattern, color: Color) {
    when (pattern) {
        BackgroundPattern.STARS -> drawStars(color)
        BackgroundPattern.CYBER_GRID -> drawCyberGrid(color)
        BackgroundPattern.CLOUDS -> drawClouds(color)
        BackgroundPattern.PIXELS -> drawPixels(color)
        BackgroundPattern.CANDY -> drawCandy(color)
        BackgroundPattern.DRAGON_SCALES -> drawDragonScales(color)
        BackgroundPattern.SUN_RAYS -> drawSunRays(color)
        BackgroundPattern.PLANETS -> drawPlanets(color)
    }
}

/** Детерминированный «звёздный» узор: точки разного размера по сетке. */
private fun DrawScope.drawStars(color: Color) {
    val cols = 14
    val rows = 26
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            // Псевдослучайное смещение внутри ячейки — узор не «рассыпается».
            val seed = (row * 31 + col * 17) % 23
            if (seed % 3 == 0) continue
            val x = (col + 0.5f + sin(seed.toFloat()) * 0.3f) / cols * size.width
            val y = (row + 0.5f + sin(seed.toFloat() * 1.7f) * 0.3f) / rows * size.height
            val r = 1f + (seed % 3) * 0.8f
            drawCircle(color = color, radius = r, center = Offset(x, y))
        }
    }
}

/** Неоновая перспективная сетка: горизонтальные линии + вертикальные с наклоном. */
private fun DrawScope.drawCyberGrid(color: Color) {
    val horizon = size.height * 0.35f
    // Горизонтальные линии, сгущающиеся к горизонту.
    for (i in 0..10) {
        val t = i / 10f
        val y = horizon + (size.height - horizon) * t * t
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
    }
    // Вертикальные лучи из точки схода.
    val vanishingX = size.width * 0.5f
    for (i in -8..8) {
        val bottomX = vanishingX + i * size.width * 0.14f
        drawLine(color, Offset(vanishingX, horizon), Offset(bottomX, size.height), strokeWidth = 1.5f)
    }
}

/** Мягкие «облачка» — группы полупрозрачных кругов. */
private fun DrawScope.drawClouds(color: Color) {
    val blobs = listOf(
        Triple(0.15f, 0.12f, 0.16f), Triple(0.55f, 0.08f, 0.12f),
        Triple(0.85f, 0.2f, 0.14f), Triple(0.3f, 0.45f, 0.18f),
        Triple(0.75f, 0.6f, 0.15f), Triple(0.1f, 0.8f, 0.13f),
        Triple(0.6f, 0.9f, 0.17f)
    )
    for ((fx, fy, fr) in blobs) {
        val cx = fx * size.width
        val cy = fy * size.height
        val r = fr * size.minDimension
        drawCircle(color, r, Offset(cx, cy))
        drawCircle(color, r * 0.7f, Offset(cx + r * 0.5f, cy + r * 0.15f))
        drawCircle(color, r * 0.55f, Offset(cx - r * 0.45f, cy + r * 0.2f))
    }
}

/** Пиксельные блоки — маленькие квадраты в шахматном порядке. */
private fun DrawScope.drawPixels(color: Color) {
    val cell = 28f
    val s = cell * 0.45f
    var row = 0
    var y = cell * 0.5f
    while (y < size.height + cell) {
        var x = cell * 0.5f + if (row % 2 == 0) 0f else cell * 0.5f
        while (x < size.width + cell) {
            val seed = ((x * 7 + y * 13).toInt()) % 5
            if (seed != 0) {
                drawRect(color, Offset(x, y), Size(s, s))
            }
            x += cell
        }
        y += cell
        row++
    }
}

/** Конфетти/бабл-гам: разбросанные кружки и «пилюли» разных размеров. */
private fun DrawScope.drawCandy(color: Color) {
    val count = 40
    for (i in 0 until count) {
        val seed = i * 37 + 11
        val fx = ((seed * 73) % 100) / 100f
        val fy = ((seed * 41) % 100) / 100f
        val x = fx * size.width
        val y = fy * size.height
        val r = 3f + (seed % 5) * 2.2f
        if (i % 3 == 0) {
            drawRoundRect(color, Offset(x, y), Size(r * 2.4f, r * 1.1f), androidx.compose.ui.geometry.CornerRadius(r * 0.55f))
        } else {
            drawCircle(color, r, Offset(x, y))
        }
    }
}

/** Чешуя дракона: аккуратная сетка ромбов (выклёванные «чешуйки»). */
private fun DrawScope.drawDragonScales(color: Color) {
    val cellW = size.width / 10f
    val cellH = size.height / 18f
    for (row in 0 until 19) {
        for (col in 0 until 11) {
            // Шахматный сдвиг — чешуя «нахлёстывается».
            val offsetX = if (row % 2 == 0) 0f else cellW * 0.5f
            val cx = col * cellW + offsetX - cellW * 0.25f
            val cy = row * cellH
            val rx = cellW * 0.42f
            val ry = cellH * 0.55f
            val path = Path().apply {
                moveTo(cx, cy - ry)
                lineTo(cx + rx, cy)
                lineTo(cx, cy + ry)
                lineTo(cx - rx, cy)
                close()
            }
            drawPath(path, color, style = Stroke(width = 1.2f))
        }
    }
}

/** Солнечные лучи: крупные полупрозрачные кольца в углах экрана. */
private fun DrawScope.drawSunRays(color: Color) {
    val corners = listOf(
        Offset(0f, 0f), Offset(size.width, 0f),
        Offset(0f, size.height), Offset(size.width, size.height)
    )
    corners.forEachIndexed { idx, corner ->
        val baseR = size.minDimension * (0.28f + idx * 0.05f)
        for (i in 0..3) {
            drawCircle(
                color = color,
                radius = baseR + i * size.minDimension * 0.09f,
                center = corner,
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/** Планеты: окружности разных размеров с тонкими кольцами-орбитами. */
private fun DrawScope.drawPlanets(color: Color) {
    val planets = listOf(
        Triple(0.18f, 0.22f, 0.055f), Triple(0.78f, 0.3f, 0.04f),
        Triple(0.55f, 0.75f, 0.07f), Triple(0.15f, 0.85f, 0.035f),
        Triple(0.88f, 0.78f, 0.05f)
    )
    for ((fx, fy, fr) in planets) {
        val cx = fx * size.width
        val cy = fy * size.height
        val r = fr * size.minDimension
        // Орбита — тонкое кольцо вокруг планеты.
        drawCircle(color = color, radius = r * 1.9f, center = Offset(cx, cy), style = Stroke(width = 1.2f))
        // Сама планета.
        drawCircle(color = color, radius = r, center = Offset(cx, cy))
        // «Спутник» на орбите.
        val orbitR = r * 1.9f
        val angle = fx * 6.28f + fy * 3.14f
        drawCircle(
            color = color,
            radius = r * 0.28f,
            center = Offset(cx + orbitR * kotlin.math.cos(angle), cy + orbitR * kotlin.math.sin(angle))
        )
    }
}
