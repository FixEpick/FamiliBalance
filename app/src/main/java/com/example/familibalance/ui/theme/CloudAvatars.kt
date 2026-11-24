package com.example.familibalance.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.example.familibalance.R

/**
 * Мультяшные аватары персонажей в стиле «Cloud Style»: крупная
 * голова с чётким тёмным контуром на фоне мягкого облака и
 * маленькими белыми облачками снизу.
 *
 * В профиль сохраняется [CloudAvatarType.name] ("CAT", "PANDA", ...).
 * Старые строковые ID/эмодзи маппятся через [cloudAvatarTypeOf].
 */
enum class CloudAvatarType(
    @StringRes val titleRes: Int,
    val unlockRule: ThemeUnlockRule = ThemeUnlockRule.Free
) {
    CAT(R.string.avatar_cat),
    PANDA(R.string.avatar_panda),
    ALIEN(R.string.avatar_alien),
    UNICORN(R.string.avatar_unicorn),
    ROBOT(R.string.avatar_robot, ThemeUnlockRule.Paid(price = 100)),
    NINJA(R.string.avatar_ninja, ThemeUnlockRule.Paid(price = 150)),
    ASTRONAUT(
        R.string.avatar_astronaut,
        ThemeUnlockRule.Achievement(
            achievementTitle = "Победитель Монстров",
            titleRes = R.string.sys_ach_wardrobe_5_title
        )
    ),
    DRAGON(
        R.string.avatar_dragon,
        ThemeUnlockRule.Achievement(
            achievementTitle = "Босс Первой Парты",
            titleRes = R.string.sys_ach_diary_10_title
        )
    )
}

/** ID аватаров, доступных бесплатно по умолчанию. */
val DefaultUnlockedAvatarIds: List<String> =
    CloudAvatarType.entries.filter { it.unlockRule is ThemeUnlockRule.Free }.map { it.name }

/**
 * Доступен ли аватар профилю: бесплатный, уже купленный
 * (name в unlockedAvatars) или привязанное достижение заявлено.
 */
fun CloudAvatarType.isAvatarUnlocked(
    unlockedAvatars: List<String>,
    claimedAchievementTitles: Set<String>
): Boolean {
    if (unlockRule is ThemeUnlockRule.Free) return true
    if (name in unlockedAvatars) return true
    val ach = unlockRule as? ThemeUnlockRule.Achievement ?: return false
    return ach.achievementTitle in claimedAchievementTitles
}

/** ID аватаров, открывающихся заявленными (isClaimed) достижениями. */
fun achievementAvatarIds(claimedAchievementTitles: Set<String>): Set<String> =
    CloudAvatarType.entries.mapNotNull { type ->
        val rule = type.unlockRule as? ThemeUnlockRule.Achievement ?: return@mapNotNull null
        if (rule.achievementTitle in claimedAchievementTitles) type.name else null
    }.toSet()

/** Маппинг сохранённого avatar-строки (enum name, эмодзи, старые ID) на персонажа. */
fun cloudAvatarTypeOf(avatarId: String?): CloudAvatarType {
    val id = avatarId?.trim().orEmpty()
    if (id.isEmpty()) return CloudAvatarType.CAT
    // Точное совпадение с именем enum (новый формат).
    CloudAvatarType.entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
        ?.let { return it }
    val lower = id.lowercase()
    return when {
        lower.contains("panda") || lower.contains("панд") || id == "🐼" -> CloudAvatarType.PANDA
        lower.contains("alien") || lower.contains("пришелец") ||
            lower.contains("иноплан") || id == "👽" || id == "🛸" -> CloudAvatarType.ALIEN
        lower.contains("unicorn") || lower.contains("единорог") || id == "🦄" -> CloudAvatarType.UNICORN
        lower.contains("astro") || lower.contains("космо") ||
            id == "🚀" || id.contains("🧑‍🚀") -> CloudAvatarType.ASTRONAUT
        lower.contains("robot") || lower.contains("робот") || id == "🤖" -> CloudAvatarType.ROBOT
        lower.contains("dragon") || lower.contains("дракон") || id == "🐉" -> CloudAvatarType.DRAGON
        lower.contains("ninja") || lower.contains("ниндзя") || id == "🥷" -> CloudAvatarType.NINJA
        lower.contains("cat") || lower.contains("кот") ||
            id == "🐱" || id == "🐈" || id == "🧒" -> CloudAvatarType.CAT
        else -> CloudAvatarType.CAT
    }
}

