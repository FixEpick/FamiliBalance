package com.example.familibalance.feature.admin.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.theme.*

/**
 * Единая карточка-аккордеон настроек админки (стиль «Валюта наград»):
 * компактный заголовок 15sp + стрелка ▼/▲, контент плавно
 * раскрывается через [AnimatedVisibility]. Свёрнута по умолчанию.
 */
@Composable
fun SettingsAccordionCard(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = GlassCardShape,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp)
                ) {
                    content()
                }
            }
        }
    }
}
