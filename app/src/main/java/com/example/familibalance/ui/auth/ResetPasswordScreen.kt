package com.example.familibalance.ui.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familibalance.R
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.ui.components.rememberBoundTextFieldState
import com.example.familibalance.viewmodel.AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus

private val ResetTextPrimary = Color(0xFFE2E8F0)
private val ResetTextSecondary = Color(0xFF94A3B8)
private val ResetPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val ResetSlate = Color(0xFF1E293B)
private val ResetSlateBorder = Color(0xFF334155)
private val ResetLime = Color(0xFFA3E635)
private val ResetRed = Color(0xFFEF4444)

private val ResetFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ResetLime,
        unfocusedBorderColor = ResetSlateBorder,
        focusedLabelColor = ResetLime,
        unfocusedLabelColor = ResetTextSecondary,
        focusedTextColor = ResetTextPrimary,
        unfocusedTextColor = ResetTextPrimary,
        cursorColor = ResetLime
    )

/**
 * Экран установки нового пароля после перехода по ссылке из письма
 * о сбросе (recovery-сессия уже импортирована через deep link).
 * «Назад»/системный back — выход из сценария без смены пароля.
 */
@Composable
fun ResetPasswordScreen(
    viewModel: AuthViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    @StringRes var errorRes by remember { mutableStateOf<Int?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    val newPasswordFieldState = rememberBoundTextFieldState(newPassword) {
        newPassword = it; errorRes = null
    }
    val confirmPasswordFieldState = rememberBoundTextFieldState(confirmPassword) {
        confirmPassword = it; errorRes = null
    }

    // Recovery-сессия импортируется из deeplink асинхронно (сетевой
    // запрос retrieveUser). Кнопку активируем только при Authenticated —
    // иначе updateUser упадёт с session not found.
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    // Authenticated недостаточно: ensureSession() может держать анонимную
    // сессию (user.email == null) — updateUser на ней упадёт/сменит не тот
    // аккаунт. Ждём именно recovery-сессию родителя с email.
    val hasSession = (sessionStatus as? SessionStatus.Authenticated)
        ?.session?.user?.email != null
    // NotAuthenticated или анонимная сессия без email — recovery не удался.
    val sessionFailed = sessionStatus is SessionStatus.NotAuthenticated ||
        (sessionStatus is SessionStatus.Authenticated && !hasSession)

    BackHandler { onCancel() }

    fun submit() {
        if (isLoading) return
        when {
            newPassword.length < 6 -> {
                errorRes = R.string.reset_password_short
                return
            }
            newPassword != confirmPassword -> {
                errorRes = R.string.reset_password_mismatch
                return
            }
        }
        isLoading = true
        errorRes = null
        errorDetail = null
        viewModel.updatePassword(newPassword) { success, detail ->
            isLoading = false
            if (success) {
                onDone()
            } else {
                errorRes = R.string.reset_password_failed
                errorDetail = detail
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reset_password_title),
                    color = ResetTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                LanguageSelectorButton()
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ResetPanelBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, ResetSlateBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedSecureTextField(
                        state = newPasswordFieldState,
                        label = { Text(stringResource(R.string.reset_password_new)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        colors = ResetFieldColors,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                GameIcon(
                                    icon = if (passwordVisible) GameIconType.EyeOff else GameIconType.Eye,
                                    tint = ResetTextSecondary,
                                    size = 20.dp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedSecureTextField(
                        state = confirmPasswordFieldState,
                        label = { Text(stringResource(R.string.reset_password_confirm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        colors = ResetFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (sessionFailed) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.reset_password_no_session),
                            color = ResetRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    errorRes?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(res),
                            color = ResetRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        errorDetail?.let { detail ->
                            Text(
                                text = detail,
                                color = ResetRed,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GameButton(
                        text = if (isLoading) stringResource(R.string.login_loading) else stringResource(R.string.reset_password_submit),
                        icon = GameIconType.Lock,
                        variant = GameButtonVariant.Electric,
                        enabled = !isLoading && hasSession,
                        onClick = { submit() }
                    )
                }
            }
        }
    }
}
