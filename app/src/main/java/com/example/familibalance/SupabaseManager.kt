package com.example.familibalance

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class FamilyProfileRow(
    val user_id: String,
    val state_json: String,
    @SerialName("parent_user_id") val parentUserId: String? = null,
    @SerialName("parent_email") val parentEmail: String? = null,
    /** auth.uid анонимной сессии ребёнка — нужен Realtime/RLS на устройстве ребёнка. */
    @SerialName("auth_user_id") val authUserId: String? = null,
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
    /** Job Realtime-подписки: null/неактивен — канал закрыт (экономия батареи в фоне). */
    private var realtimeJob: Job? = null
    /** Активный Realtime-канал — нужен для removeChannel при остановке. */
    private var realtimeChannel: RealtimeChannel? = null

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
        // Дубликаты запрещены: если подписка уже активна — выходим.
        if (realtimeJob?.isActive == true) return
        isSubscribed = true

        // 1. Первичная загрузка профилей из базы (с фильтрацией по текущему родителю)
        scope.launch(exceptionHandler) {
            loadProfilesFromCloud(context)
        }

        // 2. Подписка на изменения в реальном времени (Realtime) с авто-переподпиской
        realtimeJob = scope.launch(exceptionHandler) {
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
                        realtimeChannel?.let { ch ->
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
                    realtimeChannel = channel

                    // Серверные фильтры Realtime:
                    //  - parent_user_id = uid  → профили детей этого родителя;
                    //  - auth_user_id   = uid  → собственный профиль ребёнка
                    //    (его auth-uid хранится в auth_user_id, а user_id — это child.id,
                    //    поэтому фильтр по user_id для ребёнка не работал).
                    // Pending-строки (parent_user_id IS NULL) realtime не покрывает —
                    // их подхватывает опрос loadPendingRequests в AdminViewModel.
                    // Клиентская проверка isRowVisibleToUser остаётся как второй рубеж.
                    val changeFlows: List<Flow<PostgresAction>> = if (currentUid.isNullOrBlank()) {
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
                    val changeFlow = changeFlows.merge()

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
                                if (!userId.isNullOrEmpty() && !stateJson.isNullOrEmpty()) {
                                    val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                                    val row = FamilyProfileRow(
                                        user_id = userId,
                                        state_json = stateJson,
                                        parentUserId = parentUserId,
                                        parentEmail = parentEmail,
                                        authUserId = authUserId,
                                        status = status
                                    )
                                    if (isRowVisibleToUser(row, currentUser?.id, currentUser?.email)) {
                                        AppStateStorage.applyRemoteChildJsonAsync(context, userId, stateJson, parentUserId, status, authUserId)
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
                    isSubscribed = false
                    myChannel?.let { channel ->
                        try {
                            supabase.realtime.removeChannel(channel)
                        } catch (ex: Exception) {
                            Log.w("SupabaseManager", "Не удалось удалить Realtime-канал: ${ex.message}")
                        }
                    }
                    kotlinx.coroutines.delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
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
     * Останавливает Realtime-подписку: отменяет job и закрывает канал.
     * Вызывается при ON_STOP — в фоне WebSocket не нужен (экономия батареи).
     * Повторный startRealtimeSync после этого создаст подписку заново.
     */
    fun stopRealtimeSync() {
        realtimeJob?.cancel()
        realtimeJob = null
        isSubscribed = false
        val channel = realtimeChannel
        realtimeChannel = null
        if (channel != null) {
            scope.launch {
                runCatching { client?.realtime?.removeChannel(channel) }
            }
        }
    }

    /**
     * Ручная перезагрузка всех видимых профилей из облака.
     * Вызывается кнопкой «Синхронизировать с сервером» — pull-часть синхронизации.
     */
    fun refreshFromCloud(context: Context) {
        if (client == null) return
        scope.launch(exceptionHandler) {
            loadProfilesFromCloud(context)
        }
    }

    /** Загружает строки family_profiles и применяет видимые текущему пользователю. */
    private suspend fun loadProfilesFromCloud(context: Context) {
        val supabase = client ?: return
        try {
            val rows = supabase.from("family_profiles")
                .select(columns = Columns.list("user_id", "state_json", "parent_user_id", "parent_email", "auth_user_id", "status"))
                .decodeList<FamilyProfileRow>()

            val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            val uid = currentUser?.id
            val email = currentUser?.email

            rows.filter { isRowVisibleToUser(it, uid, email) }
                .forEach { row ->
                    AppStateStorage.applyRemoteChildJsonAsync(context, row.user_id, row.state_json, row.parentUserId, row.status, row.authUserId)
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка загрузки профилей из облака: ${e.message}")
        }
    }

    /**
     * Изоляция данных: авторизованный родитель видит только свои профили.
     * Правило: parent_user_id == auth.uid(), либо (пока нет RLS) parentUserId пуст,
     * но parentEmail внутри state_json совпадает с email текущего пользователя.
     * Гость без сессии (uid == null) не видит ничего.
     */
    private fun isRowVisibleToUser(row: FamilyProfileRow, uid: String?, email: String?): Boolean {
        // Без сессии (гость) чужие строки не показываем — строгая клиентская фильтрация.
        // Анонимный пользователь Supabase имеет uid, поэтому собственный профиль виден.
        if (uid == null) return false
        // Собственный профиль виден всегда (в т.ч. анонимному ребёнку)
        if (row.user_id == uid) return true
        if (row.parentUserId == uid) return true
        // Профиль ребёнка на его устройстве: auth.uid анонимной сессии == auth_user_id.
        if (!row.authUserId.isNullOrBlank() && row.authUserId == uid) return true
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

                // Пользователь мог выйти из аккаунта, пока шла загрузка фото —
                // не воскрешаем профиль в AppSharedState и не пишем в облако.
                if (runCatching { supabase.auth.currentUserOrNull() }.getOrNull() == null) {
                    onResult?.invoke(false)
                    return@launch
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

                val jsonString = AppStateStorage.serializeSingleChild(updatedChild)
                val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
                // uid сессии считаем «родительским» только если у пользователя есть email
                // (анонимный ребёнок не должен записываться в parent_user_id)
                val sessionParentUid = currentUser?.id?.takeIf { !currentUser.email.isNullOrBlank() }
                // auth_user_id — uid анонимной сессии ребёнка. Берём из профиля,
                // а если пусто и текущая сессия анонимная — подставляем её uid
                // (самолечение старых профилей без authUserId).
                val sessionAuthUid = currentUser?.id?.takeIf { currentUser.email.isNullOrBlank() }
                val authId = updatedChild.authUserId.ifBlank { sessionAuthUid.orEmpty() }
                // Не затираем существующую привязку: берём parentUserId профиля,
                // а если его нет — uid родительской сессии
                val parentId = updatedChild.parentUserId.ifBlank { sessionParentUid.orEmpty() }
                supabase.from("family_profiles").upsert(
                    FamilyProfileRow(
                        user_id = child.id,
                        state_json = jsonString,
                        parentUserId = parentId.ifBlank { null },
                        parentEmail = updatedChild.parentEmail.ifBlank { null },
                        authUserId = authId.ifBlank { null },
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

    /**
     * Полное удаление аккаунта родителя (Google Play requirement).
     * Порядок: 1) чистим Storage от фото квестов всех детей,
     * 2) RPC delete_my_account удаляет family_profiles и auth.users,
     * 3) локальный signOut. Возвращает true при успехе RPC.
     */
    suspend fun deleteAccount(context: Context): Boolean {
        val supabase = client ?: return false

        // 1. Storage: собираем все http(s) URL фото из локальных профилей.
        val photoUrls = AppSharedState.children.values.flatMap { child ->
            buildList {
                add(child.closetPhotoUrl)
                add(child.roomPhotoUrl)
                add(child.diaryPhotoUrl)
                child.bonusQuests.forEach { add(it.photoUrl) }
                child.customQuests.forEach { add(it.photoUrl) }
            }
        }.filterNotNull().filter { it.startsWith("http://") || it.startsWith("https://") }

        photoUrls.forEach { url ->
            runCatching { deleteQuestPhoto(url) }
        }

        // 2. RPC: удаляет строки family_profiles и самого auth.users.
        try {
            supabase.postgrest.rpc("delete_my_account", buildJsonObject { })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "delete_my_account RPC failed: ${e.message}", e)
            return false
        }

        // 3. Локальный signOut — best-effort: пользователь на сервере уже удалён.
        runCatching { supabase.auth.signOut() }
        return true
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
