package com.example.familibalance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*

@Composable
fun AchievementCard(
    title: String,
    condition: String,
    description: String,
    reward: String,
    progress: String,
    isLocked: Boolean,
    isCompleted: Boolean,
    isReadyToClaim: Boolean = false,
    onClaimClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayTitle = if (isLocked) "🔒 $title" else title
    val displayDescription = if (isLocked) "Откроется после выполнения предыдущего уровня" else description
    val displayReward = if (isLocked) "??? ₽" else reward
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
                        text = condition,
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
                Text(
                    text = "🔒",
                    fontSize = 24.sp,
                    modifier = Modifier.semantics {
                        contentDescription = lockedDesc
                    }
                )
            } else if (isCompleted) {
                Text(
                    text = "✓",
                    color = StatusGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = completedDesc
                    }
                )
            }
        }

        if (isReadyToClaim && onClaimClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onClaimClick() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🎁 Забрать награду $reward",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
