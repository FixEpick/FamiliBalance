package com.example.familibalance.feature.child.screens

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
import androidx.compose.ui.res.pluralStringResource
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
import com.example.familibalance.feature.child.rememberQuestDeliveryTarget
import com.example.familibalance.feature.child.components.*
import com.example.familibalance.ui.theme.*
import com.example.familibalance.ui.components.*
import com.example.familibalance.viewmodel.actions.*


/** Спецификация локализованного заголовка: resId + аргументы для stringResource. resId == null → показываем title как есть. */
internal data class HistoryTitleSpec(val resId: Int?, val args: List<Any> = emptyList())

/**
 * Маппинг [HistoryData] → [HistoryTitleSpec].
 *
 * Приоритет аргумента:
 * 1. [HistoryData.arg] != null → используем его напрямую (новые записи,
 *    пользовательский контент не парсим);
 * 2. arg == null → legacy-экстракция из сохранённого title по фиксированным
 *    русским префиксам (записи, созданные до появления arg);
 * 3. type == null или неизвестный → resId = null → fallback на title.
 */
internal fun historyTitleSpec(item: HistoryData): HistoryTitleSpec {
    val title = item.title
    val arg = item.arg
    return when (item.type) {
        HistoryType.SCREEN_TIME_PURCHASE -> {
            val minutes = arg?.toIntOrNull()
                ?: title.substringAfter('(', "").substringBefore(" мин", "").toIntOrNull()
            if (minutes != null) HistoryTitleSpec(R.string.hist_screen_time_purchase, listOf(minutes))
            else HistoryTitleSpec(null)
        }
        HistoryType.STREAK_BONUS -> HistoryTitleSpec(R.string.hist_streak_bonus)
        HistoryType.ACHIEVEMENT_UNLOCKED ->
            HistoryTitleSpec(R.string.hist_achievement_unlocked, listOf(arg ?: title.removePrefix("Достижение открыто: ")))
        HistoryType.CASHOUT_GIVEN ->
            HistoryTitleSpec(R.string.hist_cashout_given, listOf(arg ?: title.substringAfter('(', "").substringBefore(')', "")))
        HistoryType.CASHOUT_REQUEST ->
            HistoryTitleSpec(R.string.hist_cashout_request, listOf(arg ?: title.substringAfter('(', "").substringBefore(')', "")))
        HistoryType.CUSTOM_QUEST_DONE ->
            HistoryTitleSpec(R.string.hist_custom_quest_done, listOf(arg ?: title.removePrefix("Квест выполнен: ")))
        HistoryType.ADMIN_REWARD ->
            if (arg != null) {
                HistoryTitleSpec(R.string.hist_admin_reward, listOf(arg))
            } else if (title.startsWith("Пополнение: ")) {
                HistoryTitleSpec(R.string.hist_admin_reward, listOf(title.removePrefix("Пополнение: ")))
            } else {
                HistoryTitleSpec(R.string.hist_admin_reward_plain)
            }
        HistoryType.ADMIN_WITHDRAW ->
            if (arg != null) {
                HistoryTitleSpec(R.string.hist_admin_withdraw, listOf(arg))
            } else if (title.startsWith("Списание: ")) {
                HistoryTitleSpec(R.string.hist_admin_withdraw, listOf(title.removePrefix("Списание: ")))
            } else {
                HistoryTitleSpec(R.string.hist_admin_withdraw_plain)
            }
        HistoryType.FINE -> {
            val discounted = title.endsWith("(Скидка 50% за честное признание)")
            when {
                arg != null -> {
                    val resId = if (discounted) R.string.hist_fine_discount else R.string.hist_fine
                    HistoryTitleSpec(resId, listOf(arg))
                }
                else -> {
                    val base = title.removeSuffix(" (Скидка 50% за честное признание)")
                    when {
                        base.startsWith("Штраф: ") -> {
                            val reason = base.removePrefix("Штраф: ")
                            val resId = if (discounted) R.string.hist_fine_discount else R.string.hist_fine
                            HistoryTitleSpec(resId, listOf(reason))
                        }
                        base == "Штраф от папы" -> {
                            val resId = if (discounted) R.string.hist_fine_plain_discount else R.string.hist_fine_plain
                            HistoryTitleSpec(resId)
                        }
                        else -> HistoryTitleSpec(null)
                    }
                }
            }
        }
        HistoryType.BONUS_QUEST_APPROVED ->
            HistoryTitleSpec(R.string.hist_bonus_quest_approved, listOf(arg ?: title.removePrefix("Одобрен бонус-квест: ")))
        HistoryType.QUEST_APPROVED ->
            HistoryTitleSpec(R.string.hist_quest_approved, listOf(arg ?: title.removePrefix("Одобрен квест: ")))
        HistoryType.HONEST_APPROVED -> HistoryTitleSpec(R.string.hist_honest_approved)
        HistoryType.DIARY_GRADE_5 -> HistoryTitleSpec(R.string.hist_diary_grade5)
        HistoryType.DIARY_GRADE_4 -> HistoryTitleSpec(R.string.hist_diary_grade4)
        HistoryType.QUEST_REWARD ->
            HistoryTitleSpec(R.string.hist_quest_reward, listOf(arg ?: title.removePrefix("Награда получена: ")))
        HistoryType.BONUS_QUEST_DONE ->
            HistoryTitleSpec(R.string.hist_bonus_quest_done, listOf(arg ?: title.removePrefix("Бонус-квест выполнен: ")))
        HistoryType.DREAM_SET ->
            HistoryTitleSpec(R.string.hist_dream_set, listOf(arg ?: title.removePrefix("Заложена цель: ")))
        else -> HistoryTitleSpec(null)
    }
}

