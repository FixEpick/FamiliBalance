package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.util.currencyString

/** Карточка быстрых действий родителя: начислить / списать / штраф. */
@Composable
fun QuickActionsCard(
    childName: String,
    actionEnabled: Boolean,
    onDeposit: (amount: Int, description: String) -> Unit,
    onWithdraw: (amount: Int, description: String) -> Unit,
    onFine: (amount: Int, description: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.admin_quick_actions, childName),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.admin_quick_actions_subtitle),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            var isDeposit by remember { mutableStateOf(true) }
            var amountInput by remember { mutableStateOf("") }
            var descInput by remember { mutableStateOf("") }
            var isFine by remember { mutableStateOf(false) }

            // Переключатель режима: начислить / списать — единая капсула
            Surface(
                shape = RoundedCornerShape(50),
                color = GlassBackground,
                border = BorderStroke(1.dp, GlassStroke)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf(true to R.string.admin_mode_deposit, false to R.string.admin_mode_withdraw)
                        .forEach { (deposit, labelRes) ->
                            val selected = isDeposit == deposit
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            selected && deposit -> StatusGreen
                                            selected -> PenaltyRed
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        isDeposit = deposit
                                        if (deposit) isFine = false
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (selected) {
                                        if (deposit) SpaceBgStart else Color.White
                                    } else {
                                        if (deposit) StatusGreen else PenaltyRed
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    placeholder = {
                        Text(
                            currencyString(R.string.admin_amount_hint),
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SpaceBgStart,
                        unfocusedContainerColor = SpaceBgStart,
                        focusedBorderColor = if (isDeposit) StatusGreen else PenaltyRed,
                        unfocusedBorderColor = GlassStroke,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Button(
                    onClick = {
                        val amount = amountInput.toIntOrNull()
                        if (amount != null && amount > 0) {
                            if (isDeposit) {
                                onDeposit(amount, descInput)
                            } else if (isFine) {
                                onFine(amount, descInput)
                            } else {
                                onWithdraw(amount, descInput)
                            }
                            amountInput = ""
                            descInput = ""
                            isFine = false
                        }
                    },
                    enabled = actionEnabled &&
                        (amountInput.toIntOrNull()?.let { it > 0 } == true),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeposit) StatusGreen else PenaltyRed,
                        contentColor = if (isDeposit) SpaceBgStart else Color.White,
                        disabledContainerColor = GlassBackground,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        stringResource(R.string.admin_apply),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            OutlinedTextField(
                value = descInput,
                onValueChange = { descInput = it },
                label = { Text(stringResource(R.string.admin_desc_label), color = TextPrimary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SpaceBgStart,
                    unfocusedContainerColor = SpaceBgStart,
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (!isDeposit) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isFine,
                        onCheckedChange = { isFine = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PenaltyRed,
                            checkedTrackColor = PenaltyRed.copy(alpha = 0.4f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.admin_is_fine),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.admin_fine_presets_label),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                val finePresetReasons = listOf(
                    stringResource(R.string.admin_fine_reason_grade3),
                    stringResource(R.string.admin_fine_reason_grade2),
                    stringResource(R.string.admin_fine_reason_mischief),
                    stringResource(R.string.admin_fine_reason_lie)
                )
                val finePresets = listOf(
                    Triple(R.string.admin_fine_preset_grade3, 20, finePresetReasons[0]),
                    Triple(R.string.admin_fine_preset_grade2, 50, finePresetReasons[1]),
                    Triple(R.string.admin_fine_preset_mischief, 30, finePresetReasons[2]),
                    Triple(R.string.admin_fine_preset_lie, 100, finePresetReasons[3])
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(finePresets) { (labelRes, amount, reason) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                isFine = true
                                amountInput = amount.toString()
                                descInput = reason
                            },
                            label = { Text(currencyString(labelRes), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = PenaltyRed.copy(alpha = 0.12f),
                                labelColor = PenaltyRed
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = PenaltyRed.copy(alpha = 0.4f),
                                selectedBorderColor = PenaltyRed
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    }
}
