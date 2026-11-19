package com.example.familibalance.ui.auth

import com.example.familibalance.viewmodel.AuthUiState

/** Валидация формы «Новый игрок»: никнейм непустой, PIN ровно 4 цифры. */
fun isPlayerFormValid(state: AuthUiState): Boolean =
    !state.isLoading && state.nickname.isNotBlank() && state.pin.length == 4

/** Валидация формы родителя: кнопка активна, пока нет загрузки. */
fun isParentFormEnabled(state: AuthUiState): Boolean = !state.isLoading

