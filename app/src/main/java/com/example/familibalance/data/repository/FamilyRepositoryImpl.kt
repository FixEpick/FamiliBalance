package com.example.familibalance.data.repository

import android.content.Context
import android.util.Log
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.supabase.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** Scope для отложенной IO-инициализации (SecurePrefs/сеть) — не блокирует main. */
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Вся инициализация — на IO: SyncStatus.attach читает
        // EncryptedSharedPreferences (дешифровка на каждый ключ),
        // retryDirtyChildren может дёргать сеть. На main это давало фризы.
        initScope.launch {
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

    /**
     * Локальное сохранение (JSON-сериализация всех профилей + запись в
     * EncryptedSharedPreferences) — только на IO. Вызов с main давал
     * Skipped frames: updateChildProfile/saveState дёргаются из UI.
     */
    private fun saveLocalAsync() {
        initScope.launch { AppStateStorage.saveLocalOnly(appContext) }
    }

    override fun saveLocal() {
        saveLocalAsync()
    }

    override fun saveAndSync(onlyChildId: String?) {
        saveLocalAsync()
        if (onlyChildId != null) {
            AppSharedState.children[onlyChildId]?.let { pushChildProfile(it) }
        } else {
            AppSharedState.children.values.forEach { pushChildProfile(it) }
        }
    }

    override fun checkResets() {
        // Изменённые профили отправляем через pushChildProfile: он помечает
        // их dirty в SyncStatus и повторяет отправку при неудаче (MVI-01).
        AppStateStorage.checkResets(appContext).forEach { changed ->
            // Сброс — локальное изменение: обновляем stateUpdatedAt, чтобы
            // last-writer-wins в ProfileSync не проигнорировал его.
            val stamped = changed.copy(stateUpdatedAt = System.currentTimeMillis())
            AppSharedState.children[stamped.id] = stamped
            pushChildProfile(stamped)
        }
    }

    /** Счётчик инициированных push'ей — наблюдаемый признак для тестов/диагностики
     *  (dirty-флаг теперь ставится только на время upsert внутри ProfileSync). */
    internal val pushAttemptCount = java.util.concurrent.atomic.AtomicInteger(0)

    override fun pushChildProfile(child: ChildProfileState) {
        pushAttemptCount.incrementAndGet()
        // Dirty ставится внутри ProfileSync непосредственно перед upsert:
        // загрузка фото может идти десятки секунд, и всё это время Realtime
        // не должен игнорировать изменения админа (approve/reject) — иначе
        // наш устаревший снапшот затирал бы их при upsert (рассинхрон).
        pendingPushes.incrementAndGet()
        _isSyncing.value = true
        SupabaseManager.pushChildProfile(appContext, child) { success ->
            try {
                if (success) {
                    SyncStatus.markClean(child.id)
                    // НЕ вызываем retryDirtyChildren() здесь: иначе каждый успешный push
                    // запускает новый каскадный обход всех dirty-профилей (O(n²) запросов).
                    // Остальные dirty-профили подхватит текущий/следующий проход retryDirtyChildren.
                    //
                    // Pull после push: локальный push мог завершиться ПОСЛЕ того, как
                    // админ уже изменил профиль в облаке (approve/reject квеста, заявка
                    // на вывод). Realtime-обновление в этом окне игнорировалось
                    // (профиль был dirty), а наш upsert мог затереть чужое изменение.
                    // Стягиваем свежее состояние — applyRemoteChildJsonAsync применит
                    // его, т.к. профиль уже clean.
                    SupabaseManager.refreshFromCloud(appContext)
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
        saveLocalAsync()
        // Pull: подтягиваем свежие данные из облака (изменения с других устройств),
        // затем push: отправляем выбранного ребёнка и все dirty-профили.
        SupabaseManager.refreshFromCloud(appContext)
        val targetId = childId ?: selectedChildId
        AppSharedState.children[targetId]?.let { pushChildProfile(it) }
        retryDirtyChildren()
        // Watchdog: если за 10 сек ни один push не завершился (сессия не создана,
        // сеть мёртвая, onResult не вызван) — принудительно сбрасываем isSyncing,
        // чтобы UI не висел на вечном спиннере. Сами push'и доработают в фоне,
        // а их колбэки безопасно обнулят счётчик (guard в pushChildProfile).
        initScope.launch {
            delay(10_000)
            if (_isSyncing.value && pendingPushes.get() > 0) {
                Log.w("FamilyRepository", "syncNow timeout: isSyncing принудительно сброшен")
                pendingPushes.set(0)
                _isSyncing.value = false
            }
        }
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
                val child = AppSharedState.children[id]
                if (child != null) {
                    pushChildProfile(child)
                } else if (AppSharedState.children.isNotEmpty()) {
                    // Профиль недоступен этому устройству (удалён или принадлежит
                    // другому родителю) — push невозможен, а dirty-метка заблокировала
                    // бы isSynced навсегда. Снимаем её.
                    // (children.isEmpty() = стейт ещё не загружен — не трогаем.)
                    SyncStatus.markClean(id)
                }
            }
        } finally {
            retryInProgress.set(false)
        }
    }

    override fun updateChildProfile(profile: ChildProfileState) {
        // Модель иммутабельна: заменяем экземпляр в shared state,
        // чтобы UI и Repository видели актуальную версию профиля.
        // stateUpdatedAt — метка последнего локального изменения для
        // last-writer-wins при синхронизации (ProfileSync сравнивает
        // с удалённой версией перед upsert).
        val stamped = profile.copy(stateUpdatedAt = System.currentTimeMillis())
        AppSharedState.children[stamped.id] = stamped
        saveLocalAsync()
        pushChildProfile(stamped)
    }

    override fun addAdminNotification(message: String) {
        AppSharedState.adminNotificationLog.add(0, message)
    }
}
