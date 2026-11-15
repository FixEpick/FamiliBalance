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
    val category: String
)

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
