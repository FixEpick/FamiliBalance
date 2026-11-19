package com.example.familibalance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единый рендер системных эмодзи: фиксированный бокс + выключенный
 * includeFontPadding, чтобы глифы не «плыли» по вертикали
 * на разных версиях Android.
 */
@Composable
fun EmojiIcon(
    emoji: String,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    contentDescription: String? = null
) {
    val emojiModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Box(
        modifier = emojiModifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            color = color,
            fontSize = size.value.sp,
            fontWeight = fontWeight,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}
