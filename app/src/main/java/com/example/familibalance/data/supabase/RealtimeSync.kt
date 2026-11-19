package com.example.familibalance.data.supabase

import android.content.Context
import android.util.Log
import com.example.familibalance.data.storage.AppStateStorage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Realtime-подписка на family_profiles с авто-переподпиской. */
internal object RealtimeSync {

    /**
     * Бесконечный цикл подписки: при обрыве переподписывается с экспоненциальной
     * задержкой (1с → 30с). Выход — только через CancellationException
     * (stopRealtimeSync отменяет job).
     */
    suspend fun runSubscriptionLoop(supabase: SupabaseClient, context: Context) = coroutineScope {
        var retryDelayMs = 1_000L
        val maxRetryDelayMs = 30_000L
        // uid родителя на момент подписки — для серверного фильтра Realtime
        var subscribedUid: String? = null

        // При смене сессии (login/logout) удаляем активный канал — collect упадёт,
        // и цикл переподпишется уже с новым parent_user_id в фильтре.
        launch {
            supabase.auth.sessionStatus.collect { status ->
                val newUid = (status as? SessionStatus.Authenticated)?.session?.user?.id
                if (newUid != subscribedUid) {
                    SupabaseManager.realtimeChannel?.let { ch ->
                        runCatching { supabase.realtime.removeChannel(ch) }
                    }
                }
            }
        }

        while (true) {
            var myChannel: RealtimeChannel? = null
            try {
                // UID берём свежим при каждой попытке подписки:
                // пользователь мог залогиниться/разлогиниться между retry.
                val currentUid = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
                subscribedUid = currentUid

                val channel = supabase.realtime.channel("family_profiles_realtime")
                myChannel = channel
                SupabaseManager.realtimeChannel = channel

                val changeFlow = buildChangeFlows(channel, currentUid).merge()

                channel.subscribe()
                retryDelayMs = 1_000L // сбрасываем задержку после успешной подписки

                changeFlow.collect { action ->
                    try {
                        val record = when (action) {
                            is PostgresAction.Insert -> action.record
                            is PostgresAction.Update -> action.record
                            else -> null
                        }
                        if (record != null) {
                            val userId = record["user_id"]?.jsonPrimitive?.contentOrNull
                            val stateJson = record["state_json"]?.jsonPrimitive?.contentOrNull
                            val parentUserId = record["parent_user_id"]?.jsonPrimitive?.contentOrNull
                            val parentEmail = record["parent_email"]?.jsonPrimitive?.contentOrNull
                            val authUserId = record["auth_user_id"]?.jsonPrimitive?.contentOrNull
                            val status = record["status"]?.jsonPrimitive?.contentOrNull
                            val updatedAt = record["updated_at"]?.jsonPrimitive?.contentOrNull
                            if (!userId.isNullOrEmpty() && !stateJson.isNullOrEmpty()) {
                                val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                                val row = FamilyProfileRowTs(
                                    user_id = userId,
                                    state_json = stateJson,
                                    parentUserId = parentUserId,
                                    parentEmail = parentEmail,
                                    authUserId = authUserId,
                                    status = status,
                                    updatedAt = updatedAt
                                )
                                if (ProfileSync.isRowVisibleToUser(row, currentUser?.id, currentUser?.email)) {
                                    AppStateStorage.applyRemoteChildJsonAsync(context, userId, stateJson, parentUserId, status, authUserId, updatedAt)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("SupabaseManager", "Ошибка обработки записи Realtime: ${e.message}")
                    }
                }
                // collect завершился без исключения — не выходим из цикла,
                // а переподписываемся: канал мог быть закрыт сервером.
                Log.w("SupabaseManager", "Realtime flow завершился, переподписка через ${retryDelayMs}мс")
                SupabaseManager.isSubscribed = false
                removeChannelQuietly(supabase, myChannel)
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SupabaseManager.isSubscribed = false
                // Удаляем канал перед переподпиской, чтобы старые не копились в памяти
                removeChannelQuietly(supabase, myChannel)
                Log.e("SupabaseManager", "Обрыв Realtime, переподписка через ${retryDelayMs}мс: ${e.message}", e)
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
            }
        }
    }

    /**
     * Серверные фильтры Realtime:
     *  - parent_user_id = uid  → профили детей этого родителя;
     *  - auth_user_id   = uid  → собственный профиль ребёнка
     *    (его auth-uid хранится в auth_user_id, а user_id — это child.id,
     *    поэтому фильтр по user_id для ребёнка не работал).
     * Pending-строки (parent_user_id IS NULL) realtime не покрывает —
     * их подхватывает опрос loadPendingRequests в AdminViewModel.
     * Клиентская проверка isRowVisibleToUser остаётся как второй рубеж.
     */
    private fun buildChangeFlows(channel: RealtimeChannel, currentUid: String?): List<Flow<PostgresAction>> {
        return if (currentUid.isNullOrBlank()) {
            listOf(
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "family_profiles"
                }
            )
        } else {
            listOf(
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "family_profiles"
                    filter("parent_user_id", FilterOperator.EQ, currentUid)
                },
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "family_profiles"
                    filter("auth_user_id", FilterOperator.EQ, currentUid)
                }
            )
        }
    }

    private suspend fun removeChannelQuietly(supabase: SupabaseClient, channel: RealtimeChannel?) {
        channel?.let {
            try {
                supabase.realtime.removeChannel(it)
            } catch (ex: Exception) {
                Log.w("SupabaseManager", "Не удалось удалить Realtime-канал: ${ex.message}")
            }
        }
    }
}
