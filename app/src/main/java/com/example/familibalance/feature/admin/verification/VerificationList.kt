package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.data.model.*
import com.example.familibalance.util.*
import com.example.familibalance.ui.components.childDisplayName
import com.example.familibalance.viewmodel.actions.*


@Composable
fun VerificationAdminContent(
    onCashOutClick: () -> Unit,
    viewModel: MainViewModel
) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val diaryScanState by viewModel.diaryScanState.collectAsStateWithLifecycle()
    val isProcessingAction by viewModel.isProcessingAction.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.firstOrNull()
    if (child == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
    val hasPending = child.closetStatus == QuestStatus.PENDING ||
        child.roomStatus == QuestStatus.PENDING ||
        child.diaryStatus == QuestStatus.PENDING ||
        child.bonusQuests.any { it.status == QuestStatus.PENDING } ||
        child.customQuests.any { it.status == QuestStatus.PENDING } ||
        child.cashoutRequests.any { it.status == CashoutStatus.PENDING } ||
        child.honestAdmissionStatus == QuestStatus.PENDING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        





        // Подарки-купоны, отправленные ребёнком за монеты
        if (child.pendingParentGifts.isNotEmpty()) {
            item {
                ParentGiftsCard(
                    gifts = child.pendingParentGifts,
                    actionEnabled = !isProcessingAction,
                    onUseGift = { viewModel.useParentGift(child.id, it) }
                )
            }
        }

        // Секция 1: Ждут подтверждения
        if (hasPending) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(),
                    shape = GlassCardShape,
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.admin_pending_header, childDisplayName(child)),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.admin_pending_subtitle),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (child.closetStatus == QuestStatus.PENDING) {
            item {
                QuestVerificationCard(
                    title = stringResource(R.string.admin_quest_closet),
                    reward = "+${child.wardrobeReward} ${rewardCurrencySymbol()}",
                    description = child.closetComment.ifBlank { stringResource(R.string.admin_no_comment) },
                    photoUrl = child.closetPhotoUrl,
                    actionEnabled = !isProcessingAction,
                    onApprove = {
                        viewModel.adminApproveQuest(child.id, "closet")
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "closet", reason)
                    },
                    onRejectWithFine = { reason, withFine ->
                        viewModel.adminRejectQuest(child.id, "closet", reason, withCheatPenalty = withFine)
                    }
                )
            }
        }

        // Блок заявок на вывод (только ожидающие — отклонённые висят у ребёнка)
        if (child.cashoutRequests.any { it.status == CashoutStatus.PENDING }) {
            item {
                CashoutSection(
                    requests = child.cashoutRequests,
                    actionEnabled = !isProcessingAction,
                    onApprove = { viewModel.approveCashoutRequest(child.id, it) },
                    onReject = { requestId, reason ->
                        viewModel.rejectCashoutRequest(child.id, requestId, reason)
                    }
                )
            }
        }

        if (child.roomStatus == QuestStatus.PENDING) {
            item {
                QuestVerificationCard(
                    title = stringResource(R.string.admin_quest_room),
                    reward = "+${child.roomReward} ${rewardCurrencySymbol()}",
                    description = child.roomComment.ifBlank { stringResource(R.string.admin_no_comment) },
                    photoUrl = child.roomPhotoUrl,
                    actionEnabled = !isProcessingAction,
                    onApprove = {
                        viewModel.adminApproveQuest(child.id, "room")
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "room", reason)
                    },
                    onRejectWithFine = { reason, withFine ->
                        viewModel.adminRejectQuest(child.id, "room", reason, withCheatPenalty = withFine)
                    }
                )
            }
        }

        if (child.diaryStatus == QuestStatus.PENDING) {
            item {
                DiaryVerificationCard(
                    title = stringResource(R.string.admin_quest_diary),
                    childId = child.id,
                    comment = child.diaryComment,
                    photoUrl = child.diaryPhotoUrl,
                    scanState = diaryScanState,
                    actionEnabled = !isProcessingAction,
                    onScanRequested = { viewModel.scanDiaryPhoto(child.id, child.diaryPhotoUrl ?: "") },
                    onApprove = { count5, count4, count3, count2 ->
                        viewModel.applyDiaryGrades(child.id, count5, count4, count3, count2)
                        viewModel.resetDiaryScanState()
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "diary", reason)
                        viewModel.resetDiaryScanState()
                    },
                    onRejectWithFine = { reason, withFine ->
                        viewModel.adminRejectQuest(child.id, "diary", reason, withCheatPenalty = withFine)
                        viewModel.resetDiaryScanState()
                    }
                )
            }
        }

        items(child.bonusQuests.filter { it.status == QuestStatus.PENDING }) { bQuest ->
            QuestVerificationCard(
                title = stringResource(R.string.admin_bonus_prefix, bQuest.title),
                reward = "+${bQuest.reward} ${rewardCurrencySymbol()}",
                description = bQuest.comment.ifBlank { stringResource(R.string.admin_no_comment) },
                photoUrl = bQuest.photoUrl,
                actionEnabled = !isProcessingAction,
                onApprove = {
                    viewModel.adminApproveBonusQuest(child.id, bQuest.id)
                },
                onReject = { reason ->
                    viewModel.adminRejectBonusQuest(child.id, bQuest.id, reason)
                },
                onRejectWithFine = { reason, withFine ->
                    viewModel.adminRejectBonusQuest(child.id, bQuest.id, reason, withCheatPenalty = withFine)
                }
            )
        }

        items(child.customQuests.filter { it.status == QuestStatus.PENDING }, key = { it.id }, contentType = { "custom_quest" }) { cQuest ->
            QuestVerificationCard(
                title = stringResource(R.string.admin_quest_prefix, cQuest.title),
                reward = "+${cQuest.reward} ${rewardCurrencySymbol()}",
                description = cQuest.comment.ifBlank { stringResource(R.string.admin_no_comment) },
                photoUrl = cQuest.photoUrl,
                actionEnabled = !isProcessingAction,
                onApprove = {
                    viewModel.adminApproveCustomQuest(child.id, cQuest.id)
                },
                onReject = { reason ->
                    viewModel.adminRejectCustomQuest(child.id, cQuest.id, reason)
                },
                onRejectWithFine = { reason, withFine ->
                    viewModel.adminRejectCustomQuest(child.id, cQuest.id, reason, withCheatPenalty = withFine)
                }
            )
        }

        if (child.closetStatus != QuestStatus.PENDING &&
            child.roomStatus != QuestStatus.PENDING &&
            child.diaryStatus != QuestStatus.PENDING &&
            child.bonusQuests.none { it.status == QuestStatus.PENDING } &&
            child.customQuests.none { it.status == QuestStatus.PENDING } &&
            child.honestAdmissionStatus != QuestStatus.PENDING &&
            child.cashoutRequests.none { it.status == CashoutStatus.PENDING }) {
            item {
                Text(
                    text = stringResource(R.string.admin_no_pending, childDisplayName(child)),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        // Честное признание на проверке
        if (child.honestAdmissionStatus == QuestStatus.PENDING) {
            item {
                HonestAdmissionCard(
                    childName = childDisplayName(child),
                    admissionText = child.honestAdmissionText,
                    actionEnabled = !isProcessingAction,
                    onApprove = { viewModel.adminApproveHonestAdmission(child.id) },
                    onReject = { viewModel.adminRejectHonestAdmission(child.id) }
                )
            }
        }

        // Быстрые действия родителя
        item {
            QuickActionsCard(
                childName = childDisplayName(child),
                actionEnabled = !isProcessingAction,
                onDeposit = { amount, desc -> viewModel.adminDeposit(child.id, amount, desc) },
                onWithdraw = { amount, desc -> viewModel.adminWithdraw(child.id, amount, desc) },
                onFine = { amount, desc -> viewModel.applyFine(child.id, amount, desc) }
            )
        }

        // Кнопка выдачи наличных
        item {
            OutlinedButton(
                onClick = onCashOutClick,
                enabled = !isProcessingAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounceClick(),
                shape = AccentButtonShape,
                border = BorderStroke(1.dp, StatusGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen)
            ) {
                Text(currencyString(R.string.admin_cashout_button, child.balance), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
