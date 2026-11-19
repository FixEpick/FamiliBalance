package com.example.familibalance.data.auth

import com.example.familibalance.data.model.ChildProfileDto
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.data.PinHasher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Информация об инвайте, возвращаемая RPC validate_invite.
 * Поля парсятся толерантно (snake_case / camelCase), чтобы не зависеть
 * от точного контракта функции в Supabase.
 */
data class InviteInfo(
    val token: String,
    val childId: String,
    val childEmail: String,
    val expiresAt: String
)

@Serializable
private data class ChildInviteRow(
    val token: String,
    @SerialName("child_id") val childId: String,
    @SerialName("child_email") val childEmail: String,
    @SerialName("expires_at") val expiresAt: String
)

/**
 * Репозиторий авторизации: обёртка над Supabase Auth и RPC инвайтов.
 * Не содержит UI-логики и не используется в навигации.
 */
class AuthRepository private constructor(private val client: SupabaseClient) {

    constructor() : this(
        SupabaseManager.client ?: error("Supabase не настроен: проверьте SUPABASE_URL/SUPABASE_ANON_KEY")
    )

    /** Статус сессии (NotAuthenticated / Authenticated / RefreshFailure). */
    val sessionStatus: Flow<SessionStatus>
        get() = client.auth.sessionStatus

    /** Обычный вход по email/паролю. */
    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Регистрация родителя (владелец семейного профиля). */
    suspend fun signUpParent(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            // Роль сохраняется в user_metadata для будущей маршрутизации
            data = buildJsonObject { put("role", JsonPrimitive("parent")) }
        }
    }

    /**
     * Регистрация ребёнка. На стороне Supabase Auth вызов идентичен
     * signUpParent — роль определяется позже через инвайт/RPC.
     */
    suspend fun signUpChild(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Отправляет письмо для сброса пароля на указанный email. */
    suspend fun resetPassword(email: String) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    /** Устанавливает новый пароль для текущей recovery-сессии (после deep link). */
    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    /** Выход: очистка сессии. */
    suspend fun signOut() {
        client.auth.signOut()
    }

    private val profileJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Вход ребёнка по email родителя + имени + PIN.
     * Поиск выполняется на сервере через RPC verify_child_login —
     * клиент больше не выгружает всю таблицу family_profiles.
     * PIN дополнительно проверяется на клиенте ([PinHasher.matches])
     * на случай, если RPC возвращает кандидата без проверки PIN.
     * Возвращает найденный профиль или null.
     */
    suspend fun findChildProfile(parentEmail: String, childName: String, pin: String): ChildProfileState? {
        val email = parentEmail.trim()
        val name = childName.trim()
        if (email.isEmpty() || name.isEmpty() || pin.isEmpty()) return null

        val result = try {
            client.postgrest.rpc(
                "verify_child_login",
                buildJsonObject {
                    put("p_email", JsonPrimitive(email))
                    put("p_name", JsonPrimitive(name))
                    put("p_pin", JsonPrimitive(pin))
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "RPC verify_child_login failed", e)
            throw e
        }
        val element = runCatching {
            Json.parseToJsonElement(result.data)
        }.getOrNull() ?: return null

        // RPC возвращает массив из 0 или 1 строки { user_id, state_json }.
        val rows = when (element) {
            is JsonArray -> element
            is JsonObject -> JsonArray(listOf(element))
            else -> return null
        }
        return rows.firstNotNullOfOrNull { rowEl ->
            val row = rowEl as? JsonObject ?: return@firstNotNullOfOrNull null
            val userId = row["user_id"]?.jsonPrimitive?.contentOrNull
                ?: row["userId"]?.jsonPrimitive?.contentOrNull
                ?: return@firstNotNullOfOrNull null
            val stateJson = row["state_json"]?.jsonPrimitive?.contentOrNull
                ?: row["stateJson"]?.jsonPrimitive?.contentOrNull
                ?: return@firstNotNullOfOrNull null
            val dto = runCatching {
                profileJson.decodeFromString<ChildProfileDto>(stateJson)
            }.getOrNull() ?: return@firstNotNullOfOrNull null
            val profile = ChildProfileState.fromDto(dto).copy(id = userId)
            // Defense in depth: даже если RPC не проверяет PIN — отсекаем здесь.
            if (PinHasher.matches(pin, profile.pinCode)) profile else null
        }
    }

    /**
     * Создаёт инвайт для ребёнка: генерирует токен, вставляет запись
     * в child_invites со сроком действия 7 дней и возвращает токен.
     */
    suspend fun createInvite(childId: String, childEmail: String): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).toString()
        client.from("child_invites").insert(
            ChildInviteRow(
                token = token,
                childId = childId,
                childEmail = childEmail,
                expiresAt = expiresAt
            )
        )
        return token
    }

    /** Проверяет инвайт через RPC validate_invite. null — токен невалиден/не найден. */
    suspend fun validateInvite(token: String): InviteInfo? {
        val result = client.postgrest.rpc(
            "validate_invite",
            buildJsonObject { put("token", JsonPrimitive(token)) }
        )
        val element = runCatching {
            Json.parseToJsonElement(result.data)
        }.getOrNull() ?: return null
        return parseInviteInfo(element)
    }

    /**
     * Пытается связать профиль ребёнка с родителем по email.
     * Best-effort: если RPC/пользователя нет — возвращает false,
     * профиль всё равно сохранён с parent_user_id = "".
     */
    suspend fun linkChildToParent(childId: String, parentEmail: String): Boolean {
        if (parentEmail.isBlank()) return false
        return try {
            client.postgrest.rpc(
                "link_child_to_parent",
                buildJsonObject {
                    put("child_id", JsonPrimitive(childId))
                    put("parent_email", JsonPrimitive(parentEmail))
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Погашает инвайт через RPC redeem_invite (ребёнок активирует доступ). */
    suspend fun redeemInvite(token: String) {
        client.postgrest.rpc(
            "redeem_invite",
            buildJsonObject { put("token", JsonPrimitive(token)) }
        )
    }

    private fun parseInviteInfo(element: JsonElement): InviteInfo? {
        val obj = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject ?: return null
            else -> return null
        }
        fun str(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        } ?: ""
        val childId = str("child_id", "childId")
        val childEmail = str("child_email", "childEmail")
        val expiresAt = str("expires_at", "expiresAt")
        if (childId.isBlank() && childEmail.isBlank()) return null
        return InviteInfo(
            token = str("token"),
            childId = childId,
            childEmail = childEmail,
            expiresAt = expiresAt
        )
    }
}
