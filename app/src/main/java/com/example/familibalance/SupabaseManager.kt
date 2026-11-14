package com.example.familibalance

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class FamilyProfileRow(
    val user_id: String,
    val state_json: String,
    @SerialName("parent_user_id") val parentUserId: String? = null,
    @SerialName("parent_email") val parentEmail: String? = null,
    val status: String? = null
)

object SupabaseManager {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        Log.e("SupabaseManager", "Uncaught exception in Supabase scope: ${throwable.message}", throwable)
        isSubscribed = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private var isSubscribed = false

    val client: SupabaseClient? by lazy {
        if (!SupabaseConfig.isConfigured()) {
            Log.w("SupabaseManager", "Supabase ключи ещё не настроены!")
            null
        } else {
            try {
                createSupabaseClient(
                    supabaseUrl = SupabaseConfig.SUPABASE_URL,
                    supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
                ) {
                    httpEngine = OkHttp.create()
                    install(Postgrest)
                    install(Auth) {
                        // SharedPreferences для сохранения сессии (автовход)
                        sessionManager = SettingsSessionManager()
                    }
                    install(Realtime)
                    install(Storage)
                }
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Ошибка инициализации клиента", e)
                null
            }
        }
    }

    /**
     * Запускает загрузку данных из облака и подписку на обновления в реальном времени
     */
    fun startRealtimeSync(context: Context) {
        val supabase = client ?: return
        if (isSubscribed) return
        isSubscribed = true

        // 1. Первичная загрузка профилей из базы (с фильтрацией по текущему родителю)
        scope.launch(exceptionHandler) {
            try {
                val rows = supabase.from("family_profiles")
                    .select()
                    .decodeList<FamilyProfileRow>()

                val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                val uid = currentUser?.id
                val email = currentUser?.email

                rows.filter { isRowVisibleToUser(it, uid, email) }
                    .forEach { row ->
                        AppStateStorage.applyRemoteChildJsonAsync(context, row.user_id, row.state_json, row.parentUserId, row.status)
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SupabaseManager", "Ошибка первичной загрузки: ${e.message}")
            }
        }

        // 2. Подписка на изменения в реальном времени (Realtime) с авто-переподпиской
        scope.launch(exceptionHandler) {
            var retryDelayMs = 1_000L
            val maxRetryDelayMs = 30_000L

            while (true) {
                var myChannel: RealtimeChannel? = null
                try {
                    val channel = supabase.realtime.channel("family_profiles_realtime")
                    myChannel = channel
                    val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "family_profiles"
                    }

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
                                val status = record["status"]?.jsonPrimitive?.contentOrNull
                                if (!userId.isNullOrEmpty() && !stateJson.isNullOrEmpty()) {
                                    val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                                    val row = FamilyProfileRow(
                                        user_id = userId,
                                        state_json = stateJson,
                                        parentUserId = parentUserId,
                                        parentEmail = parentEmail,
                                        status = status
                                    )
                                    if (isRowVisibleToUser(row, currentUser?.id, currentUser?.email)) {
                                        AppStateStorage.applyRemoteChildJsonAsync(context, userId, stateJson, parentUserId, status)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("SupabaseManager", "Ошибка обработки записи Realtime: ${e.message}")
                        }
                    }
                    // collect завершился без исключения — выходим из цикла
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    isSubscribed = false
                    // Удаляем канал перед переподпиской, чтобы старые не копились в памяти
                    myChannel?.let { channel ->
                        try {
                            supabase.realtime.removeChannel(channel)
                        } catch (ex: Exception) {
                            Log.w("SupabaseManager", "Не удалось удалить Realtime-канал: ${ex.message}")
                        }
                    }
                    Log.e("SupabaseManager", "Обрыв Realtime, переподписка через ${retryDelayMs}мс: ${e.message}", e)
                    kotlinx.coroutines.delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
                }
            }
        }
    }

