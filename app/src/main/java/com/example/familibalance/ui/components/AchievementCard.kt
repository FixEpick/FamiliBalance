package com.example.familibalance.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.data.model.*
import com.example.familibalance.util.*

@Composable
fun AchievementCard(
    title: String,
    condition: String,
    description: String,
    reward: String,
    progress: String,
    isLocked: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    isReadyToClaim: Boolean = false,
    onClaimClick: (() -> Unit)? = null,
    conditionKind: AchievementConditionKind? = null,
    conditionCount: Int = 0,
    progressCurrent: Int = 0,
    progressTarget: Int = 0
) {
    val displayTitle = title
    val displayCondition = when (conditionKind) {
        AchievementConditionKind.FIVES -> pluralStringResource(R.plurals.ach_condition_fives, conditionCount, conditionCount)
        AchievementConditionKind.WARDROBE_CLEAN -> pluralStringResource(R.plurals.ach_condition_wardrobe, conditionCount, conditionCount)
        AchievementConditionKind.ROOM_CLEAN -> pluralStringResource(R.plurals.ach_condition_room, conditionCount, conditionCount)
        null -> condition
    }
    val displayDescription = if (isLocked) stringResource(R.string.ach_locked_desc) else description
    val displayReward = if (isLocked) "??? ${rewardCurrencySymbol()}" else reward
    val lockedDesc = stringResource(R.string.a11y_locked_achievement)
    val completedDesc = stringResource(R.string.a11y_achievement_completed)

    val borderColor = if (isCompleted) StatusGreen else GlassStroke

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLocked) Modifier.alpha(0.4f) else Modifier)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isLocked && condition.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayCondition,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayDescription,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayReward,
                        color = if (isLocked) TextSecondary else AccentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLocked && progress.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = progress,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isLocked) {
                EmojiIcon(
                    emoji = "🔒",
                    size = 24.dp,
                    contentDescription = lockedDesc
                )
            } else if (isCompleted) {
                EmojiIcon(
                    emoji = "✓",
                    size = 24.dp,
                    color = StatusGreen,
                    fontWeight = FontWeight.Bold,
                    contentDescription = completedDesc
                )
            }
        }

        if (!isLocked && !isCompleted && progressTarget > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (progressCurrent.toFloat() / progressTarget).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentColor,
                trackColor = GlassStroke
            )
        }

        if (isReadyToClaim && onClaimClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onClaimClick() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.ach_claim_reward, reward),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
