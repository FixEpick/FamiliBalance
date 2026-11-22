package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.GlassStroke
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary

/** Диалог выбора, чей ПИН-код менять: родительский или детский профиль. */
@Composable
fun PinPickerDialog(
    children: List<ChildProfileState>,
    onSelect: (target: String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.admin_set_pin_management),
                color = AccentColor,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val options = listOf("parent" to stringResource(R.string.admin_set_my_pin)) +
                children.map { it.id to "👦 ${childDisplayName(it)}" }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (target, label) ->
                    OutlinedButton(
                        onClick = { onSelect(target) },
                        border = BorderStroke(1.dp, GlassStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, GlassStroke),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}
