package com.example.familibalance.feature.admin.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.theme.*
import com.example.familibalance.util.currencyString
import com.example.familibalance.util.rewardCurrencySymbol
import com.example.familibalance.viewmodel.actions.earnedThisWeek
import com.example.familibalance.viewmodel.actions.resetEconomySettings
import com.example.familibalance.viewmodel.actions.updateEconomySettings

/**
 * Карточка «Настройки экономики» в родительской админке:
 * базовые награды (шкаф/комната/дневник 5), множители,
 * шанс бонусного сундука и недельный лимит заработка.
 * Изменения применяются к [ChildProfileState] и синхронизируются
 * с ребёнком через state_json.
 */
@Composable
fun EconomySettingsCard(
    child: ChildProfileState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currency = rewardCurrencySymbol()
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun update(
        closet: Int = child.baseClosetReward,
        room: Int = child.baseRoomReward,
        diary5: Int = child.baseDiaryFiveReward,
        multipliers: Boolean = child.multipliersEnabled,
        chestChance: Int = child.bonusChestChancePercent,
        weeklyLimit: Int = child.weeklyEarnLimit
    ) {
        viewModel.updateEconomySettings(
            userId = child.id,
            baseClosetReward = closet,
            baseRoomReward = room,
            baseDiaryFiveReward = diary5,
            multipliersEnabled = multipliers,
            bonusChestChancePercent = chestChance,
            weeklyEarnLimit = weeklyLimit
        )
    }

    Surface(
        shape = GlassCardShape,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.admin_economy_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
            Spacer(modifier = Modifier.height(4.dp))

            // Быстрые пресеты экономики.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EconomyPreset.entries.forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            update(
                                closet = preset.closet,
                                room = preset.room,
                                diary5 = preset.diary5,
                                multipliers = preset.multipliers,
                                chestChance = preset.chestChance,
                                weeklyLimit = preset.weeklyLimit
                            )
                        },
                        border = BorderStroke(1.dp, GlassStroke),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(preset.labelRes),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            EconomyRewardStepper(
                label = stringResource(R.string.admin_economy_reward_closet),
                value = child.baseClosetReward,
                currency = currency,
                onChange = { update(closet = it) }
            )
            EconomyRewardStepper(
                label = stringResource(R.string.admin_economy_reward_room),
                value = child.baseRoomReward,
                currency = currency,
                onChange = { update(room = it) }
            )
            EconomyRewardStepper(
                label = stringResource(R.string.admin_economy_reward_diary5),
                value = child.baseDiaryFiveReward,
                currency = currency,
                onChange = { update(diary5 = it) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.admin_economy_multipliers),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = child.multipliersEnabled,
                    onCheckedChange = { update(multipliers = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentColor,
                        checkedTrackColor = AccentColor.copy(alpha = 0.35f),
                        checkedBorderColor = AccentColor,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = GlassBackground.copy(alpha = 0.5f),
                        uncheckedBorderColor = GlassStroke
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_economy_chest_chance),
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 15, 20).forEach { chance ->
                    val selected = child.bonusChestChancePercent == chance
                    OutlinedButton(
                        onClick = { update(chestChance = chance) },
                        border = BorderStroke(
                            1.dp,
                            if (selected) AccentColor else GlassStroke
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) AccentColor.copy(alpha = 0.18f) else Color.Transparent,
                            contentColor = if (selected) AccentColor else TextSecondary
                        )
                    ) {
                        Text(
                            text = currencyString(R.string.admin_economy_chance_pct, chance),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_economy_weekly_limit),
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (child.weeklyEarnLimit <= 0) {
                    Text(
                        text = stringResource(R.string.admin_economy_no_limit),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { update(weeklyLimit = 100) }) {
                        Text(
                            text = stringResource(R.string.admin_economy_set_limit),
                            color = AccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    IconButton(
                        onClick = { update(weeklyLimit = (child.weeklyEarnLimit - 50).coerceAtLeast(50)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("−", color = AccentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${child.weeklyEarnLimit} $currency",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick = { update(weeklyLimit = (child.weeklyEarnLimit + 50).coerceAtMost(5000)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = AccentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { update(weeklyLimit = 0) }) {
                        Text(
                            text = stringResource(R.string.admin_economy_no_limit),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            // Прогресс заработка за текущую неделю (пн–вс).
            val earnedThisWeek = child.earnedThisWeek()
            Text(
                text = if (child.weeklyEarnLimit > 0) {
                    stringResource(
                        R.string.admin_economy_weekly_earned_limit,
                        earnedThisWeek,
                        child.weeklyEarnLimit,
                        currency
                    )
                } else {
                    stringResource(R.string.admin_economy_weekly_earned, earnedThisWeek, currency)
                },
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { viewModel.resetEconomySettings(child.id) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.admin_economy_reset_defaults),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            }
            }
        }
    }
}

/** Готовые пресеты экономики: мягкий / сбалансированный / строгий. */
private enum class EconomyPreset(
    val closet: Int,
    val room: Int,
    val diary5: Int,
    val multipliers: Boolean,
    val chestChance: Int,
    val weeklyLimit: Int,
    val labelRes: Int
) {
    SOFT(70, 70, 70, true, 20, 0, R.string.admin_economy_preset_soft),
    BALANCED(50, 50, 50, true, 15, 0, R.string.admin_economy_preset_balanced),
    STRICT(30, 30, 40, false, 10, 600, R.string.admin_economy_preset_strict)
}

/** Строка «название — значение» со степпером −/+. */
@Composable
private fun EconomyRewardStepper(
    label: String,
    value: Int,
    currency: String,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onChange((value - 5).coerceAtLeast(0)) },
            modifier = Modifier.size(32.dp)
        ) {
            Text("−", color = AccentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "$value $currency",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(
            onClick = { onChange((value + 5).coerceAtMost(500)) },
            modifier = Modifier.size(32.dp)
        ) {
            Text("+", color = AccentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
