package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.*
import com.example.familibalance.data.model.*
import com.example.familibalance.data.push.PushNotifier
import com.example.familibalance.data.storage.*
import com.example.familibalance.util.*


fun MainViewModel.setDreamGoal(userId: String, title: String, target: Int) {
    val profile = familyRepository.getChild(userId) ?: return
    if (target <= 0) return

    updateChildProfile(
        profile.copy(
            dreamTitle = title.ifBlank { profile.dreamTitle },
            dreamTarget = target,
            dreamSetDate = getCurrentDateString()
        )
    )
}

fun MainViewModel.claimDreamGoal(userId: String) {
    if (!tryStartProcessingAction()) return
    try {
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
        dreamSetDate = "",
        historyItems = listOf(
            HistoryData(
                title = "Заложена цель: $claimedDreamTitle",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-$target ₽",
                isIncome = false,
                category = "Цели/Мечты",
                type = HistoryType.DREAM_SET,
                amountValue = -target,
                arg = claimedDreamTitle
            )
        ) + profile.historyItems
    )

    val message = "Ребёнок ${profile.name} осуществил цель «$claimedDreamTitle» на $target ₽."
    familyRepository.addAdminNotification(message)
    PushNotifier.dreamReached(profile.parentUserId, profile.name, claimedDreamTitle, target, RewardCurrency.symbolOf(profile.rewardCurrency))

    updateChildProfile(updated)
    } finally {
        endProcessingAction()
    }
}
