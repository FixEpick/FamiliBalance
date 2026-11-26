package com.example.familibalance.feature.admin

import com.example.familibalance.AppLocaleContent
import com.example.familibalance.MainViewModel
import com.example.familibalance.R

import com.example.familibalance.data.model.*
import com.example.familibalance.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.theme.*
import com.example.familibalance.viewmodel.actions.*

@Composable
fun AdminAchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
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

    var editingAchievement by remember { mutableStateOf<AchievementConfig?>(null) }
    var deletingAchievement by remember { mutableStateOf<AchievementConfig?>(null) }

    val editDesc = stringResource(R.string.a11y_edit_button)
    val deleteDesc = stringResource(R.string.a11y_delete_button)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.achadm_create_title),
                color = AccentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

    item {
            Text(
                text = stringResource(R.string.achadm_all, child.achievements.size),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(child.achievements, key = { it.id }, contentType = { "achievement" }) { achievement ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            shape = GlassCardShape,
            colors = CardDefaults.cardColors(containerColor = GlassBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = systemTitle(achievement.title),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val branchLabel = when (achievement.branch) {
                        AchievementBranch.DIARY -> stringResource(R.string.achadm_branch_diary)
                        AchievementBranch.WARDROBE -> stringResource(R.string.achadm_branch_wardrobe)
                        AchievementBranch.ROOM -> stringResource(R.string.achadm_branch_room)
                    }
                    Text(
                        text = currencyString(R.string.achadm_meta, branchLabel, achievement.targetCount, achievement.reward),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.width(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = achievement.isVisible,
                        onCheckedChange = { viewModel.toggleAchievementVisibility(child.id, achievement.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentColor,
                            checkedTrackColor = GlassBackground,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = GlassBackground.copy(alpha = 0.5f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.width(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { editingAchievement = achievement },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "✏️",
                            fontSize = 16.sp,
                            modifier = Modifier.semantics { contentDescription = editDesc }
                        )
                    }
                    TextButton(
                        onClick = { deletingAchievement = achievement },
                        colors = ButtonDefaults.textButtonColors(contentColor = PenaltyRed),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "🗑️",
                            fontSize = 16.sp,
                            modifier = Modifier.semantics { contentDescription = deleteDesc }
                        )
                    }
                }
            }
        }
    }

    }

    editingAchievement?.let { ach ->
        AchievementEditDialog(
            achievement = ach,
            onSave = { title, description, target, reward, branch ->
                viewModel.updateAchievement(child.id, ach.id, title, description, target, reward, branch)
                editingAchievement = null
            },
            onDismiss = { editingAchievement = null }
        )
    }

    if (deletingAchievement != null) {
        AchievementDeleteDialog(
            onConfirm = {
                deletingAchievement?.let { viewModel.deleteAchievement(child.id, it.id) }
                deletingAchievement = null
            },
            onDismiss = { deletingAchievement = null }
        )
    }

    }
