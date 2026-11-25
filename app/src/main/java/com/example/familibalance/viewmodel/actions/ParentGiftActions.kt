package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.data.model.HistoryData
import com.example.familibalance.data.model.HistoryType
import com.example.familibalance.data.model.ParentGiftItem
import com.example.familibalance.data.model.parentGiftTypeOfOrNull
import com.example.familibalance.util.getCurrentDateString
import com.example.familibalance.util.getCurrentTimeString
import java.util.UUID

/**
 * Ребёнок покупает подарок-купон для родителя за монеты.
 * Списывает стоимость, добавляет [ParentGiftItem] в pendingParentGifts
 * и пишет PARENT_GIFT в историю — инвариант
 * «дельта баланса = сумма истории» соблюдается (amountValue = -price).
 * Возвращает true при успехе; false — нет профиля, неизвестный тип
 * или не хватает баланса.
 */
fun MainViewModel.sendParentGift(userId: String, giftTypeId: String): Boolean {
    val profile = familyRepository.getChild(userId) ?: return false
    val type = parentGiftTypeOfOrNull(giftTypeId) ?: return false
    if (profile.balance < type.price) return false
    val item = ParentGiftItem(
        id = UUID.randomUUID().toString(),
        giftTypeId = type.name,
        createdAt = "${getCurrentDateString()} ${getCurrentTimeString()}"
    )
    val updated = profile.copy(
        balance = profile.balance - type.price,
        pendingParentGifts = profile.pendingParentGifts + item,
        historyItems = listOf(
            HistoryData(
                title = "Подарок родителю: ${type.name}",
                time = getCurrentTimeString(),
                date = getCurrentDateString(),
                amount = "-${type.price} ₽",
                isIncome = false,
                category = "Подарки",
                type = HistoryType.PARENT_GIFT,
                amountValue = -type.price,
                arg = type.name
            )
        ) + profile.historyItems
    )
    updateChildProfile(updated)
    // UI по событию играет 8-битный звук и показывает конфетти.
    _parentGiftEvent.value = type.name
    return true
}

/** Родитель «принимает/использует» купон — удаляет его из pendingParentGifts. */
fun MainViewModel.useParentGift(userId: String, giftItemId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    val updated = profile.copy(
        pendingParentGifts = profile.pendingParentGifts.filterNot { it.id == giftItemId }
    )
    if (updated != profile) {
        updateChildProfile(updated)
    }
}
