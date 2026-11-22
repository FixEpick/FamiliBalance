package com.example.familibalance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.data.model.*

@Composable
fun TaskCard(
    title: String,
    description: String,
    reward: String,
    status: QuestStatus,
    modifier: Modifier = Modifier,
    rejectReason: String? = null,
    claimButtonText: String? = null,
    frequencyBadge: String? = null,
    /** Краткий отчёт (например, разбивка оценок дневника), показывается
     *  кликабельным текстом при READY_TO_CLAIM; клик открывает AlertDialog. */
    reportText: String? = null,
    borderColor: Color = GlassStroke,
    actionEnabled: Boolean = true,
    onExecute: () -> Unit,
    onClaim: (Offset) -> Unit
) {
    val resolvedClaimText = claimButtonText ?: stringResource(R.string.task_claim_reward)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reward,
                    color = AccentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                frequencyBadge?.let { badge ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = AccentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentColor),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            color = AccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            if (!rejectReason.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = PenaltyRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.task_rejected_by_dad, rejectReason),
                        color = PenaltyRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (status == QuestStatus.READY_TO_CLAIM && !reportText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                var showReportDialog by remember { mutableStateOf(false) }
                Text(
                    text = stringResource(R.string.task_view_report) + " ›",
                    color = AccentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showReportDialog = true }
                )
                if (showReportDialog) {
                    AppDialog(
                        onDismissRequest = { showReportDialog = false },
                        containerColor = GlassBackground,
                        title = {
                            Text(
                                text = stringResource(R.string.task_report_title),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = reportText,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showReportDialog = false }) {
                                Text(
                                    text = stringResource(R.string.task_report_close),
                                    color = AccentColor
                                )
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    QuestStatus.AVAILABLE -> {
                        Button(
                            onClick = onExecute,
                            enabled = actionEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentColor.copy(alpha = 0.2f),
                                contentColor = AccentColor
                            ),
                            border = BorderStroke(1.dp, AccentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = stringResource(R.string.task_execute), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    QuestStatus.PENDING -> {
                        Surface(
                            color = StatusYellow.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StatusYellow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.task_pending_review),
                                color = StatusYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    QuestStatus.READY_TO_CLAIM -> {
                        var buttonOffset by remember { mutableStateOf(Offset.Zero) }
                        Button(
                            onClick = { onClaim(buttonOffset) },
                            enabled = actionEnabled,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                buttonOffset = Offset(
                                    pos.x + coordinates.size.width / 2f,
                                    pos.y + coordinates.size.height / 2f
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalAppPalette.current.primaryNeon,
                                contentColor = SpaceBgStart
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = resolvedClaimText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    QuestStatus.CLAIMED, QuestStatus.APPROVED -> {
                        Surface(
                            color = StatusGreen.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StatusGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.task_done),
                                color = StatusGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
