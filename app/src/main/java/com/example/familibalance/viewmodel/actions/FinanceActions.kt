package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import kotlinx.coroutines.launch

/** Админ начисляет сумму на баланс с описанием. */
fun MainViewModel.adminDeposit(userId: String, amount: Int, description: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val title = if (description.isNotBlank()) "Пополнение: ${description.trim()}" else "Пополнение от папы"
    updateChildProfile(
        profile.copy(
            balance = profile.balance + amount,
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "+$amount ₽", true, "Квесты")
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}

/**
 * Штраф: если есть одобренное честное признание (APPROVED) — сумма делится
 * пополам, статус становится CLAIMED и скидка больше не применяется повторно.
 * Любой штраф обнуляет стрик. Обычные списания используйте [adminWithdraw].
 */
fun MainViewModel.applyFine(childId: String, amount: Int, reason: String) {
    if (!tryStartProcessingAction()) return
    try {
        applyFineInternal(childId, amount, reason)
    } finally {
        endProcessingAction()
    }
}

/** Тело штрафа без guard'а — вызывается из applyDiaryGrades, который уже держит слот. */
internal fun MainViewModel.applyFineInternal(childId: String, amount: Int, reason: String) {
    val profile = familyRepository.getChild(childId) ?: return
    val discounted = profile.honestAdmissionStatus == QuestStatus.APPROVED
    val finalAmount = if (discounted) amount / 2 else amount
    val baseTitle = if (reason.isNotBlank()) "Штраф: ${reason.trim()}" else "Штраф от папы"
    val title = if (discounted) "$baseTitle (Скидка 50% за честное признание)" else baseTitle
    updateChildProfile(
        profile.copy(
            balance = (profile.balance - finalAmount).coerceAtLeast(0),
            streakDays = 0,
            lastStreakDate = "",
            honestAdmissionStatus = if (discounted) QuestStatus.CLAIMED else profile.honestAdmissionStatus,
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "-$finalAmount ₽", false, "Штрафы")
            ) + profile.historyItems
        )
    )
}

/** Админ списывает сумму с баланса (не ниже 0). Обычное списание — стрик не трогает. */
fun MainViewModel.adminWithdraw(userId: String, amount: Int, description: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val title = if (description.isNotBlank()) "Списание: ${description.trim()}" else "Списание от папы"
    updateChildProfile(
        profile.copy(
            balance = (profile.balance - amount).coerceAtLeast(0),
            historyItems = listOf(
                HistoryData(title, getCurrentTimeString(), getCurrentDateString(), "-$amount ₽", false, "Штрафы")
            ) + profile.historyItems
        )
    )
    } finally {
        endProcessingAction()
    }
}
