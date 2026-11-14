package com.example.familibalance.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.MainViewModel
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.viewmodel.AuthEvent
import com.example.familibalance.viewmodel.AuthViewModel

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val CyberLime = Color(0xFFA3E635)
private val CyberRed = Color(0xFFEF4444)

private val playerAvatars = listOf("🦊", "🧑‍🚀", "🤖", "🐉", "🥷", "🛸")
private val interestChips = listOf(
    "💻 Кодинг" to "coding",
    "🔬 Наука" to "science",
    "🧠 Логика" to "logic",
    "🎨 3D и Дизайн" to "design"
)

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
 * Экран регистрации/входа в стиле «КвестЛаб».
 * Табы: «Новый игрок» (форма героя) и «Родитель» (Supabase Auth).
 */
@Composable
fun RegisterParentScreen(
    viewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    onPlayerSuccess: () -> Unit,
    onParentSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.AuthSuccess -> onParentSuccess()
                AuthEvent.PlayerSuccess -> onPlayerSuccess()
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
            // Шапка
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
                    Text("← Назад", color = CyberTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Регистрация",
                    color = CyberTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(72.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Табы
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberPanelBg)
                    .border(1.dp, CyberSlateBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                listOf(true to "🚀 Новый игрок", false to "🛡️ Родитель").forEach { (isPlayer, label) ->
                    val selected = state.isRegisterMode == isPlayer
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    if (isPlayer) CyberLime.copy(alpha = 0.2f) else CyberSlate
                                } else Color.Transparent
                            )
                            .clickable {
                                if (selected != isPlayer) viewModel.toggleAuthMode()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) {
                                if (isPlayer) CyberLime else CyberTextPrimary
                            } else CyberTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (state.isRegisterMode) {
                PlayerRegisterForm(
                    state = state,
                    viewModel = viewModel,
                    onSubmit = {
                        mainViewModel.createChildProfile(
                            nickname = state.nickname,
                            pin = state.pin,
                            avatar = state.avatar,
                            parentEmail = state.parentEmail
                        )
                        onPlayerSuccess()
                    }
                )
            } else {
                ParentLoginForm(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PlayerRegisterForm(
    state: com.example.familibalance.viewmodel.AuthUiState,
    viewModel: AuthViewModel,
    onSubmit: () -> Unit
) {
    // Аватар — сетка 6 эмодзи
    Text(
        text = "АВАТАР",
        color = CyberTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        playerAvatars.forEach { avatar ->
            val selected = state.avatar == avatar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) CyberLime.copy(alpha = 0.15f) else CyberPanelBg)
                    .border(
                        1.dp,
                        if (selected) CyberLime else CyberSlateBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.onAvatarSelected(avatar) },
                contentAlignment = Alignment.Center
            ) {
                Text(avatar, fontSize = 22.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Никнейм и ПИН в ряд
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::onNicknameChange,
            label = { Text("Никнейм") },
            singleLine = true,
            colors = CyberFieldColors,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.pin,
            onValueChange = viewModel::onPinChange,
            label = { Text("ПИН") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = CyberFieldColors,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Интересы — чипы мультивыбора
    Text(
        text = "ИНТЕРЕСЫ",
        color = CyberTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        interestChips.chunked(2).forEach { rowChips ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChips.forEach { (label, id) ->
                    val selected = id in state.selectedInterests
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CyberLime.copy(alpha = 0.15f) else CyberPanelBg)
                            .border(
                                1.dp,
                                if (selected) CyberLime else CyberSlateBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.onInterestToggle(id) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) CyberLime else CyberTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                if (rowChips.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Email родителя
    OutlinedTextField(
        value = state.parentEmail,
        onValueChange = viewModel::onParentEmailChange,
        label = { Text("Email родителя") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = CyberFieldColors,
        modifier = Modifier.fillMaxWidth()
    )

    state.errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = error,
            color = CyberRed,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    GameButton(
        text = "Создать героя и в бой!",
        emoji = "🚀",
        variant = GameButtonVariant.Lime,
        enabled = !state.isLoading && state.nickname.isNotBlank() && state.pin.length == 4,
        onClick = onSubmit
    )
}

@Composable
private fun ParentLoginForm(
    state: com.example.familibalance.viewmodel.AuthUiState,
    viewModel: AuthViewModel
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CyberPanelBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberSlateBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = CyberFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = CyberFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = CyberRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Переключатель Вход / Регистрация для родителя
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberSlate)
                    .border(1.dp, CyberSlateBorder, RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                listOf(false to "🔑 Вход", true to "🚀 Регистрация").forEach { (isReg, label) ->
                    val selected = state.isParentRegisterMode == isReg
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) CyberLime.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { if (!selected) viewModel.toggleParentMode() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) CyberLime else CyberTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GameButton(
                text = if (state.isParentRegisterMode) "Создать аккаунт родителя" else "Войти",
                emoji = "🔑",
                variant = GameButtonVariant.Electric,
                enabled = !state.isLoading,
                onClick = {
                    if (state.isParentRegisterMode) viewModel.signUpParent() else viewModel.signIn()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Нет аккаунта? Зарегистрируйтесь через кнопку выше на вкладке «Новый игрок» или попросите администратора.",
                color = CyberTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

