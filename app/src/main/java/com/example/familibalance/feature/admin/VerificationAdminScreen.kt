package com.example.familibalance.feature.admin

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.ui.screens.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.CashoutStatus

import com.example.familibalance.*

import com.example.familibalance.util.parseGrades
import com.example.familibalance.viewmodel.actions.*

@Composable
fun VerificationAdminContent(
    onCashOutClick: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
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
                            text = "📷 Ждут подтверждения (${child.name})",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Фотоотчёты и задания ребёнка, ожидающие проверки.",
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
                    title = "Фото шкафа",
                    reward = "+50 ₽",
                    description = child.closetComment.ifBlank { "Без комментария" },
                    photoUrl = child.closetPhotoUrl,
                    onApprove = {
                        viewModel.adminApproveQuest(child.id, "closet")
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "closet", reason, "Фото шкафа")
                    }
                )
            }
        }

        // Блок заявок на вывод
        if (child.cashoutRequests.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground),
                    border = BorderStroke(1.dp, GlassStroke)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💵 Заявки на вывод",
                            color = NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        child.cashoutRequests.forEach { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Сумма: ${request.amount} ₽",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Комментарий: ${request.comment}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.approveCashoutRequest(request.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("✅ Выдано", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.rejectCashoutRequest(request.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("❌ Отклонить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (child.roomStatus == QuestStatus.PENDING) {
            item {
                QuestVerificationCard(
                    title = "Фото комнаты",
                    reward = "+50 ₽",
                    description = child.roomComment.ifBlank { "Без комментария" },
                    photoUrl = child.roomPhotoUrl,
                    onApprove = {
                        viewModel.adminApproveQuest(child.id, "room")
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "room", reason, "Фото комнаты")
                    }
                )
            }
        }

        if (child.diaryStatus == QuestStatus.PENDING) {
            item {
                DiaryVerificationCard(
                    title = "📚 Итоги недели (Дневник)",
                    comment = child.diaryComment,
                    photoUrl = child.diaryPhotoUrl,
                    onApprove = { count5, count4, count3, count2 ->
                        viewModel.applyDiaryGrades(child.id, count5, count4, count3, count2)
                    },
                    onReject = { reason ->
                        viewModel.adminRejectQuest(child.id, "diary", reason, "Итоги недели")
                    }
                )
            }
        }

        items(child.bonusQuests.filter { it.status == QuestStatus.PENDING }) { bQuest ->
            QuestVerificationCard(
                title = "Бонус: ${bQuest.title}",
                reward = "+${bQuest.reward} ₽",
                description = bQuest.comment.ifBlank { "Без комментария" },
                photoUrl = bQuest.photoUrl,
                onApprove = {
                    viewModel.adminApproveBonusQuest(child.id, bQuest.id)
                },
                onReject = { reason ->
                    viewModel.adminRejectBonusQuest(child.id, bQuest.id, reason)
                }
            )
        }

        items(child.customQuests.filter { it.status == QuestStatus.PENDING }, key = { it.id }, contentType = { "custom_quest" }) { cQuest ->
            QuestVerificationCard(
                title = "Квест: ${cQuest.title}",
                reward = "+${cQuest.reward} ₽",
                description = cQuest.comment.ifBlank { "Без комментария" },
                photoUrl = cQuest.photoUrl,
                onApprove = {
                    viewModel.adminApproveCustomQuest(child.id, cQuest.id)
                },
                onReject = { reason ->
                    viewModel.adminRejectCustomQuest(child.id, cQuest.id, reason)
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
                    text = "✨ Нет заданий на проверке у ${child.name}",
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
                        Text("🐱 Честное признание от ${child.name}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "«${child.honestAdmissionText.ifBlank { "Ребенок не оставил комментария" }}»",
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
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("✅ Одобрить (Штраф -50%)", fontSize = 12.sp)
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
                                Text("❌ Отклонить", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Быстрые действия папы
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
                        text = "⚡ Быстрые действия папы (${child.name})",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Начисление за дневник, штрафы и ручной ввод суммы для этого профиля.",
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
                            placeholder = { Text("Сумма ₽", color = TextSecondary.copy(alpha = 0.6f)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SpaceBgStart,
                                unfocusedContainerColor = SpaceBgStart,
                                focusedBorderColor = NeonCyan,
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
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("+ Добавить", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(
                        value = depositDesc,
                        onValueChange = { depositDesc = it },
                        label = { Text("Описание (за что)", color = TextPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SpaceBgStart,
                            unfocusedContainerColor = SpaceBgStart,
                            focusedBorderColor = NeonCyan,
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
                            placeholder = { Text("Сумма ₽", color = TextSecondary.copy(alpha = 0.6f)) },
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
                            colors = ButtonDefaults.buttonColors(containerColor = PenaltyRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("- Списать", fontWeight = FontWeight.Bold)
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
                            text = "Это штраф (сбросит стрик и применит скидку)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedTextField(
                        value = withdrawDesc,
                        onValueChange = { withdrawDesc = it },
                        label = { Text("Описание (за что)", color = TextPrimary) },
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


                }
            }
        }

        // Кнопка выдачи наличных
        item {
            OutlinedButton(
                onClick = onCashOutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, StatusGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen)
            ) {
                Text("💵 Выдать наличные (${child.balance} ₽)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuestVerificationCard(
    title: String,
    reward: String,
    description: String,
    photoUrl: String?,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReasonText by remember { mutableStateOf("") }
    var showFullScreenImage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        border = BorderStroke(1.dp, GlassStroke)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Награда: $reward", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Комментарий: $description", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            if (!photoUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Фотоотчёт",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFullScreenImage = true },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✅ Одобрить", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    border = BorderStroke(1.dp, PenaltyRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("❌ Отклонить", fontSize = 12.sp)
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Причина отклонения") },
            text = {
                OutlinedTextField(
                    value = rejectReasonText,
                    onValueChange = { rejectReasonText = it },
                    label = { Text("Что нужно доделать?") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = GlassBackground,
                        unfocusedContainerColor = GlassBackground,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlassStroke
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val reason = if (rejectReasonText.isNotBlank()) rejectReasonText else "Требуется доработка"
                    onReject(reason)
                    showRejectDialog = false
                }) {
                    Text("Отклонить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showFullScreenImage && !photoUrl.isNullOrEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullScreenImage = false },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Полноэкранное фото",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoomDelta, rotation ->
                                val newScale = (scale * zoomDelta).coerceIn(1f, 5f)
                                offset = Offset(
                                    (offset.x + pan.x) * newScale / scale,
                                    (offset.y + pan.y) * newScale / scale
                                )
                                scale = newScale
                            }
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                onTap = {
                                    if (scale == 1f) {
                                        showFullScreenImage = false
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DiaryVerificationCard(
    title: String,
    photoUrl: String?,
    onApprove: (Int, Int, Int, Int) -> Unit,
    onReject: (String) -> Unit,
    comment: String = ""
) {
    val context = LocalContext.current
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReasonText by remember { mutableStateOf("") }
    var showFullScreenImage by remember { mutableStateOf(false) }

    val gradeCounts = remember { mutableStateMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0) }
    var isScanning by remember { mutableStateOf(false) }
    var scanStatusText by remember { mutableStateOf("Сканирование оценок...") }

    LaunchedEffect(photoUrl) {
        if (photoUrl.isNullOrEmpty()) return@LaunchedEffect
        isScanning = true
        scanStatusText = "Сканирование оценок..."
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val loader = coil3.SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                (result.image?.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
            }

            if (bitmap == null) {
                isScanning = false
                scanStatusText = "Не удалось загрузить фото"
                return@LaunchedEffect
            }

            val visionText = withContext(Dispatchers.IO) {
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                try {
                    val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                    kotlinx.coroutines.suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
                        val task = recognizer.process(image)
                        task.addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                        task.addOnFailureListener { e -> cont.resumeWith(Result.failure(e)) }
                        cont.invokeOnCancellation { /* Task не отменяемый; результат просто проигнорируется */ }
                    }
                } finally {
                    recognizer.close()
                }
            }

            // Защита от устаревших результатов: photoUrl мог измениться во время сканирования
            if (photoUrl != null) {
                val parsed = parseGrades(visionText.text)
                gradeCounts.clear()
                gradeCounts.putAll(parsed)
                isScanning = false
                scanStatusText = "Оценки распознаны!"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            isScanning = false
            scanStatusText = "Ошибка сканирования: ${e.message}"
        }
    }

    val total = (gradeCounts[5]!! * 50) + (gradeCounts[4]!! * 30) - (gradeCounts[3]!! * 20) - (gradeCounts[2]!! * 50)
    val finalReward = maxOf(0, total)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground),
        border = BorderStroke(1.dp, GlassStroke)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isScanning) scanStatusText else "Расчет награды по оценкам",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (comment.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Комментарий: $comment",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (!photoUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Дневник",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFullScreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                    if (isScanning) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(scanStatusText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Корректировка оценок:", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val gradeConfigs = listOf(
                Triple(5, "+50 ₽", StatusGreen),
                Triple(4, "+30 ₽", NeonCyan),
                Triple(3, "-20 ₽", StatusYellow),
                Triple(2, "-50 ₽", PenaltyRed)
            )

            gradeConfigs.forEach { (grade, rewardLabel, color) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Оценка $grade ($rewardLabel)", color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val current = gradeCounts[grade] ?: 0
                                if (current > 0) gradeCounts[grade] = current - 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${gradeCounts[grade] ?: 0}",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val current = gradeCounts[grade] ?: 0
                                gradeCounts[grade] = current + 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val summaryText = listOf(5, 4, 3, 2).map { g ->
                "Оценка $g: ${gradeCounts[g] ?: 0} шт"
            }.joinToString(", ")
            Text(summaryText, color = TextSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onApprove(
                            gradeCounts[5] ?: 0,
                            gradeCounts[4] ?: 0,
                            gradeCounts[3] ?: 0,
                            gradeCounts[2] ?: 0
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✅ Одобрить (+${finalReward} ₽)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    border = BorderStroke(1.dp, PenaltyRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PenaltyRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("❌ Отклонить", fontSize = 12.sp)
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Причина отклонения") },
            text = {
                OutlinedTextField(
                    value = rejectReasonText,
                    onValueChange = { rejectReasonText = it },
                    label = { Text("Что нужно доделать?") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = GlassBackground,
                        unfocusedContainerColor = GlassBackground,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlassStroke
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val reason = if (rejectReasonText.isNotBlank()) rejectReasonText else "Требуется доработка"
                    onReject(reason)
                    showRejectDialog = false
                }) {
                    Text("Отклонить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showFullScreenImage && !photoUrl.isNullOrEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullScreenImage = false },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Полноэкранное фото дневника",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoomDelta, rotation ->
                                val newScale = (scale * zoomDelta).coerceIn(1f, 5f)
                                offset = Offset(
                                    (offset.x + pan.x) * newScale / scale,
                                    (offset.y + pan.y) * newScale / scale
                                )
                                scale = newScale
                            }
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                onTap = {
                                    if (scale == 1f) {
                                        showFullScreenImage = false
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
