package com.example.familibalance.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ChildProfileState
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant
import kotlinx.coroutines.launch

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val CyberLime = Color(0xFFA3E635)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)

/**
 * Вход игрока по ПИН-коду. Профили берутся из [profiles] (ChildProfileState).
 * Верный PIN -> [onLoginSuccess] с id профиля; пустой список -> кнопка создания аккаунта.
 */
@Composable
fun PlayerLoginScreen(
    profiles: List<ChildProfileState>,
    onBack: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedProfile by remember { mutableStateOf<ChildProfileState?>(profiles.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    val shakeScope = androidx.compose.runtime.rememberCoroutineScope()

    // Если список профилей изменился (например, после создания) — обновляем выбор
    LaunchedEffect(profiles) {
        if (selectedProfile == null || profiles.none { it.id == selectedProfile?.id }) {
            selectedProfile = profiles.firstOrNull()
            pin = ""
            hasError = false
        }
    }

    fun checkPin() {
        val profile = selectedProfile ?: return
        if (pin == profile.pinCode) {
            onLoginSuccess(profile.id)
        } else {
            hasError = true
            pin = ""
            shakeScope.launch {
                repeat(4) { i ->
                    shakeOffset.animateTo(
                        targetValue = if (i % 2 == 0) 24f else -24f,
                        animationSpec = tween(durationMillis = 60)
                    )
                }
                shakeOffset.animateTo(0f, animationSpec = tween(durationMillis = 60))
            }
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) checkPin()
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
                    Text("← Назад", color = CyberTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Вход на станцию",
                    color = CyberTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberSlate)
                        .border(1.dp, CyberSlateBorder, RoundedCornerShape(10.dp))
                        .clickable { /* TODO: экран ввода кода */ }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🔑 Код", color = CyberTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (profiles.isEmpty()) {
                // Первый запуск — профилей нет
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🤖", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Пока нет ни одного игрока",
                        color = CyberTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Создай первый аккаунт, чтобы начать квесты",
                        color = CyberTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    GameButton(
                        text = "Создать аккаунт",
                        emoji = "➕",
                        variant = GameButtonVariant.Lime,
                        onClick = onCreateAccount
                    )
                }
            } else {
                // Профили — горизонтальный ряд карточек
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == selectedProfile?.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) CyberLime.copy(alpha = 0.12f) else CyberPanelBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) CyberLime else CyberSlateBorder
                            ),
                            modifier = Modifier
                                .width(140.dp)
                                .clickable {
                                    selectedProfile = profile
                                    pin = ""
                                    hasError = false
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CyberLime.copy(alpha = 0.15f),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(profile.avatar, fontSize = 26.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = profile.name,
                                    color = if (isSelected) CyberLime else CyberTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ур. ${profile.streakDays.coerceAtLeast(1)} • 🔥${profile.streakDays}",
                                    color = CyberTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ПИН-код
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = shakeOffset.value }
                        .clip(RoundedCornerShape(20.dp))
                        .background(CyberPanelBg)
                        .border(1.dp, CyberSlateBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Секретный ПИН-код",
                        color = CyberTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 слота
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { index ->
                            val filled = index < pin.length
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            hasError -> Color(0xFF7F1D1D).copy(alpha = 0.6f)
                                            filled -> CyberLime.copy(alpha = 0.15f)
                                            else -> CyberSlate
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            hasError -> Color(0xFFEF4444)
                                            filled -> CyberLime
                                            else -> CyberSlateBorder
                                        },
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (filled) {
                                    Text("•", color = CyberLime, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (hasError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Неверный ПИН-код", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Клавиатура 3x4
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("⚡", "0", "⌫")
                    )
                    keys.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val isSpecial = key == "⚡" || key == "⌫"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSpecial) CyberSlate else CyberPanelBg)
                                        .border(1.dp, CyberSlateBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (key) {
                                                "⌫" -> {
                                                    pin = pin.dropLast(1)
                                                    hasError = false
                                                }
                                                "⚡" -> {
                                                    // Подставляем реальный PIN выбранного профиля (для тестирования)
                                                    pin = selectedProfile?.pinCode?.take(4) ?: ""
                                                }
                                                else -> {
                                                    if (pin.length < 4) {
                                                        pin += key
                                                        hasError = false
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        color = if (isSpecial) CyberLime else CyberTextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
