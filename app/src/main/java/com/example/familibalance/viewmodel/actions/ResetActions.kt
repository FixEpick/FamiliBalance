package com.example.familibalance.viewmodel.actions

import com.example.familibalance.MainViewModel
import com.example.familibalance.data.model.QuestFrequency
import com.example.familibalance.data.model.QuestStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Дневной/недельный сброс встроенных и кастомных квестов + затухание стрика.
 * Вызывается из [MainViewModel.checkResets].
 */
internal fun MainViewModel.resetDailyQuestsIfNeeded() {
    val today = LocalDate.now().toString()
    val todayDate = LocalDate.now()
    val weekStart = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val children = familyRepository.getAllChildren()

    fun parseResetDate(raw: String): LocalDate? =
        if (raw.isBlank()) null
        else runCatching { LocalDate.parse(raw) }.getOrNull()

    /**
     * Проверяет встроенный квест: истёк ли его период (день/неделя).
     * Возвращает новую дату сброса и флаг очистки статуса.
     * Пустая/битая дата + CLAIMED/READY_TO_CLAIM — квест завис: сбрасываем
     * в AVAILABLE и ставим сегодняшнюю метку (BUG-02).
     * Пустая дата + AVAILABLE — ничего не меняем.
     * PENDING-квесты не сбрасываются (родитель ещё не проверил).
     */
    fun checkBuiltInQuest(
        frequency: QuestFrequency,
        lastResetDate: String,
        status: QuestStatus
    ): Pair<String, Boolean> {
        val parsed = parseResetDate(lastResetDate)
        if (parsed == null) {
            return when (status) {
                QuestStatus.CLAIMED, QuestStatus.READY_TO_CLAIM -> today to true
                QuestStatus.AVAILABLE -> lastResetDate to false
                else -> today to false
            }
        }
        val elapsed = when (frequency) {
            QuestFrequency.DAILY -> parsed.isBefore(todayDate)
            QuestFrequency.WEEKLY -> parsed.isBefore(weekStart)
            QuestFrequency.ONCE -> false
        }
        return if (elapsed) today to (status != QuestStatus.PENDING) else lastResetDate to false
    }

    children.forEach { child ->
        var updated = child

        // Хронологическое сравнение: lastDailyResetDate обновляем только
        // если сегодня строго позже. При откате времени маркер не регрессирует.
        val lastDailyReset = parseResetDate(child.lastDailyResetDate)
        if (lastDailyReset == null || todayDate.isAfter(lastDailyReset)) {
            updated = updated.copy(lastDailyResetDate = today)
        }

        // Шкаф
        val (closetDate, closetClear) = checkBuiltInQuest(
            child.wardrobeFrequency, child.closetResetDate, child.closetStatus
        )
        if (closetDate != child.closetResetDate || closetClear) {
            updated = updated.copy(
                closetResetDate = closetDate,
                closetStatus = if (closetClear) QuestStatus.AVAILABLE else child.closetStatus,
                closetRejectReason = if (closetClear) null else child.closetRejectReason,
                closetPhotoUrl = if (closetClear) null else child.closetPhotoUrl,
                closetComment = if (closetClear) "" else child.closetComment
            )
        }

        // Комната
        val (roomDate, roomClear) = checkBuiltInQuest(
            child.roomFrequency, child.roomResetDate, child.roomStatus
        )
        if (roomDate != child.roomResetDate || roomClear) {
            updated = updated.copy(
                roomResetDate = roomDate,
                roomStatus = if (roomClear) QuestStatus.AVAILABLE else child.roomStatus,
                roomRejectReason = if (roomClear) null else child.roomRejectReason,
                roomPhotoUrl = if (roomClear) null else child.roomPhotoUrl,
                roomComment = if (roomClear) "" else child.roomComment
            )
        }

        // Дневник
        val (diaryDate, diaryClear) = checkBuiltInQuest(
            child.diaryFrequency, child.diaryResetDate, child.diaryStatus
        )
        if (diaryDate != child.diaryResetDate || diaryClear) {
            updated = updated.copy(
                diaryResetDate = diaryDate,
                diaryStatus = if (diaryClear) QuestStatus.AVAILABLE else child.diaryStatus,
                diaryRejectReason = if (diaryClear) null else child.diaryRejectReason,
                diaryPhotoUrl = if (diaryClear) null else child.diaryPhotoUrl,
                diaryComment = if (diaryClear) "" else child.diaryComment,
                diaryCount5 = if (diaryClear) 0 else child.diaryCount5,
                diaryCount4 = if (diaryClear) 0 else child.diaryCount4,
                diaryCount3 = if (diaryClear) 0 else child.diaryCount3,
                diaryCount2 = if (diaryClear) 0 else child.diaryCount2
            )
        }

        // Auto-reset visible custom quests based on frequency
        val newQuests = updated.customQuests.map { quest ->
            if (!quest.isVisible) return@map quest
            // Квесты в полёте не трогаем: PENDING ждёт проверки родителем
            // (сброс стёр бы photoUrl и отзыв родителя), READY_TO_CLAIM —
            // одобрен и ждёт получения награды ребёнком.
            if (quest.status == QuestStatus.PENDING ||
                quest.status == QuestStatus.READY_TO_CLAIM
            ) return@map quest

            fun parseQuestResetDate(): LocalDate? =
                if (quest.lastResetDate.isBlank()) null
                else runCatching { LocalDate.parse(quest.lastResetDate) }.getOrNull()

            val shouldReset = when (quest.frequency) {
                QuestFrequency.DAILY -> {
                    val questResetDate = parseQuestResetDate()
                    questResetDate == null || questResetDate.isBefore(todayDate)
                }
                QuestFrequency.WEEKLY -> {
                    val questResetDate = parseQuestResetDate()
                    questResetDate == null || questResetDate.isBefore(weekStart)
                }
                QuestFrequency.ONCE -> false
            }

            if (shouldReset) {
                quest.copy(
                    status = QuestStatus.AVAILABLE,
                    photoUrl = null,
                    rejectReason = null,
                    lastResetDate = today
                )
            } else quest
        }

        if (newQuests != updated.customQuests) {
            updated = updated.copy(customQuests = newQuests)
        }

        // Затухание стрика: за каждый пропущенный день сгорает 1 огонёк.
        // lastStreakDate очищается — стрик не горит, пока ребёнок не отметится.
        if (updated.lastStreakDate.isNotBlank()) {
            val lastStreak = runCatching { LocalDate.parse(updated.lastStreakDate) }.getOrNull()
            if (lastStreak != null && lastStreak.isBefore(todayDate.minusDays(1))) {
                val missedDays = java.time.temporal.ChronoUnit.DAYS.between(lastStreak, todayDate).toInt()
                updated = updated.copy(
                    streakDays = (updated.streakDays - missedDays).coerceAtLeast(0),
                    lastStreakDate = ""
                )
            }
        }

        if (updated != child) {
            updateChildProfile(updated)
        }
    }
}
