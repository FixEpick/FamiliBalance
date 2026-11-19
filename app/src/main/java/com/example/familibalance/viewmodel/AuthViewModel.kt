package com.example.familibalance.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.BuildConfig
import com.example.familibalance.R
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.data.auth.AuthRepository
import com.example.familibalance.util.UiText
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegisterMode: Boolean = true,
    val email: String = "",
    val password: String = "",
    val childName: String = "",
    val selectedAge: String = "6-8",
    // Регистрация нового игрока
    val nickname: String = "",
    val pin: String = "",
    val avatar: String = "🦊",
    val selectedInterests: Set<String> = emptySet(),
    val parentEmail: String = "",
    /** Вкладка «Родитель»: false — вход, true — регистрация. */
    val isParentRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    /** Нейтральное уведомление (например, «письмо отправлено») — не ошибка. */
    val noticeMessage: UiText? = null,
    /** Момент (epoch ms), до которого заблокирован повторный запрос сброса пароля. */
    val resetCooldownUntilMs: Long = 0
)

sealed interface AuthEvent {
    /** Успешный вход/регистрация родителя -> AdminRoute. */
    data object AuthSuccess : AuthEvent
    /** Успешная регистрация/вход игрока -> ChildRoute. */
    data object PlayerSuccess : AuthEvent
}

/**
 * MVI-ViewModel для экранов авторизации.
 * Вся работа с Supabase Auth/RPC инкапсулирована в [AuthRepository].
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository? = runCatching { AuthRepository() }.getOrNull()

    /** Минимальный интервал между запросами сброса пароля (30 минут). */
    private val resetCooldownMs = 30 * 60 * 1000L
    private val appPrefs by lazy {
        SecurePrefs.get(getApplication(), "app_prefs")
    }

    init {
        // Восстанавливаем cooldown после перезапуска приложения.
        val lastResetAt = appPrefs.getLong("last_password_reset_at", 0L)
        val until = lastResetAt + resetCooldownMs
        if (until > System.currentTimeMillis()) {
            update { copy(resetCooldownUntilMs = until) }
        }
    }

    /** Статус сессии Supabase (NotAuthenticated / Authenticated / RefreshFailure). */
    val sessionStatus: StateFlow<SessionStatus> =
        authRepository?.sessionStatus?.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            // Initializing — пока Supabase восстанавливает сохранённую сессию;
            // UI по этому значению показывает splash вместо экрана входа.
            SessionStatus.Initializing
        ) ?: MutableStateFlow(SessionStatus.NotAuthenticated(isSignOut = false))

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    // --- Общие ---
    fun onEmailChange(value: String) = update { copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = update { copy(password = value, errorMessage = null) }
    fun toggleAuthMode() = update {
        copy(isRegisterMode = !isRegisterMode, errorMessage = null)
    }

    /** Переключает режим на вкладке «Родитель»: вход ↔ регистрация. */
    fun toggleParentMode() = update {
        copy(isParentRegisterMode = !isParentRegisterMode, errorMessage = null)
    }

    // --- Регистрация игрока ---
    fun onNicknameChange(value: String) = update { copy(nickname = value, errorMessage = null) }
    fun onPinChange(value: String) = update {
        copy(pin = value.filter { it.isDigit() }.take(4), errorMessage = null)
    }
    fun onAvatarSelected(avatar: String) = update { copy(avatar = avatar) }
    fun onInterestToggle(interest: String) = update {
        val interests = if (interest in selectedInterests) {
            selectedInterests - interest
        } else {
            selectedInterests + interest
        }
        copy(selectedInterests = interests)
    }
    fun onParentEmailChange(value: String) = update { copy(parentEmail = value) }

    /** Сохраняет данные нового игрока локально (Supabase Auth для игрока — позже). */
    fun registerPlayer() {
        val state = _uiState.value
        if (state.nickname.isBlank()) {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_nickname)) }
            return
        }
        if (state.pin.length != 4) {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_pin)) }
            return
        }
        update { copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // TODO: здесь будет signUpChild + сохранение профиля через инвайт
            _events.emit(AuthEvent.PlayerSuccess)
            update { copy(isLoading = false) }
        }
    }

    // --- Родитель ---
    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_credentials)) }
            return
        }
        val repo = authRepository ?: run {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_supabase)) }
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, errorMessage = null) }
            try {
                repo.signIn(state.email.trim(), state.password)
                _events.emit(AuthEvent.AuthSuccess)
            } catch (e: Exception) {
                update { copy(errorMessage = authError(e, R.string.auth_error_login_generic)) }
            } finally {
                update { copy(isLoading = false) }
            }
        }
    }

    fun signUpParent() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_credentials)) }
            return
        }
        val repo = authRepository ?: run {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_supabase)) }
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, errorMessage = null) }
            try {
                repo.signUpParent(state.email.trim(), state.password)
                _events.emit(AuthEvent.AuthSuccess)
            } catch (e: Exception) {
                update { copy(errorMessage = authError(e, R.string.auth_error_register_generic)) }
            } finally {
                update { copy(isLoading = false) }
            }
        }
    }

    /** Сброс пароля родителя: Supabase шлёт письмо со ссылкой. */
    fun resetPassword() {
        val state = _uiState.value
        if (state.email.isBlank()) {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_email)) }
            return
        }
        // Клиентский cooldown: не чаще одного письма в 30 минут.
        if (System.currentTimeMillis() < _uiState.value.resetCooldownUntilMs) return
        val repo = authRepository ?: run {
            update { copy(errorMessage = UiText.Res(R.string.auth_err_supabase)) }
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, errorMessage = null, noticeMessage = null) }
            try {
                repo.resetPassword(state.email.trim())
                val now = System.currentTimeMillis()
                appPrefs.edit().putLong("last_password_reset_at", now).apply()
                update {
                    copy(
                        noticeMessage = UiText.Res(R.string.auth_reset_sent),
                        resetCooldownUntilMs = now + resetCooldownMs
                    )
                }
            } catch (e: Exception) {
                update { copy(errorMessage = authError(e, R.string.auth_error_login_generic)) }
            } finally {
                update { copy(isLoading = false) }
            }
        }
    }

    /**
     * Устанавливает новый пароль в recovery-сессии (после перехода
     * по ссылке из письма). onResult(true) — пароль сохранён.
     */
    fun updatePassword(newPassword: String, onResult: (Boolean, String?) -> Unit) {
        val repo = authRepository ?: run {
            onResult(false, null)
            return
        }
        viewModelScope.launch {
            try {
                repo.updatePassword(newPassword)
                onResult(true, null)
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "updatePassword failed", e)
                onResult(false, if (BuildConfig.DEBUG) e.message else null)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository?.signOut() }
        }
    }

    /**
     * Ошибка auth-операции: в debug показываем сырое e.message
     * (может содержать stack/URL — только для отладки),
     * в release — generic-строку, чтобы не утекали внутренние детали.
     */
    private fun authError(e: Exception, @androidx.annotation.StringRes genericResId: Int): UiText =
        if (BuildConfig.DEBUG) {
            UiText.RawString(e.message ?: "error")
        } else {
            UiText.Res(genericResId)
        }

    private inline fun update(transform: AuthUiState.() -> AuthUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
