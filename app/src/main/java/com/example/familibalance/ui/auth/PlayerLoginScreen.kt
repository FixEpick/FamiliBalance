package com.example.familibalance.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.LoginResult
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.viewmodel.actions.loginChild

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val CyberLime = Color(0xFFA3E635)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val CyberRed = Color(0xFFEF4444)

private val CyberFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberLime,
        unfocusedBorderColor = CyberSlateBorder,
        focusedLabelColor = CyberLime,
        unfocusedLabelColor = CyberTextSecondary,
        focusedTextColor = CyberTextPrimary,
        unfocusedTextColor = CyberTextPrimary,
        cursorColor = CyberLime
    )

/**
 * Вход ребёнка: email родителя + имя ребёнка + PIN-код.
 * Поиск профиля — в Supabase (family_profiles.state_json) через [MainViewModel.loginChild].
 */
@Composable
fun PlayerLoginScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var parentEmail by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<LoginResult?>(null) }

    fun submit() {
        if (isLoading) return
        if (parentEmail.isBlank() || childName.isBlank() || pin.length != 4) {
            loginError = LoginResult.InvalidCredentials
            return
        }
        isLoading = true
        loginError = null
        viewModel.loginChild(parentEmail, childName, pin) { result ->
            isLoading = false
            when (result) {
                LoginResult.Success -> onLoginSuccess()
                else -> {
                    loginError = result
                    pin = ""
                }
            }
        }
    }

    val errorText = when (loginError) {
        LoginResult.RateLimited -> stringResource(R.string.error_rate_limited)
        LoginResult.NetworkError -> stringResource(R.string.error_network)
        LoginResult.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
        else -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp)
        ) {
            // Верхняя панель
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberSlate)
                        .border(1.dp, CyberSlateBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.common_back), color = CyberTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(R.string.login_title),
                    color = CyberTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanguageSelectorButton()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Форма входа
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CyberPanelBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberSlateBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = parentEmail,
                        onValueChange = { parentEmail = it; loginError = null },
                        label = { Text(stringResource(R.string.login_field_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = CyberFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it; loginError = null },
                        label = { Text(stringResource(R.string.login_field_name)) },
                        singleLine = true,
                        colors = CyberFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); loginError = null },
                        label = { Text(stringResource(R.string.login_field_pin)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = CyberFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorText,
                            color = CyberRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GameButton(
                        text = if (isLoading) stringResource(R.string.login_loading) else stringResource(R.string.login_submit),
                        emoji = "🔑",
                        variant = GameButtonVariant.Electric,
                        enabled = !isLoading,
                        onClick = { submit() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Нет аккаунта — переход к регистрации
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCreateAccount)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.login_create_account),
                    color = CyberLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
