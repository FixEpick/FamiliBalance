package com.example.familibalance.feature.child.components

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.util.*
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.viewmodel.actions.*


/** Элемент списка квестов на проверке внутри BottomSheet. */
private data class PendingQuestItem(
    val key: String,
    val title: String,
    val rewardText: String,
    val photoUrl: String?,
    val comment: String?
)

/** Собирает все квесты ребёнка в статусе PENDING из основных, бонусных и кастомных списков. */
private fun collectPendingQuests(child: ChildProfileState, diaryRewardText: String): List<PendingQuestItem> {
    val curSym = RewardCurrency.symbolOf(child.rewardCurrency)
    val items = mutableListOf<PendingQuestItem>()

    if (child.closetStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("closet", child.wardrobeTitle, "+${child.wardrobeReward} $curSym", child.closetPhotoUrl, null)
    }
    if (child.roomStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("room", child.roomTitle, "+${child.roomReward} $curSym", child.roomPhotoUrl, null)
    }
    if (child.diaryStatus == QuestStatus.PENDING) {
        items += PendingQuestItem("diary", child.diaryTitle, diaryRewardText, child.diaryPhotoUrl, null)
    }
    child.bonusQuests.filter { it.status == QuestStatus.PENDING }.forEach { q ->
        items += PendingQuestItem("bonus:${q.id}", q.title, "+${q.reward} $curSym", q.photoUrl, q.comment.ifBlank { null })
    }
    child.customQuests.filter { it.isVisible && it.status == QuestStatus.PENDING }.forEach { q ->
        items += PendingQuestItem("custom:${q.id}", q.title, "+${q.reward} $curSym", q.photoUrl, q.description.ifBlank { null })
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PendingQuestsBottomSheet(
    child: ChildProfileState,
    onDismiss: () -> Unit,
    onEditQuest: (String) -> Unit,
    viewModel: MainViewModel
) {
    // Награда дневника — строковый ресурс (локализуется), поэтому
    // вычисляем её до remember и передаём параметром.
    val diaryRewardText = currencyString(R.string.tasks_diary_reward)
    val pendingItems = remember(child, diaryRewardText) { collectPendingQuests(child, diaryRewardText) }
    val editDesc = stringResource(R.string.a11y_edit_button)
    val deleteDesc = stringResource(R.string.a11y_delete_button)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpaceBgStart,
        contentColor = TextPrimary
    ) {
        AppLocaleContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.child_pending_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.child_pending_subtitle),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (pendingItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.child_pending_empty),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                pendingItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassBackground),
                        border = BorderStroke(1.dp, GlassStroke)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Фото квеста
                            if (!item.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = systemTitle(item.title),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.rewardText,
                                    color = AccentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!item.comment.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "💬 ${item.comment}",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }

                            // Кнопка «Изменить» — открыть QuestDeliveryScreen для пересъёмки
                            IconButton(
                                onClick = {
                                    onEditQuest(item.key)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = editDesc,
                                    tint = AccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Кнопка «Удалить» — отменить отправку на проверку
                            IconButton(
                                onClick = { viewModel.cancelPendingQuest(child.id, item.key) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = deleteDesc,
                                    tint = PenaltyRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = AccentOnColor
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
