package com.example.familibalance.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.components.EmojiIcon
import kotlin.math.cos
import kotlin.math.sin

/** Единая форма «стеклянных» карточек детского раздела. */
val GlassCardShape = RoundedCornerShape(24.dp)

/** Форма акцентных кнопок. */
val AccentButtonShape = RoundedCornerShape(18.dp)

/**
 * Модификатор «мягкого стекла» для карточек: в светлых темах —
 * рассеянная цветная тень, у всех тем — тонкий градиентный блик
 * (стеклянная грань) вместо однотонной обводки.
 */
@Composable
fun Modifier.glassCard(): Modifier {
    val theme = LocalAppTheme.current
    val sheen = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (theme.isDark) 0.16f else 0.6f),
            Color.Transparent
        )
    )
    return this
        .then(
            if (!theme.isDark) {
                Modifier.shadow(
                    elevation = 8.dp,
                    shape = GlassCardShape,
                    spotColor = theme.accent.copy(alpha = 0.15f),
                    ambientColor = theme.accent.copy(alpha = 0.08f)
                )
            } else {
                Modifier
            }
        )
        .border(1.dp, sheen, GlassCardShape)
}

/** Плавный двухцветный градиент акцентных кнопок на базе theme.accent. */
@Composable
fun accentBrush(): Brush {
    val accent = LocalAppTheme.current.accent
    return Brush.linearGradient(
        colors = listOf(accent, accent.copy(alpha = 0.72f))
    )
}

/**
 * Оформление акцентной кнопки: мягкая цветная тень + градиентный фон.
 * Использовать вместе с Button(containerColor = Color.Transparent).
 */
@Composable
fun Modifier.accentButtonChrome(): Modifier {
    val theme = LocalAppTheme.current
    return this
        .shadow(
            elevation = 6.dp,
            shape = AccentButtonShape,
            spotColor = theme.accent.copy(alpha = 0.45f),
            ambientColor = theme.accent.copy(alpha = 0.22f)
        )
        .background(accentBrush(), AccentButtonShape)
}

/** Контрастный текст на акцентном градиенте (для hero-карточек). */
@Composable
fun heroContentColor(): Color {
    val theme = LocalAppTheme.current
    return if (theme.accent.luminance() > 0.42f) Color(0xFF101828) else Color.White
}

/**
 * Пружинящее нажатие: плавно сжимает элемент до [scaleDown] при касании
 * и возвращает обратно. Наблюдает за нажатием, не перехватывая клик —
 * безопасно вешать на Button/Card с собственным clickable.
 */
@Composable
fun Modifier.bounceClick(scaleDown: Float = 0.96f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceClickScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(interactionSource) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                try {
                    waitForUpOrCancellation()
                } finally {
                    interactionSource.tryEmit(PressInteraction.Release(press))
                }
            }
        }
}

/**
 * Сумма баланса с анимированным счётчиком: при изменении текста
 * цифры плавно сдвигаются по вертикали с fade.
 */
@Composable
fun AnimatedAmountText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(300)) { it / 2 } + fadeIn(tween(300))) togetherWith
                (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(300)))
        },
        label = "animatedAmount",
        modifier = modifier
    ) { value ->
        Text(
            text = value,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap
        )
    }
}

/** Мягкая цветная «капсула» для иконок внутри карточек (40dp, радиус 14dp). */
@Composable
fun IconBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    emojiSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        EmojiIcon(emoji = emoji, size = emojiSize)
    }
}

/**
 * Крупный полупрозрачный векторный арт-символ текущей темы
 * (планета с кольцом, солнце, кристалл, облако, спираль дракона).
 * Используется на hero-карточке баланса и может уходить за край.
 */
@Composable
fun ThemeArtIcon(
    modifier: Modifier = Modifier,
    alpha: Float = 0.35f
) {
    val theme = LocalAppTheme.current
    val accent = theme.accent
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val r = minOf(w, h) * 0.5f
        when (theme.pattern) {
            BackgroundPattern.STARS,
            BackgroundPattern.PLANETS -> drawPlanetArt(accent, cx, cy, r, alpha)
            BackgroundPattern.SUN_RAYS -> drawSunArt(accent, cx, cy, r, alpha)
            BackgroundPattern.CLOUDS,
            BackgroundPattern.PIXELS,
            BackgroundPattern.CANDY -> drawCloudArt(accent, cx, cy, r, alpha)
            BackgroundPattern.CYBER_GRID -> drawBoltArt(accent, cx, cy, r, alpha)
            BackgroundPattern.DRAGON_SCALES -> drawCrownArt(accent, cx, cy, r, alpha)
        }
    }
}

