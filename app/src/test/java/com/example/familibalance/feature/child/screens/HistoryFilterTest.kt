package com.example.familibalance.feature.child.screens

import com.example.familibalance.data.model.HistoryData
import com.example.familibalance.data.model.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты [historyFilterIndex]: фильтрация истории по [HistoryData.type]
 * с fallback на русскую строку category для legacy-записей (type == null
 * или неизвестный тип).
 */
class HistoryFilterTest {

    private fun item(
        type: String? = null,
        category: String = ""
    ) = HistoryData(
        title = "t",
        time = "10:00",
        date = "01.01.2026",
        amount = "+50 ₽",
        isIncome = true,
        category = category,
        type = type
    )

    @Test
    fun `quest-related types map to quests filter`() {
        val questTypes = listOf(
            HistoryType.QUEST_REWARD,
            HistoryType.QUEST_APPROVED,
            HistoryType.CUSTOM_QUEST_DONE,
            HistoryType.BONUS_QUEST_DONE,
            HistoryType.BONUS_QUEST_APPROVED,
            HistoryType.DIARY_GRADE_5,
            HistoryType.DIARY_GRADE_4,
            HistoryType.HONEST_APPROVED,
            HistoryType.ADMIN_REWARD
        )
        questTypes.forEach { type ->
            assertEquals("type=$type", HIST_FILTER_QUESTS, historyFilterIndex(item(type = type)))
        }
    }

    @Test
    fun `achievement types map to achievements filter`() {
        listOf(HistoryType.ACHIEVEMENT_UNLOCKED, HistoryType.STREAK_BONUS).forEach { type ->
            assertEquals("type=$type", HIST_FILTER_ACHIEVEMENTS, historyFilterIndex(item(type = type)))
        }
    }

    @Test
    fun `screen time type maps to screen time filter`() {
        assertEquals(HIST_FILTER_SCREEN_TIME, historyFilterIndex(item(type = HistoryType.SCREEN_TIME_PURCHASE)))
    }

    @Test
    fun `fine and admin withdraw map to fines filter`() {
        listOf(HistoryType.FINE, HistoryType.ADMIN_WITHDRAW).forEach { type ->
            assertEquals("type=$type", HIST_FILTER_FINES, historyFilterIndex(item(type = type)))
        }
    }

    @Test
    fun `cashout and dream types map to other filter`() {
        listOf(HistoryType.CASHOUT_GIVEN, HistoryType.CASHOUT_REQUEST, HistoryType.DREAM_SET).forEach { type ->
            assertEquals("type=$type", HIST_FILTER_OTHER, historyFilterIndex(item(type = type)))
        }
    }

    @Test
    fun `type wins over category - no string matching for typed records`() {
        // Запись с type=FINE и категорией "Квесты" должна попасть в Штрафы,
        // а не в Квесты — type приоритетнее сохранённой строки category.
        assertEquals(
            HIST_FILTER_FINES,
            historyFilterIndex(item(type = HistoryType.FINE, category = "Квесты"))
        )
    }

    @Test
    fun `legacy null type falls back to russian category`() {
        assertEquals(HIST_FILTER_QUESTS, historyFilterIndex(item(type = null, category = "Квесты")))
        assertEquals(HIST_FILTER_QUESTS, historyFilterIndex(item(type = null, category = "Дневник")))
        assertEquals(HIST_FILTER_QUESTS, historyFilterIndex(item(type = null, category = "Бонус-квесты")))
        assertEquals(HIST_FILTER_QUESTS, historyFilterIndex(item(type = null, category = "Кастомные квесты")))
        assertEquals(HIST_FILTER_ACHIEVEMENTS, historyFilterIndex(item(type = null, category = "Достижения")))
        assertEquals(HIST_FILTER_ACHIEVEMENTS, historyFilterIndex(item(type = null, category = "Бонусы")))
        assertEquals(HIST_FILTER_SCREEN_TIME, historyFilterIndex(item(type = null, category = "Экранное время")))
        assertEquals(HIST_FILTER_FINES, historyFilterIndex(item(type = null, category = "Штрафы")))
    }

    @Test
    fun `legacy uncategorized and unknown type go to other`() {
        assertEquals(HIST_FILTER_OTHER, historyFilterIndex(item(type = null, category = "Вывод средств")))
        assertEquals(HIST_FILTER_OTHER, historyFilterIndex(item(type = null, category = "Цели/Мечты")))
        assertEquals(HIST_FILTER_OTHER, historyFilterIndex(item(type = null, category = "")))
        // Неизвестный type (запись из будущей версии) — тоже category-fallback.
        assertEquals(HIST_FILTER_QUESTS, historyFilterIndex(item(type = "FUTURE_TYPE", category = "Квесты")))
        assertEquals(HIST_FILTER_OTHER, historyFilterIndex(item(type = "FUTURE_TYPE", category = "Неизвестная")))
    }
}
