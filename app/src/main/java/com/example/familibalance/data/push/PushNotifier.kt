package com.example.familibalance.data.push

import android.util.Log
import com.example.familibalance.data.supabase.SupabaseManager
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Отправка пушей через Supabase Edge Function `send-push`.
 *
 * Клиент НЕ хранит ключи Firebase — только вызывает функцию с payload'ом.
 * Edge Function сама находит fcm_token'ы в device_tokens и шлёт через
 * Firebase HTTP v1 API.
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
     */
    fun notify(
        targetUserId: String,
        targetRole: String,
        title: String,
        body: String,
        screen: String,
        channel: String,
        priority: String = "normal"
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
                    }
                )
            }.onFailure {
                Log.w("PushNotifier", "send-push failed: ${it.message}")
            }
        }
    }

    // ---------- Удобные обёртки под события приложения ----------

    /** Родителю: ребёнок отправил фотоотчёт на проверку. */
    fun photoReportSubmitted(parentUserId: String, childName: String, questTitle: String, reward: Int) {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Новый фотоотчёт на проверку",
            body = "$childName сдал(а) квест «$questTitle» (+$reward ₽).",
            screen = "verification",
            channel = PushChannels.PARENT_MODERATION,
            priority = "high"
        )
    }

    /** Родителю: заявка на вывод наличных. */
    fun cashoutRequested(parentUserId: String, childName: String, amount: Int) {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Запрос на вывод средств",
            body = "$childName хочет вывести $amount ₽.",
            screen = "verification",
            channel = PushChannels.PARENT_MODERATION,
            priority = "high"
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
            priority = "high"
        )
    }

    /** Родителю: цель-мечта достигнута. */
    fun dreamReached(parentUserId: String, childName: String, dreamTitle: String, target: Int) {
        notify(
            targetUserId = parentUserId,
            targetRole = "parent",
            title = "Цель-мечта накоплена!",
            body = "$childName накопил(а) на «$dreamTitle» ($target ₽).",
            screen = "progress",
            channel = PushChannels.PARENT_MODERATION
        )
    }

    /** Ребёнку: квест одобрен, можно забирать награду. */
    fun questApproved(childId: String, questTitle: String, reward: Int) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Квест выполнен! +$reward ₽",
            body = "Родитель проверил «$questTitle». Забери награду в заданиях!",
            screen = "tasks",
            channel = PushChannels.QUESTS,
            priority = "high"
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
            channel = PushChannels.QUESTS
        )
    }

    /** Ребёнку: вывод наличных одобрен. */
    fun cashoutApproved(childId: String, amount: Int) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Заявка на вывод одобрена!",
            body = "Родитель подтвердил вывод $amount ₽. Можно забирать!",
            screen = "main",
            channel = PushChannels.QUESTS,
            priority = "high"
        )
    }

    /** Ребёнку: новый бонус-квест от родителя. */
    fun bonusQuestAdded(childId: String, questTitle: String, reward: Int) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Новый квест в КвестЛабе!",
            body = "Появилось задание «$questTitle» на +$reward ₽.",
            screen = "tasks",
            channel = PushChannels.QUESTS
        )
    }

    /** Ребёнку: штраф / обновление баланса. */
    fun balanceUpdated(childId: String, amount: Int, reason: String) {
        notify(
            targetUserId = childId,
            targetRole = "child",
            title = "Обновление баланса: −$amount ₽",
            body = "Списан штраф: $reason.",
            screen = "history",
            channel = PushChannels.BALANCE
        )
    }
}