    /**
     * Изоляция данных: авторизованный родитель видит только свои профили.
     * Правило: parent_user_id == auth.uid(), либо (пока нет RLS) parentUserId пуст,
     * но parentEmail внутри state_json совпадает с email текущего пользователя.
     * Гость (uid == null) видит всё — как раньше.
     */
    private fun isRowVisibleToUser(row: FamilyProfileRow, uid: String?, email: String?): Boolean {
        if (uid == null) return true
        // Собственный профиль виден всегда (в т.ч. анонимному ребёнку)
        if (row.user_id == uid) return true
        if (row.parentUserId == uid) return true
        if (!row.parentUserId.isNullOrBlank()) return false
        // Pending-запрос: виден родителю по parent_email (колонка или state_json)
        val rowParentEmail = row.parentEmail
        if (!rowParentEmail.isNullOrBlank()) {
            return !email.isNullOrBlank() && rowParentEmail.equals(email, ignoreCase = true)
        }
        val parentEmail = runCatching {
            Json.parseToJsonElement(row.state_json)
                .jsonObject["parentEmail"]
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return !email.isNullOrBlank() && parentEmail.equals(email, ignoreCase = true)
    }

    /**
     * Отправляет обновлённое состояние конкретного ребёнка в Supabase
     * (а если есть локальное фото — сначала загружает его в облачный Storage)
     */
    fun pushChildProfile(context: Context, child: ChildProfileState, onResult: ((Boolean) -> Unit)? = null) {
        val supabase = client ?: run {
            onResult?.invoke(false)
            return
        }

        scope.launch(exceptionHandler) {
            try {
                var hasUploadedNewPhoto = false

                // Читаем текущие значения фото (snapshot-state читается с любого потока)
                val closetUrl = child.closetPhotoUrl
                val roomUrl = child.roomPhotoUrl
                val diaryUrl = child.diaryPhotoUrl
                val bonusPhotos = child.bonusQuests.map { it.id to it.photoUrl }
                val customPhotos = child.customQuests.map { it.id to it.photoUrl }
                val pendingPhotos = child.pendingQuests.mapIndexed { i, q -> i to q.photoUri }

                // Сетевые загрузки — на IO (мы уже в scope с Dispatchers.IO)
                suspend fun uploadIfLocal(current: String?): String? {
                    if (current.isNullOrBlank()) return current
                    val uri = runCatching { Uri.parse(current) }.getOrNull() ?: return current
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return current
                    val publicUrl = withTimeoutOrNull(30_000) {
                        uploadPhotoToStorage(context, child.id, uri)
                    } ?: return current
                    hasUploadedNewPhoto = true
                    return publicUrl
                }

                val newCloset = uploadIfLocal(closetUrl)
                val newRoom = uploadIfLocal(roomUrl)
                val newDiary = uploadIfLocal(diaryUrl)
                val newBonus = bonusPhotos.map { (id, url) -> id to uploadIfLocal(url) }
                val newCustom = customPhotos.map { (id, url) -> id to uploadIfLocal(url) }

                val newPending = pendingPhotos.map { (index, uri) ->
                    if (uri != null) {
                        val scheme = uri.scheme?.lowercase()
                        if (scheme != "http" && scheme != "https") {
                            val publicUrl = withTimeoutOrNull(30_000) {
                                uploadPhotoToStorage(context, child.id, uri)
                            }
                            if (publicUrl != null) {
                                hasUploadedNewPhoto = true
                                index to Uri.parse(publicUrl)
                            } else index to uri
                        } else index to uri
                    } else index to uri
                }

                // Применяем результаты на Main: собираем новый иммутабельный профиль
                val updatedChild = withContext(Dispatchers.Main) {
                    var updated = child.copy(
                        closetPhotoUrl = newCloset,
                        roomPhotoUrl = newRoom,
                        diaryPhotoUrl = newDiary
                    )

                    newBonus.forEach { (id, url) ->
                        updated = updated.copy(
                            bonusQuests = updated.bonusQuests.map {
                                if (it.id == id) it.copy(photoUrl = url) else it
                            }
                        )
                    }

                    newCustom.forEach { (id, url) ->
                        updated = updated.copy(
                            customQuests = updated.customQuests.map {
                                if (it.id == id) it.copy(photoUrl = url) else it
                            }
                        )
                    }

                    newPending.forEach { (index, uri) ->
                        if (index < updated.pendingQuests.size && updated.pendingQuests[index].photoUri != uri) {
                            updated = updated.copy(
                                pendingQuests = updated.pendingQuests.mapIndexed { i, q ->
                                    if (i == index) q.copy(photoUri = uri) else q
                                }
                            )
                        }
                    }

                    AppSharedState.children[child.id] = updated
                    updated
                }

                if (hasUploadedNewPhoto) {
                    withContext(Dispatchers.Main) {
                        AppStateStorage.saveLocalOnly(context)
                    }
                }

                val jsonString = AppStateStorage.serializeSingleChild(updatedChild).toString()
                val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                // uid сессии считаем «родительским» только если у пользователя есть email
                // (анонимный ребёнок не должен записываться в parent_user_id)
                val sessionParentUid = currentUser?.id?.takeIf { !currentUser.email.isNullOrBlank() }
                // Не затираем существующую привязку: берём parentUserId профиля,
                // а если его нет — uid родительской сессии
                val parentId = updatedChild.parentUserId.ifBlank { sessionParentUid.orEmpty() }
                supabase.from("family_profiles").upsert(
                    FamilyProfileRow(
                        user_id = child.id,
                        state_json = jsonString,
                        parentUserId = parentId.ifBlank { null },
                        parentEmail = updatedChild.parentEmail.ifBlank { null },
                        status = updatedChild.status.ifBlank { "active" }
                    )
                )
                onResult?.invoke(true)
                Log.d("SupabaseManager", "Профиль ${child.id} успешно отправлен в Supabase")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isRlsOrAuthError(e)) {
                    Log.e(
                        "SupabaseManager",
                        "Ошибка RLS/доступа при отправке профиля ${child.id}: ${e.message}. " +
                                "Проверьте политики доступа (RLS) таблицы family_profiles в Supabase",
                        e
                    )
                } else {
                    Log.e("SupabaseManager", "Ошибка отправки профиля ${child.id} в Supabase: ${e.message}", e)
                }
                onResult?.invoke(false)
            }
        }
    }

