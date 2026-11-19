package com.example.familibalance.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.familibalance.data.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Отслеживает состояние синхронизации с Supabase (offline-first).
 *
 * Профиль помечается dirty при попытке push и clean — при успехе.
 * Dirty-список персистится в SharedPreferences, поэтому переживает
 * перезапуск приложения: при старте и появлении сети dirty-профили
 * отправляются повторно.
 *
 * Пока есть dirty-профили, [isSynced] = false (UI может показать «Нет сети»),
 * а входящие Realtime-обновления для dirty-профилей игнорируются
 * (приоритет у локальных, ещё не отправленных изменений).
 */
object SyncStatus {

    private const val PREFS_NAME = "sync_status"
    private const val KEY_DIRTY_CHILD_IDS = "dirty_child_ids"
    private const val KEY_CLOUD_VERSIONS = "cloud_versions"

    private val _isSynced = MutableStateFlow(true)
    val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()

    private val dirtyChildIds = mutableSetOf<String>()
    /** user_id -> server updated_at последней синхронизированной версии облака. */
    private val cloudVersions = mutableMapOf<String, String>()
    private var prefs: SharedPreferences? = null

    /** Вызывать один раз при старте приложения (из FamilyRepositoryImpl). */
    @Synchronized
    fun attach(context: Context) {
        if (prefs != null) return
        prefs = SecurePrefs.get(context.applicationContext, PREFS_NAME)
        dirtyChildIds.addAll(prefs?.getStringSet(KEY_DIRTY_CHILD_IDS, emptySet()) ?: emptySet())
        (prefs?.getStringSet(KEY_CLOUD_VERSIONS, emptySet()) ?: emptySet())
            .forEach { entry ->
                val sep = entry.indexOf('|')
                if (sep > 0) cloudVersions[entry.substring(0, sep)] = entry.substring(sep + 1)
            }
        _isSynced.value = dirtyChildIds.isEmpty()
    }

    private fun persistDirtyIds() {
        prefs?.edit()?.putStringSet(KEY_DIRTY_CHILD_IDS, dirtyChildIds.toSet())?.apply()
    }

    private fun persistCloudVersions() {
        prefs?.edit()?.putStringSet(
            KEY_CLOUD_VERSIONS,
            cloudVersions.map { "${it.key}|${it.value}" }.toSet()
        )?.apply()
    }

    /** Server updated_at последней синхронизированной версии ("" — ещё не синхронизировали). */
    @Synchronized
    fun cloudVersion(childId: String): String = cloudVersions[childId].orEmpty()

    @Synchronized
    fun setCloudVersion(childId: String, serverTs: String?) {
        if (serverTs.isNullOrBlank()) return
        val prev = cloudVersions[childId]
        if (prev == null || isTsNewer(serverTs, prev)) {
            cloudVersions[childId] = serverTs
            persistCloudVersions()
        }
    }

    /**
     * Сравнение timestamptz-строк (ISO 8601 от PostgREST). Лексикографическое
     * сравнение корректно для одинакового смещения (+00:00) и переменной
     * дробной части; при нераспознанном формате — fallback на строковое.
     */
    fun isTsNewer(a: String, b: String): Boolean {
        val ia = runCatching { java.time.OffsetDateTime.parse(a).toInstant() }.getOrNull()
        val ib = runCatching { java.time.OffsetDateTime.parse(b).toInstant() }.getOrNull()
        return if (ia != null && ib != null) ia > ib else a > b
    }

    @Synchronized
    fun markDirty(childId: String) {
        dirtyChildIds.add(childId)
        _isSynced.value = false
        persistDirtyIds()
    }

    /**
     * Снимает dirty-метку с профиля.
     * @return true, если dirty-список стал пустым (всё синхронизировано).
     */
    @Synchronized
    fun markClean(childId: String): Boolean {
        dirtyChildIds.remove(childId)
        persistDirtyIds()
        val allClean = dirtyChildIds.isEmpty()
        if (allClean) {
            _isSynced.value = true
        }
        return allClean
    }

    @Synchronized
    fun isChildDirty(childId: String): Boolean = dirtyChildIds.contains(childId)

    @Synchronized
    fun hasDirtyChildren(): Boolean = dirtyChildIds.isNotEmpty()

    @Synchronized
    fun dirtyChildIdsSnapshot(): Set<String> = dirtyChildIds.toSet()
}
