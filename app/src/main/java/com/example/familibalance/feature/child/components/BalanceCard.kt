package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.AppLocaleContent
import com.example.familibalance.R
import com.example.familibalance.data.model.CashoutRequest
import com.example.familibalance.data.model.CashoutStatus
import com.example.familibalance.util.*
import com.example.familibalance.ui.theme.*

/** Карточка баланса: аватар, сумма, бейдж банка. onBadgePositioned — для CoinFlightOverlay. */
@Composable
fun BalanceCard(
    avatar: String,
    animatedBalance: Int,
    onBadgePositioned: (Offset) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInRoot()
                    onBadgePositioned(Offset(pos.x + coordinates.size.width - 80f, pos.y + coordinates.size.height / 2f))
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = LocalAppPalette.current.primaryNeon.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(avatar, fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.child_balance), color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "$animatedBalance ${rewardCurrencySymbol()}",
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = GlassBackground,
                border = BorderStroke(1.dp, GlassStroke),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪙", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.child_bank_name),
                        color = LocalAppPalette.current.primaryNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Кнопка «Вывести» + список активных заявок на вывод под балансом. */
@Composable
fun CashoutSection(
    cashoutRequests: List<CashoutRequest>,
    onCashOutClick: () -> Unit,
    onDismissRejected: (String) -> Unit = {}
) {
    var reasonDialogRequest by remember { mutableStateOf<CashoutRequest?>(null) }

    // Активная заявка (ждёт подтверждения или отклонена, но «Понятно» не нажато)
    // блокирует кнопку — ребёнок не должен создавать дубликаты.
    val hasActiveRequest = cashoutRequests.any {
        it.status == CashoutStatus.PENDING || it.status == CashoutStatus.REJECTED
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = onCashOutClick,
            enabled = !hasActiveRequest,
            border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LocalAppPalette.current.primaryNeon,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(R.string.child_cashout_btn),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (cashoutRequests.isEmpty()) {
            Text(
                text = stringResource(R.string.child_no_cashout_requests),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.child_cashout_requests_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                cashoutRequests.forEach { request ->
                    val isRejected = request.status == CashoutStatus.REJECTED
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRejected) PenaltyRed.copy(alpha = 0.12f)
                            else CardBgDark.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRejected) PenaltyRed.copy(alpha = 0.5f) else GlassStroke
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currencyString(R.string.child_cashout_amount, request.amount),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.child_cashout_comment, cashoutCommentDisplay(request.comment)),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (isRejected) {
                                    Text(
                                        text = stringResource(R.string.child_cashout_rejected),
                                        color = PenaltyRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.child_cashout_why),
                                        color = AccentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { reasonDialogRequest = request }
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.child_cashout_waiting),
                                        color = StatusYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Окно с причиной отклонения: «Понятно» убирает плашку с главного экрана.
    reasonDialogRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { reasonDialogRequest = null },
            containerColor = SpaceBgStart,
            title = {
                AppLocaleContent {
                    Text(
                        stringResource(R.string.child_cashout_rejected),
                        color = PenaltyRed
                    )
                }
            },
            text = {
                AppLocaleContent {
                    Text(
                        text = request.rejectReason.orEmpty().ifBlank {
                            stringResource(R.string.admin_reject_default_reason)
                        },
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissRejected(request.id)
                        reasonDialogRequest = null
                    }
                ) {
                    AppLocaleContent {
                        Text(stringResource(R.string.child_cashout_reason_ok))
                    }
                }
            }
        )
    }
}
