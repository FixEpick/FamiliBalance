package com.example.familibalance.feature.child.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.AppLocaleState
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

/**
 * Шторка «Настройки» детского раздела: звуковые эффекты, виброотклик,
 * выбор языка и выход из аккаунта. Все цвета — из LocalAppTheme,
 * корректно смотрится в тёмных и светлых темах.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSettingsBottomSheet(
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onLanguageSelect: (String) -> Unit,
    onLogoutClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val theme = LocalAppTheme.current
    val settingsTitle = stringResource(R.string.nav_settings)
    val soundSection = stringResource(R.string.settings_sound_section)
    val soundLabel = stringResource(R.string.settings_sound_effects)
    val vibrationLabel = stringResource(R.string.settings_vibration)
    val languageSection = stringResource(R.string.settings_language)
    val logoutLabel = stringResource(R.string.logout_confirm)
    val langRu = stringResource(R.string.language_ru)
    val langEn = stringResource(R.string.language_en)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = theme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Заголовок
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloudStyleIcon(type = CloudIconType.SETTINGS, size = 26.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = settingsTitle,
                    color = theme.primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Секция «Звук и отклик» ---
            SettingsSectionCard(theme) {
                Text(
                    text = soundSection,
                    color = theme.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingsSwitchRow(
                    theme = theme,
                    label = soundLabel,
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggle
                )
                SettingsSwitchRow(
                    theme = theme,
                    label = vibrationLabel,
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationToggle
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Секция «Язык приложения» ---
            SettingsSectionCard(theme) {
                Text(
                    text = languageSection,
                    color = theme.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LanguageChip(
                        theme = theme,
                        label = langRu,
                        selected = AppLocaleState.tag == "ru",
                        onClick = { onLanguageSelect("ru") }
                    )
                    LanguageChip(
                        theme = theme,
                        label = langEn,
                        selected = AppLocaleState.tag == "en",
                        onClick = { onLanguageSelect("en") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = theme.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // --- Аккаунт: выход ---
            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PenaltyRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(logoutLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    theme: AppThemeConfig,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.cardBackground)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun SettingsSwitchRow(
    theme: AppThemeConfig,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = theme.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = theme.accent,
                checkedThumbColor = theme.onAccentText,
                uncheckedTrackColor = theme.cardBorder,
                uncheckedThumbColor = theme.secondaryText
            )
        )
    }
}

@Composable
private fun LanguageChip(
    theme: AppThemeConfig,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) theme.accent else theme.cardBackground)
            .border(
                width = 1.dp,
                color = if (selected) theme.accent else theme.cardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) theme.onAccentText else theme.primaryText,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
