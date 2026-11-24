package com.example.familibalance.viewmodel.actions

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import com.example.familibalance.DiaryScanState
import com.example.familibalance.LoginResult
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.PinHasher
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.auth.AuthRepository
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

// Rate-limit входа ребёнка хранится в SecurePrefs (EncryptedSharedPreferences),
// чтобы счётчики и блокировки переживали перезапуск приложения.
// Ключи: "attempts_<rateKey>" -> Int, "lock_<rateKey>" -> epoch ms блокировки.
private fun MainViewModel.rateLimitPrefs() =
    SecurePrefs.get(getApplication(), "login_rate_limit_prefs")

private const val LOGIN_MAX_ATTEMPTS = 5
private const val LOGIN_LOCK_BASE_MS = 60_000L

/** Переключает активного ребёнка и эмитит новое состояние. pushRemote — полная синхронизация с Supabase. */
fun MainViewModel.selectChild(childId: String, pushRemote: Boolean = false) {
    familyRepository.selectChild(childId, pushRemote)
    // Сбрасываем стейт OCR-дневника: результат скана привязан к конкретному ребёнку
    _diaryScanState.value = DiaryScanState.Idle
    emitFamilyState()
}

/** Смена аватара ребёнка из детского раздела: copy() + updateChildProfile (push в Supabase). */
fun MainViewModel.updateAvatar(userId: String, avatar: String) {
    val profile = familyRepository.getChild(userId) ?: return
    updateChildProfile(profile.copy(avatar = avatar))
}

/** Смена ПИН-кода ребёнка из админки: copy() + updateChildProfile (push в Supabase). */
fun MainViewModel.updateChildPin(childId: String, newPin: String) {
    val child = familyRepository.getChild(childId) ?: return
    updateChildProfile(child.copy(pinCode = PinHasher.hash(newPin, PinHasher.generateSalt())))
}

/**
 * Создаёт новый профиль ребёнка из формы регистрации:
 * генерирует childId, сохраняет профиль (локально + push в Supabase),
 * пытается связать с родителем по email и выбирает профиль активным.
 */
fun MainViewModel.createChildProfile(
    nickname: String,
    pin: String,
    avatar: String,
    parentEmail: String,
    onResult: ((ChildProfileState) -> Unit)? = null
) {
    viewModelScope.launch {
        // Если сессии нет (устройство ребёнка) — входим анонимно,
        // чтобы у ребёнка был собственный auth.uid для своей строки в family_profiles.
        var sessionUser = runCatching {
            SupabaseManager.client?.auth?.currentUserOrNull()
        }.getOrNull()
        if (sessionUser == null) {
            runCatching { SupabaseManager.client?.auth?.signInAnonymously() }
            sessionUser = runCatching {
                SupabaseManager.client?.auth?.currentUserOrNull()
            }.getOrNull()
        }

        val sessionEmail = sessionUser?.email
        val sessionUid = sessionUser?.id.orEmpty()
        val email = sessionEmail?.trim().orEmpty().ifBlank { parentEmail.trim() }
        // Родительская сессия (есть email) -> профиль сразу active.
        // Анонимная сессия ребёнка -> pending, ждёт подтверждения родителя.
        val isParentSession = !sessionEmail.isNullOrBlank()
        val status = if (isParentSession) "active" else "pending"
        if (email.isBlank()) {
            android.util.Log.w("MainViewModel", "createChildProfile: нет email родителя — запрос не сможет быть подтверждён")
        }

        val childId = "user_${System.currentTimeMillis()}"
        val profile = ChildProfileState(
            id = childId,
            name = nickname.ifBlank { "Игрок" },
            avatar = avatar.ifBlank { "🧒" },
            // Пустой PIN -> pinCode="" (PIN не установлен): matches() вернёт
            // false, вход возможен только после установки PIN родителем.
            pinCode = if (pin.isBlank()) "" else PinHasher.hash(pin, PinHasher.generateSalt()),
            parentUserId = if (isParentSession) sessionUid else "",
            // auth_user_id — uid анонимной сессии ребёнка (нужен для Realtime/RLS).
            // При родительской сессии оставляем пустым: ребёнок привяжет свой uid при первом входе.
            authUserId = if (isParentSession) "" else sessionUid,
            parentEmail = email,
            status = status
        )
        // updateChildProfile кладёт профиль в AppSharedState и пушит в Supabase
        updateChildProfile(profile)
        selectChild(childId)

        // Best-effort привязка к родителю по email (только для родительской сессии)
        if (isParentSession && email.isNotBlank()) {
            runCatching { AuthRepository().linkChildToParent(childId, email) }
        }
        // Новый ребёнок сразу считается вошедшим (status = active)
        if (status == "active") {
            getApplication<Application>()
                .let { SecurePrefs.get(it, "app_prefs") }
                .edit {
                    putBoolean("is_logged_in", true)
                    putString("logged_in_child_id", childId)
                    putBoolean("is_admin_logged_in", false)
                }
        }
        onResult?.invoke(profile)
    }
}

