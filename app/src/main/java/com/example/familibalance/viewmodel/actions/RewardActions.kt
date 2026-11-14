package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.*

fun MainViewModel.createCashoutRequest(amount: Int, comment: String) {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

    if (amount <= 0 || amount > profile.balance) {
        return
    }

    val request = CashoutRequest(
        amount = amount,
        comment = comment.ifBlank { "Вывод наличных" },
        timestamp = System.currentTimeMillis()
    )

    updateChildProfile(profile.copy(cashoutRequests = profile.cashoutRequests + request))
}

fun MainViewModel.approveCashoutRequest(requestId: String) {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

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
                category = "Вывод средств"
            )
        ) + profile.historyItems,
        cashoutRequests = profile.cashoutRequests.filterNot { it.id == requestId }
    )
    updateChildProfile(updated)
}

fun MainViewModel.rejectCashoutRequest(requestId: String) {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

    updateChildProfile(
        profile.copy(cashoutRequests = profile.cashoutRequests.filterNot { it.id == requestId })
    )
}

fun MainViewModel.setDreamGoal(userId: String, title: String, target: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    if (target <= 0) return

    updateChildProfile(
        profile.copy(
            dreamTitle = title.ifBlank { profile.dreamTitle },
            dreamTarget = target
        )
    )
}

fun MainViewModel.claimDreamGoal(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return

    val target = profile.dreamTarget
    if (target <= 0 || profile.balance < target) {
        return
    }

    val claimedDreamTitle = profile.dreamTitle
    val updated = profile.copy(
        balance = profile.balance - target,
        dreamTitle = "",
        dreamTarget = 0,
        historyItems = listOf(
            HistoryData(
                title = "Заложена цель: $claimedDreamTitle",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-$target ₽",
                isIncome = false,
                category = "Цели/Мечты"
            )
        ) + profile.historyItems
    )

    val message = "Ребёнок ${profile.name} осуществил цель «$claimedDreamTitle» на $target ₽."
    familyRepository.addAdminNotification(message)

    updateChildProfile(updated)
}
