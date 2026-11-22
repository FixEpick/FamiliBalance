package com.example.familibalance.feature.child.screens

import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.feature.child.QuestDeliveryScreen
import com.example.familibalance.feature.child.QuestDeliveryTarget
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*


@Composable
fun AchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(1) }
    val tabs = listOf(
        stringResource(R.string.ach_tab_all),
        stringResource(R.string.ach_tab_in_progress),
        stringResource(R.string.ach_tab_hall)
    )

    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val currentChild = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
    if (currentChild == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.admin_no_data),
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }
    val context = LocalContext.current
    val giftDesc = stringResource(R.string.a11y_gift_icon)
    val giftToastText = stringResource(R.string.ach_gift_toast)
    val allAchievements = remember(currentChild) { getAchievements(currentChild) }

    val displayList = remember(selectedTab, allAchievements) {
        when (selectedTab) {
            1 -> allAchievements.filter { !it.isCompleted && !it.isLocked }
            2 -> allAchievements.filter { it.isCompleted }
            else -> allAchievements
        }
    }

    val completedCount = remember(allAchievements) {
        allAchievements.count { it.isCompleted }
    }

    val haptic = LocalHapticFeedback.current
    var balanceBadgePosition by remember { mutableStateOf(Offset.Zero) }
    var activeCoinFlights by remember { mutableStateOf<List<CoinFlightData>>(emptyList()) }
    var confettiTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(confettiTrigger) {
        if (confettiTrigger > 0) {
            delay(2500)
            confettiTrigger = 0
        }
    }

    val triggerCoinFlight: (Offset, () -> Unit) -> Unit = { buttonOffset, onComplete ->
        confettiTrigger++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        RewardSoundPlayer.playCoin()

        lateinit var flight: CoinFlightData
        flight = CoinFlightData(
            startX = if (buttonOffset != Offset.Zero) buttonOffset.x else 200f,
            startY = if (buttonOffset != Offset.Zero) buttonOffset.y else 500f,
            onFinished = {
                onComplete()
                activeCoinFlights = activeCoinFlights.filter { it != flight }
            }
        )
        activeCoinFlights = activeCoinFlights + flight
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Закреплённый мини-баланс — не скроллится
        MiniBalanceBar(
            balance = currentChild.balance,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                balanceBadgePosition = Offset(pos.x + coordinates.size.width - 80f, pos.y + coordinates.size.height / 2f)
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.ach_title),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pluralStringResource(R.plurals.ach_ranks, allAchievements.size, completedCount, allAchievements.size),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                val isAllCompleted = completedCount == allAchievements.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rankFraction = if (allAchievements.isEmpty()) 0f
                        else (completedCount.toFloat() / allAchievements.size).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardBgDark.copy(alpha = 0.85f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(rankFraction)
                                .background(AccentColor, RoundedCornerShape(4.dp))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isAllCompleted) {
                                    Modifier
                                        .border(1.5.dp, GoldBright, CircleShape)
                                        .background(GoldBright.copy(alpha = 0.25f), CircleShape)
                                } else {
                                    Modifier.alpha(0.3f)
                                }
                            )
                            .clickable {
                                Toast.makeText(
                                    context,
                                    giftToastText,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🎁",
                            fontSize = 20.sp,
                            modifier = Modifier.semantics {
                                contentDescription = giftDesc
                            }
                        )
                    }
                }
            }
        }

        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBgStart)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tabName ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tabName, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FilterSelectedBg.copy(alpha = 0.92f),
                            selectedLabelColor = AccentColor,
                            containerColor = FilterBg.copy(alpha = 0.85f),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedTab == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = AccentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        items(displayList, contentType = { "achievement" }) { item ->
            AchievementCard(
                title = systemTitle(item.title),
                condition = item.condition,
                description = systemDesc(item.description),
                reward = item.reward,
                progress = item.progress,
                isLocked = item.isLocked,
                isCompleted = item.isCompleted,
                isReadyToClaim = item.isReadyToClaim,
                conditionKind = item.conditionKind,
                conditionCount = item.conditionCount,
                progressCurrent = item.progressCurrent,
                progressTarget = item.conditionCount,
                onClaimClick = {
                    if (item.id.isNotBlank()) {
                        triggerCoinFlight(Offset.Zero) {
                            viewModel.claimAchievementReward(currentChild.id, item.id)
                        }
                    }
                }
            )
        }
        }
    }

        CoinFlightOverlay(
            flights = activeCoinFlights,
            badgePosition = balanceBadgePosition,
            onFlightFinished = { flight ->
                flight.onFinished()
            }
        )
        if (confettiTrigger > 0) {
            ConfettiOverlay(trigger = confettiTrigger, modifier = Modifier.fillMaxSize())
        }
    }
}
