package com.example.familibalance.feature.admin

import androidx.compose.foundation.BorderStroke
import com.example.familibalance.ui.screens.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import coil3.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.theme.*
import com.example.familibalance.CashoutStatus

import com.example.familibalance.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*

@Composable
fun ProgressAdminContent(viewModel: MainViewModel) {
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val child = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    var showAchievementsDialog by remember { mutableStateOf(false) }
    var showDreamGoalDialog by remember { mutableStateOf(false) }
    var dreamTitle by remember { mutableStateOf(child.dreamTitle) }
    var dreamTargetVal by remember { mutableStateOf(child.dreamTarget) }

    val validHistory = child.historyItems.filterNot { it.title.startsWith("Квест на проверке:") || it.title.startsWith("Одобрен квест:") }
    val completedQuests = validHistory.count { it.isIncome }
    val totalEarned = validHistory.filter { it.isIncome }.mapNotNull { it.amount.removeSuffix(" ₽").removePrefix("+").toIntOrNull() }.sum()
    val allAchievements: List<AchievementItemData> = getAchievements(child)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBackground),
                border = BorderStroke(1.dp, AccentColor.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (child.dreamTarget > 0) {
                        Text("🎯 Цель-мечта: ${child.dreamTitle}", color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Накоплено: ${child.balance} ₽ из ${child.dreamTarget} ₽", color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
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
                        Text(if (child.dreamTarget > 0) "✏️ Изменить цель" else "🎯 Задать цель", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
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
                    Text("📈 Статистика квестов", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Выполнено квестов", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$completedQuests", color = AccentColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Всего заработано", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("+$totalEarned ₽", color = StatusGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Текущий баланс", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${child.balance} ₽", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                Text("🏅 Достижения (${allAchievements.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
    if (showDreamGoalDialog) {
        AlertDialog(
            onDismissRequest = { showDreamGoalDialog = false },
            containerColor = SpaceBgStart,
            title = { Text(if (child.dreamTarget > 0) "✏️ Изменить цель" else "🎯 Задать цель", color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dreamTitle, onValueChange = { dreamTitle = it }, label = { Text("Название цели", color = TextPrimary) }, singleLine = true, colors = editorFieldColors())
                    OutlinedTextField(value = if (dreamTargetVal > 0) dreamTargetVal.toString() else "", onValueChange = { dreamTargetVal = it.toIntOrNull() ?: 0 }, label = { Text("Сумма (₽)", color = TextPrimary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = editorFieldColors())
                }
            },
            confirmButton = {
                Button(
                    enabled = dreamTitle.isNotBlank() && dreamTargetVal > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = SpaceBgStart, disabledContainerColor = AccentColor.copy(alpha = 0.25f), disabledContentColor = Color.White.copy(alpha = 0.5f)),
                    onClick = { viewModel.setDreamGoal(child.id, dreamTitle.trim(), dreamTargetVal); showDreamGoalDialog = false }
                ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDreamGoalDialog = false }) { Text("Отмена", color = TextSecondary) } }
        )
    }
    if (showAchievementsDialog) {
        AlertDialog(
            onDismissRequest = { showAchievementsDialog = false },
            containerColor = SpaceBgStart,
            title = { Text("🏅 Достижения (${allAchievements.size})", color = AccentColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    allAchievements.forEach { ach ->
                        AchievementCard(title = ach.title, condition = ach.condition, description = ach.description, reward = ach.reward, progress = ach.progress, isLocked = ach.isLocked, isCompleted = ach.isCompleted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAchievementsDialog = false }) { Text("Закрыть", color = AccentColor, fontWeight = FontWeight.Bold) } }
        )
    }
}
