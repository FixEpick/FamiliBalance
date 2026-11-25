package com.example.familibalance.feature.child.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.data.model.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*


/**
 * Контент «Тарифы и награды» без обёртки ModalBottomSheet —
 * используется внутри [InfoBottomSheet] на одноимённой вкладке.
 */
@Composable
internal fun EconomySheetContent(
    child: ChildProfileState
) {
    val achievements = remember(child) { getAchievements(child) }
    val claimedTitles = remember(child) {
        child.achievements.filter { it.isClaimed }.map { it.title }.toSet()
    }
    val curSym = RewardCurrency.symbolOf(child.rewardCurrency)
    val branchTitles = mapOf(
        AchievementBranch.DIARY to stringResource(R.string.eco_branch_diary),
        AchievementBranch.WARDROBE to stringResource(R.string.eco_branch_wardrobe),
        AchievementBranch.ROOM to stringResource(R.string.eco_branch_room)
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            // ===== Секция 1: Заработок (базовые ставки) =====
            item {
                EconomySectionTitle(stringResource(R.string.economy2_earn_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_wardrobe_photo), "+${child.wardrobeReward} $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_room_photo), "+${child.roomReward} $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade5), "+50 $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade4), "+30 $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_xp), stringResource(R.string.eco_xp_value))
            }

            // ===== Секция 2: Бонусы и множители =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy2_bonuses_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_streak), currencyString(R.string.eco_streak_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_perfect_day), currencyString(R.string.eco_perfect_day_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_chest), currencyString(R.string.eco_chest_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_level_up), stringResource(R.string.eco_level_up_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_reward_mult), stringResource(R.string.eco_reward_mult_value))
            }

            // ===== Секция 3: На что потратить =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy2_spend_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_time30), "${ScreenTimeTariffs.PACKAGES[0].cost} $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time60), "${ScreenTimeTariffs.PACKAGES[1].cost} $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time90), "${ScreenTimeTariffs.PACKAGES[2].cost} $curSym")
            }
            item {
                EconomyRow(stringResource(R.string.eco_parent_gifts), currencyString(R.string.eco_parent_gifts_value))
            }

            // ===== Секция 4: Штрафы и честное признание =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy2_fines_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade3), "−20 $curSym", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade2), "−50 $curSym", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_mischief), "−30 $curSym", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_lie), "−100 $curSym", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_honest), stringResource(R.string.eco_honest_discount))
            }
            item {
                EconomyRow(stringResource(R.string.eco_parent_fine), stringResource(R.string.eco_set_by_dad), PenaltyRed)
            }

            // ===== Секция 5: Достижения и магазин =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy2_shop_section))
            }
            AchievementBranch.entries.forEach { branch ->
                val branchItems = child.achievements
                    .filter { it.isVisible && it.branch == branch }
                    .sortedBy { it.targetCount }
                if (branchItems.isNotEmpty()) {
                    item {
                        Text(
                            text = branchTitles[branch] ?: branch.name,
                            color = LocalAppPalette.current.primaryNeon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    branchItems.forEach { config ->
                        val display = achievements.firstOrNull { it.id == config.id }
                        val isLocked = display?.isLocked == true
                        item {
                            EconomyRow(
                                title = systemTitle(config.title),
                                value = if (isLocked) "??? $curSym" else "+${config.reward} $curSym",
                                valueColor = if (isLocked) TextSecondary else StatusGreen
                            )
                        }
                    }
                }
            }

            // ===== Секция 5: Темы оформления =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_themes_section))
            }
            AllAppThemes.forEach { theme ->
                val unlocked = theme.isThemeUnlocked(child.unlockedThemes, claimedTitles)
                item(key = "theme_${theme.id}") {
                    ThemeEconomyRow(theme = theme, unlocked = unlocked)
                }
            }

            // ===== Секция 6: Аватары =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_avatars_section))
            }
            CloudAvatarType.entries.forEach { type ->
                val unlocked = type.isAvatarUnlocked(child.unlockedAvatars, claimedTitles)
                item(key = "avatar_${type.name}") {
                    AvatarEconomyRow(type = type, unlocked = unlocked)
                }
            }

            // ===== Секция 5 (продолжение): Рамки аватара =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_frames_section))
            }
            AvatarFrameType.entries.forEach { type ->
                val unlocked = type.isFrameUnlocked(child.unlockedFrames, child.level)
                item(key = "frame_${type.name}") {
                    FrameEconomyRow(type = type, unlocked = unlocked)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
    }
}

@Composable
private fun EconomySectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EconomyRow(
    title: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextMuted, fontSize = 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ThemeEconomyRow(
    theme: AppThemeConfig,
    unlocked: Boolean
) {
    val title = if (theme.titleRes != 0) stringResource(theme.titleRes) else theme.title
    val condition = when (val rule = theme.unlockRule) {
        is ThemeUnlockRule.Free -> stringResource(R.string.theme_free_label)
        is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
        is ThemeUnlockRule.Achievement ->
            stringResource(R.string.theme_ach_prefix, stringResource(rule.titleRes))
        is ThemeUnlockRule.Level -> stringResource(R.string.frame_level_req, rule.minLevel)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextMuted, fontSize = 14.sp)
        Text(
            text = buildAnnotatedString {
                append(condition)
                if (unlocked) {
                    append("  ")
                    withStyle(SpanStyle(color = StatusGreen)) {
                        append(stringResource(R.string.theme_unlocked_label))
                    }
                }
            },
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FrameEconomyRow(
    type: AvatarFrameType,
    unlocked: Boolean
) {
    val title = stringResource(type.titleRes)
    val condition = when (val rule = type.unlockRule) {
        is ThemeUnlockRule.Free -> stringResource(R.string.theme_free_label)
        is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
        is ThemeUnlockRule.Level -> stringResource(R.string.frame_level_req, rule.minLevel)
        is ThemeUnlockRule.Achievement ->
            stringResource(R.string.theme_ach_prefix, stringResource(rule.titleRes))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextMuted, fontSize = 14.sp)
        Text(
            text = buildAnnotatedString {
                append(condition)
                if (unlocked) {
                    append("  ")
                    withStyle(SpanStyle(color = StatusGreen)) {
                        append(stringResource(R.string.theme_unlocked_label))
                    }
                }
            },
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AvatarEconomyRow(
    type: CloudAvatarType,
    unlocked: Boolean
) {
    val title = stringResource(type.titleRes)
    val condition = when (val rule = type.unlockRule) {
        is ThemeUnlockRule.Free -> stringResource(R.string.theme_free_label)
        is ThemeUnlockRule.Paid -> currencyString(R.string.theme_price, rule.price)
        is ThemeUnlockRule.Achievement ->
            stringResource(R.string.theme_ach_prefix, stringResource(rule.titleRes))
        is ThemeUnlockRule.Level -> stringResource(R.string.frame_level_req, rule.minLevel)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextMuted, fontSize = 14.sp)
        Text(
            text = buildAnnotatedString {
                append(condition)
                if (unlocked) {
                    append("  ")
                    withStyle(SpanStyle(color = StatusGreen)) {
                        append(stringResource(R.string.theme_unlocked_label))
                    }
                }
            },
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