private val AvOutlineLight = Color(0xFF1E293B)
private val AvOutlineDark = Color(0xFF090D16)
private val AvBgLight = Color(0xFFBFEAF5)

@Composable
fun CloudAvatar(
    avatarId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null
) {
    val theme = LocalAppTheme.current
    val type = cloudAvatarTypeOf(avatarId)
    val outline = if (theme.isDark) AvOutlineDark else AvOutlineLight
    val cloudBg = if (theme.isDark) theme.accent.copy(alpha = 0.22f) else AvBgLight
    val mod = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier
    Canvas(modifier = mod.size(size)) {
        val unit = this.size.width / 100f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            // Слой 1: облако-подложка
            drawCloudUnit(50f, 58f, 36f, cloudBg)
            // Слой 2: персонаж
            drawAvatarCharacter(type, outline)
            // Слой 3: маленькие белые облачка снизу
            drawMiniCloudUnit(24f, 92f, 9f, outline)
            drawMiniCloudUnit(76f, 94f, 7.5f, outline)
        }
    }
}

private fun DrawScope.drawAvatarCharacter(type: CloudAvatarType, outline: Color) {
    val sw = 1.8f
    when (type) {
        CloudAvatarType.CAT -> drawCatAvatar(outline, sw)
        CloudAvatarType.PANDA -> drawPandaAvatar(outline, sw)
        CloudAvatarType.ALIEN -> drawAlienAvatar(outline, sw)
        CloudAvatarType.UNICORN -> drawUnicornAvatar(outline, sw)
        CloudAvatarType.ASTRONAUT -> drawAstronautAvatar(outline, sw)
        CloudAvatarType.ROBOT -> drawRobotAvatar(outline, sw)
        CloudAvatarType.DRAGON -> drawDragonAvatar(outline, sw)
        CloudAvatarType.NINJA -> drawNinjaAvatar(outline, sw)
    }
}

// ---------- Общие детали лица ----------

private fun DrawScope.drawEyes(outline: Color, sw: Float, y: Float = 54f, dx: Float = 9f) {
    drawCircle(outline, 3.4f, Offset(50f - dx, y))
    drawCircle(outline, 3.4f, Offset(50f + dx, y))
    drawCircle(Color.White, 1.1f, Offset(50f - dx + 1f, y - 1f))
    drawCircle(Color.White, 1.1f, Offset(50f + dx + 1f, y - 1f))
}

private fun DrawScope.drawTriangle(
    x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
    fill: Color, outline: Color, sw: Float
) {
    val p = Path().apply {
        moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
    }
    drawPath(p, fill)
    drawPath(p, outline, style = Stroke(width = sw))
}

// ---------- Персонажи ----------

private fun DrawScope.drawCatAvatar(outline: Color, sw: Float) {
    val fur = Color(0xFFFFB74D)
    val inner = Color(0xFFFFAB91)
    // Уши
    drawTriangle(28f, 42f, 22f, 14f, 46f, 30f, fur, outline, sw)
    drawTriangle(72f, 42f, 78f, 14f, 54f, 30f, fur, outline, sw)
    drawTriangle(31f, 37f, 27f, 21f, 42f, 31f, inner, outline, sw * 0.6f)
    drawTriangle(69f, 37f, 73f, 21f, 58f, 31f, inner, outline, sw * 0.6f)
    // Голова
    drawCircle(fur, 27f, Offset(50f, 56f), style = Stroke(width = sw))
    drawCircle(fur, 26f, Offset(50f, 56f))
    drawEyes(outline, sw, y = 52f)
    // Нос и рот
    val nose = Path().apply {
        moveTo(47f, 60f); lineTo(53f, 60f); lineTo(50f, 64f); close()
    }
    drawPath(nose, Color(0xFFEC407A))
    drawLine(outline, Offset(50f, 64f), Offset(50f, 67f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 67f), Offset(45f, 70f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 67f), Offset(55f, 70f), strokeWidth = sw * 0.6f)
    // Усы
    listOf(38f, 44f, 50f).forEach { y ->
        drawLine(outline.copy(alpha = 0.6f), Offset(16f, y + 4f), Offset(32f, y + 2f), strokeWidth = sw * 0.45f)
        drawLine(outline.copy(alpha = 0.6f), Offset(84f, y + 4f), Offset(68f, y + 2f), strokeWidth = sw * 0.45f)
    }
}

