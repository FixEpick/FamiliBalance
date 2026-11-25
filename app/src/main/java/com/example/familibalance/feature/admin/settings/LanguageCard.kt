package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleState
import com.example.familibalance.R
import com.example.familibalance.ui.components.languageOptions
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary

/**
 * Карточка-аккордеон «Язык интерфейса»: вместо BottomSheet список
 * языков раскрывается внутри карточки, выбранный помечен ✓.
 * Свёрнута по умолчанию.
 */
@Composable
fun LanguageCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTag = AppLocaleState.tag
    val currentNameRes = languageOptions
        .firstOrNull { it.tag == currentTag }
        ?.nameRes ?: R.string.language_ru

    SettingsAccordionCard(
        title = stringResource(R.string.admin_set_language),
        modifier = modifier,
        trailing = {
            Text(
                text = stringResource(currentNameRes),
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    ) {
        languageOptions.forEach { option ->
            val selected = option.tag == currentTag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppLocaleState.set(context, option.tag) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(option.nameRes),
                    color = if (selected) AccentColor else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Text("✓", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            text = stringResource(R.string.admin_set_language_note),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
