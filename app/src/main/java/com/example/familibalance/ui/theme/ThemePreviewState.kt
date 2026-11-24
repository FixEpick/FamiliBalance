package com.example.familibalance.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Временный live-preview темы оформления для закрытых тем
 * (Paid / Achievement) в «Магазине тем».
 *
 * Живёт только в UI-памяти: не сохраняется в профиль ребёнка
 * и не синхронизируется с Supabase. MainActivity подставляет
 * [previewThemeId] вместо selectedThemeId в FamiliBalanceTheme,
 * поэтому весь интерфейс перекрашивается в реальном времени.
 * Сбрасывается при выходе из детского раздела (DisposableEffect
 * в ChildSection) и кнопкой «Вернуть мою тему» / ✕.
 */
object ThemePreviewState {
    var previewThemeId: String? by mutableStateOf(null)
}
