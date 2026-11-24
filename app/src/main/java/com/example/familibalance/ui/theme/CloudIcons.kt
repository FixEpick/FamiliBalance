package com.example.familibalance.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Мультяшно-иллюстративные иконки «Cloud Style» для детского раздела:
 * цветной объект с чётким контуром на фоне мягкого облака
 * и маленькими белыми облачками у нижнего края.
 *
 * Рисуются через Canvas в координатах 0..100 и масштабируются
 * без размытия — от 18.dp в навбаре до 44.dp в карточках.
 */
enum class CloudIconType {
    HOME, TASKS, ACHIEVEMENTS, HISTORY, GOAL, SCREEN_TIME,
    STREAK, BANK, SHOP_THEMES, SETTINGS, HONEST_CAT
}

private val CloudOutlineLight = Color(0xFF1E293B)
private val CloudOutlineDark = Color(0xFF090D16)
private val CloudBgLight = Color(0xFFBFEAF5)
private val CloudMiniFill = Color(0xFFFFFFFF)

// Палитра объектов
private val C_Turquoise = Color(0xFF26C6DA)
private val C_Coral = Color(0xFFEF5350)
private val C_Amber = Color(0xFFFFB300)
private val C_Emerald = Color(0xFF66BB6A)
private val C_Blue = Color(0xFF42A5F5)
private val C_Pink = Color(0xFFEC407A)
private val C_Purple = Color(0xFFAB47BC)
private val C_Orange = Color(0xFFFFA726)
private val C_Gold = Color(0xFFFFD54F)
private val C_Sky = Color(0xFF81D4FA)
private val C_Paper = Color(0xFFFFF8E1)
private val C_Steel = Color(0xFF90A4AE)
private val C_Cat = Color(0xFFFFCC80)
private val C_CatInner = Color(0xFFFFAB91)

@Composable
fun CloudStyleIcon(
    type: CloudIconType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null
) {
    val theme = LocalAppTheme.current
    val outline = if (theme.isDark) CloudOutlineDark else CloudOutlineLight
    val cloudBg = if (theme.isDark) theme.accent.copy(alpha = 0.22f) else CloudBgLight
    val mod = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier
    Canvas(modifier = mod.size(size)) {
        val unit = this.size.width / 100f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            // Слой 1: облако-подложка
            drawCloudUnit(50f, 60f, 34f, cloudBg)
            // Слой 2: основной объект
            drawCloudObject(type, outline, theme.cardBackground)
            // Слой 3: маленькие белые облачка у нижнего края
            drawMiniCloudUnit(26f, 92f, 9f, outline)
            drawMiniCloudUnit(74f, 94f, 7.5f, outline)
        }
    }
}

/** Пышное облако из пересекающихся кругов и «подушки» снизу (координаты 0..100). */
internal fun DrawScope.drawCloudUnit(cx: Float, cy: Float, r: Float, fill: Color) {
    drawCircle(fill, r, Offset(cx, cy))
    drawCircle(fill, r * 0.72f, Offset(cx - r * 0.85f, cy + r * 0.28f))
    drawCircle(fill, r * 0.72f, Offset(cx + r * 0.85f, cy + r * 0.28f))
    drawRoundRect(
        color = fill,
        topLeft = Offset(cx - r * 1.15f, cy - r * 0.1f),
        size = androidx.compose.ui.geometry.Size(r * 2.3f, r * 1.05f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.55f)
    )
}

internal fun DrawScope.drawMiniCloudUnit(cx: Float, cy: Float, r: Float, outline: Color) {
    val fill = CloudMiniFill
    drawCircle(fill, r, Offset(cx, cy))
    drawCircle(fill, r * 0.7f, Offset(cx - r * 0.8f, cy + r * 0.3f))
    drawCircle(fill, r * 0.7f, Offset(cx + r * 0.8f, cy + r * 0.3f))
    drawRoundRect(
        color = fill,
        topLeft = Offset(cx - r * 1.1f, cy - r * 0.05f),
        size = androidx.compose.ui.geometry.Size(r * 2.2f, r * 0.95f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.5f),
        style = Stroke(width = 1.1f)
    )
    // Тонкий контур облачка
    drawCircle(outline.copy(alpha = 0.35f), r * 1.02f, Offset(cx, cy), style = Stroke(width = 0.8f))
}