private fun DrawScope.drawPlanetArt(accent: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val body = accent.copy(alpha = 0.30f * alpha * 3f)
    val ring = accent.copy(alpha = 0.55f * alpha * 3f)
    // Орбита — сплюснутый эллипс.
    drawOval(
        color = ring,
        topLeft = Offset(cx - r * 1.25f, cy - r * 0.38f),
        size = Size(r * 2.5f, r * 0.76f),
        style = Stroke(width = r * 0.07f)
    )
    // Планета.
    drawCircle(color = body, radius = r * 0.62f, center = Offset(cx, cy))
    // Блик на планете.
    drawCircle(
        color = Color.White.copy(alpha = 0.35f * alpha * 3f),
        radius = r * 0.2f,
        center = Offset(cx - r * 0.2f, cy - r * 0.22f)
    )
    // Спутник на орбите.
    val orbitRx = r * 1.25f
    val orbitRy = r * 0.38f
    val angle = 0.6f
    drawCircle(
        color = ring,
        radius = r * 0.11f,
        center = Offset(
            cx + orbitRx * cos(angle),
            cy + orbitRy * sin(angle)
        )
    )
}

private fun DrawScope.drawSunArt(accent: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val core = accent.copy(alpha = 0.38f * alpha * 3f)
    val ray = accent.copy(alpha = 0.5f * alpha * 3f)
    // Лучи.
    for (i in 0 until 12) {
        val angle = i * (Math.PI * 2 / 12).toFloat()
        val inner = r * 0.72f
        val outer = r * 1.05f
        drawLine(
            color = ray,
            start = Offset(cx + inner * cos(angle), cy + inner * sin(angle)),
            end = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
            strokeWidth = r * 0.06f
        )
    }
    drawCircle(color = core, radius = r * 0.6f, center = Offset(cx, cy))
    drawCircle(
        color = Color.White.copy(alpha = 0.4f * alpha * 3f),
        radius = r * 0.22f,
        center = Offset(cx - r * 0.14f, cy - r * 0.16f)
    )
}

private fun DrawScope.drawCloudArt(accent: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val c = accent.copy(alpha = 0.30f * alpha * 3f)
    drawCircle(color = c, radius = r * 0.5f, center = Offset(cx, cy))
    drawCircle(color = c, radius = r * 0.38f, center = Offset(cx + r * 0.42f, cy + r * 0.08f))
    drawCircle(color = c, radius = r * 0.34f, center = Offset(cx - r * 0.4f, cy + r * 0.12f))
    drawCircle(color = c, radius = r * 0.3f, center = Offset(cx + r * 0.1f, cy - r * 0.34f))
}

/** Стилизованная неоновая молния для кибер-темы. */
private fun DrawScope.drawBoltArt(accent: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val fill = accent.copy(alpha = 0.30f * alpha * 3f)
    val edge = accent.copy(alpha = 0.65f * alpha * 3f)
    val path = Path().apply {
        moveTo(cx + r * 0.18f, cy - r * 0.85f)
        lineTo(cx - r * 0.48f, cy + r * 0.12f)
        lineTo(cx - r * 0.04f, cy + r * 0.12f)
        lineTo(cx - r * 0.18f, cy + r * 0.85f)
        lineTo(cx + r * 0.48f, cy - r * 0.18f)
        lineTo(cx + r * 0.04f, cy - r * 0.18f)
        close()
    }
    drawPath(path, color = fill)
    drawPath(path, color = edge, style = Stroke(width = r * 0.05f))
    // Неоновое свечение вдоль оси молнии.
    drawLine(
        color = edge,
        start = Offset(cx - r * 0.05f, cy - r * 0.55f),
        end = Offset(cx - r * 0.12f, cy + r * 0.45f),
        strokeWidth = r * 0.05f
    )
}

/** Стилизованная корона из плавных линий для темы дракона. */
private fun DrawScope.drawCrownArt(accent: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val fill = accent.copy(alpha = 0.28f * alpha * 3f)
    val edge = accent.copy(alpha = 0.6f * alpha * 3f)
    // Обод короны.
    val band = Path().apply {
        moveTo(cx - r * 0.75f, cy + r * 0.15f)
        lineTo(cx + r * 0.75f, cy + r * 0.15f)
        lineTo(cx + r * 0.75f, cy + r * 0.55f)
        lineTo(cx - r * 0.75f, cy + r * 0.55f)
        close()
    }
    drawPath(band, color = fill)
    drawPath(band, color = edge, style = Stroke(width = r * 0.045f))
    // Три зубца.
    val crown = Path().apply {
        moveTo(cx - r * 0.75f, cy + r * 0.15f)
        lineTo(cx - r * 0.75f, cy - r * 0.35f)
        lineTo(cx - r * 0.38f, cy + r * 0.05f)
        lineTo(cx, cy - r * 0.6f)
        lineTo(cx + r * 0.38f, cy + r * 0.05f)
        lineTo(cx + r * 0.75f, cy - r * 0.35f)
        lineTo(cx + r * 0.75f, cy + r * 0.15f)
        close()
    }
    drawPath(crown, color = fill)
    drawPath(crown, color = edge, style = Stroke(width = r * 0.05f))
    // «Жемчужины» на зубцах.
    val pearls = listOf(
        Offset(cx - r * 0.75f, cy - r * 0.35f),
        Offset(cx, cy - r * 0.6f),
        Offset(cx + r * 0.75f, cy - r * 0.35f)
    )
    for (p in pearls) {
        drawCircle(color = edge, radius = r * 0.07f, center = p)
    }
}
