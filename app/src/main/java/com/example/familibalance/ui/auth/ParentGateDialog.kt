package com.example.familibalance.ui.auth

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.random.Random

private val CyberTextPrimary = Color(0xFFE2E8F0)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberPanelBg = Color(0xFF0F172A).copy(alpha = 0.95f)
private val CyberSlate = Color(0xFF1E293B)
private val CyberSlateBorder = Color(0xFF334155)
private val CyberLime = Color(0xFFA3E635)
private val CyberRed = Color(0xFFEF4444)

private data class MathChallenge(
    val a: Int,
    val b: Int,
    val answers: List<Int>
) {
    val correct: Int get() = a * b
}

private fun generateChallenge(): MathChallenge {
    val a = Random.nextInt(12, 18)  // 12..17
    val b = Random.nextInt(3, 7)    // 3..6
    val correct = a * b
    val wrongs = generateSequence { Random.nextInt(correct - 15, correct + 16) }
        .filter { it != correct && it > 0 }
        .distinct()
        .take(2)
        .toList()
    return MathChallenge(a, b, (wrongs + correct).shuffled())
}

/**
 * Родительский «замок» в стиле КвестЛаб: пример на умножение.
 * Правильный ответ -> [onSuccess], неверный -> ошибка + новый пример.
 */
@Composable
fun ParentGateDialog(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var challenge by remember { mutableStateOf(generateChallenge()) }
    var hasError by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
                    text = "ПРОВЕРКА РОДИТЕЛЯ",
                    color = CyberLime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Реши пример, чтобы продолжить",
                    color = CyberTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${challenge.a} × ${challenge.b} = ?",
                    color = CyberTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )

                if (hasError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "НЕВЕРНО! ПОПРОБУЙ ЕЩЁ РАЗ",
                        color = CyberRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                challenge.answers.chunked(3).forEach { rowAnswers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowAnswers.forEach { answer ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, CyberSlateBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (answer == challenge.correct) {
                                            onSuccess()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            hasError = true
                                            challenge = generateChallenge()
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = answer.toString(),
                                    color = CyberTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        repeat(3 - rowAnswers.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = CyberTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
