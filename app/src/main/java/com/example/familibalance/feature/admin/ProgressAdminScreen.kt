package com.example.familibalance.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*

@Composable
fun ProgressAdminContent(viewModel: MainViewModel) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
    if (child == null) {
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
    var showAchievementsDialog by remember { mutableStateOf(false) }
    var showDreamGoalDialog by remember { mutableStateOf(false) }
    var dreamTitle by remember { mutableStateOf(child.dreamTitle) }
    var dreamTargetVal by remember { mutableIntStateOf(child.dreamTarget) }

    // Тяжёлые вычисления — только при смене child, не на каждую рекомпозицию.
    val validHistory = remember(child) {
        child.historyItems.filterNot { it.title.startsWith("Квест на проверке:") || it.title.startsWith("Одобрен квест:") }
    }
    val completedQuests = remember(validHistory) { validHistory.count { it.isIncome } }
    val totalEarned = remember(validHistory) {
        validHistory.filter { it.isIncome }.sumOf {
            it.amountValue ?: it.amount.removeSuffix(" ₽").removePrefix("+").toIntOrNull() ?: 0
        }
    }
    val allAchievements: List<AchievementItemData> = remember(child) { getAchievements(child) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        item {
            OutlinedButton(
                onClick = {
                    dreamTitle = child.dreamTitle
                    dreamTargetVal = child.dreamTarget
                    showDreamGoalDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor)
            ) {
                Text(if (child.dreamTarget > 0) stringResource(R.string.admin_dream_edit) else stringResource(R.string.admin_dream_set), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBackground),
                border = BorderStroke(1.dp, GlassStroke)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.admin_stats_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.admin_stat_quests), color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$completedQuests", color = AccentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.admin_stat_earned), color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("+$totalEarned ${rewardCurrencySymbol()}", color = StatusGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.admin_stat_balance), color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${child.balance} ${rewardCurrencySymbol()}", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showAchievementsDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassBackground, contentColor = AccentColor)
            ) {
                Text(stringResource(R.string.admin_achievements_title, allAchievements.size), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBackground),
                border = BorderStroke(1.dp, GlassStroke)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (child.dreamTarget > 0) {
                        Text(stringResource(R.string.admin_dream_title, child.dreamTitle), color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyString(R.string.admin_dream_progress, child.balance, child.dreamTarget), color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        val dreamFraction = (child.balance.toFloat() / child.dreamTarget).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { dreamFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = AccentColor,
                            trackColor = GlassStroke,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Text(stringResource(R.string.admin_dream_empty_title), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.admin_dream_empty_hint), color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (showDreamGoalDialog) {
        AppDialog(
            onDismissRequest = { showDreamGoalDialog = false },
            title = { Text(if (child.dreamTarget > 0) stringResource(R.string.admin_dream_edit) else stringResource(R.string.admin_dream_set), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedField(value = dreamTitle, onValueChange = { dreamTitle = it }, label = { Text(stringResource(R.string.admin_goal_name_label), color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    AppOutlinedField(value = if (dreamTargetVal > 0) dreamTargetVal.toString() else "", onValueChange = { dreamTargetVal = it.toIntOrNull() ?: 0 }, label = { Text(currencyString(R.string.child_cashout_amount_label), color = TextPrimary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = editorFieldColors())
                }
            },
            confirmButton = {
                Button(
                    enabled = dreamTitle.isNotBlank() && dreamTargetVal > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = GlassStroke, disabledContentColor = TextSecondary),
                    onClick = { viewModel.setDreamGoal(child.id, dreamTitle.trim(), dreamTargetVal); showDreamGoalDialog = false }
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDreamGoalDialog = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) } }
        )
    }
    if (showAchievementsDialog) {
        AppDialog(
            onDismissRequest = { showAchievementsDialog = false },
            title = { Text(stringResource(R.string.admin_achievements_title, allAchievements.size), color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    allAchievements.forEach { ach ->
                        AchievementCard(title = systemTitle(ach.title), condition = ach.condition, description = systemDesc(ach.description), reward = ach.reward, progress = ach.progress, isLocked = ach.isLocked, isCompleted = ach.isCompleted, conditionKind = ach.conditionKind, conditionCount = ach.conditionCount, progressCurrent = ach.progressCurrent, progressTarget = ach.conditionCount)
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart),
                    onClick = { showAchievementsDialog = false }
                ) { Text(stringResource(R.string.update_btn_close), fontWeight = FontWeight.Bold) }
            }
        )
    }
}
