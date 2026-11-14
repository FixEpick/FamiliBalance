package com.example.familibalance.data

import android.content.Context
import com.example.familibalance.SupabaseManager
import io.github.jan.supabase.auth.auth

/**
 * Хранение родительского 4-значного ПИН-кода в SharedPreferences.
 * Ключ привязан к auth.uid(): у разных родителей на одном устройстве — разные коды.
 * Дефолтный ПИН — "0000".
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

    fun getPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(pinKey(), DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(pinKey(), pin).apply()
    }
}
