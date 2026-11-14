package com.example.familibalance

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class FamilyProfileRow(
    val user_id: String,
    val state_json: String
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

        // 1. Первичная загрузка всех профилей из базы
        scope.launch(exceptionHandler) {
            try {
                val rows = supabase.from("family_profiles")
                    .select()
                    .decodeList<FamilyProfileRow>()

                rows.forEach { row ->
                    AppStateStorage.applyRemoteChildJsonAsync(context, row.user_id, row.state_json)
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
                try {
                    val myChannel = supabase.realtime.channel("family_profiles_realtime")
                    val changeFlow = myChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "family_profiles"
                    }

                    myChannel.subscribe()
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
                                if (!userId.isNullOrEmpty() && !stateJson.isNullOrEmpty()) {
                                    AppStateStorage.applyRemoteChildJsonAsync(context, userId, stateJson)
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
                    Log.e("SupabaseManager", "Обрыв Realtime, переподписка через ${retryDelayMs}мс: ${e.message}", e)
                    kotlinx.coroutines.delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
                }
            }
        }
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

                // Применяем результаты на Main
                withContext(Dispatchers.Main) {
                    child.closetPhotoUrl = newCloset
                    child.roomPhotoUrl = newRoom
                    child.diaryPhotoUrl = newDiary

                    newBonus.forEach { (id, url) ->
                        val idx = child.bonusQuests.indexOfFirst { it.id == id }
                        if (idx != -1 && child.bonusQuests[idx].photoUrl != url) {
                            child.bonusQuests[idx] = child.bonusQuests[idx].copy(photoUrl = url)
                        }
                    }

                    newCustom.forEach { (id, url) ->
                        val idx = child.customQuests.indexOfFirst { it.id == id }
                        if (idx != -1 && child.customQuests[idx].photoUrl != url) {
                            child.customQuests[idx] = child.customQuests[idx].copy(photoUrl = url)
                        }
                    }

                    newPending.forEach { (index, uri) ->
                        if (index < child.pendingQuests.size && child.pendingQuests[index].photoUri != uri) {
                            val q = child.pendingQuests[index]
                            child.pendingQuests[index] = q.copy(photoUri = uri)
                        }
                    }
                }

                if (hasUploadedNewPhoto) {
                    withContext(Dispatchers.Main) {
                        AppStateStorage.saveLocalOnly(context)
                    }
                }

                val jsonString = AppStateStorage.serializeSingleChild(child).toString()
                supabase.from("family_profiles").upsert(
                    FamilyProfileRow(
                        user_id = child.id,
                        state_json = jsonString
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
