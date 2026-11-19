package com.example.familibalance.ui.auth

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.MainViewModel
import com.example.familibalance.R
import com.example.familibalance.data.SecurePrefs
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.ExitConfirmDialog
import com.example.familibalance.util.asString
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import com.example.familibalance.ui.components.GameIcon
import com.example.familibalance.ui.components.GameIconType
import com.example.familibalance.ui.components.LanguageSelectorButton
import kotlinx.coroutines.delay
import com.example.familibalance.ui.components.childDisplayName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.example.familibalance.viewmodel.AuthEvent
import com.example.familibalance.viewmodel.AuthViewModel
import com.example.familibalance.viewmodel.PendingRequestsViewModel
import com.example.familibalance.viewmodel.PendingState
import com.example.familibalance.viewmodel.actions.createChildProfile

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val CyberLime = Color(0xFFA3E635)
private val CyberRed = Color(0xFFEF4444)

private val playerAvatars = listOf(
    "🤖", "🚀", "👾", "👽", "🦊", "🐉",
    "🦸‍♂️", "🐱", "🐼", "🐺"
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
    modifier: Modifier = Modifier,
    initialParentLogin: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    // Профиль, отправленный на подтверждение родителю (status = "pending")
    var pendingChild by remember { mutableStateOf<ChildProfileState?>(null) }

    val context = LocalContext.current
    var rememberMe by remember { mutableStateOf(false) }
    // Вход родителя по email/паролю — доступен по ссылке внизу формы
    var showParentLogin by remember { mutableStateOf(initialParentLogin) }
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler { showExitDialog = true }

    // Восстанавливаем «Запомнить меня»: подставляем сохранённый email
    LaunchedEffect(Unit) {
        val prefs = SecurePrefs.get(context, "app_prefs")
        if (prefs.getBoolean("is_remember_me", false)) {
            rememberMe = true
            prefs.getString("saved_admin_email", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { viewModel.onEmailChange(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.AuthSuccess -> {
                    saveRememberMe(context, viewModel.uiState.value.email.trim(), rememberMe)
                    onParentSuccess()
                }
                AuthEvent.PlayerSuccess -> onPlayerSuccess()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        val pending = pendingChild
        if (pending != null) {
            PendingRequestScreen(
                child = pending,
                mainViewModel = mainViewModel,
                onApproved = onPlayerSuccess
            )
            return@Box
        }

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
                Text(
                    text = stringResource(
                        if (showParentLogin) R.string.parent_area_title else R.string.register_title
                    ),
                    color = CyberTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                LanguageSelectorButton()
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (showParentLogin) {
                ParentLoginForm(
                    state = state,
                    viewModel = viewModel,
                    rememberMe = rememberMe,
                    onRememberMeChange = { rememberMe = it }
                )
            } else {
                PlayerRegisterForm(
                    state = state,
                    viewModel = viewModel,
                    onSubmit = {
                        mainViewModel.createChildProfile(
                            nickname = state.nickname,
                            pin = state.pin,
                            avatar = state.avatar,
                            parentEmail = state.parentEmail,
                            onResult = { profile ->
                                if (profile.status == "pending") {
                                    pendingChild = profile
                                } else {
                                    onPlayerSuccess()
                                }
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Скрытый вход для родителя (email/пароль Supabase Auth)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showParentLogin = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(icon = GameIconType.Trophy, tint = CyberTextSecondary, size = 16.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.parent_area_link),
                            color = CyberTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
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

@Composable
fun PlayerRegisterForm(
    state: com.example.familibalance.viewmodel.AuthUiState,
    viewModel: AuthViewModel,
    onSubmit: () -> Unit
) {
    var pinVisible by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CyberPanelBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberSlateBorder)
    ) {
    Column(modifier = Modifier.padding(20.dp)) {
    // Аватар — сетка 6 колонок
    Text(
        text = stringResource(R.string.reg_avatar),
        color = CyberTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    playerAvatars.chunked(6).forEach { rowAvatars ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowAvatars.forEach { avatar ->
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
            // Пустые ячейки в последнем ряду, чтобы сетка не «плыла»
            repeat(6 - rowAvatars.size) {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Никнейм и ПИН в ряд
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::onNicknameChange,
            label = { Text(stringResource(R.string.reg_nickname)) },
            singleLine = true,
            colors = CyberFieldColors,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.pin,
            onValueChange = viewModel::onPinChange,
            label = { Text(stringResource(R.string.reg_pin)) },
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
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        text = stringResource(R.string.reg_pin_hint),
        color = CyberTextSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Email родителя — на него придёт запрос на подтверждение
    OutlinedTextField(
        value = state.parentEmail,
        onValueChange = viewModel::onParentEmailChange,
        label = { Text(stringResource(R.string.reg_parent_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = CyberFieldColors,
        supportingText = {
            Text(
                text = stringResource(R.string.reg_parent_email_hint),
                color = CyberTextSecondary,
                fontSize = 11.sp
            )
        },
        modifier = Modifier.fillMaxWidth()
    )

    state.errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = error.asString(),
            color = CyberRed,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    GameButton(
        text = stringResource(R.string.reg_submit),
        icon = GameIconType.Rocket,
        variant = GameButtonVariant.Lime,
        enabled = isPlayerFormValid(state),
        onClick = onSubmit,
        modifier = Modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(16.dp),
            ambientColor = CyberLime,
            spotColor = CyberLime
        )
    )
    }
    }
}

@Composable
private fun ParentLoginForm(
    state: com.example.familibalance.viewmodel.AuthUiState,
    viewModel: AuthViewModel,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CyberPanelBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberSlateBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Переключатель Вход / Регистрация для родителя — над полями
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
                    val selected = state.isParentRegisterMode == isReg
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
                            .clickable { if (!selected) viewModel.toggleParentMode() }
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

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.parent_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = CyberFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.parent_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = CyberFieldColors,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        GameIcon(
                            icon = if (passwordVisible) GameIconType.EyeOff else GameIconType.Eye,
                            tint = CyberTextSecondary,
                            size = 20.dp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!state.isParentRegisterMode) {
                val cooldownUntil = state.resetCooldownUntilMs
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(cooldownUntil) {
                    while (cooldownUntil > System.currentTimeMillis()) {
                        nowMs = System.currentTimeMillis()
                        delay(1000)
                    }
                    nowMs = System.currentTimeMillis()
                }
                if (cooldownUntil > nowMs) {
                    val remainingSec = ((cooldownUntil - nowMs) / 1000).coerceAtLeast(0)
                    Text(
                        text = stringResource(
                            R.string.auth_reset_cooldown,
                            remainingSec / 60,
                            remainingSec % 60
                        ),
                        color = CyberTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showResetDialog = true }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = stringResource(R.string.auth_forgot_password),
                            color = CyberLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // «Запомнить меня» — сохраняет email в SharedPreferences
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRememberMeChange(!rememberMe) }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = onRememberMeChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = CyberLime,
                        uncheckedColor = CyberTextSecondary
                    )
                )
                Text(
                    text = stringResource(R.string.remember_me),
                    color = CyberTextSecondary,
                    fontSize = 13.sp
                )
            }

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error.asString(),
                    color = CyberRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.noticeMessage?.let { notice ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = notice.asString(),
                    color = CyberLime,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isParentRegisterMode) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { consent = !consent }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = consent,
                        onCheckedChange = { consent = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyberLime,
                            uncheckedColor = CyberTextSecondary
                        )
                    )
                    Text(
                        text = stringResource(R.string.parent_consent),
                        color = CyberTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GameButton(
                text = stringResource(if (state.isParentRegisterMode) R.string.parent_btn_register else R.string.parent_btn_login),
                icon = GameIconType.Lock,
                variant = GameButtonVariant.Electric,
                enabled = isParentFormEnabled(state) && (!state.isParentRegisterMode || consent),
                onClick = {
                    if (state.isParentRegisterMode) viewModel.signUpParent() else viewModel.signIn()
                }
            )

            if (showResetDialog) {
                ExitConfirmDialog(
                    title = stringResource(R.string.reset_confirm_title),
                    message = stringResource(R.string.reset_confirm_message, state.email.trim()),
                    confirmText = stringResource(R.string.reset_confirm_send),
                    cancelText = stringResource(R.string.action_cancel),
                    onConfirm = {
                        showResetDialog = false
                        viewModel.resetPassword()
                    },
                    onDismiss = { showResetDialog = false }
                )
            }
        }
    }
}

/**
 * Экран ожидания подтверждения родителем.
 * Каждые 5 секунд опрашивает family_profiles: как только status станет "active" —
 * обновляет локальный профиль и вызывает [onApproved] (переход в ChildRoute).
 */
@Composable
fun PendingRequestScreen(
    child: ChildProfileState,
    mainViewModel: MainViewModel,
    onApproved: () -> Unit
) {
    // MVI: вся логика polling/backoff — в PendingRequestsViewModel.
    // UI только транслирует lifecycle и реагирует на State.
    val pendingVm: PendingRequestsViewModel = viewModel { PendingRequestsViewModel(mainViewModel) }
    val pendingState by pendingVm.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            pendingVm.onResume(child)
        } else {
            pendingVm.onPause()
        }
    }
    LaunchedEffect(pendingState) {
        if (pendingState is PendingState.Approved) onApproved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GameIcon(icon = GameIconType.Hourglass, tint = CyberLime, size = 64.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.pending_title),
            color = CyberLime,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pending_waiting, child.avatar, childDisplayName(child)),
            color = CyberTextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pending_hint),
            color = CyberTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pending_checking),
            color = CyberTextSecondary,
            fontSize = 12.sp
        )
    }
}

