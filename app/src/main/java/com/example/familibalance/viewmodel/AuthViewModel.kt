package com.example.familibalance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.data.auth.AuthRepository
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
    val errorMessage: String? = null
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
class AuthViewModel : ViewModel() {

    private val authRepository: AuthRepository? = runCatching { AuthRepository() }.getOrNull()

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
            update { copy(errorMessage = "Введите никнейм") }
            return
        }
        if (state.pin.length != 4) {
            update { copy(errorMessage = "ПИН должен содержать 4 цифры") }
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
            update { copy(errorMessage = "Введите email и пароль") }
            return
        }
        val repo = authRepository ?: run {
            update { copy(errorMessage = "Supabase не настроен") }
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, errorMessage = null) }
            try {
                repo.signIn(state.email.trim(), state.password)
                _events.emit(AuthEvent.AuthSuccess)
            } catch (e: Exception) {
                update { copy(errorMessage = e.message ?: "Ошибка входа") }
            } finally {
                update { copy(isLoading = false) }
            }
        }
    }

    fun signUpParent() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            update { copy(errorMessage = "Введите email и пароль") }
            return
        }
        val repo = authRepository ?: run {
            update { copy(errorMessage = "Supabase не настроен") }
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, errorMessage = null) }
            try {
                repo.signUpParent(state.email.trim(), state.password)
                _events.emit(AuthEvent.AuthSuccess)
            } catch (e: Exception) {
                update { copy(errorMessage = e.message ?: "Ошибка регистрации") }
            } finally {
                update { copy(isLoading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository?.signOut() }
        }
    }

    private inline fun update(transform: AuthUiState.() -> AuthUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
