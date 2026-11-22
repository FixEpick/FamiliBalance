package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassBackground
import com.example.familibalance.ui.theme.StatusYellow
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.viewmodel.PendingRequestUi

/** Секция «Запросы на добавление»: заголовок, пустое состояние и список pending-запросов. */
fun LazyListScope.pendingRequestsSection(
    requests: List<PendingRequestUi>,
    onApprove: (String) -> Unit
) {
    item {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.admin_set_requests_title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
    if (requests.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.admin_set_no_requests),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    } else {
        items(requests, key = { it.id }) { row ->
            PendingRequestRow(row = row, onApprove = onApprove)
            Spacer(modifier = Modifier.height(10.dp))
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun PendingRequestRow(
    row: PendingRequestUi,
    onApprove: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassBackground,
        border = BorderStroke(1.dp, StatusYellow.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.avatar, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.admin_set_request_join, row.name),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.admin_set_request_email, row.email),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = { onApprove(row.id) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.admin_set_approve), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
