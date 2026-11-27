package com.example.familibalance.data.storage

import android.content.Context
import android.util.Log
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
    @Volatile
    var selectedChildId: String = "user_1"

    /** Событие: удалённое состояние применено к [children] (Realtime / первичная загрузка). */
    val remoteStateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    // Профили загружаются из локального хранилища / Supabase.
    // Дефолтных «Пользователь 1/2» больше нет — первый запуск показывает пустой список.
    // ConcurrentHashMap: children мутируется из Realtime-потока (IO) и Main —
    // обычный mutableMapOf давал ConcurrentModificationException.
    val children = ConcurrentHashMap<String, ChildProfileState>()

    /**
     * Единый lock для read-modify-write мутаций [children].
     * ConcurrentHashMap атомарен только на одну операцию put/get — связка
     * «прочитать свежую версию → трансформировать → записать» без lock'а
     * теряла обновления при гонке локальной правки и Realtime-применения.
     * Все записи в children должны идти через [write] (или быть единственным put).
     */
    private val writeLock = Any()

    fun <T> write(block: () -> T): T = synchronized(writeLock, block)

    /** Атомарный read-modify-write одного профиля. Возвращает новый экземпляр или null. */
    fun mutateChild(id: String, transform: (ChildProfileState) -> ChildProfileState): ChildProfileState? =
        write {
            val current = children[id] ?: return@write null
            val next = transform(current)
            if (next != current) children[id] = next
            next
        }

    /** Атомарный read-modify-write всех профилей (replaceAll под общим lock'ом). */
    fun mutateAll(transform: (ChildProfileState) -> ChildProfileState) {
        write {
            children.replaceAll { _, child -> transform(child) }
        }
    }

    val currentChild: ChildProfileState
        get() = children[selectedChildId]
            ?: children["user_1"]
            ?: children.values.firstOrNull()
            ?: ChildProfileState(id = "user_1", name = "Игрок", avatar = "🧒")
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

    /** Сериализует saveLocalOnly: параллельные вызовы (IO-корутины,
     *  Realtime, UI) не должны писать в prefs вразнобой. */
    private val saveLock = Any()

    fun saveLocalOnly(context: Context) {
        synchronized(saveLock) {
            saveLocalOnlyInternal(context)
        }
    }

    private fun saveLocalOnlyInternal(context: Context) {
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
            Log.e("AppStateStorage", "saveLocalOnly failed", e)
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
                // Проверка и запись — под общим write-lock: локальная правка
                // не может вклиниться между LWW-решением и put.
                val profile = AppSharedState.write {
                    var skip = false
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
                            skip = true
                        }
                    }
                    if (skip) {
                        null
                    } else {
                        ChildProfileState.fromDto(dto)
                            .copy(
                                parentUserId = parentUserId?.takeIf { it.isNotBlank() } ?: dto.parentUserId,
                                authUserId = authUserId?.takeIf { it.isNotBlank() } ?: dto.authUserId,
                                status = status?.takeIf { it.isNotBlank() } ?: dto.status
                            )
                            .also { AppSharedState.children[userId] = it }
                    }
                } ?: return@withContext
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
            Log.e("AppStateStorage", "applyRemoteChildJsonAsync failed", e)
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
                // Под общим write-lock: loadState может идти параллельно
                // с Realtime/локальными мутациями — не затираем их.
                AppSharedState.write {
                    childrenObj.keys().forEach { id ->
                        if (childrenObj.has(id)) {
                            val childObj = childrenObj.getJSONObject(id)
                            try {
                                val dto = jsonFormat.decodeFromString<ChildProfileDto>(childObj.toString())
                                AppSharedState.children[id] = ChildProfileState.fromDto(dto).copy(id = id)
                            } catch (e: Exception) {
                                Log.e("AppStateStorage", "child profile parse failed: $id", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppStateStorage", "loadState failed", e)
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
                AppSharedState.mutateAll { child ->
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
            Log.e("AppStateStorage", "checkResets failed", e)
        }
        return changedChildren
    }
}
