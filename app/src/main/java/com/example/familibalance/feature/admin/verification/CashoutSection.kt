package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.CashoutRequest
import com.example.familibalance.data.model.CashoutStatus
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.theme.*

/** Секция заявок на вывод наличных: approve/reject по каждой заявке. */
@Composable
fun CashoutSection(
    requests: List<CashoutRequest>,
    actionEnabled: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit
) {
    // Показываем только ожидающие заявки: отклонённые остаются
    // у ребёнка до нажатия «Понятно» и родителю не нужны.
    val pendingRequests = requests.filter { it.status == CashoutStatus.PENDING }
    if (pendingRequests.isEmpty()) return

    var rejectTarget by remember { mutableStateOf<CashoutRequest?>(null) }
    var rejectReasonText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.admin_cashout_requests_title),
                color = AccentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            pendingRequests.forEach { request ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    // Текст заявки сверху
                    Text(
                        text = currencyString(R.string.admin_cashout_amount, request.amount),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.admin_cashout_comment, cashoutCommentDisplay(request.comment)),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Кнопки снизу, на всю ширину поровну
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApprove(request.id) },
                            enabled = actionEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = AccentButtonShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .bounceClick()
                        ) {
                            Text(stringResource(R.string.admin_cashout_approved), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                rejectReasonText = ""
                                rejectTarget = request
                            },
                            enabled = actionEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                            shape = AccentButtonShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .bounceClick()
                        ) {
                            Text(stringResource(R.string.admin_cashout_reject), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Диалог причины отклонения — по аналогии с отклонением фотоотчёта.
    rejectTarget?.let { target ->
        val defaultRejectReason = stringResource(R.string.admin_reject_default_reason)
        AppDialog(
            onDismissRequest = { rejectTarget = null },
            title = {
                Text(stringResource(R.string.admin_reject_reason_title), color = TextPrimary)
            },
            text = {
                Column(modifier = Modifier.imePadding()) {
                    RejectReasonTemplateChips(onSelect = { rejectReasonText = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    AppOutlinedField(
                        value = rejectReasonText,
                        onValueChange = { rejectReasonText = it },
                        label = {
                            Text(
                                stringResource(R.string.admin_reject_reason_hint),
                                color = TextSecondary
                            )
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = if (rejectReasonText.isNotBlank()) rejectReasonText else defaultRejectReason
                        onReject(target.id, reason)
                        rejectTarget = null
                    },
                    enabled = actionEnabled
                ) {
                    Text(stringResource(R.string.admin_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
