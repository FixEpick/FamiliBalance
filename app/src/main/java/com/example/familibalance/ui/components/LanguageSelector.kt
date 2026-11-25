package com.example.familibalance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.AppLocaleState
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*

internal data class LanguageOption(val tag: String, @androidx.annotation.StringRes val nameRes: Int)

internal val languageOptions = listOf(
    LanguageOption("ru", R.string.language_ru),
    LanguageOption("en", R.string.language_en)
)

/**
 * Кнопка-триггер переключателя языка: компактная иконка 🌐.
 * Открывает [LanguageSelectionBottomSheet].
 */
@Composable
fun LanguageSelectorButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    val langDesc = stringResource(R.string.a11y_language_button)
    IconButton(onClick = { showSheet = true }, modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AccentColor.copy(alpha = 0.12f))
                .border(1.dp, AccentColor.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GameIcon(
                icon = GameIconType.Language,
                tint = AccentColor,
                size = 18.dp,
                modifier = Modifier.semantics { contentDescription = langDesc }
            )
        }
    }

    if (showSheet) {
        LanguageSelectionBottomSheet(
            currentTag = AppLocaleState.tag,
            onSelect = { tag ->
                AppLocaleState.set(context, tag)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * Строка выбора языка: вся область кликабельна, справа показан
 * текущий язык и шеврон. Открывает [LanguageSelectionBottomSheet].
 */
@Composable
fun LanguageSelectorRow(
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val currentNameRes = languageOptions
        .firstOrNull { it.tag == AppLocaleState.tag }
        ?.nameRes ?: R.string.language_ru
    val currentName = stringResource(currentNameRes)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable { showSheet = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currentName,
            color = AccentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        GameIcon(
            icon = GameIconType.ArrowForward,
            tint = AccentColor,
            size = 16.dp
        )
    }

    if (showSheet) {
        LanguageSelectionBottomSheet(
            currentTag = AppLocaleState.tag,
            onSelect = { tag ->
                AppLocaleState.set(context, tag)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * BottomSheet выбора языка в киберпанк-стиле.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionBottomSheet(
    currentTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpaceBgStart,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.language_sheet_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            languageOptions.forEach { option ->
                val selected = option.tag == currentTag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option.tag) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(option.nameRes),
                        color = if (selected) AccentColor else TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Text("✅", fontSize = 16.sp)
                    }
                }
                HorizontalDivider(color = GlassStroke)
            }
        }
        }
    }
}
