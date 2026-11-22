package com.example.familibalance.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource

/**
 * Валюта наград: только символ отображения, БЕЗ конвертации курса.
 * Баланс 1580 при смене ₽ → $ остаётся 1580 $.
 *
 * В хранилище (state_json, история, уведомления) суммы хранятся
 * в каноническом виде с ₽ — символ подменяется только на рендере
 * через [localize] / [currencyString].
 */
object RewardCurrency {

    data class Currency(val code: String, val symbol: String)

    const val DEFAULT_CODE = "RUB"

    val all: List<Currency> = listOf(
        Currency("RUB", "₽"),   // Российский рубль
        Currency("USD", "$"),   // Доллар США
        Currency("EUR", "€"),   // Евро
        Currency("GBP", "£"),   // Фунт стерлингов
        Currency("KZT", "₸"),   // Казахстанский тенге
        Currency("BYN", "Br"),  // Белорусский рубль
        Currency("COIN", "🪙")  // Универсальные монеты
    )

    fun symbolOf(code: String?): String =
        all.firstOrNull { it.code == code }?.symbol ?: symbolOf(DEFAULT_CODE)

    fun codeOf(symbolOrCode: String?): String =
        all.firstOrNull { it.code == symbolOrCode || it.symbol == symbolOrCode }?.code
            ?: DEFAULT_CODE

    /** "1580 ₽" / "1580 $" — сумма с символом валюты. */
    fun format(amount: Int, code: String?): String = "$amount ${symbolOf(code)}"

    /** Заменяет захардкоженный ₽ в строке на символ нужной валюты. */
    fun localize(text: String, code: String?): String =
        if (symbolOf(code) == "₽") text else text.replace("₽", symbolOf(code))
}

/** Код валюты текущего контекста (ребёнок в ChildSection / выбранный ребёнок в AdminScreen). */
val LocalRewardCurrency = staticCompositionLocalOf { RewardCurrency.DEFAULT_CODE }

/** Символ валюты для текущего контекста: ₽, $, €, £, ₸, Br, 🪙. */
@Composable
fun rewardCurrencySymbol(): String = RewardCurrency.symbolOf(LocalRewardCurrency.current)

/** stringResource с подменой ₽ на символ текущей валюты. */
@Composable
fun currencyString(@StringRes resId: Int, vararg args: Any): String =
    RewardCurrency.localize(stringResource(resId, *args), LocalRewardCurrency.current)

/** Локализованное название валюты по коду (RUB/USD/EUR/GBP/KZT/BYN/COIN). */
@Composable
fun currencyName(code: String): String = when (code) {
    "USD" -> stringResource(com.example.familibalance.R.string.cur_usd)
    "EUR" -> stringResource(com.example.familibalance.R.string.cur_eur)
    "GBP" -> stringResource(com.example.familibalance.R.string.cur_gbp)
    "KZT" -> stringResource(com.example.familibalance.R.string.cur_kzt)
    "BYN" -> stringResource(com.example.familibalance.R.string.cur_byn)
    "COIN" -> stringResource(com.example.familibalance.R.string.cur_coin)
    else -> stringResource(com.example.familibalance.R.string.cur_rub)
}