    /** Определяет ошибки доступа: RLS-политики, 401/403, проблемы с JWT/anon-ключом. */
    private fun isRlsOrAuthError(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        val className = e::class.simpleName.orEmpty()
        return msg.contains("row-level security", ignoreCase = true) ||
                msg.contains("new row violates", ignoreCase = true) ||
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("Unauthorized", ignoreCase = true) ||
                msg.contains("JWT", ignoreCase = true) ||
                className.contains("Unauthorized", ignoreCase = true)
    }

    private suspend fun uploadPhotoToStorage(context: Context, userId: String, uri: Uri): String? {
        val supabase = client ?: return null
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8)
            val fileName = "$userId/quest_${System.currentTimeMillis()}_$uniqueId.jpg"
            val bucket = supabase.storage.from("quest-photos")
            bucket.upload(fileName, bytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка загрузки фото в Storage: ${e.message}")
            null
        }
    }

    suspend fun deleteQuestPhoto(photoUrl: String): Boolean {
        if (photoUrl.isBlank()) {
            return true
        }

        val supabase = client ?: return true

        // Извлекаем путь относительно бакета quest-photos, убирая query-параметры
        val fileName = photoUrl
            .substringAfter("/quest-photos/", "")
            .substringBefore("?")
            .substringBefore("#")

        if (fileName.isBlank()) {
            Log.w("SupabaseManager", "Не удалось извлечь путь к файлу из URL: $photoUrl")
            return false
        }

        return try {
            supabase.storage.from("quest-photos").delete(fileName)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка удаления фото из Storage: ${e.message}")
            false
        }
    }
}
