package com.example.familibalance.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpaceBgStart = Color(0xFF0F122B)
val SpaceBgEnd = Color(0xFF070919)
val GlassBackground = Color(0xFF0D132B).copy(alpha = 0.90f)
val GlassStroke = Color(0x4D4D90FF)
val NeonCyan = Color(0xFF5CE1E6)

/** Акцентный цвет текущей темы (primaryNeon палитры). Использовать вместо NeonCyan в Composable. */
val AccentColor: Color
    @Composable get() = LocalAppPalette.current.primaryNeon
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF)
/** Приглушённый серый для мелких подписей в подвалах карточек (контрастнее на тёмном фоне). */
val TextMuted = Color(0xFF94A3B8)
val StatusGreen = Color(0xFF4CAF50)
val StatusYellow = Color(0xFFFFC107)
val PenaltyRed = Color(0xFFFF5252)
val SpaceBgOverlay = Color(0xFF070B1E)
val TopBarBg = Color(0xFF0B1026)
val CardBgDark = Color(0xFF131B3A)
val AdminOverlayBg = Color(0xFF0A0E27)
val GoldBright = Color(0xFFFFD700)
val FilterSelectedBg = Color(0xFF0E2A3D)
val FilterBg = Color(0xFF0E142D)
