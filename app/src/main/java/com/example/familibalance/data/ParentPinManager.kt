package com.example.familibalance.data

import android.content.Context
import com.example.familibalance.SupabaseManager
import io.github.jan.supabase.auth.auth

/**
 * Хранение родительского 4-значного ПИН-кода в SharedPreferences.
 * В prefs сохраняется только SHA-256 хэш ([PinHasher]); открытый PIN не хранится.
 * Ключ привязан к auth.uid(): у разных родителей на одном устройстве — разные коды.
 * Дефолтного PIN нет: пока [isPinSet] == false, [getPin] возвращает null
 * и [verifyPin] всегда false — доступ только после явной установки PIN.
 */
object ParentPinManager {
    private const val PREFS_NAME = "parent_pin_prefs"
    private const val KEY_PIN_SET = "pin_is_set"

    private fun pinKey(): String {
        val uid = runCatching {
            SupabaseManager.client?.auth?.currentUserOrNull()?.id
        }.getOrNull()
        return "pin_${uid ?: "guest"}"
    }

    /** true, если пользователь хотя бы раз задал родительский PIN на этом устройстве. */
    fun isPinSet(context: Context): Boolean {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        return prefs.getBoolean(KEY_PIN_SET, false)
    }

    /**
     * Сырое сохранённое значение (хэш или legacy-открытый текст).
     * null — PIN ещё не задан или не найден для текущего auth.uid().
     */
    fun getPin(context: Context): String? {
        if (!isPinSet(context)) return null
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        return prefs.getString(pinKey(), null)
    }

    /** Проверка введённого ПИНа: без сохранённого PIN доступа нет. */
    fun verifyPin(context: Context, entered: String): Boolean {
        val stored = getPin(context) ?: return false
        return PinHasher.matches(entered, stored)
    }

    fun setPin(context: Context, pin: String) {
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        prefs.edit()
            .putString(pinKey(), PinHasher.hash(pin, PinHasher.generateSalt()))
            .putBoolean(KEY_PIN_SET, true)
            .apply()
    }
}
