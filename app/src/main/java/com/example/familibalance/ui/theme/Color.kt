package com.example.familibalance.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Цвета фона/карточек/текста теперь берутся из активной AppThemeConfig
// через LocalAppTheme — единый источник правды для всех экранов.
// Семантические цвета (статусы, штрафы) остаются константами.

val SpaceBgStart: Color
    @Composable get() = LocalAppTheme.current.background
val SpaceBgEnd: Color
    @Composable get() = LocalAppTheme.current.backgroundGradientEnd
val GlassBackground: Color
    @Composable get() = LocalAppTheme.current.cardBackground.copy(alpha = 0.90f)
val GlassStroke: Color
    @Composable get() = LocalAppTheme.current.cardBorder
val NeonCyan: Color
    @Composable get() = LocalAppTheme.current.accent

/** Акцентный цвет текущей темы. Использовать вместо NeonCyan в Composable. */
val AccentColor: Color
    @Composable get() = LocalAppTheme.current.accent

/** Цвет текста на акцентных кнопках (контрастный к AccentColor). */
val AccentOnColor: Color
    @Composable get() = LocalAppTheme.current.onAccentText

val TextPrimary: Color
    @Composable get() = LocalAppTheme.current.primaryText
val TextSecondary: Color
    @Composable get() = LocalAppTheme.current.secondaryText
/** Приглушённый серый для мелких подписей в подвалах карточек. */
val TextMuted: Color
    @Composable get() = LocalAppTheme.current.secondaryText.copy(alpha = 0.7f)
val StatusGreen = Color(0xFF4CAF50)
val StatusYellow = Color(0xFFFFC107)
val PenaltyRed = Color(0xFFFF5252)
val SpaceBgOverlay: Color
    @Composable get() = LocalAppTheme.current.backgroundGradientEnd
val TopBarBg: Color
    @Composable get() = LocalAppTheme.current.cardBackground
val CardBgDark: Color
    @Composable get() = LocalAppTheme.current.cardBackground
val AdminOverlayBg: Color
    @Composable get() = LocalAppTheme.current.background
val GoldBright = Color(0xFFFFD700)
val FilterSelectedBg: Color
    @Composable get() = LocalAppTheme.current.accent.copy(alpha = 0.15f)
val FilterBg: Color
    @Composable get() = LocalAppTheme.current.cardBackground
