package com.example.familibalance.data.model

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class QuestStatus {
    AVAILABLE,
    PENDING,
    READY_TO_CLAIM,
    CLAIMED,
    APPROVED
}

@Serializable
data class BonusQuest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val reward: Int,
    val description: String = "",
    val status: QuestStatus = QuestStatus.AVAILABLE,
    val createdDate: String = "",
    val rejectReason: String? = null,
    val photoUrl: String? = null,
    val comment: String = ""
)

@Serializable
enum class QuestFrequency {
    ONCE,
    DAILY,
    WEEKLY
}

@Serializable
data class CustomQuest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val reward: Int = 0,
    val description: String = "",
    val frequency: QuestFrequency = QuestFrequency.ONCE,
    val isVisible: Boolean = true,
    val status: QuestStatus = QuestStatus.AVAILABLE,
    val photoUrl: String? = null,
    val rejectReason: String? = null,
    val lastResetDate: String = "",
    val comment: String = ""
)

@Serializable
data class HistoryData(
    val title: String,
    val time: String,
    val date: String,
    val amount: String,
    val isIncome: Boolean,
    val category: String,
    /** Тип записи для локализации на рендере (HistoryScreen). null — legacy-запись. */
    val type: String? = null,
    /** Сумма операции в ₽ со знаком (+доход/-расход). null — не денежная запись. */
    val amountValue: Int? = null,
    /** Динамический аргумент для локализованного шаблона (название квеста/достижения и т.п.). null — нет аргумента или legacy-запись. */
    val arg: String? = null
)

/**
 * Строковые константы типов [HistoryData.type].
 * Строки, а не enum: неизвестное значение из БД не крашит десериализацию
 * (forward-compat при добавлении новых типов без обновления приложения).
 */
object HistoryType {
    const val SCREEN_TIME_PURCHASE = "SCREEN_TIME_PURCHASE"
    const val STREAK_BONUS = "STREAK_BONUS"
    const val ACHIEVEMENT_UNLOCKED = "ACHIEVEMENT_UNLOCKED"
    const val CASHOUT_GIVEN = "CASHOUT_GIVEN"
    const val CASHOUT_REQUEST = "CASHOUT_REQUEST"
    const val CUSTOM_QUEST_DONE = "CUSTOM_QUEST_DONE"
    const val ADMIN_REWARD = "ADMIN_REWARD"
    const val ADMIN_WITHDRAW = "ADMIN_WITHDRAW"
    const val FINE = "FINE"
    const val BONUS_QUEST_APPROVED = "BONUS_QUEST_APPROVED"
    const val QUEST_APPROVED = "QUEST_APPROVED"
    const val HONEST_APPROVED = "HONEST_APPROVED"
    const val DIARY_GRADE_5 = "DIARY_GRADE_5"
    const val DIARY_GRADE_4 = "DIARY_GRADE_4"
    const val QUEST_REWARD = "QUEST_REWARD"
    const val BONUS_QUEST_DONE = "BONUS_QUEST_DONE"
    const val DREAM_SET = "DREAM_SET"
}

enum class CashoutStatus {
    PENDING,
    PROCESSED,
    REJECTED
}

@Serializable
data class CashoutRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val amount: Int,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PendingQuestData(
    val title: String,
    val reward: String,
    val description: String,
    val photoUrl: String? = null,
    @Transient val photoUri: Uri? = null
)
