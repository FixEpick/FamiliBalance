package com.example.familibalance.ui.auth

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.LoginResult
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.ExitConfirmDialog
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.components.LanguageSelectorButton
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.actions.createChildProfile
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
 * Единый экран ребёнка: вкладки «Вход» (email родителя + имя + PIN)
 * и «Регистрация» (герой: никнейм, PIN, аватар, интересы, email родителя).
 * Поиск профиля — в Supabase через [MainViewModel.loginChild].
 */
@Composable
fun PlayerLoginScreen(
    viewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    // Профиль, отправленный на подтверждение родителю (status = "pending")
    var pendingChild by remember { mutableStateOf<ChildProfileState?>(null) }
    val authState by authViewModel.uiState.collectAsState()

    var parentEmail by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<LoginResult?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler { showExitDialog = true }

    fun submitLogin() {
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

        val pending = pendingChild
        if (pending != null) {
            PendingRequestScreen(
                child = pending,
                mainViewModel = viewModel,
                onApproved = onLoginSuccess
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp)
        ) {
            // Верхняя панель
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    color = CyberTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                LanguageSelectorButton()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Табы: Вход / Регистрация
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberSlate)
                    .border(1.dp, CyberSlateBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                listOf(
                    Triple(false, R.string.parent_login, GameIconType.Lock),
                    Triple(true, R.string.parent_register, GameIconType.Rocket)
                ).forEach { (isReg, labelRes, tabIcon) ->
                    val selected = isRegisterMode == isReg
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) CyberLime.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) CyberLime else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { isRegisterMode = isReg }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(
                                icon = tabIcon,
                                tint = if (selected) CyberLime else CyberTextSecondary,
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(labelRes),
                                color = if (selected) CyberLime else CyberTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isRegisterMode) {
                PlayerRegisterForm(
                    state = authState,
                    viewModel = authViewModel,
                    onSubmit = {
                        viewModel.createChildProfile(
                            nickname = authState.nickname,
                            pin = authState.pin,
                            avatar = authState.avatar,
                            parentEmail = authState.parentEmail,
                            onResult = { profile ->
                                if (profile.status == "pending") {
                                    pendingChild = profile
                                } else {
                                    onLoginSuccess()
                                }
                            }
                        )
                    }
                )
            } else {
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
                            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = CyberFieldColors,
                            trailingIcon = {
                                IconButton(onClick = { pinVisible = !pinVisible }) {
                                    GameIcon(
                                        icon = if (pinVisible) GameIconType.EyeOff else GameIconType.Eye,
                                        tint = CyberTextSecondary,
                                        size = 20.dp
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.child_pin_hint),
                            color = CyberTextSecondary,
                            fontSize = 11.sp
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
                            icon = GameIconType.Lock,
                            variant = GameButtonVariant.Electric,
                            enabled = !isLoading,
                            onClick = { submitLogin() }
                        )
                    }
                }
            }
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                title = stringResource(R.string.auth_exit_title),
                message = stringResource(R.string.auth_exit_message),
                confirmText = stringResource(R.string.auth_exit_confirm),
                cancelText = stringResource(R.string.action_cancel),
                onConfirm = {
                    showExitDialog = false
                    onBack()
                },
                onDismiss = { showExitDialog = false }
            )
        }
    }
}
