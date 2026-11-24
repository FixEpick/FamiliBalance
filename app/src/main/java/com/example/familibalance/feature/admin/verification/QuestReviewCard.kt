package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

import com.example.familibalance.R
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.util.*


@Composable
fun QuestVerificationCard(
    title: String,
    reward: String,
    description: String,
    photoUrl: String?,
    actionEnabled: Boolean = true,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onRejectWithFine: ((String, Boolean) -> Unit)? = null
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReasonText by remember { mutableStateOf("") }
    var applyLieFine by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(stringResource(R.string.admin_reward_label, reward), color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.admin_comment_label, description), color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            if (!photoUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(R.string.admin_photo_report),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFullScreenImage = true },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onApprove,
                    enabled = actionEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = AccentButtonShape,
                    modifier = Modifier.bounceClick(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.admin_approve), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    enabled = actionEnabled,
                    border = BorderStroke(1.dp, PenaltyRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                    shape = AccentButtonShape,
                    modifier = Modifier.bounceClick(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.admin_reject), fontSize = 12.sp)
                }
            }
        }
    }

    if (showRejectDialog) {
        val defaultRejectReason = stringResource(R.string.admin_reject_default_reason)
        AppDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text(stringResource(R.string.admin_reject_reason_title), color = TextPrimary) },
            text = {
                Column {
                    RejectReasonTemplateChips(onSelect = { rejectReasonText = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    AppOutlinedField(
                        value = rejectReasonText,
                        onValueChange = { rejectReasonText = it },
                        label = { Text(stringResource(R.string.admin_reject_reason_hint), color = TextSecondary) }
                    )
                    if (onRejectWithFine != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = applyLieFine,
                                onCheckedChange = { applyLieFine = it },
                                colors = CheckboxDefaults.colors(checkedColor = PenaltyRed)
                            )
                            Text(
                                text = currencyString(R.string.admin_reject_fine_lie),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val reason = if (rejectReasonText.isNotBlank()) rejectReasonText else defaultRejectReason
                    if (applyLieFine && onRejectWithFine != null) {
                        onRejectWithFine(reason, true)
                    } else {
                        onReject(reason)
                    }
                    applyLieFine = false
                    showRejectDialog = false
                }, enabled = actionEnabled) {
                    Text(stringResource(R.string.admin_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showFullScreenImage && !photoUrl.isNullOrEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullScreenImage = false },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(R.string.a11y_fullscreen_photo),
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
                                        showFullScreenImage = false
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullScreenImage = false },
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
