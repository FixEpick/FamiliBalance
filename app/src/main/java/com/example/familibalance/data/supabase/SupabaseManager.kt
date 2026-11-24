package com.example.familibalance.data.supabase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.familibalance.BuildConfig
import com.example.familibalance.SupabaseConfig
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject

object SupabaseManager {
    internal val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        Log.e("SupabaseManager", "Uncaught exception in Supabase scope: ${throwable.message}", throwable)
        isSubscribed = false
    }

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    internal var isSubscribed = false
    /** Job Realtime-подписки: null/неактивен — канал закрыт (экономия батареи в фоне). */
    private var realtimeJob: Job? = null
    /** Активный Realtime-канал — нужен для removeChannel при остановке. */
    internal var realtimeChannel: RealtimeChannel? = null

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
                        // Deeplink для auth-писем (сброс пароля и т.п.):
                        // Supabase шлёт ссылку familibalance://auth#access_token=...
                        scheme = "familibalance"
                        host = "auth"
                    }
                    install(Realtime)
                    install(Storage)
                    install(Functions)
                }
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Ошибка инициализации клиента", e)
                null
            }
        }
    }

    /**
     * Гарантирует наличие сессии перед синхронизацией.
     * Ждёт завершения загрузки сохранённой сессии (до 5 сек), чтобы не создать
     * анонимную сессию поверх валидной родительской. Если пользователя нет
     * (авто-вход ребёнка, гость) — выполняет signInAnonymously: без сессии
     * push/upsert отклоняются, а dirty-профили навсегда блокируют isSynced.
     */
    suspend fun ensureSession() {
        Log.d("SupabaseManager", "Connecting to URL: ${BuildConfig.SUPABASE_URL}")
        val supabase = client ?: return
        withTimeoutOrNull(5_000) {
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }
        val hasUser = runCatching { supabase.auth.currentUserOrNull() }.getOrNull() != null
        if (!hasUser) {
            runCatching { supabase.auth.signInAnonymously() }
                .onFailure { Log.w("SupabaseManager", "signInAnonymously failed: ${it.message}") }
            return
        }
        // После долгого простоя access token протухает: первый запрос уходит
        // с expired token, и SDK тратит round-trip на авто-refresh. Обновляем
        // заранее, если токен истёк или истекает в ближайшие 2 минуты.
        val expiresAtSec = runCatching { supabase.auth.currentSessionOrNull()?.expiresAt }
            .getOrNull()
        val nowSec = System.currentTimeMillis() / 1000
        if (expiresAtSec != null && expiresAtSec - nowSec < 120L) {
            runCatching { supabase.auth.refreshCurrentSession() }
                .onFailure { Log.w("SupabaseManager", "refreshCurrentSession failed: ${it.message}") }
        }
    }

    /**
     * Перехватывает auth deep link (сброс пароля, подтверждение email)
     * и импортирует сессию из fragment/code ссылки.
     */
    fun handleAuthDeepLink(intent: Intent) {
        val supabase = client ?: return
        val data = intent.data
        Log.d("SupabaseManager", "Auth deeplink received: host=${data?.host}, hasFragment=${!data?.fragment.isNullOrBlank()}")
        // Логируем только ИМЕНА параметров фрагмента — значения содержат токены.
        data?.fragment?.split("&")?.forEach { part ->
            val key = part.substringBefore("=")
            if (key.isNotBlank()) Log.d("SupabaseManager", "deeplink fragment param: $key")
        }
        // Supabase может вернуть error-фрагмент (протухшая/использованная ссылка) —
        // в нём нет access_token, handleDeeplinks упадёт. Логируем и выходим.
        val fragment = data?.fragment.orEmpty()
        if (fragment.contains("error=") || fragment.contains("error_code=")) {
            Log.e("SupabaseManager", "Auth deeplink contains error params — link expired or invalid")
            return
        }
        runCatching {
            supabase.handleDeeplinks(
                intent,
                onSessionSuccess = { session ->
                    Log.d("SupabaseManager", "Auth session imported from deeplink: user=${session.user?.id}")
                }
            )
        }.onFailure { Log.e("SupabaseManager", "handleDeeplinks failed", it) }
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
            ProfileSync.loadProfilesFromCloud(context)
        }

        // 2. Подписка на изменения в реальном времени (Realtime) с авто-переподпиской
        realtimeJob = scope.launch(exceptionHandler) {
            RealtimeSync.runSubscriptionLoop(supabase, context)
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
    fun refreshFromCloud(context: Context) = ProfileSync.refreshFromCloud(context)

    /**
     * Отправляет обновлённое состояние конкретного ребёнка в Supabase
     * (а если есть локальное фото — сначала загружает его в облачный Storage)
     */
    fun pushChildProfile(context: Context, child: ChildProfileState, onResult: ((Boolean) -> Unit)? = null) =
        ProfileSync.pushChildProfile(context, child, onResult)

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
            runCatching { PhotoStorage.deleteQuestPhoto(url) }
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

    suspend fun deleteQuestPhoto(photoUrl: String): Boolean = PhotoStorage.deleteQuestPhoto(photoUrl)

    /**
     * Удаление профиля ребёнка по инициативе родителя (Настройки).
     * Порядок: 1) best-effort чистим Storage от фото профиля,
     * 2) DELETE строки family_profiles (user_id = childId),
     * 3) DELETE device_tokens ребёнка (best-effort),
     * 4) локально убираем профиль из AppSharedState и сохраняем.
     * Возвращает false, если облачное удаление не удалось (RLS/сеть) —
     * тогда локальный профиль сохраняем, чтобы не было рассинхрона.
     */
    suspend fun deleteChildProfile(context: Context, childId: String): Boolean {
        val supabase = client ?: return false
        val profile = AppSharedState.children[childId] ?: return false

        // 1. Storage: фото квестов/квестов-дневника этого профиля.
        val photoUrls = buildList {
            add(profile.closetPhotoUrl)
            add(profile.roomPhotoUrl)
            add(profile.diaryPhotoUrl)
            profile.bonusQuests.forEach { add(it.photoUrl) }
            profile.customQuests.forEach { add(it.photoUrl) }
        }.filterNotNull().filter { it.startsWith("http://") || it.startsWith("https://") }
        photoUrls.forEach { url -> runCatching { PhotoStorage.deleteQuestPhoto(url) } }

        // 2. DELETE family_profiles — под RLS родителя (parent_user_id = auth.uid()).
        try {
            supabase.from("family_profiles").delete {
                filter { eq("user_id", childId) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SupabaseManager", "deleteChildProfile family_profiles failed: ${e.message}", e)
            return false
        }

        // 3. device_tokens ребёнка — best-effort (политики могут не позволять).
        runCatching {
            supabase.from("device_tokens").delete {
                filter { eq("user_id", childId) }
            }
        }

        // 4. Локальное удаление.
        AppSharedState.children.remove(childId)
        if (AppSharedState.selectedChildId == childId) {
            AppSharedState.selectedChildId = AppSharedState.children.keys.firstOrNull() ?: "user_1"
        }
        com.example.familibalance.data.repository.SyncStatus.markClean(childId)
        AppStateStorage.saveLocalOnly(context)
        return true
    }
}
