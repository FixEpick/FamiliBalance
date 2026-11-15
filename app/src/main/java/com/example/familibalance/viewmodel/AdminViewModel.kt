package com.example.familibalance.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.AppSharedState
import com.example.familibalance.FamilyProfileRow
import com.example.familibalance.MainViewModel
import com.example.familibalance.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** UI-модель pending-запроса: JSON из state_json распарсен один раз при загрузке. */
data class PendingRequestUi(
    val id: String,
    val name: String,
    val avatar: String,
    val email: String
)

/** Одноразовые события для UI (Toast и т.п.). */
sealed interface AdminEvent {
    data class ShowMessage(val text: String) : AdminEvent
}

/**
 * ViewModel экрана настроек родителя: загрузка и одобрение запросов
 * на добавление детей (family_profiles, status = "pending").
 *
 * Compose-слой: собирает [pendingRequests]/[isLoading] через StateFlow,
 * реагирует на [events] (Toast), вызывает [onResume]/[onPause]/[approveRequest].
 */
class AdminViewModel(
    private val mainViewModel: MainViewModel
) : ViewModel() {

    private val _pendingRequests = MutableStateFlow<List<PendingRequestUi>>(emptyList())
    val pendingRequests: StateFlow<List<PendingRequestUi>> = _pendingRequests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<AdminEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AdminEvent> = _events.asSharedFlow()

    private var pollJob: Job? = null

    /** Запустить/возобновить polling (каждые 10с). Идемпотентно. */
    fun onResume() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadPendingRequests()
                delay(10_000)
            }
        }
    }

    /** Остановить polling (уход в фон / dispose). */
    fun onPause() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Одобрить запрос: привязать профиль к текущему родителю. */
    fun approveRequest(id: String) {
        val supabase = SupabaseManager.client ?: return
        viewModelScope.launch {
            val me = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            if (me == null) {
                _events.emit(AdminEvent.ShowMessage("Ошибка: нет активной сессии"))
                return@launch
            }
            val updateResult = runCatching {
                supabase.from("family_profiles").update({
                    set("parent_user_id", me.id)
                    set("parent_email", me.email)
                    set("status", "active")
                }) {
                    filter { eq("user_id", id) }
                }
            }
            if (updateResult.isFailure) {
                Log.e("AdminViewModel", "approveRequest failed", updateResult.exceptionOrNull())
                _events.emit(AdminEvent.ShowMessage("Ошибка при одобрении запроса"))
                return@launch
            }
            // Если профиль уже есть локально — сразу помечаем активным и привязываем
            AppSharedState.children[id]?.let { child ->
                mainViewModel.updateChildProfile(
                    child.copy(parentUserId = me.id, status = "active")
                )
            }
            _pendingRequests.update { list -> list.filterNot { it.id == id } }
            _events.emit(AdminEvent.ShowMessage("Запрос одобрен — ребёнок добавлен в семью"))
        }
    }

    private suspend fun loadPendingRequests() {
        val supabase = SupabaseManager.client ?: return
        val me = runCatching { supabase.auth.currentUserOrNull() }.getOrNull() ?: return
        val myEmail = me.email ?: return
        _isLoading.value = true
        try {
            val rows = runCatching {
                supabase.from("family_profiles")
                    .select(columns = Columns.list("user_id", "state_json", "parent_user_id", "parent_email", "status"))
                    .decodeList<FamilyProfileRow>()
            }.getOrNull() ?: return
            _pendingRequests.value = rows
                .filter {
                    it.status == "pending" && it.parentEmail.equals(myEmail, ignoreCase = true)
                }
                .map { row ->
                    PendingRequestUi(
                        id = row.user_id,
                        name = parseChildField(row.state_json, "name") ?: "Игрок",
                        avatar = parseChildField(row.state_json, "avatar") ?: "🧒",
                        email = row.parentEmail.orEmpty()
                    )
                }
        } finally {
            _isLoading.value = false
        }
    }

    /** Достаёт строковое поле из state_json профиля (name/avatar/...). */
    private fun parseChildField(stateJson: String, key: String): String? {
        return runCatching {
            Json.parseToJsonElement(stateJson)
                .jsonObject[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
