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

    profile.cashoutRequests = profile.cashoutRequests + request
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.approveCashoutRequest(requestId: String) {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

    val index = profile.cashoutRequests.indexOfFirst { it.id == requestId }
    if (index == -1) {
        return
    }

    val request = profile.cashoutRequests[index]

    if (request.amount <= 0 || request.amount > profile.balance) {
        return
    }

    profile.balance -= request.amount
    profile.historyItems.add(
        0,
        HistoryData(
            title = "Вывод средств (${request.comment})",
            time = getCurrentTimeString(),
            date = getCurrentDateString(),
            amount = "-${request.amount} ₽",
            isIncome = false,
            category = "Вывод средств"
        )
    )
    profile.cashoutRequests = profile.cashoutRequests.minusElement(request)
    updateChildProfile(profile)
}

fun MainViewModel.rejectCashoutRequest(requestId: String) {
    val profile = familyRepository.getChild(familyRepository.selectedChildId) ?: return

    profile.cashoutRequests = profile.cashoutRequests.filterNot { it.id == requestId }
    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.setDreamGoal(userId: String, title: String, target: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    if (target <= 0) return

    profile.dreamTitle = title.ifBlank { profile.dreamTitle }
    profile.dreamTarget = target

    saveState()
    familyRepository.pushChildProfile(profile)
}

fun MainViewModel.claimDreamGoal(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return

    val target = profile.dreamTarget
    if (target <= 0 || profile.balance < target) {
        return
    }

    profile.balance -= target
    val claimedDreamTitle = profile.dreamTitle
    profile.dreamTitle = ""
    profile.dreamTarget = 0

    profile.historyItems.add(
        0,
        HistoryData(
            title = "Заложена цель: $claimedDreamTitle",
            time = getCurrentTimeString(),
            date = getCurrentDateString(),
            amount = "-$target ₽",
            isIncome = false,
            category = "Цели/Мечты"
        )
    )

    val message = "Ребёнок ${profile.name} осуществил цель «$claimedDreamTitle» на $target ₽."
    familyRepository.addAdminNotification(message)

    updateChildProfile(profile)
}
