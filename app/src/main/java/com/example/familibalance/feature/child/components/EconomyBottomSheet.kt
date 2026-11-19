package com.example.familibalance.feature.child.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EconomyBottomSheet(
    child: ChildProfileState,
    onDismissRequest: () -> Unit
) {
    val achievements = remember(child) { getAchievements(child) }
    val branchTitles = mapOf(
        AchievementBranch.DIARY to stringResource(R.string.eco_branch_diary),
        AchievementBranch.WARDROBE to stringResource(R.string.eco_branch_wardrobe),
        AchievementBranch.ROOM to stringResource(R.string.eco_branch_room)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = GlassStroke)
        }
    ) {
        AppLocaleContent {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.economy_title),
                    color = AccentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // ===== Секция 1: Доходы =====
            item {
                EconomySectionTitle(stringResource(R.string.economy_earn_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_wardrobe_photo), "+${child.wardrobeReward} ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_room_photo), "+${child.roomReward} ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade5), "+50 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade4), "+30 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_streak), stringResource(R.string.eco_streak_value))
            }
            item {
                EconomyRow(stringResource(R.string.eco_dream), stringResource(R.string.eco_dream_value))
            }

            // ===== Секция 2: Расходы =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_spend_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_time30), "100 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time60), "200 ₽")
            }
            item {
                EconomyRow(stringResource(R.string.eco_time90), "300 ₽")
            }

            // ===== Секция 3: Штрафы =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_fines_section))
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade3), "−20 ₽", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_diary_grade2), "−50 ₽", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_mischief), "−30 ₽", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_lie), "−100 ₽", PenaltyRed)
            }
            item {
                EconomyRow(stringResource(R.string.eco_honest), stringResource(R.string.eco_honest_discount))
            }
            item {
                EconomyRow(stringResource(R.string.eco_parent_fine), stringResource(R.string.eco_set_by_dad), PenaltyRed)
            }

            // ===== Секция 4: Достижения =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                EconomySectionTitle(stringResource(R.string.economy_achievements_section))
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
                                title = config.title,
                                value = if (isLocked) "??? ₽" else "+${config.reward} ₽",
                                valueColor = if (isLocked) TextSecondary else StatusGreen
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = SpaceBgStart
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.rules_understood), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
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
