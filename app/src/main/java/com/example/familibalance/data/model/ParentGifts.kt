package com.example.familibalance.data.model

import androidx.annotation.StringRes
import com.example.familibalance.R

/**
 * Каталог подарков-купонов, которые ребёнок покупает за монеты
 * и отправляет родителю (см. MainViewModel.sendParentGift).
 * В истории/профиле хранится [ParentGiftType.name].
 */
enum class ParentGiftType(
    @StringRes val titleRes: Int,
    val price: Int
) {
    THANK_YOU(R.string.gift_thank_you, 30),
    HUGS(R.string.gift_hugs, 50),
    HELP_CHORES(R.string.gift_help_chores, 80),
    MOVIE_NIGHT(R.string.gift_movie_night, 120)
}

/** Маппинг сохранённого giftTypeId на тип подарка; неизвестное → null. */
fun parentGiftTypeOfOrNull(giftTypeId: String?): ParentGiftType? {
    val id = giftTypeId?.trim().orEmpty()
    if (id.isEmpty()) return null
    return ParentGiftType.entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
}
