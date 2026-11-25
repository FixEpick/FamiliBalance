package com.example.familibalance.util

/**
 * Тарифы покупки экранного времени: минуты → цена в монетах.
 * Единый источник для шторки покупки, карточки «Экономика»
 * и unit-тестов.
 */
object ScreenTimeTariffs {

    data class Package(val minutes: Int, val cost: Int)

    val PACKAGES: List<Package> = listOf(
        Package(minutes = 30, cost = 100),
        Package(minutes = 60, cost = 220),
        Package(minutes = 90, cost = 320)
    )
}
