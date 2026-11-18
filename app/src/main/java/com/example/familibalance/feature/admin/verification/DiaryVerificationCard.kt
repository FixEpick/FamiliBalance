package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*

import com.example.familibalance.viewmodel.actions.*


@Composable
fun DiaryVerificationCard(
    title: String,
    photoUrl: String?,
    scanState: DiaryScanState,
    actionEnabled: Boolean = true,
    onScanRequested: () -> Unit,
    onApprove: (Int, Int, Int, Int) -> Unit,
    onReject: (String) -> Unit,
    comment: String = ""
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReasonText by remember { mutableStateOf("") }
    var showFullScreenImage by remember { mutableStateOf(false) }

    val gradeCounts = remember { mutableStateMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0) }

    // Запуск OCR — только через ViewModel; UI лишь сообщает о желании сканировать
    LaunchedEffect(photoUrl) {
        if (!photoUrl.isNullOrEmpty()) onScanRequested()
    }

    // Применение результата сканирования к локальным счётчикам (ручная корректировка остаётся в UI)
    LaunchedEffect(scanState) {
        val success = scanState as? DiaryScanState.Success ?: return@LaunchedEffect
        gradeCounts.clear()
        gradeCounts.putAll(success.gradeCounts)
    }

    val isScanning = scanState is DiaryScanState.Loading
    val scanStatusText = when (scanState) {
        is DiaryScanState.Loading -> stringResource(R.string.admin_diary_scanning)
        is DiaryScanState.Success -> stringResource(R.string.admin_diary_scan_success)
        is DiaryScanState.Error -> stringResource(R.string.admin_diary_scan_error, scanState.message)
        DiaryScanState.Idle -> stringResource(R.string.admin_diary_reward_calc)
    }

    val total = (gradeCounts[5] ?: 0) * 50 + (gradeCounts[4] ?: 0) * 30 - (gradeCounts[3] ?: 0) * 20 - (gradeCounts[2] ?: 0) * 50
    val finalReward = maxOf(0, total)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        border = BorderStroke(1.dp, GlassStroke)
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
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(scanStatusText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.admin_diary_grade_adjust), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val gradeConfigs = listOf(
                Triple(5, "+50 ₽", StatusGreen),
                Triple(4, "+30 ₽", AccentColor),
                Triple(3, "-20 ₽", StatusYellow),
                Triple(2, "-50 ₽", PenaltyRed)
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
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.admin_approve_with_reward, finalReward), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    enabled = actionEnabled,
                    border = BorderStroke(1.dp, PenaltyRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.admin_reject), fontSize = 12.sp)
                }
            }
        }
    }

    if (showRejectDialog) {
        val defaultRejectReason = stringResource(R.string.admin_reject_default_reason)
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            containerColor = SpaceBgStart,
            title = { Text(stringResource(R.string.admin_reject_reason_title), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = rejectReasonText,
                    onValueChange = { rejectReasonText = it },
                    label = { Text(stringResource(R.string.admin_reject_reason_hint), color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = GlassBackground,
                        unfocusedContainerColor = GlassBackground,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = GlassStroke
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val reason = if (rejectReasonText.isNotBlank()) rejectReasonText else defaultRejectReason
                    onReject(reason)
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
            onDismiss = { showFullScreenImage = false }
        )
    }
}
