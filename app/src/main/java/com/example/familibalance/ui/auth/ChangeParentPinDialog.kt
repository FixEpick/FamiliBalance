package com.example.familibalance.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.familibalance.data.ParentPinManager
import kotlinx.coroutines.launch

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.95f)
private val CyberSlate = Color(0xFF1E293B)
private val CyberLime = Color(0xFFA3E635)
private val CyberRed = Color(0xFFEF4444)

/**
 * Диалог смены родительского ПИН-кода.
 * Шаг 1: ввести текущий ПИН (сверка с [ParentPinManager]).
 * Шаг 2: ввести новый 4-значный ПИН -> сохранение.
 */
@Composable
fun ChangeParentPinDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(1) } // 1 — текущий, 2 — новый
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }
    val shakeScope = rememberCoroutineScope()

    fun shakeAndReset(message: String) {
        hasError = true
        statusMessage = message
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

    LaunchedEffect(pin) {
        if (pin.length != 4) return@LaunchedEffect
        when (step) {
            1 -> {
                if (pin == ParentPinManager.getPin(context)) {
                    pin = ""
                    hasError = false
                    statusMessage = null
                    step = 2
                } else {
                    shakeAndReset("НЕВЕРНЫЙ ТЕКУЩИЙ ПИН")
                }
            }
            2 -> {
                ParentPinManager.setPin(context, pin)
                onSaved()
                onDismiss()
            }
        }
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
                        Text("🔑", fontSize = 30.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "РОДИТЕЛЬСКИЙ ПИН-КОД",
                    color = CyberLime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (step == 1) "Введи текущий ПИН" else "Придумай новый ПИН (4 цифры)",
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

                    statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = if (hasError) CyberRed else CyberLime,
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
                                statusMessage = null
                            }
                        },
                        onBackspace = {
                            pin = pin.dropLast(1)
                            hasError = false
                            statusMessage = null
                        }
                    )
                }

                if (step == 2) {
                    TextButton(onClick = {
                        step = 1
                        pin = ""
                        hasError = false
                        statusMessage = null
                    }) {
                        Text("Назад", color = CyberTextSecondary, fontSize = 13.sp)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = CyberTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
