package com.example.familibalance.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyTimeBottomSheet(
    currentBalance: Int,
    boughtMinutesToday: Int,
    dailyLimit: Int,
    onBuyTime: (cost: Int, minutes: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val minutesLeftToday = (dailyLimit - boughtMinutesToday).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "⏳ Обмен коинов на время",
                color = NeonCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Дневной лимит: $dailyLimit минут. Осталось доступно сегодня: $minutesLeftToday мин.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            val tariffs = listOf(
                TariffData("⏳ 30 минут", 50, 30),
                TariffData("⏳ 60 минут", 100, 60),
                TariffData("⏳ 90 минут", 150, 90)
            )

            tariffs.forEach { tariff ->
                val isAffordable = (currentBalance >= tariff.cost) && (boughtMinutesToday + tariff.minutes <= dailyLimit)
                Card(
                    onClick = {
                        if (isAffordable) {
                            onBuyTime(tariff.cost, tariff.minutes)
                        }
                    },
                    enabled = isAffordable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(
                            width = 1.dp,
                            color = if (isAffordable) GlassStroke else GlassStroke.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAffordable) GlassBackground else GlassBackground.copy(alpha = 0.15f),
                        disabledContainerColor = GlassBackground.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tariff.title,
                            color = if (isAffordable) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (isAffordable) NeonCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isAffordable) NeonCyan else TextSecondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${tariff.cost} ₽",
                                color = if (isAffordable) NeonCyan else TextSecondary.copy(alpha = 0.4f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TariffData(
    val title: String,
    val cost: Int,
    val minutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesBottomSheet(
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpaceBgStart,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = GlassStroke
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "📜 Свиток правил",
                color = NeonCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📴", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Нет интернета? Не переживай! Все твои квесты и награды сохранены на телефоне. Как только появится сеть, они сами отправятся в облако.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            RuleBlock(
                title = "📖 Как работает твой баланс",
                text = "Все заработанные рубли падают в твой кошелёк. Ты можешь менять их на время в телефоне (50 ₽ = 30 минут) ИЛИ ты можешь в любой момент вывести все деньги — то есть забрать весь свой баланс настоящими бумажными деньгами на руки у папы!"
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = "💰 Как заработать:",
                text = "• Оценка «5» в дневнике: +50 ₽\n• Оценка «4» в дневнике: +30 ₽\n• Фото шкафа (идеальный порядок): +50 ₽\n• Фото комнаты (заправленная кровать и порядок): +50 ₽\n• Бонусы за Достижения: от 10 ₽ до 1000 ₽ за ранг «Гигачад Знаний» (+ Главный Секретный Подарок при выполнении всех достижений!)."
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = "🔴 За что вычитается денежка (Штрафы)",
                text = "За оценку «3» списывается -20 ₽, за оценку «2» списывается -50 ₽. Но если ты обманул или скрыл правду, но сам честно рассказал об этом папе через кошечку — папа одобрит признание и уменьшит штраф за этот проступок ровно в половину! (до -10 ₽ за тройку и до -25 ₽ за двойку)."
            )

            Spacer(modifier = Modifier.height(12.dp))

            RuleBlock(
                title = "🏆 Достижения",
                text = "Получай бонусы за новые ранги от 10 ₽ до 1000 ₽ за ранг Гигачад Знаний + Главный Секретный Подарок при выполнении всех достижений!"
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = SpaceBgStart
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Понятно!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RuleBlock(
    title: String,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AchievementsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(1) }
    val tabs = listOf("Все", "В процессе", "Зал славы")

    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val currentChild = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val context = LocalContext.current
    val allAchievements = getAchievements(currentChild)

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Достижения",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Разблокировано $completedCount из ${allAchievements.size} рангов",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                val isAllCompleted = completedCount == allAchievements.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / allAchievements.size.toFloat() },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonCyan,
                        trackColor = Color(0xFF131B3A).copy(alpha = 0.85f)
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isAllCompleted) {
                                    Modifier
                                        .border(1.5.dp, Color(0xFFFFD700), CircleShape)
                                        .background(Color(0xFFFFD700).copy(alpha = 0.25f), CircleShape)
                                } else {
                                    Modifier.alpha(0.3f)
                                }
                            )
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "Главный Секретный Подарок от папы за открытие всех 16 рангов!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎁", fontSize = 20.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tabName ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tabName, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0E2A3D).copy(alpha = 0.92f),
                            selectedLabelColor = NeonCyan,
                            containerColor = Color(0xFF0E142D).copy(alpha = 0.85f),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedTab == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        items(displayList, contentType = { "achievement" }) { item ->
            AchievementCard(
                title = item.title,
                condition = item.condition,
                description = item.description,
                reward = item.reward,
                progress = item.progress,
                isLocked = item.isLocked,
                isCompleted = item.isCompleted,
                isReadyToClaim = item.isReadyToClaim,
                onClaimClick = {
                    if (item.id.isNotBlank()) {
                        viewModel.claimAchievementReward(currentChild.id, item.id)
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    showVerificationCard: Boolean = true
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filters = listOf("Все", "Квесты", "Достижения", "Экранное время", "Штрафы")
    val familyState by viewModel.familyState.collectAsStateWithLifecycle()
    val currentChild = familyState.children[familyState.selectedChildId]
        ?: familyState.children.values.first()
    val rawHistoryItems = currentChild.historyItems
    val historyItems = remember(rawHistoryItems.size) {
        rawHistoryItems.filterNot { it.title.startsWith("Квест на проверке:") || it.title.startsWith("Одобрен квест:") }
    }

    val filteredItems = remember(selectedFilter, historyItems.size) {
        if (selectedFilter == 0) {
            historyItems
        } else {
            val category = filters[selectedFilter]
            historyItems.filter { it.category == category }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showVerificationCard) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏳", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Квесты на проверке",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ждет проверки папой",
                                color = StatusYellow,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters.size) { index ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = { Text(filters[index], fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = GlassBackground,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        items(filteredItems) { item ->
            HistoryItem(
                title = item.title,
                time = item.time,
                date = item.date,
                amount = item.amount,
                isIncome = item.isIncome
            )
        }
    }
}
