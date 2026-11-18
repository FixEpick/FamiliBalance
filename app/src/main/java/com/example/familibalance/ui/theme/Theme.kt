package com.example.familibalance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemePalette(
    val id: String,
    val primaryNeon: Color,
    val secondaryNeon: Color
)

val AppThemes = listOf(
    AppThemePalette("COSMIC_BLUE", Color(0xFF5CE1E6), Color(0xFF7B61FF)),
    AppThemePalette("CYBER_PINK", Color(0xFFFF0080), Color(0xFFE91E63)),
    AppThemePalette("TOXIC_GREEN", Color(0xFF39FF14), Color(0xFF00FF41)),
    AppThemePalette("SOLAR_GOLD", Color(0xFFFFD700), Color(0xFFFFA500)),
    AppThemePalette("INFERNO_RED", Color(0xFFFF0040), Color(0xFFFF1744)),
    AppThemePalette("GALAXY_PURPLE", Color(0xFFB000FF), Color(0xFF8A2BE2)),
    AppThemePalette("ARCTIC_ICE", Color(0xFF00FFFF), Color(0xFF87CEEB)),
    AppThemePalette("SUNSET_ORANGE", Color(0xFFFF8C00), Color(0xFFFF4500))
)

val LocalAppPalette = compositionLocalOf { AppThemes.first() }

@Composable
fun FamiliBalanceTheme(
    themeId: String = "COSMIC_BLUE",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Отключаем динамические цвета, чтобы не ломать наш космос
    content: @Composable () -> Unit
) {
    val currentPalette = AppThemes.firstOrNull { it.id == themeId } ?: AppThemes.first()

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = currentPalette.primaryNeon,
            secondary = currentPalette.secondaryNeon,
            tertiary = SpaceBgStart,
            background = SpaceBgEnd,
            surface = GlassBackground
        )
    } else {
        lightColorScheme(
            primary = currentPalette.primaryNeon,
            secondary = currentPalette.secondaryNeon,
            tertiary = SpaceBgStart,
            background = SpaceBgEnd,
            surface = GlassBackground
        )
    }

    CompositionLocalProvider(LocalAppPalette provides currentPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
