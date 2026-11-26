package com.example.familibalance.feature.admin.verification

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

import com.example.familibalance.R
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.util.*


@Composable
fun DiaryVerificationCard(
    title: String,
    childId: String,
    photoUrl: String?,
    scanState: DiaryScanState,
    actionEnabled: Boolean = true,
    onScanRequested: () -> Unit,
    onApprove: (Int, Int, Int, Int) -> Unit,
    onReject: (String) -> Unit,
    onRejectWithFine: ((String, Boolean) -> Unit)? = null,
    comment: String = ""
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReasonText by remember { mutableStateOf("") }
    var applyLieFine by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }

    val gradeCounts = remember(childId) { mutableStateMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0) }

    // Запуск OCR — только через ViewModel; UI лишь сообщает о желании сканировать
    LaunchedEffect(photoUrl) {
        if (!photoUrl.isNullOrEmpty()) onScanRequested()
    }

    // Применение результата сканирования к локальным счётчикам (ручная корректировка остаётся в UI)
    // Ключ по childId: результат OCR не должен применяться, если карточка
    // уже показывает другого ребёнка (scanState — глобальный StateFlow).
    LaunchedEffect(scanState, childId) {
        val success = scanState as? DiaryScanState.Success ?: return@LaunchedEffect
        if (success.childId != childId) return@LaunchedEffect
        gradeCounts.clear()
        gradeCounts.putAll(success.gradeCounts)
    }

    val isScanning = scanState is DiaryScanState.Loading
    val scanStatusText = when (scanState) {
        is DiaryScanState.Loading -> stringResource(R.string.admin_diary_scanning)
        is DiaryScanState.Success -> stringResource(R.string.admin_diary_scan_success)
        is DiaryScanState.Error -> stringResource(R.string.admin_diary_scan_error, scanState.message.asString())
        DiaryScanState.Idle -> stringResource(R.string.admin_diary_reward_calc)
    }

    val total = (gradeCounts[5] ?: 0) * 50 + (gradeCounts[4] ?: 0) * 30 - (gradeCounts[3] ?: 0) * 20 - (gradeCounts[2] ?: 0) * 50
    val finalReward = maxOf(0, total)

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
                    Text(
                        text = if (isScanning) scanStatusText else stringResource(R.string.admin_diary_reward_calc),
                        color = AccentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (comment.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.admin_comment_label, comment),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (scanState is DiaryScanState.Error) {
                        TextButton(onClick = onScanRequested) {
                            Text(stringResource(R.string.admin_diary_rescan), color = AccentColor, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (!photoUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = stringResource(R.string.admin_diary_photo),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFullScreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                    if (isScanning) {
                        // Лазерный сканер: полупрозрачная вуаль + светящаяся
                        // горизонтальная линия, бегущая сверху вниз.
                        val infiniteTransition = rememberInfiniteTransition(label = "diaryScan")
                        val scanProgress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scanLine"
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(scanStatusText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                // Сканирующая линия
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .align(Alignment.TopStart)
                                        .offset {
                                            IntOffset(
                                                0,
                                                ((180.dp - 24.dp) * scanProgress).roundToPx()
                                            )
                                        }
                                        .height(3.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    AccentColor.copy(alpha = 0.9f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.admin_diary_grade_adjust), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val gradeConfigs = listOf(
                Triple(5, "+50 ${rewardCurrencySymbol()}", StatusGreen),
                Triple(4, "+30 ${rewardCurrencySymbol()}", AccentColor),
                Triple(3, "-20 ${rewardCurrencySymbol()}", StatusYellow),
                Triple(2, "-50 ${rewardCurrencySymbol()}", PenaltyRed)
            )

            gradeConfigs.forEach { (grade, rewardLabel, color) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.admin_diary_grade_label, grade, rewardLabel), color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val current = gradeCounts[grade] ?: 0
                                if (current > 0) gradeCounts[grade] = current - 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${gradeCounts[grade] ?: 0}",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val current = gradeCounts[grade] ?: 0
                                gradeCounts[grade] = current + 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val summaryText = listOf(5, 4, 3, 2).map { g ->
                stringResource(R.string.admin_diary_grade_summary, g, gradeCounts[g] ?: 0)
            }.joinToString(", ")
            Text(summaryText, color = TextSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onApprove(
                            gradeCounts[5] ?: 0,
                            gradeCounts[4] ?: 0,
                            gradeCounts[3] ?: 0,
                            gradeCounts[2] ?: 0
                        )
                    },
                    enabled = actionEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = AccentButtonShape,
                    modifier = Modifier.bounceClick(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(currencyString(R.string.admin_approve_with_reward, finalReward), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        val cheatRejectReason = stringResource(R.string.admin_reject_reason_cheat)
        AppDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text(stringResource(R.string.admin_reject_reason_title), color = TextPrimary) },
            text = {
                Column(modifier = Modifier.imePadding()) {
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
                        if (applyLieFine) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.admin_reject_fine_streak_warning),
                                color = PenaltyRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val reason = if (rejectReasonText.isNotBlank()) {
                        rejectReasonText
                    } else if (applyLieFine) {
                        cheatRejectReason
                    } else {
                        defaultRejectReason
                    }
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
        FullScreenImageDialog(
            photoUrl = photoUrl,
            contentDescription = stringResource(R.string.a11y_fullscreen_diary_photo),
            onDismiss = { showFullScreenImage = false }
        )
    }
}