/**
 * Вход ребёнка по email родителя + имени + PIN.
 * При успехе: профиль импортируется в локальный стейт, сохраняется
 * UUID устройства (для будущих пушей) и выбирается активным.
 */
fun MainViewModel.loginChild(
    parentEmail: String,
    childName: String,
    pin: String,
    onResult: (LoginResult) -> Unit
) {
    // Локальный rate-limit: после LOGIN_MAX_ATTEMPTS неудач — блокировка.
    // Данные персистентны (SecurePrefs) — brute-force не сбрасывается
    // перезапуском приложения.
    val rateKey = "${parentEmail.trim().lowercase()}|${childName.trim().lowercase()}"
    val attemptsKey = "attempts_$rateKey"
    val lockKey = "lock_$rateKey"
    val ratePrefs = rateLimitPrefs()
    val now = System.currentTimeMillis()
    val lockedUntil = ratePrefs.getLong(lockKey, 0L)
    if (now < lockedUntil) {
        onResult(LoginResult.RateLimited)
        return
    }
    // Блокировка истекла — подчищаем устаревший ключ.
    if (lockedUntil > 0L) {
        ratePrefs.edit { remove(lockKey) }
    }

    fun registerFailure() {
        val attempts = ratePrefs.getInt(attemptsKey, 0) + 1
        ratePrefs.edit {
            if (attempts >= LOGIN_MAX_ATTEMPTS) {
                // Блокировка растёт с каждой новой серией неудач: 60с, 120с, ...
                val lockMs = LOGIN_LOCK_BASE_MS * attempts.coerceAtMost(10)
                putLong(lockKey, System.currentTimeMillis() + lockMs)
                remove(attemptsKey)
            } else {
                putInt(attemptsKey, attempts)
            }
        }
    }

    viewModelScope.launch {
        val repo = runCatching { AuthRepository() }.getOrNull()
        if (repo == null) {
            onResult(LoginResult.NetworkError)
            return@launch
        }
        val profile = try {
            repo.findChildProfile(parentEmail, childName, pin)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "loginChild: ошибка сети/RPC", e)
            onResult(LoginResult.NetworkError)
            return@launch
        }
        if (profile == null) {
            registerFailure()
            onResult(LoginResult.InvalidCredentials)
            return@launch
        }
        // Успешный вход — сбрасываем счётчик и блокировку.
        ratePrefs.edit {
            remove(attemptsKey)
            remove(lockKey)
        }

        // Устройство ребёнка должно иметь собственную анонимную сессию Supabase:
        // её auth.uid сохраняем в authUserId, чтобы Realtime/RLS могли
        // отличать «свою» строку family_profiles от чужих.
        val supabase = SupabaseManager.client
        var authUid = runCatching { supabase?.auth?.currentUserOrNull()?.id }.getOrNull().orEmpty()
        val sessionEmail = runCatching { supabase?.auth?.currentUserOrNull()?.email }.getOrNull()
        if (supabase != null && (authUid.isEmpty() || !sessionEmail.isNullOrBlank())) {
            runCatching { supabase.auth.signInAnonymously() }
            authUid = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull().orEmpty()
        }

        // Миграция legacy PIN: если pinCode в старом формате (без ':'),
        // перехэшируем введённый PIN с солью — при следующем входе
        // уже используется безопасный формат.
        val migratedPinCode = if (!profile.pinCode.contains(':')) {
            PinHasher.hash(pin, PinHasher.generateSalt())
        } else {
            profile.pinCode
        }
        // ANDROID_ID не используем: Google Play считает его device identifier
        // и может отклонить приложение. Вместо него — случайный UUID,
        // один раз сгенерированный и сохранённый в SecurePrefs.
        val appPrefs = SecurePrefs.get(getApplication<Application>(), "app_prefs")
        val deviceId = appPrefs.getString("device_uuid", null) ?: run {
            java.util.UUID.randomUUID().toString().also {
                appPrefs.edit { putString("device_uuid", it) }
            }
        }
        val updated = profile.copy(
            deviceId = deviceId,
            authUserId = authUid.ifBlank { profile.authUserId },
            pinCode = migratedPinCode
        )
        // updateChildProfile: запись в AppSharedState + локальное сохранение
        // + pushChildProfile (markDirty в SyncStatus + отправка в Supabase).
        familyRepository.updateChildProfile(updated)
        selectChild(updated.id)
        // Флаги автовхода: при следующем запуске откроем ChildSection без Welcome
        getApplication<Application>()
            .let { SecurePrefs.get(it, "app_prefs") }
            .edit {
                putBoolean("is_logged_in", true)
                putString("logged_in_child_id", updated.id)
                putBoolean("is_admin_logged_in", false)
            }
        onResult(LoginResult.Success)
    }
}
