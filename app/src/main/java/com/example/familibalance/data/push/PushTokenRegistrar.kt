package com.example.familibalance.data.push

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.supabase.SupabaseManager
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Регистрация FCM-токена устройства в таблице `device_tokens` (Supabase).
 *
 * user_id: для ребёнка — id профиля (family_profiles.user_id),
 *          для родителя — auth.uid() сессии Supabase.
 * role:    "child" | "parent" — разделяет потоки уведомлений, чтобы
 *          на одном устройстве родитель не получал детские пуши.
 */
object PushTokenRegistrar {

    private const val PREFS = "push_prefs"
    private const val KEY_USER_ID = "registered_user_id"
    private const val KEY_ROLE = "registered_role"
    private const val KEY_TOKEN = "registered_token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Вызывается из FCM-сервиса при onNewToken — перерегистрируем текущую роль. */
    fun onTokenRefreshed(context: Context, token: String) {
        val prefs = SecurePrefs.get(context, PREFS)
        val userId = prefs.getString(KEY_USER_ID, null)
        val role = prefs.getString(KEY_ROLE, null)
        if (userId.isNullOrBlank() || role.isNullOrBlank()) return
        upsert(context, userId, role, token)
    }

    /** Регистрирует устройство как «ребёнок» (user_id = id профиля ребёнка). */
    fun registerChild(context: Context, childId: String) {
        register(context, childId, "child")
    }

    /** Регистрирует устройство как «родитель» (user_id = auth.uid сессии). */
    fun registerParent(context: Context, parentAuthUid: String) {
        register(context, parentAuthUid, "parent")
    }

    private fun register(context: Context, userId: String, role: String) {
        val prefs = SecurePrefs.get(context, PREFS)
        Log.d("PushTokenRegistrar", "register called: role=$role user=$userId")
        // Тот же пользователь+роль уже зарегистрирован — перевыпуск токена
        // обработает onTokenRefreshed; повторный upsert не нужен.
        if (prefs.getString(KEY_USER_ID, null) == userId &&
            prefs.getString(KEY_ROLE, null) == role
        ) {
            Log.d("PushTokenRegistrar", "already registered, skip")
            return
        }
        scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                .onFailure { Log.w("PushTokenRegistrar", "token fetch failed: ${it.message}") }
                .getOrNull() ?: return@launch
            upsert(context, userId, role, token)
        }
    }

    private fun upsert(context: Context, userId: String, role: String, token: String) {
        val supabase = SupabaseManager.client
        if (supabase == null) {
            Log.w("PushTokenRegistrar", "supabase client is null, skip upsert")
            return
        }
        scope.launch {
            runCatching {
                supabase.postgrest.from("device_tokens").upsert(
                    buildJsonObject {
                        put("user_id", userId)
                        put("role", role)
                        put("fcm_token", token)
                        put("platform", "android")
                    }
                )
            }.onSuccess {
                SecurePrefs.get(context, PREFS).edit {
                    putString(KEY_USER_ID, userId)
                    putString(KEY_ROLE, role)
                    putString(KEY_TOKEN, token)
                }
                Log.d("PushTokenRegistrar", "token registered: role=$role user=$userId")
            }.onFailure {
                Log.w("PushTokenRegistrar", "token upsert failed: ${it.message}")
            }
        }
    }

    /**
     * Удаляет токен текущего устройства при выходе из аккаунта.
     * Без этого на общем устройстве пуши продолжали бы приходить
     * прежней роли.
     */
    fun unregister(context: Context) {
        val prefs = SecurePrefs.get(context, PREFS)
        val userId = prefs.getString(KEY_USER_ID, null)
        val role = prefs.getString(KEY_ROLE, null)
        val token = prefs.getString(KEY_TOKEN, null)
        prefs.edit { clear() }
        if (userId.isNullOrBlank() || token.isNullOrBlank()) return
        val supabase = SupabaseManager.client ?: return
        scope.launch {
            runCatching {
                supabase.postgrest.from("device_tokens").delete {
                    filter {
                        eq("user_id", userId)
                        eq("role", role ?: "")
                        eq("fcm_token", token)
                    }
                }
            }.onFailure {
                Log.w("PushTokenRegistrar", "token delete failed: ${it.message}")
            }
        }
    }
}