/** Индексы фильтров истории — позиции чипсов в [HistoryScreen]. */
internal const val HIST_FILTER_ALL = 0
internal const val HIST_FILTER_QUESTS = 1
internal const val HIST_FILTER_ACHIEVEMENTS = 2
internal const val HIST_FILTER_SCREEN_TIME = 3
internal const val HIST_FILTER_FINES = 4
internal const val HIST_FILTER_OTHER = 5

/**
 * Маппинг записи истории на индекс фильтра — по [HistoryData.type],
 * а не по локализуемой строке category.
 *
 * Для legacy-записей (type == null или неизвестный тип) — fallback на
 * русскую строку category: это data-контракт старых записей, которые
 * создавались до появления поля type.
 */
internal fun historyFilterIndex(item: HistoryData): Int = when (item.type) {
    HistoryType.QUEST_REWARD, HistoryType.QUEST_APPROVED,
    HistoryType.CUSTOM_QUEST_DONE, HistoryType.BONUS_QUEST_DONE,
    HistoryType.BONUS_QUEST_APPROVED, HistoryType.DIARY_GRADE_5,
    HistoryType.DIARY_GRADE_4, HistoryType.HONEST_APPROVED,
    HistoryType.ADMIN_REWARD -> HIST_FILTER_QUESTS
    HistoryType.ACHIEVEMENT_UNLOCKED, HistoryType.STREAK_BONUS -> HIST_FILTER_ACHIEVEMENTS
    HistoryType.SCREEN_TIME_PURCHASE -> HIST_FILTER_SCREEN_TIME
    HistoryType.FINE, HistoryType.ADMIN_WITHDRAW -> HIST_FILTER_FINES
    HistoryType.CASHOUT_GIVEN, HistoryType.CASHOUT_REQUEST,
    HistoryType.DREAM_SET -> HIST_FILTER_OTHER
    else -> when (item.category) {
        "Квесты", "Дневник", "Бонус-квесты", "Кастомные квесты" -> HIST_FILTER_QUESTS
        "Достижения", "Бонусы" -> HIST_FILTER_ACHIEVEMENTS
        "Экранное время" -> HIST_FILTER_SCREEN_TIME
        "Штрафы" -> HIST_FILTER_FINES
        else -> HIST_FILTER_OTHER
    }
}

