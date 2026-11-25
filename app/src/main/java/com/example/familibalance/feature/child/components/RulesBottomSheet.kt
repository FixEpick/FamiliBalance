package com.example.familibalance.feature.child.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.ui.theme.*


/**
 * Контент «Святка правил» без обёртки ModalBottomSheet —
 * используется внутри [InfoBottomSheet] на вкладке «Правила».
 */
@Composable
internal fun RulesSheetContent(
    wardrobeReward: Int,
    roomReward: Int
) {
    val offlineDesc = stringResource(R.string.a11y_offline_status)
    Column {
        Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentColor.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiIcon(
                        emoji = "📴",
                        size = 22.dp,
                        contentDescription = offlineDesc
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.rules_offline),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            RuleBlock(
                title = stringResource(R.string.rules2_balance_title),
                text = stringResource(R.string.rules2_balance_text),
                defaultExpanded = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules2_quests_title),
                text = stringResource(R.string.rules2_quests_text),
                defaultExpanded = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules2_levels_title),
                text = stringResource(R.string.rules2_levels_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules2_streaks_title),
                text = stringResource(R.string.rules2_streaks_text)
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = stringResource(R.string.rules2_honest_gifts_title),
                text = stringResource(R.string.rules2_honest_gifts_text)
            )
    }
}

@Composable
private fun RuleBlock(
    title: String,
    text: String,
    defaultExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = text,
                        color = TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
