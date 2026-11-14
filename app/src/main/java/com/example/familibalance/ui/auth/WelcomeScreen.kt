package com.example.familibalance.ui.auth

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.ui.components.CyberBackground
import com.example.familibalance.ui.components.GameButton
import com.example.familibalance.ui.components.GameButtonVariant

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.85f)
private val CyberLime = Color(0xFFA3E635)
private val CyberLimeDim = Color(0xFF65A30D)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val RobotBody = Color(0xFF334155)
private val RobotDark = Color(0xFF0F172A)
private val RobotEye = Color(0xFFA3E635)

/**
 * Диалоговое облачко с «хвостиком» вниз — рисуется через Canvas.
 */
@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberPanelBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberLime.copy(alpha = 0.35f))
        ) {
            Text(
                text = text,
                color = CyberTextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        // Хвостик облачка — треугольник
        Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f - 8f, 0f)
                lineTo(size.width / 2f + 8f, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, CyberPanelBg)
        }
    }
}

/**
 * Робот-маскот «КвестЛаб», нарисованный через Canvas:
 * голова, антенна, визор с зелёными глазами, «уши».
 */
@Composable
private fun RobotMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 140.dp, height = 150.dp)) {
        val w = size.width
        val h = size.height

        // Антенна
        drawLine(
            color = RobotBody,
            start = Offset(w / 2f, h * 0.10f),
            end = Offset(w / 2f, h * 0.22f),
            strokeWidth = 5f
        )
        drawCircle(color = RobotEye, radius = 7f, center = Offset(w / 2f, h * 0.08f))
        drawCircle(color = RobotEye.copy(alpha = 0.3f), radius = 12f, center = Offset(w / 2f, h * 0.08f))

        // Уши
        drawRoundRect(
            color = RobotBody,
            topLeft = Offset(w * 0.06f, h * 0.30f),
            size = Size(w * 0.10f, h * 0.22f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = RobotBody,
            topLeft = Offset(w * 0.84f, h * 0.30f),
            size = Size(w * 0.10f, h * 0.22f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Голова
        drawRoundRect(
            color = RobotBody,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(22f, 22f)
        )
        // Контур головы
        drawRoundRect(
            color = CyberLimeDim,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(22f, 22f),
            style = Stroke(width = 3f)
        )

        // Визор
        drawRoundRect(
            color = RobotDark,
            topLeft = Offset(w * 0.26f, h * 0.34f),
            size = Size(w * 0.48f, h * 0.24f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        // Глаза
        drawCircle(color = RobotEye, radius = 8f, center = Offset(w * 0.40f, h * 0.46f))
        drawCircle(color = RobotEye, radius = 8f, center = Offset(w * 0.60f, h * 0.46f))
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(w * 0.40f - 2f, h * 0.46f - 2f))
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(w * 0.60f - 2f, h * 0.46f - 2f))

        // Рот-светодиод
        drawRoundRect(
            color = CyberLime.copy(alpha = 0.8f),
            topLeft = Offset(w * 0.44f, h * 0.64f),
            size = Size(w * 0.12f, h * 0.05f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Шея/корпус
        drawRoundRect(
            color = RobotBody,
            topLeft = Offset(w * 0.36f, h * 0.80f),
            size = Size(w * 0.28f, h * 0.10f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = RobotDark,
            topLeft = Offset(w * 0.30f, h * 0.88f),
            size = Size(w * 0.40f, h * 0.10f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }
}

/**
 * Стартовый экран «КвестЛаб»: выбор роли (игрок / родитель).
 */
@Composable
fun WelcomeScreen(
    onPlayerClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onParentGatePassed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showParentGate by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        CyberBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верх: логотип
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "КВЕСТЛАБ",
                    color = CyberLime,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Семейная квестовая вселенная",
                    color = CyberTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Центр: облачко + робот
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpeechBubble(text = "Привет, космический исследователь! Готов к новым квестам? 🚀")
                Spacer(modifier = Modifier.height(14.dp))
                RobotMascot()
            }

            // Низ: кнопки
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // «Я Игрок» — неоновое свечение
                GameButton(
                    text = "Я Игрок",
                    emoji = "🎮",
                    variant = GameButtonVariant.Lime,
                    onClick = onPlayerClick,
                    modifier = Modifier.shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = CyberLime,
                        spotColor = CyberLime
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // «Создать аккаунт» — тёмная с неоновой рамкой
                GameButton(
                    text = "Создать новый аккаунт игрока",
                    emoji = "➕",
                    variant = GameButtonVariant.Slate,
                    onClick = onRegisterClick
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showParentGate = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛡️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Для родителей",
                        color = CyberTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showParentGate) {
        ParentGateDialog(
            onSuccess = {
                showParentGate = false
                onParentGatePassed()
            },
            onDismiss = { showParentGate = false }
        )
    }
}
