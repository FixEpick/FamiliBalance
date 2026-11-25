package com.example.familibalance.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.familibalance.R

enum class BackgroundPattern { STARS, CYBER_GRID, CLOUDS, PIXELS, CANDY, DRAGON_SCALES, SUN_RAYS, PLANETS }

/**
 * Правило разблокировки темы в «Магазине тем».
 * Достижения матчатся по заголовку: id достижений генерируются
 * случайным UUID при создании профиля и не являются стабильным ключом.
 */
sealed interface ThemeUnlockRule {
    /** Доступна сразу, без условий. */
    data object Free : ThemeUnlockRule
    /** Покупка за монеты с баланса ребёнка. */
    data class Paid(val price: Int) : ThemeUnlockRule
    /** Открывается автоматически при заявке (isClaimed) достижения с таким заголовком. */
    data class Achievement(val achievementTitle: String, @StringRes val titleRes: Int) : ThemeUnlockRule
    /** Открывается автоматически при достижении минимального уровня. */
    data class Level(val minLevel: Int) : ThemeUnlockRule
}

data class AppThemeConfig(
    val id: String,
    val title: String,
    @StringRes val titleRes: Int = 0,
    val isDark: Boolean,
    val background: Color,
    val backgroundGradientEnd: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccentText: Color,
    val pattern: BackgroundPattern,
    val unlockRule: ThemeUnlockRule = ThemeUnlockRule.Free
)

val AllAppThemes = listOf(
    AppThemeConfig(
        id = "COSMIC_BLUE",
        title = "Космический синий",
        titleRes = R.string.theme_name_cosmic_blue,
        isDark = true,
        background = Color(0xFF0B0F19),
        backgroundGradientEnd = Color(0xFF161D36),
        cardBackground = Color(0xFF131B33),
        cardBorder = Color(0xFF2A3A5F),
        primaryText = Color(0xFFF1F5F9),
        secondaryText = Color(0xFF94A3B8),
        accent = Color(0xFF38BDF8),
        onAccentText = Color(0xFF090D16),
        pattern = BackgroundPattern.STARS,
        unlockRule = ThemeUnlockRule.Free
    ),
    AppThemeConfig(
        id = "CYBER_NEON",
        title = "Кибер-неон",
        titleRes = R.string.theme_name_cyber_neon,
        isDark = true,
        background = Color(0xFF090D16),
        backgroundGradientEnd = Color(0xFF131B2E),
        cardBackground = Color(0xFF101828),
        cardBorder = Color(0xFF1E3A4A),
        primaryText = Color(0xFFE2E8F0),
        secondaryText = Color(0xFF7DD3FC),
        accent = Color(0xFF00F5D4),
        onAccentText = Color(0xFF090D16),
        pattern = BackgroundPattern.CYBER_GRID,
        unlockRule = ThemeUnlockRule.Paid(price = 150)
    ),
    AppThemeConfig(
        id = "GALAXY_SUNSET",
        title = "Галактический закат",
        titleRes = R.string.theme_name_galaxy_sunset,
        isDark = true,
        background = Color(0xFF1A0B2E),
        backgroundGradientEnd = Color(0xFF2E1042),
        cardBackground = Color(0xD926123D),
        cardBorder = Color(0xFF581C87),
        primaryText = Color(0xFFFAF5FF),
        secondaryText = Color(0xFFD8B4FE),
        accent = Color(0xFFF43F5E),
        onAccentText = Color(0xFFFFFFFF),
        pattern = BackgroundPattern.PLANETS,
        unlockRule = ThemeUnlockRule.Paid(price = 250)
    ),
    AppThemeConfig(
        id = "GOLDEN_DRAGON",
        title = "Золотой дракон",
        titleRes = R.string.theme_name_golden_dragon,
        isDark = true,
        background = Color(0xFF18120B),
        backgroundGradientEnd = Color(0xFF291D10),
        cardBackground = Color(0xD9241A10),
        cardBorder = Color(0xFF78350F),
        primaryText = Color(0xFFFFFBEB),
        secondaryText = Color(0xFFD6C7B2),
        accent = Color(0xFFF59E0B),
        onAccentText = Color(0xFF18120B),
        pattern = BackgroundPattern.DRAGON_SCALES,
        unlockRule = ThemeUnlockRule.Achievement(
            achievementTitle = "Босс Первой Парты",
            titleRes = R.string.sys_ach_diary_10_title
        )
    ),
    AppThemeConfig(
        id = "CLOUD_LIGHT",
        title = "Облачный день",
        titleRes = R.string.theme_name_cloud_light,
        isDark = false,
        background = Color(0xFFEFF6FF),
        backgroundGradientEnd = Color(0xFFDBEAFE),
        cardBackground = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFBFDBFE),
        primaryText = Color(0xFF0F172A),
        secondaryText = Color(0xFF475569),
        accent = Color(0xFF2563EB),
        onAccentText = Color(0xFFFFFFFF),
        pattern = BackgroundPattern.CLOUDS,
        unlockRule = ThemeUnlockRule.Free
    ),
    AppThemeConfig(
        id = "PIXEL_CRAFT",
        title = "Пиксель-крафт",
        titleRes = R.string.theme_name_pixel_craft,
        isDark = false,
        background = Color(0xFFECFDF5),
        backgroundGradientEnd = Color(0xFFD1FAE5),
        cardBackground = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFA7F3D0),
        primaryText = Color(0xFF064E3B),
        secondaryText = Color(0xFF047857),
        accent = Color(0xFF10B981),
        onAccentText = Color(0xFFFFFFFF),
        pattern = BackgroundPattern.PIXELS,
        unlockRule = ThemeUnlockRule.Paid(price = 150)
    ),
    AppThemeConfig(
        id = "BUBBLE_GUM",
        title = "Бабл-гам",
        titleRes = R.string.theme_name_bubble_gum,
        isDark = false,
        background = Color(0xFFFDF2F8),
        backgroundGradientEnd = Color(0xFFFCE7F3),
        cardBackground = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFFBCFE8),
        primaryText = Color(0xFF500724),
        secondaryText = Color(0xFF831843),
        accent = Color(0xFFEC4899),
        onAccentText = Color(0xFFFFFFFF),
        pattern = BackgroundPattern.CANDY,
        unlockRule = ThemeUnlockRule.Paid(price = 200)
    ),
    AppThemeConfig(
        id = "SUNNY_CITRUS",
        title = "Солнечный цитрус",
        titleRes = R.string.theme_name_sunny_citrus,
        isDark = false,
        background = Color(0xFFFFFBEB),
        backgroundGradientEnd = Color(0xFFFEF3C7),
        cardBackground = Color(0xFFFFFFFF),
        cardBorder = Color(0xFFFDE68A),
        primaryText = Color(0xFF451A03),
        secondaryText = Color(0xFF78350F),
        accent = Color(0xFFEA580C),
        onAccentText = Color(0xFFFFFFFF),
        pattern = BackgroundPattern.SUN_RAYS,
        unlockRule = ThemeUnlockRule.Achievement(
            achievementTitle = "Архитектор Уюта",
            titleRes = R.string.sys_ach_room_10_title
        )
    )
)

