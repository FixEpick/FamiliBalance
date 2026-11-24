package com.example.familibalance.feature.admin.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.AppOutlinedField
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.ui.theme.AccentButtonShape
import com.example.familibalance.ui.theme.AccentColor
import com.example.familibalance.ui.theme.AccentOnColor
import com.example.familibalance.ui.theme.SpaceBgStart
import com.example.familibalance.ui.theme.TextPrimary
import com.example.familibalance.ui.theme.TextSecondary
import com.example.familibalance.ui.theme.IconBadge
import com.example.familibalance.ui.theme.accentButtonChrome
import com.example.familibalance.ui.theme.bounceClick
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import com.example.familibalance.util.currencyString

/** Диалог создания бонус-квеста для выбранного ребёнка. */
@Composable
fun AddBonusQuestDialog(
    child: ChildProfileState,
    onConfirm: (title: String, description: String, reward: Int, requiresPhoto: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var bonusTitle by rememberSaveable { mutableStateOf("") }
    var bonusDesc by rememberSaveable { mutableStateOf("") }
    var bonusReward by rememberSaveable { mutableStateOf("50") }
    var bonusRequiresPhoto by rememberSaveable { mutableStateOf(true) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(emoji = "🎁", size = 32.dp, emojiSize = 16.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.admin_set_bonus_title, childDisplayName(child)),
                    color = AccentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppOutlinedField(
                    value = bonusTitle,
                    onValueChange = { bonusTitle = it },
                    label = { Text(stringResource(R.string.admin_set_bonus_name), color = TextPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )
                AppOutlinedField(
                    value = bonusDesc,
                    onValueChange = { bonusDesc = it },
                    label = { Text(stringResource(R.string.admin_set_bonus_desc), color = TextPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )
                AppOutlinedField(
                    value = bonusReward,
                    onValueChange = { bonusReward = it },
                    label = { Text(currencyString(R.string.admin_set_bonus_reward), color = TextPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = bonusRequiresPhoto,
                        onCheckedChange = { bonusRequiresPhoto = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                    )
                    Text(
                        text = stringResource(R.string.admin_set_bonus_photo),
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rewardInt = bonusReward.toIntOrNull() ?: 50
                    if (bonusTitle.isNotBlank()) {
                        onConfirm(bonusTitle.trim(), bonusDesc.trim(), rewardInt, bonusRequiresPhoto)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .accentButtonChrome()
                    .bounceClick(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = AccentOnColor
                ),
                shape = AccentButtonShape
            ) {
                Text(stringResource(R.string.admin_set_create), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}
