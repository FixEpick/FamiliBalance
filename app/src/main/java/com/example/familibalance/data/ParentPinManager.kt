package com.example.familibalance.data

import android.content.Context
import com.example.familibalance.SupabaseManager
import io.github.jan.supabase.auth.auth

/**
 * Хранение родительского 4-значного ПИН-кода в SharedPreferences.
 * В prefs сохраняется только SHA-256 хэш ([PinHasher]); открытый PIN не хранится.
 * Ключ привязан к auth.uid(): у разных родителей на одном устройстве — разные коды.
 * Дефолтный ПИН — "0000" (legacy-значение, проверяется через [PinHasher.matches]).
 */
object ParentPinManager {
    private const val PREFS_NAME = "parent_pin_prefs"
    private const val DEFAULT_PIN = "0000"

    private fun pinKey(): String {
        val uid = runCatching {
            SupabaseManager.client?.auth?.currentUserOrNull()?.id
        }.getOrNull()
        return "pin_${uid ?: "guest"}"
    }

    /** Сырое сохранённое значение (хэш или legacy-открытый текст). Для обратной совместимости. */
    fun getPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(pinKey(), DEFAULT_PIN) ?: DEFAULT_PIN
    }

    /** Проверка введённого ПИНа: хэш сравнивается с хэшем, legacy — с открытым текстом. */
    fun verifyPin(context: Context, entered: String): Boolean {
        return PinHasher.matches(entered, getPin(context))
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(pinKey(), PinHasher.hash(pin)).apply()
    }
}
