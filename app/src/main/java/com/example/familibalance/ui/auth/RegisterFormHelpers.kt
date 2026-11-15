package com.example.familibalance.ui.auth

import android.content.Context
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.viewmodel.AuthUiState
import com.example.familibalance.viewmodel.AuthViewModel

/** Валидация формы «Новый игрок»: никнейм непустой, PIN ровно 4 цифры. */
fun isPlayerFormValid(state: AuthUiState): Boolean =
    !state.isLoading && state.nickname.isNotBlank() && state.pin.length == 4

/** Валидация формы родителя: кнопка активна, пока нет загрузки. */
fun isParentFormEnabled(state: AuthUiState): Boolean = !state.isLoading

/** Восстанавливает «Запомнить меня»: подставляет сохранённый email в форму.
 *  Возвращает true, если флаг был установлен (для локального состояния UI). */
fun restoreRememberMe(context: Context, viewModel: AuthViewModel): Boolean {
    val prefs = SecurePrefs.get(context, "app_prefs")
    val remembered = prefs.getBoolean("is_remember_me", false)
    if (remembered) {
        prefs.getString("saved_admin_email", null)
            ?.takeIf { it.isNotBlank() }
            ?.let { viewModel.onEmailChange(it) }
    }
    return remembered
}

/** Сохраняет/удаляет email родителя по флагу «Запомнить меня». */
fun saveRememberMe(context: Context, email: String, remember: Boolean) {
    val prefs = SecurePrefs.get(context, "app_prefs")
    prefs.edit()
        .putBoolean("is_remember_me", remember)
        .apply {
            if (remember && email.isNotBlank()) {
                putString("saved_admin_email", email)
            } else {
                remove("saved_admin_email")
            }
        }
        .apply()
}
