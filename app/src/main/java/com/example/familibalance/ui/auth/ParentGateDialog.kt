package com.example.familibalance.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.familibalance.R
import com.example.familibalance.data.ParentPinManager
import kotlinx.coroutines.launch

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.95f)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val CyberLime = Color(0xFFA3E635)
private val CyberRed = Color(0xFFEF4444)

/**
 * Переиспользуемая цифровая клавиатура ПИН-кода (тёмный неоновый стиль).
 * onDigit — цифра 0-9, onBackspace — стереть последнюю.
 */
@Composable
internal fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(modifier = modifier.fillMaxWidth()) {
        keys.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowKeys.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f).height(52.dp))
                    } else {
                        val isSpecial = key == "⌫"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSpecial) CyberSlate else CyberPanelBg)
                                .border(1.dp, CyberSlateBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isSpecial) onBackspace() else onDigit(key)
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
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** 4 слота ввода ПИН-кода с подсветкой ошибки. */
@Composable
internal fun PinSlots(
    pin: String,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            hasError -> CyberRed
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
}

/**
 * Родительский «замок» в стиле КвестЛаб: 4-значный ПИН-код.
 * ПИН хранится в [ParentPinManager]; если PIN ещё не задан —
 * показывается диалог создания ([ChangeParentPinDialog]).
 * Верный ПИН -> [onSuccess], неверный -> тряска + очистка.
 */
@Composable
fun ParentGateDialog(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // PIN не задан — доступа нет: сначала создаём родительский PIN.
    val pinExists = remember { ParentPinManager.getPin(context) != null }
    if (!pinExists) {
        ChangeParentPinDialog(
            onDismiss = onDismiss,
            onSaved = { onSuccess() }
        )
        return
    }
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    val shakeScope = rememberCoroutineScope()

    fun checkPin() {
        if (ParentPinManager.verifyPin(context, pin)) {
            onSuccess()
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CyberPanelBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberLime.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyberLime.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberLime.copy(alpha = 0.5f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🔐", fontSize = 30.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.pin_parent_check_title),
                    color = CyberLime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = stringResource(R.string.pin_parent_check_subtitle),
                    color = CyberTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = shakeOffset.value },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PinSlots(pin = pin, hasError = hasError)

                    if (hasError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.login_pin_error),
                            color = CyberRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PinKeypad(
                        onDigit = { digit ->
                            if (pin.length < 4) {
                                pin += digit
                                hasError = false
                            }
                        },
                        onBackspace = {
                            pin = pin.dropLast(1)
                            hasError = false
                        }
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = CyberTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
