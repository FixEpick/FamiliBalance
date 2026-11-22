package com.example.familibalance.data.push

import android.util.Log
import com.example.familibalance.data.supabase.SupabaseManager
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Отправка пушей через Supabase Edge Function `send-push`.
 *
 * Клиент НЕ хранит ключи Firebase — только вызывает функцию с payload'ом.
 * Edge Function сама находит fcm_token'ы в device_tokens и шлёт через
 * Firebase HTTP v1 API.
 *
 * Локализация: каждая обёртка передаёт `title_key`/`body_key` (имена
 * string-ресурсов) и аргументы — получатель резолвит их в своей локали
 * (см. [PushTexts]). Текстовые `title`/`body` остаются как fallback
 * для старых версий приложения.
 *
 * Все вызовы fire-and-forget: ошибка сети/функции только логируется,
 * на UX приложения не влияет.
 */
object PushNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @param targetUserId id профиля ребёнка (child) или auth.uid родителя (parent)
     * @param targetRole   "child" | "parent"
     * @param screen       deep-link экран: "main"|"tasks"|"history"|"verification"|"progress"
     * @param channel      id канала: PushChannels.QUESTS / PARENT_MODERATION / BALANCE
     * @param priority     "high" | "normal"
     * @param titleKey/bodyKey   имена string-ресурсов для локализации на получателе
     * @param titleArgs/bodyArgs аргументы шаблона (JSON-массив строк в data-payload)
     */
    fun notify(
        targetUserId: String,
        targetRole: String,
        title: String,
        body: String,
        screen: String,
        channel: String,
        priority: String = "normal",
        titleKey: String? = null,
        bodyKey: String? = null,
        titleArgs: List<String> = emptyList(),
        bodyArgs: List<String> = emptyList()
    ) {
        if (targetUserId.isBlank()) return
        val supabase = SupabaseManager.client ?: return
        scope.launch {
            runCatching {
                supabase.functions.invoke(
                    function = "send-push",
                    body = buildJsonObject {
                        put("target_user_id", targetUserId)
                        put("target_role", targetRole)
                        put("title", title)
                        put("body", body)
                        put("screen", screen)
                        put("channel", channel)
                        put("priority", priority)
                        if (titleKey != null) put("title_key", titleKey)
                        if (bodyKey != null) put("body_key", bodyKey)
                        if (titleArgs.isNotEmpty()) {
                            put("title_args", buildJsonArray { titleArgs.forEach { add(JsonPrimitive(it)) } }.toString())
                        }
                        if (bodyArgs.isNotEmpty()) {
                            put("body_args", buildJsonArray { bodyArgs.forEach { add(JsonPrimitive(it)) } }.toString())
                        }
                    }
                )
            }.onFailure {
                Log.w("PushNotifier", "send-push failed: ${it.message}")
            }
        }
    }

    // ---------- Удобные обёртки под события приложения ----------

    /** Родителю: ребёнок отправил фотоотчёт на проверку. */
    fun photoReportSubmitted(parentUserId: String, childName: String, questTitle: String, reward: Int, currency: String = "₽") {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Новый фотоотчёт на проверку",
            body = "$childName сдал(а) квест «$questTitle» (+$reward $currency).",
            screen = "verification",
            channel = PushChannels.PARENT_MODERATION,
            priority = "high",
            titleKey = "push_photo_report_title",
            bodyKey = "push_photo_report_body",
            bodyArgs = listOf(childName, questTitle, reward.toString(), currency)
        )
    }

    /** Родителю: заявка на вывод наличных. */
    fun cashoutRequested(parentUserId: String, childName: String, amount: Int, currency: String = "₽") {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Запрос на вывод средств",
            body = "$childName хочет вывести $amount $currency.",
            screen = "verification",
            channel = PushChannels.PARENT_MODERATION,
            priority = "high",
            titleKey = "push_cashout_req_title",
            bodyKey = "push_cashout_req_body",
            bodyArgs = listOf(childName, amount.toString(), currency)
        )
    }

    /** Родителю: честное признание. */
    fun honestAdmissionSubmitted(parentUserId: String, childName: String) {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Честное признание от ребёнка",
            body = "$childName отправил(а) признание. Проверьте сообщение.",
            screen = "verification",
            channel = PushChannels.PARENT_MODERATION,
            priority = "high",
            titleKey = "push_honest_sub_title",
            bodyKey = "push_honest_sub_body",
            bodyArgs = listOf(childName)
        )
    }

    /** Родителю: цель-мечта достигнута. */
    fun dreamReached(parentUserId: String, childName: String, dreamTitle: String, target: Int, currency: String = "₽") {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Цель-мечта накоплена!",
            body = "$childName накопил(а) на «$dreamTitle» ($target $currency).",
            screen = "progress",
            channel = PushChannels.PARENT_MODERATION,
            titleKey = "push_dream_title",
            bodyKey = "push_dream_body",
            bodyArgs = listOf(childName, dreamTitle, target.toString(), currency)
        )
    }

    /** Ребёнку: квест одобрен, можно забирать награду. */
    fun questApproved(childId: String, questTitle: String, reward: Int, currency: String = "₽") {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Квест выполнен! +$reward $currency",
            body = "Родитель проверил «$questTitle». Забери награду в заданиях!",
            screen = "tasks",
            channel = PushChannels.QUESTS,
            priority = "high",
            titleKey = "push_quest_ok_title",
            bodyKey = "push_quest_ok_body",
            titleArgs = listOf(reward.toString(), currency),
            bodyArgs = listOf(questTitle)
        )
    }

    /** Ребёнку: квест отклонён, нужно переделать. */
    fun questRejected(childId: String, questTitle: String, reason: String) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Квест нужно переделать",
            body = "Родитель оставил комментарий к «$questTitle»: $reason.",
            screen = "tasks",
            channel = PushChannels.QUESTS,
            titleKey = "push_quest_rej_title",
            bodyKey = "push_quest_rej_body",
            bodyArgs = listOf(questTitle, reason)
        )
    }

    /** Ребёнку: вывод наличных одобрен. */
    fun cashoutApproved(childId: String, amount: Int, currency: String = "₽") {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Заявка на вывод одобрена!",
            body = "Родитель подтвердил вывод $amount $currency. Можно забирать!",
            screen = "main",
            channel = PushChannels.QUESTS,
            priority = "high",
            titleKey = "push_cashout_ok_title",
            bodyKey = "push_cashout_ok_body",
            bodyArgs = listOf(amount.toString(), currency)
        )
    }

    /** Ребёнку: вывод наличных отклонён. */
    fun cashoutRejected(childId: String, amount: Int, currency: String = "₽") {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Заявка на вывод отклонена",
            body = "Родитель отклонил вывод $amount $currency.",
            screen = "main",
            channel = PushChannels.QUESTS,
            titleKey = "push_cashout_rej_title",
            bodyKey = "push_cashout_rej_body",
            bodyArgs = listOf(amount.toString(), currency)
        )
    }

    /** Ребёнку: честное признание отклонено. */
    fun honestAdmissionRejected(childId: String) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Честное признание отклонено",
            body = "Родитель не принял признание. Можно отправить новое через 7 дней.",
            screen = "main",
            channel = PushChannels.QUESTS,
            titleKey = "push_honest_rej_title",
            bodyKey = "push_honest_rej_body"
        )
    }

    /** Ребёнку: новый бонус-квест от родителя. */
    fun bonusQuestAdded(childId: String, questTitle: String, reward: Int, currency: String = "₽") {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Новый квест в Семейном Балансе!",
            body = "Появилось задание «$questTitle» на +$reward $currency.",
            screen = "tasks",
            channel = PushChannels.QUESTS,
            titleKey = "push_bonus_quest_title",
            bodyKey = "push_bonus_quest_body",
            bodyArgs = listOf(questTitle, reward.toString(), currency)
        )
    }

    /** Ребёнку: штраф / обновление баланса. */
    fun balanceUpdated(childId: String, amount: Int, reason: String, currency: String = "₽") {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Обновление баланса: −$amount $currency",
            body = "Списан штраф: $reason.",
            screen = "history",
            channel = PushChannels.BALANCE,
            titleKey = "push_balance_title",
            bodyKey = "push_balance_body",
            titleArgs = listOf(amount.toString(), currency),
            bodyArgs = listOf(reason)
        )
    }
}
