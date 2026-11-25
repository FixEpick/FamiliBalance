package com.example.familibalance.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.model.ChildProfileDto
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.model.QuestStatus
import com.example.familibalance.data.model.toDto
import com.example.familibalance.util.getCurrentDateString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object AppSharedState {
    var selectedChildId: String = "user_1"
    var tempImageUri: Uri? = null

    /** Событие: удалённое состояние применено к [children] (Realtime / первичная загрузка). */
    val remoteStateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    // Профили загружаются из локального хранилища / Supabase.
    // Дефолтных «Пользователь 1/2» больше нет — первый запуск показывает пустой список.
    // ConcurrentHashMap: children мутируется из Realtime-потока (IO) и Main —
    // обычный mutableMapOf давал ConcurrentModificationException.
    val children = ConcurrentHashMap<String, ChildProfileState>()

    val currentChild: ChildProfileState
        get() = children[selectedChildId]
            ?: children["user_1"]
            ?: children.values.firstOrNull()
            ?: ChildProfileState(id = "user_1", name = "Игрок", avatar = "🧒")

    val adminNotificationLog = mutableListOf<String>()
    var adminNotificationMessage: String? = null
}

object AppStateStorage {
    private const val PREFS_NAME = "familibalance_prefs"
    private const val KEY_STATE = "app_state_json"
    private const val KEY_LAST_DAILY = "last_daily_reset"
    private const val KEY_LAST_WEEKLY = "last_weekly_reset"

