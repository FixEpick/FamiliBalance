package com.example.familibalance.viewmodel.actions

import androidx.lifecycle.viewModelScope
import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.storage.*
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.util.*
import kotlinx.coroutines.launch

/** Админ выдаёт наличные: обнуляет баланс, гасит все заявки на вывод и пишет в историю. */
fun MainViewModel.adminCashoutAll(userId: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(userId) ?: return
    val amount = profile.balance
    if (amount > 0) {
        updateChildProfile(
            profile.copy(
                balance = 0,
                // Заявки на вывод гасим: после обнуления баланса их нельзя
                // ни одобрить (amount > balance), ни оставить — они бы блокировали
                // новые заявки (availableBalance ушёл бы в минус).
                cashoutRequests = emptyList(),
                historyItems = listOf(
                    HistoryData(
                        title = "Выдача наличных папой (${profile.name})",
                        time = getCurrentTimeString(),
                        date = getCurrentDateString(),
                        amount = "-$amount ₽",
                        isIncome = false,
                        category = "Вывод средств",
                        type = HistoryType.CASHOUT_GIVEN,
                        amountValue = -amount,
                        arg = profile.name
                    )
                ) + profile.historyItems
            )
        )
    } else if (profile.cashoutRequests.isNotEmpty()) {
        // Баланс уже 0, но висят заявки — просто очищаем их.
        updateChildProfile(profile.copy(cashoutRequests = emptyList()))
    }
    } finally {
        endProcessingAction()
    }
}

fun MainViewModel.createCashoutRequest(amount: Int, comment: String) {
    if (!tryStartProcessingAction()) return
    try {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

    val pendingTotal = profile.cashoutRequests.sumOf { it.amount }
    val availableBalance = profile.balance - pendingTotal
    if (amount <= 0 || amount > availableBalance) {
        return
    }

    val request = CashoutRequest(
        amount = amount,
        comment = comment.ifBlank { "Вывод наличных" },
        timestamp = System.currentTimeMillis()
    )

    updateChildProfile(profile.copy(cashoutRequests = profile.cashoutRequests + request))
    } finally {
        endProcessingAction()
    }
}

fun MainViewModel.approveCashoutRequest(childId: String, requestId: String) {
    if (!tryStartProcessingAction()) return
    try {
    // childId обязателен: на устройстве админа selectedChildId может
    // указывать на другого ребёнка — заявка просто не находилась.
    val profile = familyRepository.getChild(childId) ?: return

    val request = profile.cashoutRequests.firstOrNull { it.id == requestId } ?: return

    if (request.amount <= 0 || request.amount > profile.balance) {
        return
    }

    val updated = profile.copy(
        balance = profile.balance - request.amount,
        historyItems = listOf(
            HistoryData(
                title = "Вывод средств (${request.comment})",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-${request.amount} ₽",
                isIncome = false,
                category = "Вывод средств",
                type = HistoryType.CASHOUT_REQUEST,
                amountValue = -request.amount,
                arg = request.comment
            )
        ) + profile.historyItems,
        cashoutRequests = profile.cashoutRequests.filterNot { it.id == requestId }
    )
    updateChildProfile(updated)
    } finally {
        endProcessingAction()
    }
}

fun MainViewModel.rejectCashoutRequest(childId: String, requestId: String) {
    val profile = familyRepository.getChild(childId) ?: return

    updateChildProfile(
        profile.copy(cashoutRequests = profile.cashoutRequests.filterNot { it.id == requestId })
    )
}