/** ID тем, доступных бесплатно по умолчанию (по одной в каждой группе). */
val DefaultUnlockedThemeIds: List<String> =
    AllAppThemes.filter { it.unlockRule is ThemeUnlockRule.Free }.map { it.id }

/**
 * Доступна ли тема профилю: бесплатная, уже купленная/выданная
 * (id в unlockedThemes) или привязанное достижение заявлено.
 */
fun AppThemeConfig.isThemeUnlocked(
    unlockedThemes: List<String>,
    claimedAchievementTitles: Set<String>
): Boolean {
    if (unlockRule is ThemeUnlockRule.Free) return true
    if (id in unlockedThemes) return true
    val ach = unlockRule as? ThemeUnlockRule.Achievement ?: return false
    return ach.achievementTitle in claimedAchievementTitles
}

/** ID тем, которые открываются заявленными (isClaimed) достижениями. */
fun achievementThemeIds(claimedAchievementTitles: Set<String>): Set<String> =
    AllAppThemes.mapNotNull { theme ->
        val rule = theme.unlockRule as? ThemeUnlockRule.Achievement ?: return@mapNotNull null
        if (rule.achievementTitle in claimedAchievementTitles) theme.id else null
    }.toSet()

val LocalAppTheme = staticCompositionLocalOf { AllAppThemes.first() }

fun getThemeById(id: String?): AppThemeConfig =
    AllAppThemes.firstOrNull { it.id == id } ?: AllAppThemes.first()
