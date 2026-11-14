package com.example.familibalance.data.repository

import android.content.Context
import com.example.familibalance.AppSharedState
import com.example.familibalance.AppStateStorage
import com.example.familibalance.ChildProfileState
import com.example.familibalance.SupabaseManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Реализация [FamilyRepository] поверх текущего Storage Layer:
 * AppSharedState (в памяти) + AppStateStorage (SharedPreferences) + SupabaseManager (облако).
 */
class FamilyRepositoryImpl(context: Context) : FamilyRepository {

    private val appContext = context.applicationContext

    override val isSynced: StateFlow<Boolean> = SyncStatus.isSynced

    init {
        // Восстанавливаем dirty-статусы с диска и запускаем монитор сети
        SyncStatus.attach(appContext)
        NetworkMonitor.start(appContext)

        // Сеть появилась -> автоматически досылаем несинхронизированные профили
        NetworkMonitor.addListener { online ->
            if (online && SyncStatus.hasDirtyChildren()) {
                retryDirtyChildren()
            }
        }

        // Стартовая проверка: если остались dirty-профили с прошлого запуска —
        // пробуем отправить (при offline push просто неудачен, статус сохранится)
        retryDirtyChildren()
    }

    override fun childrenSnapshot(): Map<String, ChildProfileState> =
        AppSharedState.children.toMap()

    override fun getChild(userId: String): ChildProfileState? =
        AppSharedState.children[userId]

    override fun getAllChildren(): List<ChildProfileState> =
        AppSharedState.children.values.toList()

    override var selectedChildId: String
        get() = AppSharedState.selectedChildId
        set(value) {
            AppSharedState.selectedChildId = value
        }

    override fun selectChild(childId: String, pushRemote: Boolean) {
        AppSharedState.selectedChildId = childId
        if (pushRemote) {
            saveAndSync()
        } else {
            saveLocal()
        }
    }

    override fun loadState() {
        AppStateStorage.loadState(appContext)
        // После загрузки профилей досылаем всё, что осталось dirty с прошлого запуска
        retryDirtyChildren()
    }

    override fun saveLocal() {
        AppStateStorage.saveLocalOnly(appContext)
    }

    override fun saveAndSync(onlyChildId: String?) {
        AppStateStorage.saveLocalOnly(appContext)
        if (onlyChildId != null) {
            AppSharedState.children[onlyChildId]?.let { pushChildProfile(it) }
        } else {
            AppSharedState.children.values.forEach { pushChildProfile(it) }
        }
    }

    override fun checkResets() {
        AppStateStorage.checkResets(appContext)
    }

    override fun pushChildProfile(child: ChildProfileState) {
        // Помечаем dirty до отправки: входящие Realtime-обновления для этого
        // профиля будут игнорироваться, пока локальные правки не подтверждены.
        SyncStatus.markDirty(child.id)
        SupabaseManager.pushChildProfile(appContext, child) { success ->
            if (success) {
                SyncStatus.markClean(child.id)
                retryDirtyChildren()
            }
            // При неудаче профиль остаётся dirty — повторим при следующем push.
        }
    }

    override fun syncNow(childId: String?) {
        AppStateStorage.saveLocalOnly(appContext)
        val targetId = childId ?: selectedChildId
        AppSharedState.children[targetId]?.let { pushChildProfile(it) }
        retryDirtyChildren()
    }

    /** Повторяет отправку всех профилей, у которых есть неподтверждённые изменения. */
    private fun retryDirtyChildren() {
        SyncStatus.dirtyChildIdsSnapshot().forEach { id ->
            AppSharedState.children[id]?.let { pushChildProfile(it) }
        }
    }

    override fun updateChildProfile(profile: ChildProfileState) {
        AppStateStorage.saveLocalOnly(appContext)
        pushChildProfile(profile)
    }

    override fun addAdminNotification(message: String) {
        AppSharedState.adminNotificationLog.add(0, message)
    }
}
