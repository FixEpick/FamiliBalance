package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.AccentButtonShape
import com.example.familibalance.ui.theme.CloudIconType
import com.example.familibalance.ui.theme.CloudStyleIcon
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.GlassCardShape
import com.example.familibalance.ui.theme.PenaltyRed
import com.example.familibalance.ui.theme.StatusGreen
import com.example.familibalance.ui.theme.StatusYellow
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.ui.theme.bounceClick
import com.example.familibalance.ui.theme.glassCard

/** Карточка «Честное признание» на проверке у родителя. */
@Composable
fun HonestAdmissionCard(
    childName: String,
    admissionText: String,
    actionEnabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(),
        shape = GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CloudStyleIcon(
                    type = CloudIconType.HONEST_CAT,
                    size = 28.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.admin_honest_title, childName),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.admin_honest_quote,
                    admissionText.ifBlank { stringResource(R.string.admin_no_comment_text) }
                ),
                color = TextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )
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
                    Text(stringResource(R.string.admin_approve_honest), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onReject,
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
}