private fun DrawScope.drawCloudObject(
    type: CloudIconType,
    outline: Color,
    holeColor: Color
) {
    val sw = 1.8f // толщина контура в единицах 0..100 (~1.8dp на 40dp иконке)
    when (type) {
        CloudIconType.HOME -> drawHome(outline, sw)
        CloudIconType.TASKS -> drawTasks(outline, sw)
        CloudIconType.ACHIEVEMENTS -> drawTrophy(outline, sw)
        CloudIconType.HISTORY -> drawCoinArrow(outline, sw)
        CloudIconType.GOAL -> drawTarget(outline, sw)
        CloudIconType.SCREEN_TIME -> drawHourglass(outline, sw)
        CloudIconType.STREAK -> drawFlame(outline, sw)
        CloudIconType.BANK -> drawCoins(outline, sw)
        CloudIconType.SHOP_THEMES -> drawPalette(outline, sw, holeColor)
        CloudIconType.SETTINGS -> drawGear(outline, sw)
        CloudIconType.HONEST_CAT -> drawCat(outline, sw)
    }
}

private fun DrawScope.drawHome(outline: Color, sw: Float) {
    // Крыша
    val roof = Path().apply {
        moveTo(20f, 54f); lineTo(50f, 26f); lineTo(80f, 54f); close()
    }
    drawPath(roof, C_Coral, style = Stroke(width = sw))
    // Стены
    drawRoundRect(
        C_Amber,
        topLeft = Offset(30f, 54f),
        size = androidx.compose.ui.geometry.Size(40f, 32f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        style = Stroke(width = sw)
    )
    // Дверь и окно
    drawRoundRect(Color(0xFF8D6E63), Offset(45f, 66f), androidx.compose.ui.geometry.Size(10f, 20f), androidx.compose.ui.geometry.CornerRadius(2f), style = Stroke(width = sw * 0.7f))
    drawRoundRect(C_Sky, Offset(34f, 60f), androidx.compose.ui.geometry.Size(8f, 8f), androidx.compose.ui.geometry.CornerRadius(2f), style = Stroke(width = sw * 0.7f))
}

private fun DrawScope.drawTasks(outline: Color, sw: Float) {
    // Свиток/лист
    drawRoundRect(
        C_Paper,
        topLeft = Offset(24f, 22f),
        size = androidx.compose.ui.geometry.Size(52f, 62f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
        style = Stroke(width = sw)
    )
    // Строки
    listOf(34f, 46f, 58f).forEach { y ->
        drawRoundRect(Color(0xFFB0BEC5), Offset(32f, y), androidx.compose.ui.geometry.Size(28f, 4f), androidx.compose.ui.geometry.CornerRadius(2f))
    }
    // Галочка в кружке
    drawCircle(C_Emerald, 11f, Offset(66f, 68f), style = Stroke(width = sw))
    val check = Path().apply {
        moveTo(60f, 68f); lineTo(64.5f, 72.5f); lineTo(72f, 63f)
    }
    drawPath(check, Color.White, style = Stroke(width = sw * 1.1f))
}

private fun DrawScope.drawTrophy(outline: Color, sw: Float) {
    // Кубок
    val cup = Path().apply {
        moveTo(36f, 24f); lineTo(64f, 24f); lineTo(60f, 50f); lineTo(40f, 50f); close()
    }
    drawPath(cup, C_Gold, style = Stroke(width = sw))
    // Ручки
    drawCircle(C_Gold, 8f, Offset(33f, 36f), style = Stroke(width = sw * 0.9f))
    drawCircle(C_Gold, 8f, Offset(67f, 36f), style = Stroke(width = sw * 0.9f))
    // Ножка и основание
    drawRoundRect(C_Gold, Offset(46f, 50f), androidx.compose.ui.geometry.Size(8f, 10f), androidx.compose.ui.geometry.CornerRadius(2f), style = Stroke(width = sw * 0.8f))
    drawRoundRect(C_Amber, Offset(34f, 62f), androidx.compose.ui.geometry.Size(32f, 9f), androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(width = sw))
    // Блик
    drawCircle(Color.White.copy(alpha = 0.7f), 3f, Offset(44f, 34f))
}

private fun DrawScope.drawCoinArrow(outline: Color, sw: Float) {
    // Монетка
    drawCircle(C_Gold, 24f, Offset(46f, 52f), style = Stroke(width = sw))
    drawCircle(C_Amber, 17f, Offset(46f, 52f), style = Stroke(width = sw * 0.7f))
    // Круговая стрелка обновления
    drawArc(
        C_Coral, startAngle = 210f, sweepAngle = 130f, useCenter = false,
        topLeft = Offset(56f, 40f),
        size = androidx.compose.ui.geometry.Size(26f, 26f),
        style = Stroke(width = sw * 1.1f)
    )
    // Наконечник стрелки
    val head = Path().apply {
        moveTo(80f, 40f); lineTo(74f, 44f); lineTo(79f, 49f); close()
    }
    drawPath(head, C_Coral)
}

private fun DrawScope.drawTarget(outline: Color, sw: Float) {
    // Мишень
    drawCircle(Color.White, 25f, Offset(50f, 52f), style = Stroke(width = sw))
    drawCircle(C_Coral, 17f, Offset(50f, 52f), style = Stroke(width = sw * 0.8f))
    drawCircle(C_Gold, 9f, Offset(50f, 52f), style = Stroke(width = sw * 0.8f))
    // Стрела снизу вверх
    drawLine(outline, Offset(50f, 84f), Offset(50f, 44f), strokeWidth = sw * 0.9f)
    val head = Path().apply {
        moveTo(50f, 36f); lineTo(44f, 46f); lineTo(56f, 46f); close()
    }
    drawPath(head, outline)
    drawCircle(C_Emerald, 4f, Offset(50f, 84f), style = Stroke(width = sw * 0.7f))
}

private fun DrawScope.drawHourglass(outline: Color, sw: Float) {
    // Стекло
    val glass = Path().apply {
        moveTo(36f, 32f); lineTo(64f, 32f); lineTo(50f, 54f); close()
        moveTo(36f, 72f); lineTo(64f, 72f); lineTo(50f, 54f); close()
    }
    drawPath(glass, C_Sky, style = Stroke(width = sw))
    // Песок
    val sand = Path().apply {
        moveTo(43f, 68f); lineTo(57f, 68f); lineTo(50f, 58f); close()
    }
    drawPath(sand, C_Amber)
    // Рамки
    drawRoundRect(outline, Offset(32f, 26f), androidx.compose.ui.geometry.Size(36f, 7f), androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(width = sw * 0.9f))
    drawRoundRect(outline, Offset(32f, 71f), androidx.compose.ui.geometry.Size(36f, 7f), androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(width = sw * 0.9f))
}

private fun DrawScope.drawFlame(outline: Color, sw: Float) {
    // Внешнее пламя
    val flame = Path().apply {
        moveTo(50f, 18f)
        cubicTo(66f, 34f, 74f, 46f, 70f, 60f)
        cubicTo(66f, 74f, 56f, 82f, 50f, 82f)
        cubicTo(44f, 82f, 34f, 74f, 30f, 60f)
        cubicTo(26f, 46f, 34f, 34f, 50f, 18f)
        close()
    }
    drawPath(flame, C_Orange, style = Stroke(width = sw))
    // Внутреннее пламя
    val inner = Path().apply {
        moveTo(50f, 40f)
        cubicTo(58f, 48f, 60f, 56f, 56f, 64f)
        cubicTo(52f, 70f, 48f, 70f, 44f, 64f)
        cubicTo(40f, 56f, 42f, 48f, 50f, 40f)
        close()
    }
    drawPath(inner, C_Gold)
    // Блик
    drawCircle(Color.White.copy(alpha = 0.8f), 3.5f, Offset(42f, 52f))
}

private fun DrawScope.drawCoins(outline: Color, sw: Float) {
    // Стопка монет (вид сбоку)
    listOf(78f, 64f, 50f).forEachIndexed { i, y ->
        val w = 44f - i * 4f
        val x = 50f - w / 2f
        drawRoundRect(
            C_Gold,
            topLeft = Offset(x, y),
            size = androidx.compose.ui.geometry.Size(w, 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
            style = Stroke(width = sw)
        )
        drawRoundRect(
            C_Amber,
            topLeft = Offset(x + 3f, y + 2f),
            size = androidx.compose.ui.geometry.Size(w - 6f, 3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f)
        )
    }
    // Одна монета сверху
    drawCircle(C_Gold, 9f, Offset(64f, 40f), style = Stroke(width = sw))
    drawCircle(C_Amber, 5f, Offset(64f, 40f), style = Stroke(width = sw * 0.6f))
}

private fun DrawScope.drawPalette(outline: Color, sw: Float, holeColor: Color) {
    // Палитра художника
    val palette = Path().apply {
        moveTo(50f, 22f)
        cubicTo(72f, 22f, 82f, 36f, 78f, 52f)
        cubicTo(74f, 68f, 62f, 80f, 48f, 80f)
        cubicTo(38f, 80f, 30f, 74f, 28f, 66f)
        cubicTo(26f, 58f, 32f, 52f, 40f, 50f)
        cubicTo(48f, 48f, 52f, 42f, 50f, 34f)
        cubicTo(49f, 28f, 49f, 24f, 50f, 22f)
        close()
    }
    drawPath(palette, Color(0xFFFFCCBC), style = Stroke(width = sw))
    // Отверстие для пальца
    drawCircle(holeColor, 7f, Offset(64f, 34f), style = Stroke(width = sw * 0.8f))
    // Кляксы красок
    drawCircle(C_Coral, 4.5f, Offset(38f, 44f))
    drawCircle(C_Blue, 4.5f, Offset(50f, 60f))
    drawCircle(C_Emerald, 4.5f, Offset(62f, 56f))
    drawCircle(C_Gold, 4f, Offset(42f, 68f))
}

private fun DrawScope.drawGear(outline: Color, sw: Float) {
    // Зубья шестерёнки
    for (i in 0 until 8) {
        withTransform({
            rotate(i * 45f, Offset(50f, 52f))
        }) {
            drawRoundRect(
                C_Steel,
                topLeft = Offset(45.5f, 20f),
                size = androidx.compose.ui.geometry.Size(9f, 13f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                style = Stroke(width = sw * 0.8f)
            )
        }
    }
    // Обод и ступица
    drawCircle(C_Steel, 21f, Offset(50f, 52f), style = Stroke(width = sw))
    drawCircle(Color(0xFFCFD8DC), 13f, Offset(50f, 52f), style = Stroke(width = sw * 0.8f))
    drawCircle(outline, 5f, Offset(50f, 52f))
}

private fun DrawScope.drawCat(outline: Color, sw: Float) {
    // Уши
    val earL = Path().apply {
        moveTo(30f, 44f); lineTo(24f, 18f); lineTo(48f, 32f); close()
    }
    val earR = Path().apply {
        moveTo(70f, 44f); lineTo(76f, 18f); lineTo(52f, 32f); close()
    }
    drawPath(earL, C_Cat, style = Stroke(width = sw))
    drawPath(earR, C_Cat, style = Stroke(width = sw))
    // Голова
    drawCircle(C_Cat, 26f, Offset(50f, 56f), style = Stroke(width = sw))
    // Внутренние уши
    val earInL = Path().apply {
        moveTo(32f, 40f); lineTo(29f, 25f); lineTo(43f, 34f); close()
    }
    val earInR = Path().apply {
        moveTo(68f, 40f); lineTo(71f, 25f); lineTo(57f, 34f); close()
    }
    drawPath(earInL, C_CatInner)
    drawPath(earInR, C_CatInner)
    // Глаза
    drawCircle(outline, 3.2f, Offset(41f, 52f))
    drawCircle(outline, 3.2f, Offset(59f, 52f))
    drawCircle(Color.White, 1.1f, Offset(42f, 51f))
    drawCircle(Color.White, 1.1f, Offset(60f, 51f))
    // Нос и рот
    val nose = Path().apply {
        moveTo(47f, 60f); lineTo(53f, 60f); lineTo(50f, 64f); close()
    }
    drawPath(nose, C_Pink)
    drawLine(outline, Offset(50f, 64f), Offset(50f, 67f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 67f), Offset(45f, 70f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 67f), Offset(55f, 70f), strokeWidth = sw * 0.6f)
    // Усы
    listOf(38f, 44f, 50f).forEach { y ->
        drawLine(outline.copy(alpha = 0.6f), Offset(18f, y + 4f), Offset(32f, y + 2f), strokeWidth = sw * 0.45f)
        drawLine(outline.copy(alpha = 0.6f), Offset(82f, y + 4f), Offset(68f, y + 2f), strokeWidth = sw * 0.45f)
    }
}

/**
 * Капсула-«бейдж» в стиле IconBadge, но с CloudStyleIcon внутри —
 * для карточек и заголовков шторок детского раздела.
 */
@Composable
fun CloudBadge(
    type: CloudIconType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.62f
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        CloudStyleIcon(type = type, size = iconSize)
    }
}
