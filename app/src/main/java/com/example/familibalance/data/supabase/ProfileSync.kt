package com.example.familibalance.data.supabase

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import com.example.familibalance.data.model.ChildProfileDto
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.repository.SyncStatus
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    /** auth.uid анонимной сессии ребёнка — нужен Realtime/RLS на устройстве ребёнка. */
    @SerialName("auth_user_id") val authUserId: String? = null,
    val status: String? = null
)

/** Строка с серверным updated_at — только для SELECT (в upsert не входит,
 *  чтобы не затирать значение триггера). */
@Serializable
data class FamilyProfileRowTs(
    val user_id: String,
    val state_json: String,
    @SerialName("parent_user_id") val parentUserId: String? = null,
    @SerialName("parent_email") val parentEmail: String? = null,
    @SerialName("auth_user_id") val authUserId: String? = null,
    val status: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** Синхронизация профилей: pull из облака, push в облако, фильтрация видимости. */
internal object ProfileSync {

    /** JSON для чтения stateUpdatedAt из удалённого state_json (last-writer-wins). */
    private val stateJsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Загружает строки family_profiles и применяет видимые текущему пользователю. */
    suspend fun loadProfilesFromCloud(context: Context) {
        val supabase = SupabaseManager.client ?: return
        // Ждём готовности сессии (в т.ч. авто-refresh протухшего токена
        // после долгого простоя) — иначе SELECT уходит с expired JWT.
        SupabaseManager.ensureSession()
        try {
            val rows = supabase.from("family_profiles")
                .select(columns = Columns.list("user_id", "state_json", "parent_user_id", "parent_email", "auth_user_id", "status", "updated_at"))
                .decodeList<FamilyProfileRowTs>()

            val currentUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            val uid = currentUser?.id
            val email = currentUser?.email

            rows.filter { isRowVisibleToUser(it, uid, email) }
                .forEach { row ->
                    AppStateStorage.applyRemoteChildJsonAsync(context, row.user_id, row.state_json, row.parentUserId, row.status, row.authUserId, row.updatedAt)
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "Ошибка загрузки профилей из облака: ${e.message}")
        }
    }

    /**
     * Ручная перезагрузка всех видимых профилей из облака.
     * Вызывается кнопкой «Синхронизировать с сервером» — pull-часть синхронизации.
     */
    fun refreshFromCloud(context: Context) {
        if (SupabaseManager.client == null) return
        SupabaseManager.scope.launch(SupabaseManager.exceptionHandler) {
            loadProfilesFromCloud(context)
        }
    }

    /**
     * Изоляция данных: авторизованный родитель видит только свои профили.
     * Правило: parent_user_id == auth.uid(), либо (пока нет RLS) parentUserId пуст,
     * но parentEmail внутри state_json совпадает с email текущего пользователя.
     * Гость без сессии (uid == null) не видит ничего.
     */
    fun isRowVisibleToUser(row: FamilyProfileRowTs, uid: String?, email: String?): Boolean {
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
        val supabase = SupabaseManager.client ?: run {
            onResult?.invoke(false)
            return
        }

        SupabaseManager.scope.launch(SupabaseManager.exceptionHandler) {
            // Ждём готовности сессии перед любой сетевой работой (upload фото,
            // select/upsert): при долгом простое access token протухает, и без
            // этого первый запрос уходил бы с expired JWT до авто-refresh.
            SupabaseManager.ensureSession()
            try {
                var hasUploadedNewPhoto = false
                // true, если хотя бы одно локальное фото не удалось загрузить
                // в Storage: тогда upsert с локальным URI делать НЕЛЬЗЯ —
                // админ получил бы битую ссылку content:///file://.
                // Прерываем push: профиль остаётся dirty и будет повторён.
                var uploadFailed = false

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
                    val uri = runCatching { current.toUri() }.getOrNull() ?: return current
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return current
                    val publicUrl = withTimeoutOrNull(30_000) {
                        PhotoStorage.uploadPhotoToStorage(context, child.id, uri)
                    }
                    if (publicUrl == null) {
                        uploadFailed = true
                        return current
                    }
                    // Локальный файл больше не нужен: в state_json уйдёт
                    // публичный URL, а filesDir не должен копить фото.
                    com.example.familibalance.util.PhotoStorage
                        .deleteLocalFileIfPresent(context, current)
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
                                PhotoStorage.uploadPhotoToStorage(context, child.id, uri)
                            }
                            if (publicUrl != null) {
                                com.example.familibalance.util.PhotoStorage
                                    .deleteLocalFileIfPresent(context, uri.toString())
                                hasUploadedNewPhoto = true
                                index to publicUrl.toUri()
                            } else {
                                uploadFailed = true
                                index to uri
                            }
                        } else index to uri
                    } else index to uri
                }

                // Хоть одно фото не загрузилось — прерываем push ДО upsert:
                // в облако не должен уйти локальный URI. Помечаем dirty,
                // чтобы retryDirtyChildren повторил попытку при появлении сети.
                if (uploadFailed) {
                    SyncStatus.markDirty(child.id)
                    onResult?.invoke(false)
                    Log.w("SupabaseManager", "Профиль ${child.id}: фото не загружено — push отложен")
                    return@launch
                }

                // Пользователь мог выйти из аккаунта, пока шла загрузка фото —
                // не воскрешаем профиль в AppSharedState и не пишем в облако.
                if (runCatching { supabase.auth.currentUserOrNull() }.getOrNull() == null) {
                    onResult?.invoke(false)
                    return@launch
                }

                // Применяем результаты на Main: собираем новый иммутабельный профиль.
                // ВАЖНО: берём СВЕЖИЙ снапшот из AppSharedState, а не устаревший
                // `child` — за время загрузки фото Realtime мог применить изменения
                // админа (approve/reject). Фото-URL подставляем только в те поля,
                // которые remote не трогал (значение совпадает со старым снапшотом).
                val updatedChild = withContext(Dispatchers.Main) {
                    val fresh = AppSharedState.children[child.id] ?: child
                    var updated = fresh.copy(
                        closetPhotoUrl = if (fresh.closetPhotoUrl == child.closetPhotoUrl) newCloset else fresh.closetPhotoUrl,
                        roomPhotoUrl = if (fresh.roomPhotoUrl == child.roomPhotoUrl) newRoom else fresh.roomPhotoUrl,
                        diaryPhotoUrl = if (fresh.diaryPhotoUrl == child.diaryPhotoUrl) newDiary else fresh.diaryPhotoUrl
                    )

                    newBonus.forEach { (id, url) ->
                        val origUrl = child.bonusQuests.firstOrNull { it.id == id }?.photoUrl
                        updated = updated.copy(
                            bonusQuests = updated.bonusQuests.map {
                                if (it.id == id && it.photoUrl == origUrl) it.copy(photoUrl = url) else it
                            }
                        )
                    }

                    newCustom.forEach { (id, url) ->
                        val origUrl = child.customQuests.firstOrNull { it.id == id }?.photoUrl
                        updated = updated.copy(
                            customQuests = updated.customQuests.map {
                                if (it.id == id && it.photoUrl == origUrl) it.copy(photoUrl = url) else it
                            }
                        )
                    }

                    newPending.forEach { (index, uri) ->
                        val origUri = child.pendingQuests.getOrNull(index)?.photoUri
                        if (index < updated.pendingQuests.size &&
                            updated.pendingQuests[index].photoUri == origUri &&
                            updated.pendingQuests[index].photoUri != uri
                        ) {
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
                    // Мы уже в scope с Dispatchers.IO — saveLocalOnly
                    // (JSON + EncryptedSharedPreferences) не должен идти на Main.
                    AppStateStorage.saveLocalOnly(context)
                }

                // Dirty только на время upsert: Realtime-обновления, пришедшие
                // во время загрузки фото, уже применены к fresh-снапшоту выше.
                SyncStatus.markDirty(child.id)
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

                // Last-writer-wins: если в облаке уже лежит более свежая версия
                // (например, админ отклонил квест, пока у нас шла загрузка фото),
                // не затираем её устаревшим снапшотом — применяем удалённую
                // версию локально и считаем синхронизацию достигнутой.
                // stateUpdatedAt == 0 — legacy-профиль без метки: всегда пишем.
                val remoteRow = runCatching {
                    supabase.from("family_profiles")
                        .select(columns = Columns.list("state_json", "parent_user_id", "parent_email", "auth_user_id", "status", "updated_at")) {
                            filter { eq("user_id", child.id) }
                        }
                        .decodeList<FamilyProfileRowTs>()
                        .firstOrNull()
                }.getOrNull()
                // Порядок версий — по СЕРВЕРНОМУ updated_at (часы Supabase), а не по
                // клиентским stateUpdatedAt: они расходятся между устройствами
                // (эмуляторы, ручная установка времени) и ломали LWW.
                val lastCloudVer = com.example.familibalance.data.repository.SyncStatus.cloudVersion(child.id)
                val remoteVer = remoteRow?.updatedAt
                val remoteChanged = remoteVer != null &&
                    (lastCloudVer.isEmpty() ||
                        com.example.familibalance.data.repository.SyncStatus.isTsNewer(remoteVer, lastCloudVer))
                if (remoteChanged) {
                    // Облако изменилось с нашей последней синхронизации — возможен
                    // конфликт. Tiebreak по клиентским часам (редкий случай).
                    val remoteIsNewer = remoteRow.let { row ->
                        runCatching {
                            val remoteTs = stateJsonFormat
                                .decodeFromString<ChildProfileDto>(row.state_json)
                                .stateUpdatedAt
                            remoteTs > 0 && remoteTs > updatedChild.stateUpdatedAt
                        }.getOrDefault(false)
                    }
                    if (remoteIsNewer) {
                        remoteRow.let { row ->
                            AppStateStorage.applyRemoteChildJsonAsync(
                                context, row.user_id, row.state_json,
                                row.parentUserId, row.status, row.authUserId, row.updatedAt
                            )
                        }
                        onResult?.invoke(true)
                        Log.d("SupabaseManager", "Профиль ${child.id}: удалённая версия новее — upsert пропущен")
                        return@launch
                    }
                }
                // !remoteChanged: облако не менялось с нашей последней синхронизации —
                // upsert безопасен при любых расхождениях клиентских часов.

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
                // Фиксируем серверную версию, которую только что записали —
                // следующие push'и смогут отличить «облако не менялось» от конфликта.
                runCatching {
                    supabase.from("family_profiles")
                        .select(columns = Columns.list("updated_at")) {
                            filter { eq("user_id", child.id) }
                        }
                        .decodeList<FamilyProfileRowTs>()
                        .firstOrNull()?.updatedAt
                }.getOrNull()?.let {
                    com.example.familibalance.data.repository.SyncStatus.setCloudVersion(child.id, it)
                }
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
    fun isRlsOrAuthError(e: Exception): Boolean {
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
}
