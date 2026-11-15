package com.example.familibalance.ui.auth

import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.familibalance.R

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.95f)
private val CyberLime = Color(0xFFA3E635)

/**
 * Диалог задания нового 4-значного ПИН-кода ребёнку.
 * Без проверки старого ПИН — родитель уже авторизован.
 * При вводе 4 цифр -> [onPinEntered], затем [onDismiss].
 */
@Composable
fun SetChildPinDialog(
    childName: String,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            onPinEntered(pin)
            onDismiss()
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
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text("👦", fontSize = 30.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.pin_child_title),
                    color = CyberLime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = stringResource(R.string.pin_child_subtitle, childName),
                    color = CyberTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                PinSlots(pin = pin, hasError = false)

                Spacer(modifier = Modifier.height(16.dp))

                PinKeypad(
                    onDigit = { digit -> if (pin.length < 4) pin += digit },
                    onBackspace = { pin = pin.dropLast(1) }
                )

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = CyberTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
