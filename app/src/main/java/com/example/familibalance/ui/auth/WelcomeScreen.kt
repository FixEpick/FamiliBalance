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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
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
                .padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Логотип
            Text(
                text = "КВЕСТЛАБ",
                color = CyberLime,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "Семейная квестовая вселенная",
                color = CyberTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Маскот-дрон + диалоговое облачко
            Box(contentAlignment = Alignment.TopCenter) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CyberPanelBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33A3E635))
                ) {
                    Text(
                        text = "Привет, космический исследователь! Готов к новым квестам? 🚀",
                        color = CyberTextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(46.dp))
                Text("🤖", fontSize = 72.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопки
            GameButton(
                text = "Я Игрок",
                emoji = "🎮",
                variant = GameButtonVariant.Lime,
                onClick = onPlayerClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            GameButton(
                text = "Создать новый аккаунт игрока",
                emoji = "➕",
                variant = GameButtonVariant.Slate,
                onClick = onRegisterClick
            )

            Spacer(modifier = Modifier.height(20.dp))

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