    internal val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun saveLocalOnly(context: Context) {
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val rootObj = org.json.JSONObject()
            val childrenObj = org.json.JSONObject()

            AppSharedState.children.forEach { (id, child) ->
                childrenObj.put(id, org.json.JSONObject(serializeSingleChild(child)))
            }
            rootObj.put("children", childrenObj)
            rootObj.put("selectedChildId", AppSharedState.selectedChildId)

            prefs.edit {
                putString(KEY_STATE, rootObj.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun serializeSingleChild(child: ChildProfileState): String {
        return jsonFormat.encodeToString(child.toDto())
    }

    /**
     * Удалённая версия строго новее локальной? По монотонному stateVersion;
     * при равных версиях (legacy-профили без счётчика) — по stateUpdatedAt.
     */
    private fun isRemoteStateNewer(dto: ChildProfileDto, local: ChildProfileState): Boolean =
        if (dto.stateVersion > 0 || local.stateVersion > 0) {
            dto.stateVersion > local.stateVersion
        } else {
            dto.stateUpdatedAt > local.stateUpdatedAt
        }

    fun applyRemoteChildJson(context: Context, userId: String, jsonStr: String, parentUserId: String? = null, status: String? = null, authUserId: String? = null, remoteUpdatedAt: String? = null) {
        try {
            val dto = jsonFormat.decodeFromString<ChildProfileDto>(jsonStr)
            // Дубль уже синхронизированной версии (server updated_at не новее
            // последней применённой) — пропускаем, чтобы echo собственных push'ей
            // и повторные Realtime-события не гоняли UI по кругу.
            val lastCloudVer = com.example.familibalance.data.repository.SyncStatus.cloudVersion(userId)
            if (remoteUpdatedAt != null && lastCloudVer.isNotEmpty() &&
                !com.example.familibalance.data.repository.SyncStatus.isTsNewer(remoteUpdatedAt, lastCloudVer)
            ) return
            // Dirty-профиль: локальные правки ещё не ушли в облако.
            // Применяем удалённую версию, только если она строго новее
            // локальной (last-writer-wins) — иначе одобрение админа
            // навсегда игнорировалось, а старый PENDING перезаписывал облако.
            // Критерий — монотонный stateVersion (часы устройств не важны);
            // для legacy-профилей без счётчика — fallback на stateUpdatedAt.
            if (com.example.familibalance.data.repository.SyncStatus.isChildDirty(userId)) {
                val local = AppSharedState.children[userId]
                if (local != null && !isRemoteStateNewer(dto, local)) return
            }
            // Колонка parent_user_id в БД важнее значения внутри state_json
            val profile = ChildProfileState.fromDto(dto)
                .copy(
                    parentUserId = parentUserId?.takeIf { it.isNotBlank() } ?: dto.parentUserId,
                    authUserId = authUserId?.takeIf { it.isNotBlank() } ?: dto.authUserId,
                    status = status?.takeIf { it.isNotBlank() } ?: dto.status
                )
            AppSharedState.children[userId] = profile
            saveLocalOnly(context)
            com.example.familibalance.data.repository.SyncStatus.setCloudVersion(userId, remoteUpdatedAt)
            // Удалённая версия применена — локальные несинхронизированные
            // правки устарели (last-writer-wins): снимаем dirty-метку,
            // иначе retryDirtyChildren позже отправит старый снапшот
            // и перезапишет approve/reject админа (ping-pong статусов).
            com.example.familibalance.data.repository.SyncStatus.markClean(userId)
            AppSharedState.remoteStateChanged.tryEmit(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Suspend-вариант: парсит JSON на IO-потоке, применяет на Main.
     * Используется из SupabaseManager (Realtime/первичная загрузка), чтобы не фризить UI.
     */
    suspend fun applyRemoteChildJsonAsync(context: Context, userId: String, jsonStr: String, parentUserId: String? = null, status: String? = null, authUserId: String? = null, remoteUpdatedAt: String? = null) {
        try {
            // Декод JSON + запись в children (ConcurrentHashMap) + saveLocalOnly
            // (JSON-сериализация + EncryptedSharedPreferences) — целиком на IO:
            // saveLocalOnly внутри withContext(Main) фризил UI на Realtime-обновлениях.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dto = jsonFormat.decodeFromString<ChildProfileDto>(jsonStr)
                // Дубль уже синхронизированной версии (server updated_at не новее
                // последней применённой) — пропускаем echo собственных push'ей.
                val lastCloudVer = com.example.familibalance.data.repository.SyncStatus.cloudVersion(userId)
                if (remoteUpdatedAt != null && lastCloudVer.isNotEmpty() &&
                    !com.example.familibalance.data.repository.SyncStatus.isTsNewer(remoteUpdatedAt, lastCloudVer)
                ) return@withContext
                // Dirty-профиль: применяем удалённую версию, только если она
                // строго новее локальной — иначе Realtime-апдейты админа
                // (approve/reject) терялись, пока профиль был dirty.
                // Сравнение по монотонному stateVersion; legacy (0/0) — по ts.
                if (com.example.familibalance.data.repository.SyncStatus.isChildDirty(userId)) {
                    val local = AppSharedState.children[userId]
                    if (local != null && !isRemoteStateNewer(dto, local)) {
                        // local.ts == remote.ts — это эхо собственного push'а
                        // (в облаке лежит наш же снапшот): не проблема, debug.
                        // local.ts > remote.ts — реальные локальные правки ещё
                        // не ушли в облако: пропуск осознанный, warning.
                        if (local.stateUpdatedAt == dto.stateUpdatedAt) {
                            android.util.Log.d(
                                "AppStateStorage",
                                "remote update SKIPPED (dirty+echo): user=$userId ts=${dto.stateUpdatedAt}"
                            )
                        } else {
                            android.util.Log.w(
                                "AppStateStorage",
                                "remote update SKIPPED (dirty+LWW): user=$userId " +
                                    "local.ts=${local.stateUpdatedAt} remote.ts=${dto.stateUpdatedAt}"
                            )
                        }
                        return@withContext
                    }
                }
                val profile = ChildProfileState.fromDto(dto)
                    .copy(
                        parentUserId = parentUserId?.takeIf { it.isNotBlank() } ?: dto.parentUserId,
                        authUserId = authUserId?.takeIf { it.isNotBlank() } ?: dto.authUserId,
                        status = status?.takeIf { it.isNotBlank() } ?: dto.status
                    )
                AppSharedState.children[userId] = profile
                saveLocalOnly(context)
                android.util.Log.d(
                    "AppStateStorage",
                    "remote update APPLIED: user=$userId ts=${dto.stateUpdatedAt} " +
                        "closet=${profile.closetStatus} room=${profile.roomStatus} diary=${profile.diaryStatus}"
                )
                com.example.familibalance.data.repository.SyncStatus.setCloudVersion(userId, remoteUpdatedAt)
                // См. applyRemoteChildJson: применённая удалённая версия
                // делает локальные правки устаревшими — dirty снимаем,
                // иначе старый PENDING перезапишет статус админа.
                com.example.familibalance.data.repository.SyncStatus.markClean(userId)
                AppSharedState.remoteStateChanged.tryEmit(Unit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(context: Context) {
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val jsonStr = prefs.getString(KEY_STATE, null) ?: return
            val rootObj = org.json.JSONObject(jsonStr)

            if (rootObj.has("selectedChildId")) {
                AppSharedState.selectedChildId = rootObj.getString("selectedChildId")
            }

            if (rootObj.has("children")) {
                val childrenObj = rootObj.getJSONObject("children")
                childrenObj.keys().forEach { id ->
                    if (childrenObj.has(id)) {
                        val childObj = childrenObj.getJSONObject(id)
                        try {
                            val dto = jsonFormat.decodeFromString<ChildProfileDto>(childObj.toString())
                            AppSharedState.children[id] = ChildProfileState.fromDto(dto).copy(id = id)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Дневной сброс boughtMinutesToday/bonusQuests.
     * Возвращает список изменённых профилей — репозиторий сам пометит их
     * dirty и отправит в Supabase (MVI-01: без прямого push в обход SyncStatus).
     */
    fun checkResets(context: Context): List<ChildProfileState> {
        val changedChildren = mutableListOf<ChildProfileState>()
        try {
            val prefs = SecurePrefs.get(context, PREFS_NAME)
            val calendar = Calendar.getInstance()
            val todayStr = getCurrentDateString()
            val currentWeekStr = "${calendar.get(Calendar.YEAR)}-W${calendar.get(Calendar.WEEK_OF_YEAR)}"

            val savedDaily = prefs.getString(KEY_LAST_DAILY, null)
            val savedWeekly = prefs.getString(KEY_LAST_WEEKLY, null)

            if (savedDaily == null || savedWeekly == null) {
                prefs.edit {
                    putString(KEY_LAST_DAILY, todayStr)
                    putString(KEY_LAST_WEEKLY, currentWeekStr)
                }
                return changedChildren
            }

            var changed = false

            // Хронологическое сравнение: сбрасываем только если сегодня
            // строго позже сохранённой даты. При откате времени/часового
            // пояса (сегодня «раньше» вчерашнего) сброс не срабатывает.
            val savedDailyDate = runCatching {
                java.time.LocalDate.parse(
                    savedDaily,
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                )
            }.getOrNull()
            val todayDate = java.time.LocalDate.now()
            val isNewDay = savedDailyDate == null || todayDate.isAfter(savedDailyDate)

            if (isNewDay) {
                // Сбросы встроенных квестов (шкаф/комната/дневник) выполняет
                // MainViewModel.resetDailyQuestsIfNeeded с учётом frequency каждого квеста.
                AppSharedState.children.replaceAll { _, child ->
                    val reset = child.copy(
                        boughtMinutesToday = 0,
                        bonusQuests = child.bonusQuests.filter { it.status != QuestStatus.CLAIMED }
                    )
                    if (reset != child) changedChildren += reset
                    reset
                }
                prefs.edit { putString(KEY_LAST_DAILY, todayStr) }
                changed = true
            }

            if (savedWeekly != currentWeekStr) {
                prefs.edit { putString(KEY_LAST_WEEKLY, currentWeekStr) }
            }

            if (changed) {
                saveLocalOnly(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return changedChildren
    }
}