/** Локализованный заголовок записи истории; fallback — сохранённый title. */
@Composable
private fun localizedHistoryTitle(item: HistoryData): String {
    val spec = historyTitleSpec(item)
    return if (spec.resId != null) {
        stringResource(spec.resId, *spec.args.toTypedArray())
    } else {
        item.title
    }
}

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    showVerificationCard: Boolean = true
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    var showPendingSheet by remember { mutableStateOf(false) }
    // Пересъёмка квеста из BottomSheet: в rememberSaveable — только
    // String-ключ (как в TasksScreen), цель резолвится из child.
    var editingQuestKey by rememberSaveable { mutableStateOf<String?>(null) }
    val filterLabels = listOf(
        stringResource(R.string.history_filter_all),
        stringResource(R.string.history_filter_quests),
        stringResource(R.string.history_filter_achievements),
        stringResource(R.string.history_filter_screentime),
        stringResource(R.string.history_filter_fines),
        stringResource(R.string.history_filter_other)
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
    val rawHistoryItems = currentChild.historyItems
    val historyItems = remember(rawHistoryItems.size) {
        rawHistoryItems.filterNot { it.title.startsWith("Квест на проверке:") || it.title.startsWith("Одобрен квест:") }
    }

    val filteredItems = remember(selectedFilter, historyItems.size) {
        if (selectedFilter == HIST_FILTER_ALL) {
            historyItems
        } else {
            historyItems.filter { historyFilterIndex(it) == selectedFilter }
        }
    }

    // Количество квестов, ожидающих проверки папой
    val pendingCount = remember(currentChild) {
        listOf(currentChild.closetStatus, currentChild.roomStatus, currentChild.diaryStatus)
            .count { it == QuestStatus.PENDING } +
            currentChild.bonusQuests.count { it.status == QuestStatus.PENDING } +
            currentChild.customQuests.count { it.status == QuestStatus.PENDING }
    }

    // Полноэкранная отправка/пересъёмка квеста из BottomSheet
    val activeEdit = rememberQuestDeliveryTarget(currentChild, editingQuestKey)
    if (activeEdit != null) {
        QuestDeliveryScreen(
            questName = activeEdit.displayName,
            rewardText = activeEdit.rewardText,
            onSendClick = { comment, photoUri ->
                val photoUrlStr = photoUri?.toString()
                when {
                    activeEdit.key == "closet" || activeEdit.key == "room" || activeEdit.key == "diary" -> {
                        viewModel.submitQuestForReview(currentChild.id, activeEdit.key, photoUrlStr, comment)
                    }
                    activeEdit.key.startsWith("bonus:") -> {
                        viewModel.submitBonusQuestByChild(currentChild.id, activeEdit.key.removePrefix("bonus:"), photoUri, comment)
                    }
                    activeEdit.key.startsWith("custom:") -> {
                        viewModel.submitCustomQuestPhoto(currentChild.id, activeEdit.key.removePrefix("custom:"), photoUrlStr.orEmpty(), comment)
                    }
                }
                editingQuestKey = null
            },
            onBackClick = { editingQuestKey = null },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
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
                        .clickable { showPendingSheet = true }
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
                                text = stringResource(R.string.child_pending_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (pendingCount > 0) {
                                Text(
                                    text = pluralStringResource(R.plurals.history_pending_waiting, pendingCount, pendingCount),
                                    color = StatusYellow,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.child_pending_empty),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("›", color = TextSecondary, fontSize = 20.sp)
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
                items(filterLabels.size) { index ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = { Text(filterLabels[index], fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentColor.copy(alpha = 0.2f),
                            selectedLabelColor = AccentColor,
                            containerColor = GlassBackground,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == index,
                            borderColor = GlassStroke,
                            selectedBorderColor = AccentColor
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
                title = localizedHistoryTitle(item),
                time = item.time,
                date = item.date,
                amount = item.amount,
                isIncome = item.isIncome
            )
        }

    }

    Text(
        text = stringResource(R.string.history_version, com.example.familibalance.BuildConfig.VERSION_NAME),
        color = TextSecondary.copy(alpha = 0.5f),
        fontSize = 10.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
    )
    }

    if (showPendingSheet) {
        PendingQuestsBottomSheet(
            child = currentChild,
            onDismiss = { showPendingSheet = false },
            onEditQuest = { key ->
                showPendingSheet = false
                editingQuestKey = key
            },
            viewModel = viewModel
        )
    }
}
