package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel

/** Дефолты настроек экономики (совпадают с дефолтами полей профиля). */
private const val DEFAULT_BASE_REWARD = 50
private const val DEFAULT_CHEST_CHANCE_PERCENT = 15

/**
 * Обновляет настройки экономики ребёнка (родительская админка).
 * baseClosetReward/baseRoomReward зеркалятся в wardrobeReward/roomReward —
 * именно их читают клейм наград и карточки заданий, поэтому базовые
 * цены на UI всегда актуальны.
 */
fun MainViewModel.updateEconomySettings(
    userId: String,
    baseClosetReward: Int,
    baseRoomReward: Int,
    baseDiaryFiveReward: Int,
    multipliersEnabled: Boolean,
    bonusChestChancePercent: Int,
    weeklyEarnLimit: Int
) {
    val profile = familyRepository.getChild(userId) ?: return
    val closet = baseClosetReward.coerceIn(0, 1000)
    val room = baseRoomReward.coerceIn(0, 1000)
    val diary5 = baseDiaryFiveReward.coerceIn(0, 1000)
    updateChildProfile(
        profile.copy(
            baseClosetReward = closet,
            baseRoomReward = room,
            baseDiaryFiveReward = diary5,
            multipliersEnabled = multipliersEnabled,
            bonusChestChancePercent = bonusChestChancePercent.coerceIn(0, 100),
            weeklyEarnLimit = weeklyEarnLimit.coerceAtLeast(0),
            wardrobeReward = closet,
            roomReward = room
        )
    )
}

/** «Сбросить по умолчанию»: базовые награды 50, множители вкл, сундук 15%, без лимита. */
fun MainViewModel.resetEconomySettings(userId: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(
        profile.copy(
            baseClosetReward = DEFAULT_BASE_REWARD,
            baseRoomReward = DEFAULT_BASE_REWARD,
            baseDiaryFiveReward = DEFAULT_BASE_REWARD,
            multipliersEnabled = true,
            bonusChestChancePercent = DEFAULT_CHEST_CHANCE_PERCENT,
            weeklyEarnLimit = 0,
            wardrobeReward = DEFAULT_BASE_REWARD,
            roomReward = DEFAULT_BASE_REWARD
        )
    )
}
