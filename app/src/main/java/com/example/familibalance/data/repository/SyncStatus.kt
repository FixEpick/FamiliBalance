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

    private val _isSynced = MutableStateFlow(true)
    val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()

    private val dirtyChildIds = mutableSetOf<String>()
    private var prefs: SharedPreferences? = null

    /** Вызывать один раз при старте приложения (из FamilyRepositoryImpl). */
    @Synchronized
    fun attach(context: Context) {
        if (prefs != null) return
        prefs = SecurePrefs.get(context.applicationContext, PREFS_NAME)
        dirtyChildIds.addAll(prefs?.getStringSet(KEY_DIRTY_CHILD_IDS, emptySet()) ?: emptySet())
        _isSynced.value = dirtyChildIds.isEmpty()
    }

    private fun persistDirtyIds() {
        prefs?.edit()?.putStringSet(KEY_DIRTY_CHILD_IDS, dirtyChildIds.toSet())?.apply()
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
