package com.example.familibalance.feature.admin.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.ui.theme.*
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
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
    val lieFineReason = stringResource(R.string.admin_fine_reason_lie)
    val hasPending = child.closetStatus == QuestStatus.PENDING ||
        child.roomStatus == QuestStatus.PENDING ||
        child.diaryStatus == QuestStatus.PENDING ||
        child.bonusQuests.any { it.status == QuestStatus.PENDING } ||
        child.customQuests.any { it.status == QuestStatus.PENDING } ||
        child.cashoutRequests.isNotEmpty() ||
        child.honestAdmissionStatus == QuestStatus.PENDING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        





        // Секция 1: Ждут подтверждения
        if (hasPending) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
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
                    reward = "+50 ₽",
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
                        viewModel.adminRejectQuest(child.id, "closet", reason)
                        if (withFine) viewModel.applyFine(child.id, 100, lieFineReason)
                    }
                )
            }
        }

        // Блок заявок на вывод
        if (child.cashoutRequests.isNotEmpty()) {
            item {
                CashoutSection(
                    requests = child.cashoutRequests,
                    actionEnabled = !isProcessingAction,
                    onApprove = { viewModel.approveCashoutRequest(child.id, it) },
                    onReject = { viewModel.rejectCashoutRequest(child.id, it) }
                )
            }
        }

        if (child.roomStatus == QuestStatus.PENDING) {
            item {
                QuestVerificationCard(
                    title = stringResource(R.string.admin_quest_room),
                    reward = "+50 ₽",
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
                        viewModel.adminRejectQuest(child.id, "room", reason)
                        if (withFine) viewModel.applyFine(child.id, 100, lieFineReason)
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
                        viewModel.adminRejectQuest(child.id, "diary", reason)
                        viewModel.resetDiaryScanState()
                        if (withFine) viewModel.applyFine(child.id, 100, lieFineReason)
                    }
                )
            }
        }

        items(child.bonusQuests.filter { it.status == QuestStatus.PENDING }) { bQuest ->
            QuestVerificationCard(
                title = stringResource(R.string.admin_bonus_prefix, bQuest.title),
                reward = "+${bQuest.reward} ₽",
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
                    viewModel.adminRejectBonusQuest(child.id, bQuest.id, reason)
                    if (withFine) viewModel.applyFine(child.id, 100, lieFineReason)
                }
            )
        }

        items(child.customQuests.filter { it.status == QuestStatus.PENDING }, key = { it.id }, contentType = { "custom_quest" }) { cQuest ->
            QuestVerificationCard(
                title = stringResource(R.string.admin_quest_prefix, cQuest.title),
                reward = "+${cQuest.reward} ₽",
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
                    viewModel.adminRejectCustomQuest(child.id, cQuest.id, reason)
                    if (withFine) viewModel.applyFine(child.id, 100, lieFineReason)
                }
            )
        }

        if (child.closetStatus != QuestStatus.PENDING &&
            child.roomStatus != QuestStatus.PENDING &&
            child.diaryStatus != QuestStatus.PENDING &&
            child.bonusQuests.none { it.status == QuestStatus.PENDING } &&
            child.customQuests.none { it.status == QuestStatus.PENDING } &&
            child.honestAdmissionStatus != QuestStatus.PENDING &&
            child.cashoutRequests.isEmpty()) {
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground),
                    border = BorderStroke(1.dp, StatusYellow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.admin_honest_title, childDisplayName(child)), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.admin_honest_quote, child.honestAdmissionText.ifBlank { stringResource(R.string.admin_no_comment_text) }),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    viewModel.adminApproveHonestAdmission(child.id)
                                },
                                enabled = !isProcessingAction,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.admin_approve_honest), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.adminRejectHonestAdmission(child.id)
                                },
                                border = BorderStroke(1.dp, PenaltyRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.admin_reject), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Быстрые действия родителя
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.admin_quick_actions, childDisplayName(child)),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.admin_quick_actions_subtitle),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    var depositInput by remember { mutableStateOf("") }
                    var depositDesc by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = depositInput,
                            onValueChange = { depositInput = it },
                            placeholder = { Text(stringResource(R.string.admin_amount_hint), color = TextSecondary.copy(alpha = 0.6f)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SpaceBgStart,
                                unfocusedContainerColor = SpaceBgStart,
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = GlassStroke,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val amount = depositInput.toIntOrNull()
                                if (amount != null && amount > 0) {
                                    viewModel.adminDeposit(child.id, amount, depositDesc)
                                    depositInput = ""
                                    depositDesc = ""
                                }
                            },
                            enabled = !isProcessingAction,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(stringResource(R.string.admin_add), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(
                        value = depositDesc,
                        onValueChange = { depositDesc = it },
                        label = { Text(stringResource(R.string.admin_desc_label), color = TextPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SpaceBgStart,
                            unfocusedContainerColor = SpaceBgStart,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = GlassStroke,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    var withdrawInput by remember { mutableStateOf("") }
                    var withdrawDesc by remember { mutableStateOf("") }
                    var isFine by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = withdrawInput,
                            onValueChange = { withdrawInput = it },
                            placeholder = { Text(stringResource(R.string.admin_amount_hint), color = TextSecondary.copy(alpha = 0.6f)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SpaceBgStart,
                                unfocusedContainerColor = SpaceBgStart,
                                focusedBorderColor = PenaltyRed,
                                unfocusedBorderColor = GlassStroke,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                val amount = withdrawInput.toIntOrNull()
                                if (amount != null && amount > 0) {
                                    if (isFine) {
                                        viewModel.applyFine(child.id, amount, withdrawDesc)
                                    } else {
                                        viewModel.adminWithdraw(child.id, amount, withdrawDesc)
                                    }
                                    withdrawInput = ""
                                    withdrawDesc = ""
                                    isFine = false
                                }
                            },
                            enabled = !isProcessingAction,
                            colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(stringResource(R.string.admin_withdraw), fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isFine,
                            onCheckedChange = { isFine = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PenaltyRed,
                                checkedTrackColor = PenaltyRed.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.admin_is_fine),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedTextField(
                        value = withdrawDesc,
                        onValueChange = { withdrawDesc = it },
                        label = { Text(stringResource(R.string.admin_desc_label), color = TextPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SpaceBgStart,
                            unfocusedContainerColor = SpaceBgStart,
                            focusedBorderColor = PenaltyRed,
                            unfocusedBorderColor = GlassStroke,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.admin_fine_presets_label),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val finePresetReasons = listOf(
                        stringResource(R.string.admin_fine_reason_grade3),
                        stringResource(R.string.admin_fine_reason_grade2),
                        stringResource(R.string.admin_fine_reason_mischief),
                        stringResource(R.string.admin_fine_reason_lie)
                    )
                    val finePresets = listOf(
                        Triple(R.string.admin_fine_preset_grade3, 20, finePresetReasons[0]),
                        Triple(R.string.admin_fine_preset_grade2, 50, finePresetReasons[1]),
                        Triple(R.string.admin_fine_preset_mischief, 30, finePresetReasons[2]),
                        Triple(R.string.admin_fine_preset_lie, 100, finePresetReasons[3])
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(finePresets) { (labelRes, amount, reason) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    isFine = true
                                    withdrawInput = amount.toString()
                                    withdrawDesc = reason
                                },
                                label = { Text(stringResource(labelRes), fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = PenaltyRed.copy(alpha = 0.12f),
                                    labelColor = PenaltyRed
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = PenaltyRed.copy(alpha = 0.4f),
                                    selectedBorderColor = PenaltyRed
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }


                }
            }
        }

        // Кнопка выдачи наличных
        item {
            OutlinedButton(
                onClick = onCashOutClick,
                enabled = !isProcessingAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StatusGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen)
            ) {
                Text(stringResource(R.string.admin_cashout_button, child.balance), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
