package com.example.familibalance.data.repository

import android.content.Context
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.supabase.SupabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Реализация [FamilyRepository] поверх текущего Storage Layer:
 * AppSharedState (в памяти) + AppStateStorage (SharedPreferences) + SupabaseManager (облако).
 */
class FamilyRepositoryImpl(context: Context) : FamilyRepository {

    private val appContext = context.applicationContext

    /** Защита от рекурсивного шторма: повторный вход в retryDirtyChildren игнорируется. */
    private val retryInProgress = AtomicBoolean(false)

    /** Число push-запросов в полёте — для UI-статуса «синхронизация идёт». */
    private val pendingPushes = AtomicInteger(0)
    private val _isSyncing = MutableStateFlow(false)

    override val isSynced: StateFlow<Boolean> = SyncStatus.isSynced
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override val onRemoteStateChanged: SharedFlow<Unit> = AppSharedState.remoteStateChanged

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
        // Изменённые профили отправляем через pushChildProfile: он помечает
        // их dirty в SyncStatus и повторяет отправку при неудаче (MVI-01).
        AppStateStorage.checkResets(appContext).forEach { pushChildProfile(it) }
    }

    override fun pushChildProfile(child: ChildProfileState) {
        // Помечаем dirty до отправки: входящие Realtime-обновления для этого
        // профиля будут игнорироваться, пока локальные правки не подтверждены.
        SyncStatus.markDirty(child.id)
        pendingPushes.incrementAndGet()
        _isSyncing.value = true
        SupabaseManager.pushChildProfile(appContext, child) { success ->
            try {
                if (success) {
                    SyncStatus.markClean(child.id)
                    // НЕ вызываем retryDirtyChildren() здесь: иначе каждый успешный push
                    // запускает новый каскадный обход всех dirty-профилей (O(n²) запросов).
                    // Остальные dirty-профили подхватит текущий/следующий проход retryDirtyChildren.
                }
                // При неудаче профиль остаётся dirty — повторим при следующем push.
            } finally {
                if (pendingPushes.decrementAndGet() <= 0) {
                    pendingPushes.set(0)
                    _isSyncing.value = false
                }
            }
        }
    }

    override fun syncNow(childId: String?) {
        AppStateStorage.saveLocalOnly(appContext)
        // Pull: подтягиваем свежие данные из облака (изменения с других устройств),
        // затем push: отправляем выбранного ребёнка и все dirty-профили.
        SupabaseManager.refreshFromCloud(appContext)
        val targetId = childId ?: selectedChildId
        AppSharedState.children[targetId]?.let { pushChildProfile(it) }
        retryDirtyChildren()
    }

    /** Повторяет отправку всех профилей, у которых есть неподтверждённые изменения. */
    private fun retryDirtyChildren() {
        // Один проход за раз: если синхронизация уже идёт (старт приложения +
        // NetworkMonitor + loadState могут вызвать её одновременно) — пропускаем.
        if (!retryInProgress.compareAndSet(false, true)) return
        try {
            // Снимок dirty-списка: новые dirty, появившиеся во время прохода,
            // будут обработаны следующим триггером (network/push/forceSync).
            SyncStatus.dirtyChildIdsSnapshot().forEach { id ->
                AppSharedState.children[id]?.let { pushChildProfile(it) }
            }
        } finally {
            retryInProgress.set(false)
        }
    }

    override fun updateChildProfile(profile: ChildProfileState) {
        // Модель иммутабельна: заменяем экземпляр в shared state,
        // чтобы UI и Repository видели актуальную версию профиля.
        AppSharedState.children[profile.id] = profile
        AppStateStorage.saveLocalOnly(appContext)
        pushChildProfile(profile)
    }

    override fun addAdminNotification(message: String) {
        AppSharedState.adminNotificationLog.add(0, message)
    }
}
