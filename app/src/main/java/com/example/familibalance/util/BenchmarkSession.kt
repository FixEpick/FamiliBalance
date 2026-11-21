package com.example.familibalance.util

import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.model.HistoryData
import com.example.familibalance.data.model.HistoryType

/**
 * Режим бенчмарка Baseline Profile: MainActivity выставляет [requested]
 * из Intent extra `SKIP_ONBOARDING_FOR_TEST`, а MainViewModel в init
 * подставляет тестовый профиль ребёнка и флаги автовхода — приложение
 * сразу открывает ChildSection, минуя онбординг и форму входа.
 *
 * В обычной жизни флаг всегда false, логика не меняется.
 */
object BenchmarkSession {

    const val EXTRA_SKIP_ONBOARDING = "SKIP_ONBOARDING_FOR_TEST"
    const val BENCH_CHILD_ID = "bench_child"

    @Volatile
    var requested: Boolean = false

    /** true — режим бенчмарка админки: AdminRoute с тестовыми детьми. */
    @Volatile
    var adminRequested: Boolean = false

    fun createChildProfile(): ChildProfileState {
        val history = (1..12).map { i ->
            HistoryData(
                title = "Тестовое задание $i",
                time = "12:0$i",
                date = "21.11.2026",
                amount = "+${10 * i} ₽",
                isIncome = true,
                category = "Квесты",
                type = HistoryType.QUEST_REWARD,
                amountValue = 10 * i
            )
        }
        return ChildProfileState(
            id = BENCH_CHILD_ID,
            name = "Тестовый Игрок",
            avatar = "🧒",
            balance = 240,
            streakDays = 5,
            historyItems = history
        )
    }

    /**
     * Тестовые профили детей для бенчмарка админки: два ребёнка,
     * у первого — ожидающие заявки (вкладка «Проверка» с бейджем),
     * история и достижения для «Прогресса»/«Истории».
     */
    fun createAdminChildren(parentUid: String): List<ChildProfileState> {
        val history1 = (1..10).map { i ->
            HistoryData(
                title = "Квест $i",
                time = "1$i:30",
                date = "2$i.11.2026",
                amount = "+${15 * i} ₽",
                isIncome = true,
                category = "Квесты",
                type = HistoryType.QUEST_REWARD,
                amountValue = 15 * i
            )
        }
        val history2 = (1..6).map { i ->
            HistoryData(
                title = "Дневник $i",
                time = "09:0$i",
                date = "1$i.11.2026",
                amount = "+100 ₽",
                isIncome = true,
                category = "Дневник",
                type = HistoryType.DIARY_GRADE_5,
                amountValue = 100
            )
        }
        return listOf(
            ChildProfileState(
                id = "bench_child_1",
                name = "Аня",
                avatar = "👧",
                balance = 320,
                streakDays = 7,
                parentUserId = parentUid,
                closetStatus = com.example.familibalance.data.model.QuestStatus.PENDING,
                closetPhotoUrl = null,
                closetComment = "Убрала шкаф",
                diaryStatus = com.example.familibalance.data.model.QuestStatus.PENDING,
                diaryComment = "Оценка 5",
                historyItems = history1
            ),
            ChildProfileState(
                id = "bench_child_2",
                name = "Миша",
                avatar = "👦",
                balance = 150,
                streakDays = 3,
                parentUserId = parentUid,
                roomStatus = com.example.familibalance.data.model.QuestStatus.PENDING,
                roomComment = "Убрал комнату",
                historyItems = history2
            )
        )
    }
}
