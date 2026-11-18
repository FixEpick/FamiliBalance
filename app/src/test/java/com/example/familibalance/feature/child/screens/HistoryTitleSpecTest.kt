package com.example.familibalance.feature.child.screens

import com.example.familibalance.R
import com.example.familibalance.data.model.HistoryData
import com.example.familibalance.data.model.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты маппинга [historyTitleSpec]: type → resId + аргументы.
 * Покрывает fallback-ветки (type == null, неизвестный type, legacy-экстракция
 * из title при arg == null) и приоритет arg над парсингом title.
 */
class HistoryTitleSpecTest {

    private fun item(
        title: String,
        type: String? = null,
        arg: String? = null
    ) = HistoryData(
        title = title,
        time = "10:00",
        date = "01.01.2026",
        amount = "+50 ₽",
        isIncome = true,
        category = "Квесты",
        type = type,
        amountValue = 50,
        arg = arg
    )

    @Test
    fun `type null falls back to title`() {
        val spec = historyTitleSpec(item(title = "Что-то старое", type = null))
        assertNull(spec.resId)
    }

    @Test
    fun `unknown type falls back to title`() {
        val spec = historyTitleSpec(item(title = "X", type = "NO_SUCH_TYPE"))
        assertNull(spec.resId)
    }

    @Test
    fun `every known type maps to its resId with arg`() {
        val cases = listOf(
            HistoryType.SCREEN_TIME_PURCHASE to R.string.hist_screen_time_purchase,
            HistoryType.STREAK_BONUS to R.string.hist_streak_bonus,
            HistoryType.ACHIEVEMENT_UNLOCKED to R.string.hist_achievement_unlocked,
            HistoryType.CASHOUT_GIVEN to R.string.hist_cashout_given,
            HistoryType.CASHOUT_REQUEST to R.string.hist_cashout_request,
            HistoryType.CUSTOM_QUEST_DONE to R.string.hist_custom_quest_done,
            HistoryType.ADMIN_REWARD to R.string.hist_admin_reward,
            HistoryType.ADMIN_WITHDRAW to R.string.hist_admin_withdraw,
            HistoryType.FINE to R.string.hist_fine,
            HistoryType.BONUS_QUEST_APPROVED to R.string.hist_bonus_quest_approved,
            HistoryType.QUEST_APPROVED to R.string.hist_quest_approved,
            HistoryType.HONEST_APPROVED to R.string.hist_honest_approved,
            HistoryType.DIARY_GRADE_5 to R.string.hist_diary_grade5,
            HistoryType.DIARY_GRADE_4 to R.string.hist_diary_grade4,
            HistoryType.QUEST_REWARD to R.string.hist_quest_reward,
            HistoryType.BONUS_QUEST_DONE to R.string.hist_bonus_quest_done,
            HistoryType.DREAM_SET to R.string.hist_dream_set
        )
        cases.forEach { (type, resId) ->
            // SCREEN_TIME_PURCHASE парсит arg как Int — даём числовую строку
            val arg = if (type == HistoryType.SCREEN_TIME_PURCHASE) "15" else "arg"
            val spec = historyTitleSpec(item(title = "ignored", type = type, arg = arg))
            assertEquals("type=$type", resId, spec.resId)
        }
    }

    @Test
    fun `arg wins over title parsing - colon inside quest name`() {
        // Квест «Поездка: в парк» — arg хранит контент целиком,
        // парсинг title не нужен и не ломается на ':' внутри названия.
        val spec = historyTitleSpec(
            item(
                title = "Квест выполнен: Поездка: в парк",
                type = HistoryType.CUSTOM_QUEST_DONE,
                arg = "Поездка: в парк"
            )
        )
        assertEquals(R.string.hist_custom_quest_done, spec.resId)
        assertEquals(listOf("Поездка: в парк"), spec.args)
    }

