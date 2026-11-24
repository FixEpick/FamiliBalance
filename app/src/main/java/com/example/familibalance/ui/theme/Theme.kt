package com.example.familibalance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class AppThemePalette(
    val id: String,
    val primaryNeon: Color,
    val secondaryNeon: Color
)

private fun paletteForTheme(config: AppThemeConfig) = AppThemePalette(
    id = config.id,
    primaryNeon = config.accent,
    secondaryNeon = config.accent.copy(alpha = 0.6f)
)

/**
 * Легаси-палитра неоновых акцентов для компонентов, которые ещё
 * используют LocalAppPalette. Строится из AppThemeConfig: primary —
 * акцент темы, secondary — его приглушённая версия.
 */
val LocalAppPalette = compositionLocalOf { paletteForTheme(AllAppThemes.first()) }

@Composable
fun FamiliBalanceTheme(
    themeId: String = "COSMIC_BLUE",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Отключаем динамические цвета, чтобы не ломать нашу тему
    content: @Composable () -> Unit
) {
    val config = getThemeById(themeId)
    // Фактическая тёмность определяется темой, а не системной настройкой.
    val useDark = config.isDark

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = config.accent,
            onPrimary = config.onAccentText,
            secondary = config.accent,
            tertiary = config.backgroundGradientEnd,
            background = config.background,
            surface = config.cardBackground
        )
    } else {
        lightColorScheme(
            primary = config.accent,
            onPrimary = config.onAccentText,
            secondary = config.accent,
            tertiary = config.backgroundGradientEnd,
            background = config.background,
            surface = config.cardBackground
        )
    }

    // Иконки статус-бара должны контрастировать с фоном: в светлых темах — тёмные.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !config.isDark
                isAppearanceLightNavigationBars = !config.isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalAppTheme provides config,
        LocalAppPalette provides paletteForTheme(config)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
