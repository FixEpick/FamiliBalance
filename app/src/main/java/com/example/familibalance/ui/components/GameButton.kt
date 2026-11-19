package com.example.familibalance.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GameButtonVariant(
    val background: Color,
    val shadow: Color,
    val border: Color?,
    val text: Color
) {
    Lime(Color(0xFFA3E635), Color(0xFF4D7C0F), null, Color(0xFF0F172A)),
    Slate(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF334155), Color(0xFFE2E8F0)),
    Electric(Color(0xFF3B82F6), Color(0xFF1D4ED8), null, Color(0xFFFFFFFF)),
    Amber(Color(0xFFF59E0B), Color(0xFFB45309), null, Color(0xFF0F172A))
}

/**
 * Киберпанк-кнопка с 3D-эффектом: при нажатии «вдавливается»
 * (смещается вниз, нижняя тень уменьшает толщину).
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: GameIconType? = null,
    variant: GameButtonVariant = GameButtonVariant.Lime,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val faceOffset by animateDpAsState(
        targetValue = if (pressed && enabled) 4.dp else 0.dp,
        label = "faceOffset"
    )
    val depth by animateDpAsState(
        targetValue = if (pressed && enabled) 2.dp else 6.dp,
        label = "depth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 8.dp)
    ) {
        // «Дно» кнопки — создаёт 3D-толщину
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, depth.roundToPx()) }
                .clip(shape)
                .background(if (enabled) variant.shadow else variant.shadow.copy(alpha = 0.4f))
        )
        // Лицевая часть
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, faceOffset.roundToPx()) }
                .clip(shape)
                .background(if (enabled) variant.background else variant.background.copy(alpha = 0.5f))
                .then(
                    if (variant.border != null) {
                        Modifier.border(1.dp, variant.border, shape)
                    } else Modifier
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    GameIcon(
                        icon = icon,
                        tint = if (enabled) variant.text else variant.text.copy(alpha = 0.6f),
                        size = 22.dp
                    )
                    Box(modifier = Modifier.padding(start = 8.dp))
                } else if (!emoji.isNullOrBlank()) {
                    Text(text = emoji, fontSize = 20.sp)
                    Box(modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) variant.text else variant.text.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
