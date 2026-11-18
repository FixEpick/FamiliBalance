package com.example.familibalance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private data class LanguageOption(val tag: String, @androidx.annotation.StringRes val nameRes: Int)

private val languageOptions = listOf(
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
        Text(
            "🌐",
            fontSize = 20.sp,
            modifier = Modifier.semantics { contentDescription = langDesc }
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
