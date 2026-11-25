package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.GamificationEngine
import com.example.familibalance.ui.components.AppDialog
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.theme.*

/** Диалог бонусного сундука: показывает выпавшую награду (монеты или XP). */
@Composable
fun BonusChestDialog(
    reward: GamificationEngine.BonusChestReward,
    onDismiss: () -> Unit
) {
    val rewardText = when (reward) {
        is GamificationEngine.BonusChestReward.Coins -> "+${reward.amount} ₽"
        is GamificationEngine.BonusChestReward.Xp -> "+${reward.amount} XP"
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(
                    icon = GameIconType.Gift,
                    tint = LocalAppPalette.current.primaryNeon,
                    size = 32.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bonus_chest_title),
                    color = LocalAppPalette.current.primaryNeon,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.bonus_chest_you_got),
                    color = TextMuted,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rewardText,
                    color = LocalAppPalette.current.primaryNeon,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
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
                Text(stringResource(R.string.bonus_chest_claim), fontWeight = FontWeight.Bold)
            }
        }
    )
}