private fun DrawScope.drawPandaAvatar(outline: Color, sw: Float) {
    val black = Color(0xFF37474F)
    // Уши
    drawCircle(black, 10f, Offset(31f, 32f), style = Stroke(width = sw))
    drawCircle(black, 10f, Offset(69f, 32f), style = Stroke(width = sw))
    // Голова
    drawCircle(Color.White, 27f, Offset(50f, 56f), style = Stroke(width = sw))
    drawCircle(Color.White, 26f, Offset(50f, 56f))
    // Пятна вокруг глаз
    drawOval(black, Offset(31f, 44f), androidx.compose.ui.geometry.Size(18f, 20f))
    drawOval(black, Offset(51f, 44f), androidx.compose.ui.geometry.Size(18f, 20f))
    // Глаза внутри пятен
    drawCircle(Color.White, 4f, Offset(40f, 53f))
    drawCircle(Color.White, 4f, Offset(60f, 53f))
    drawCircle(outline, 2f, Offset(40f, 53f))
    drawCircle(outline, 2f, Offset(60f, 53f))
    // Нос и улыбка
    drawOval(black, Offset(46f, 63f), androidx.compose.ui.geometry.Size(8f, 6f))
    drawLine(outline, Offset(50f, 66f), Offset(50f, 69f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(45f, 72f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(55f, 72f), strokeWidth = sw * 0.6f)
}

private fun DrawScope.drawAlienAvatar(outline: Color, sw: Float) {
    val skin = Color(0xFF81C784)
    // Антенна
    drawLine(skin, Offset(50f, 32f), Offset(50f, 18f), strokeWidth = sw * 0.9f)
    drawCircle(Color(0xFFFFD54F), 5f, Offset(50f, 14f), style = Stroke(width = sw * 0.8f))
    // Голова (чуть вытянутая)
    drawOval(skin, Offset(24f, 30f), androidx.compose.ui.geometry.Size(52f, 56f), style = Stroke(width = sw))
    drawOval(skin, Offset(25f, 31f), androidx.compose.ui.geometry.Size(50f, 54f))
    // Большие глаза
    drawOval(outline, Offset(33f, 46f), androidx.compose.ui.geometry.Size(13f, 17f))
    drawOval(outline, Offset(54f, 46f), androidx.compose.ui.geometry.Size(13f, 17f))
    drawCircle(Color.White, 3f, Offset(38f, 51f))
    drawCircle(Color.White, 3f, Offset(59f, 51f))
    // Улыбка
    drawLine(outline, Offset(44f, 72f), Offset(56f, 72f), strokeWidth = sw * 0.8f)
    drawLine(outline, Offset(44f, 72f), Offset(47f, 75f), strokeWidth = sw * 0.8f)
    drawLine(outline, Offset(56f, 72f), Offset(53f, 75f), strokeWidth = sw * 0.8f)
}

private fun DrawScope.drawUnicornAvatar(outline: Color, sw: Float) {
    val mane = Color(0xFFEC407A)
    val muzzle = Color(0xFFFCE4EC)
    // Рог
    drawTriangle(44f, 34f, 50f, 6f, 56f, 34f, Color(0xFFFFD54F), outline, sw)
    drawLine(outline.copy(alpha = 0.5f), Offset(47f, 26f), Offset(53f, 26f), strokeWidth = sw * 0.4f)
    drawLine(outline.copy(alpha = 0.5f), Offset(48.5f, 18f), Offset(51.5f, 18f), strokeWidth = sw * 0.4f)
    // Грива (пятна сбоку)
    listOf(30f, 40f, 50f, 60f).forEach { y ->
        drawCircle(mane, 5f, Offset(76f, y + 8f), style = Stroke(width = sw * 0.7f))
    }
    // Голова
    drawCircle(Color(0xFFF3E5F5), 27f, Offset(50f, 56f), style = Stroke(width = sw))
    drawCircle(Color(0xFFF3E5F5), 26f, Offset(50f, 56f))
    drawEyes(outline, sw, y = 50f)
    // Морда
    drawOval(muzzle, Offset(38f, 60f), androidx.compose.ui.geometry.Size(24f, 16f), style = Stroke(width = sw * 0.7f))
    drawCircle(Color(0xFFEC407A), 2.4f, Offset(46f, 64f))
    drawCircle(Color(0xFFEC407A), 2.4f, Offset(54f, 64f))
    drawLine(outline, Offset(50f, 66f), Offset(50f, 69f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(46f, 72f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(54f, 72f), strokeWidth = sw * 0.6f)
    // Блик-звёздочка
    val star = Path().apply {
        moveTo(78f, 18f); lineTo(80f, 23f); lineTo(85f, 25f); lineTo(80f, 27f)
        lineTo(78f, 32f); lineTo(76f, 27f); lineTo(71f, 25f); lineTo(76f, 23f); close()
    }
    drawPath(star, Color.White, style = Stroke(width = sw * 0.6f))
}

private fun DrawScope.drawAstronautAvatar(outline: Color, sw: Float) {
    val suit = Color(0xFFCFD8DC)
    val skin = Color(0xFFFFCC80)
    // Шлем (внешний круг)
    drawCircle(suit, 30f, Offset(50f, 54f), style = Stroke(width = sw))
    drawCircle(suit, 29f, Offset(50f, 54f))
    // Визор-шлем: светлая зона
    drawRoundRect(
        Color(0xFFB3E5FC).copy(alpha = 0.85f),
        topLeft = Offset(28f, 36f),
        size = androidx.compose.ui.geometry.Size(44f, 26f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        style = Stroke(width = sw * 0.7f)
    )
    // Лицо
    drawCircle(skin, 20f, Offset(50f, 60f), style = Stroke(width = sw * 0.8f))
    drawCircle(skin, 19f, Offset(50f, 60f))
    drawEyes(outline, sw * 0.8f, y = 58f, dx = 7f)
    // Улыбка
    drawLine(outline, Offset(45f, 68f), Offset(55f, 68f), strokeWidth = sw * 0.6f)
    // Антенна на шлеме
    drawLine(suit, Offset(50f, 24f), Offset(50f, 14f), strokeWidth = sw * 0.8f)
    drawCircle(Color(0xFFEF5350), 4f, Offset(50f, 11f), style = Stroke(width = sw * 0.7f))
}

private fun DrawScope.drawRobotAvatar(outline: Color, sw: Float) {
    val body = Color(0xFFB0BEC5)
    val dark = Color(0xFF37474F)
    // Антенна
    drawLine(body, Offset(50f, 30f), Offset(50f, 16f), strokeWidth = sw * 0.9f)
    drawCircle(Color(0xFFEF5350), 5f, Offset(50f, 12f), style = Stroke(width = sw * 0.8f))
    // Голова
    drawRoundRect(
        body,
        topLeft = Offset(30f, 28f),
        size = androidx.compose.ui.geometry.Size(40f, 46f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
        style = Stroke(width = sw)
    )
    drawRoundRect(body, Offset(31f, 29f), androidx.compose.ui.geometry.Size(38f, 44f), androidx.compose.ui.geometry.CornerRadius(9f))
    // Ушки- bolts
    drawCircle(body, 5f, Offset(27f, 44f), style = Stroke(width = sw * 0.7f))
    drawCircle(body, 5f, Offset(73f, 44f), style = Stroke(width = sw * 0.7f))
    // Визор
    drawRoundRect(
        dark,
        topLeft = Offset(36f, 42f),
        size = androidx.compose.ui.geometry.Size(28f, 14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
    )
    drawCircle(Color(0xFF4DD0E1), 3f, Offset(44f, 49f))
    drawCircle(Color(0xFF4DD0E1), 3f, Offset(56f, 49f))
    // Рот-решётка
    for (i in 0 until 3) {
        drawLine(outline, Offset(42f, 64f + i * 4f), Offset(58f, 64f + i * 4f), strokeWidth = sw * 0.5f)
    }
}

private fun DrawScope.drawDragonAvatar(outline: Color, sw: Float) {
    val skin = Color(0xFF66BB6A)
    val belly = Color(0xFFA5D6A7)
    val horn = Color(0xFFFFF9C4)
    // Рога
    drawTriangle(34f, 36f, 28f, 12f, 46f, 30f, horn, outline, sw)
    drawTriangle(66f, 36f, 72f, 12f, 54f, 30f, horn, outline, sw)
    // Шипы на макушке
    drawTriangle(44f, 30f, 47f, 18f, 50f, 30f, horn, outline, sw * 0.7f)
    drawTriangle(50f, 30f, 53f, 18f, 56f, 30f, horn, outline, sw * 0.7f)
    // Голова
    drawCircle(skin, 27f, Offset(50f, 56f), style = Stroke(width = sw))
    drawCircle(skin, 26f, Offset(50f, 56f))
    drawEyes(outline, sw, y = 50f)
    // Морда
    drawRoundRect(
        belly,
        topLeft = Offset(36f, 58f),
        size = androidx.compose.ui.geometry.Size(28f, 16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f),
        style = Stroke(width = sw * 0.7f)
    )
    drawCircle(outline, 2.2f, Offset(44f, 64f))
    drawCircle(outline, 2.2f, Offset(56f, 64f))
    drawLine(outline, Offset(50f, 66f), Offset(50f, 69f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(46f, 72f), strokeWidth = sw * 0.6f)
    drawLine(outline, Offset(50f, 69f), Offset(54f, 72f), strokeWidth = sw * 0.6f)
    // Улыбка от ушей морды
    drawLine(outline.copy(alpha = 0.6f), Offset(38f, 62f), Offset(32f, 60f), strokeWidth = sw * 0.5f)
    drawLine(outline.copy(alpha = 0.6f), Offset(62f, 62f), Offset(68f, 60f), strokeWidth = sw * 0.5f)
}

private fun DrawScope.drawNinjaAvatar(outline: Color, sw: Float) {
    val wrap = Color(0xFF455A64)
    val band = Color(0xFFEF5350)
    val mask = Color(0xFF263238)
    // Хвосты повязки
    drawTriangle(30f, 38f, 14f, 30f, 28f, 48f, band, outline, sw * 0.8f)
    drawTriangle(70f, 38f, 86f, 30f, 72f, 48f, band, outline, sw * 0.8f)
    // Голова
    drawCircle(wrap, 27f, Offset(50f, 56f), style = Stroke(width = sw))
    drawCircle(wrap, 26f, Offset(50f, 56f))
    // Повязка на лоб
    drawRoundRect(
        band,
        topLeft = Offset(26f, 38f),
        size = androidx.compose.ui.geometry.Size(48f, 9f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        style = Stroke(width = sw * 0.7f)
    )
    // Глаза (белые щели)
    drawRoundRect(Color.White, Offset(36f, 50f), androidx.compose.ui.geometry.Size(11f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
    drawRoundRect(Color.White, Offset(53f, 50f), androidx.compose.ui.geometry.Size(11f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
    drawCircle(outline, 2f, Offset(41f, 53f))
    drawCircle(outline, 2f, Offset(59f, 53f))
    // Маска нижней части лица
    drawRoundRect(
        mask,
        topLeft = Offset(36f, 62f),
        size = androidx.compose.ui.geometry.Size(28f, 12f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
    )
}