    @Test
    fun `legacy extraction keeps colon inside quest name`() {
        // arg == null → legacy removePrefix: контент после префикса
        // сохраняется целиком, включая ':' внутри названия.
        val spec = historyTitleSpec(
            item(
                title = "Квест выполнен: Поездка: в парк",
                type = HistoryType.CUSTOM_QUEST_DONE
            )
        )
        assertEquals(R.string.hist_custom_quest_done, spec.resId)
        assertEquals(listOf("Поездка: в парк"), spec.args)
    }

    @Test
    fun `arg wins for cashout comment with parentheses`() {
        // Комментарий со скобками ломал бы legacy substringAfter('(');
        // с arg парсинг не выполняется.
        val spec = historyTitleSpec(
            item(
                title = "Вывод средств (купить (хлеб))",
                type = HistoryType.CASHOUT_REQUEST,
                arg = "купить (хлеб)"
            )
        )
        assertEquals(R.string.hist_cashout_request, spec.resId)
        assertEquals(listOf("купить (хлеб)"), spec.args)
    }

    @Test
    fun `screen time minutes from arg and from legacy title`() {
        val withArg = historyTitleSpec(
            item(title = "ignored", type = HistoryType.SCREEN_TIME_PURCHASE, arg = "20")
        )
        assertEquals(R.string.hist_screen_time_purchase, withArg.resId)
        assertEquals(listOf(20), withArg.args)

        val legacy = historyTitleSpec(
            item(title = "Покупка экранного времени (15 мин)", type = HistoryType.SCREEN_TIME_PURCHASE)
        )
        assertEquals(R.string.hist_screen_time_purchase, legacy.resId)
        assertEquals(listOf(15), legacy.args)
    }

    @Test
    fun `fine discounted and plain variants`() {
        val discounted = historyTitleSpec(
            item(
                title = "Штраф: Разбил вазу (Скидка 50% за честное признание)",
                type = HistoryType.FINE
            )
        )
        assertEquals(R.string.hist_fine_discount, discounted.resId)
        assertEquals(listOf("Разбил вазу"), discounted.args)

        val plain = historyTitleSpec(
            item(title = "Штраф от папы", type = HistoryType.FINE)
        )
        assertEquals(R.string.hist_fine_plain, plain.resId)
        assertEquals(emptyList<Any>(), plain.args)

        // arg != null + discounted title → discount-вариант сохраняется
        val withArg = historyTitleSpec(
            item(
                title = "Штраф: Разбил вазу (Скидка 50% за честное признание)",
                type = HistoryType.FINE,
                arg = "Разбил вазу"
            )
        )
        assertEquals(R.string.hist_fine_discount, withArg.resId)
        assertEquals(listOf("Разбил вазу"), withArg.args)
    }

    @Test
    fun `admin reward and withdraw plain vs described`() {
        val rewardPlain = historyTitleSpec(
            item(title = "Пополнение от папы", type = HistoryType.ADMIN_REWARD)
        )
        assertEquals(R.string.hist_admin_reward_plain, rewardPlain.resId)

        val rewardDesc = historyTitleSpec(
            item(title = "Пополнение: Деньги на книгу", type = HistoryType.ADMIN_REWARD)
        )
        assertEquals(R.string.hist_admin_reward, rewardDesc.resId)
        assertEquals(listOf("Деньги на книгу"), rewardDesc.args)

        val withdrawDesc = historyTitleSpec(
            item(title = "Списание: Игрушка", type = HistoryType.ADMIN_WITHDRAW)
        )
        assertEquals(R.string.hist_admin_withdraw, withdrawDesc.resId)
        assertEquals(listOf("Игрушка"), withdrawDesc.args)

        val withdrawPlain = historyTitleSpec(
            item(title = "Списание от папы", type = HistoryType.ADMIN_WITHDRAW)
        )
        assertEquals(R.string.hist_admin_withdraw_plain, withdrawPlain.resId)
    }

    @Test
    fun `quest reward legacy extraction`() {
        val spec = historyTitleSpec(
            item(title = "Награда получена: 📚 Итоги недели (Дневник)", type = HistoryType.QUEST_REWARD)
        )
        assertEquals(R.string.hist_quest_reward, spec.resId)
        assertEquals(listOf("📚 Итоги недели (Дневник)"), spec.args)
    }
}
