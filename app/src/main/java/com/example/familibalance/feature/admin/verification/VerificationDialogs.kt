package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.R
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.ui.theme.bounceClick

/**
 * Ряд кликабельных чипов-шаблонов причин отклонения.
 * Нажатие подставляет фразу в поле причины (onSelect).
 */
@Composable
fun RejectReasonTemplateChips(onSelect: (String) -> Unit) {
    val templates = listOf(
        stringResource(R.string.admin_reject_tpl_retake),
        stringResource(R.string.admin_reject_tpl_finish),
        stringResource(R.string.admin_reject_tpl_partial)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        templates.forEach { template ->
            Surface(
                color = AccentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .bounceClick()
                    .clickable { onSelect(template) }
            ) {
                Text(
                    text = template,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Полноэкранный просмотр фото с pinch-zoom и двойным тапом для сброса. */
@Composable
fun FullScreenImageDialog(
    photoUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppLocaleContent {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.a11y_fullscreen_diary_photo),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoomDelta, rotation ->
                            val newScale = (scale * zoomDelta).coerceIn(1f, 5f)
                            offset = Offset(
                                (offset.x + pan.x) * newScale / scale,
                                (offset.y + pan.y) * newScale / scale
                            )
                            scale = newScale
                        }
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            onTap = {
                                if (scale == 1f) {
                                    onDismiss()
                                }
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                val closeDesc = stringResource(R.string.a11y_close_button)
                Text(
                    "✕",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = closeDesc }
                )
            }
        }
        }
    }
}
