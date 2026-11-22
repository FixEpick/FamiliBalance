package com.example.familibalance

import android.content.Intent
import com.example.familibalance.data.push.handlePushIntent
import com.example.familibalance.data.supabase.SupabaseManager

/**
 * Обработка входящих intent'ов MainActivity (onCreate / onNewIntent):
 *  - auth deep link familibalance://auth — сброс пароля;
 *  - push-уведомления — deep-link навигация через PushNavBus.
 *
 * Вынесено из Activity: сама Activity только проксирует intent сюда.
 */
class MainActivityIntents(
    private val onPasswordResetPending: () -> Unit
) {
    fun handle(intent: Intent?) {
        handleAuthDeepLink(intent)
        handlePushIntent(intent)
    }

    /**
     * Ловит auth deep link (сброс пароля): помечает сценарий и передаёт
     * intent в Supabase — handleDeeplinks импортирует recovery-сессию.
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "familibalance" && data.host == "auth") {
            onPasswordResetPending()
            SupabaseManager.handleAuthDeepLink(intent)
        }
    }
}
