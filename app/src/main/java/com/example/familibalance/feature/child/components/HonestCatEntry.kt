package com.example.familibalance.feature.child.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.QuestStatus
import com.example.familibalance.ui.theme.*

/** Кнопка/статус «Честного признания» по статусу квеста и кулдауну. */
@Composable
fun HonestCatEntry(
    status: QuestStatus,
    cooldownDaysLeft: Int,
    onOpen: () -> Unit
) {
    when (status) {
        QuestStatus.AVAILABLE, QuestStatus.APPROVED -> {
            if (cooldownDaysLeft > 0) {
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassStroke),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TextSecondary,
                        disabledContainerColor = GlassBackground
                    )
                ) {
                    Text(
                        stringResource(R.string.child_honest_cooldown, cooldownDaysLeft),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LocalAppPalette.current.primaryNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppPalette.current.primaryNeon)
                ) {
                    Text(stringResource(R.string.child_honest_btn), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (status == QuestStatus.APPROVED) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusGreen.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = stringResource(R.string.child_honest_approved_note),
                        color = StatusGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
        QuestStatus.PENDING -> {
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = StatusYellow,
                    disabledContainerColor = GlassBackground
                )
            ) {
                Text(stringResource(R.string.child_honest_pending), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        QuestStatus.CLAIMED -> {
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = TextSecondary,
                    disabledContainerColor = GlassBackground
                )
            ) {
                Text(
                    stringResource(R.string.child_honest_claimed),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        QuestStatus.READY_TO_CLAIM -> {
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StatusGreen),
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = StatusGreen,
                    disabledContainerColor = GlassBackground
                )
            ) {
                Text(
                    stringResource(R.string.child_honest_ready),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
