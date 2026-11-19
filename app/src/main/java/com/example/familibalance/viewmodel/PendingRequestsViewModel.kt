package com.example.familibalance.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.supabase.FamilyProfileRow
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Состояние экрана ожидания подтверждения родителем (MVI state).
 */
sealed interface PendingState {
    data object Loading : PendingState
    data object Pending : PendingState
    /** Родитель подтвердил — профиль активирован, можно переходить дальше. */
    data object Approved : PendingState
    data class Error(val message: String) : PendingState
}

/**
 * ViewModel для [PendingRequestScreen]: polling family_profiles с серверной
 * фильтрацией по user_id и exponential backoff 5с -> 30с.
 *
 * Compose-слой вызывает [onResume]/[onPause] по lifecycle (RESUMED/не-RESUMED),
 * ViewModel не знает о Compose.
 */
class PendingRequestsViewModel(
    private val mainViewModel: MainViewModel
) : ViewModel() {

    private val _state = MutableStateFlow<PendingState>(PendingState.Loading)
    val state: StateFlow<PendingState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Запустить/возобновить опрос. Идемпотентно: повторный вызов при активном job — no-op. */
    fun onResume(child: ChildProfileState) {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var delayMs = 5_000L
            _state.value = PendingState.Loading
            while (isActive) {
                val row = runCatching {
                    val supabase = SupabaseManager.client ?: return@runCatching null
                    supabase.from("family_profiles")
                        .select { filter { eq("user_id", child.id) } }
                        .decodeList<FamilyProfileRow>()
                        .firstOrNull()
                }.getOrNull()

                if (row?.status == "active") {
                    mainViewModel.updateChildProfile(
                        child.copy(
                            status = "active",
                            parentUserId = row.parentUserId ?: child.parentUserId
                        )
                    )
                    _state.value = PendingState.Approved
                    break
                }
                _state.value = PendingState.Pending
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Остановить опрос (уход в фон / dispose). */
    fun onPause() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        pollJob?.cancel()
    }
}
