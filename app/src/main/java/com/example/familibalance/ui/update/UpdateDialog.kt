package com.example.familibalance.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.viewmodel.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    when (state) {
        UpdateUiState.Idle,
        UpdateUiState.Checking,
        UpdateUiState.Dismissed -> Unit

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Доступно обновление ${state.release.tagName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    val notes = state.release.body.lineSequence()
                        .filter { it.isNotBlank() }
                        .take(10)
                        .joinToString("\n")
                    if (notes.isNotBlank()) {
                        Text(
                            stripMarkdown(notes) + if (state.release.body.lines().size > 10) "\n..." else "",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    } else {
                        Text("Вышла новая версия приложения.", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = onUpdate) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Позже") }
            }
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${state.progress}%", fontSize = 13.sp)
                }
            },
            confirmButton = {}
        )

        is UpdateUiState.ReadyToInstall -> {
            // Запускаем установщик сразу при переходе в это состояние
            androidx.compose.runtime.LaunchedEffect(state) {
                context.startActivity(state.intent)
                onDismiss()
            }
        }

        is UpdateUiState.NeedInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Нужно разрешение", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Чтобы установить обновление, разрешите приложению установку из неизвестных источников. Скачивание начнётся автоматически после возвращения в приложение.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) { Text("Отмена") }
            }
        )

        is UpdateUiState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Нужно разрешение", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Разрешите установку приложений из этого источника в настройках.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(state.intent)
                    onDismiss()
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) { Text("Отмена") }
            }
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Обновление", fontWeight = FontWeight.Bold) },
            text = { Text(state.message, fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = onDismiss) { Text("Закрыть") }
            }
        )
    }
}

/** Убирает базовую Markdown-разметку из Release Notes GitHub для чистого отображения. */
private fun stripMarkdown(text: String): String {
    return text
        .replace(Regex("```[\\s\\S]*?```"), "")          // блоки кода
        .replace(Regex("`([^`]+)`"), "$1")               // инлайн-код
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "") // заголовки
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")     // жирный **
        .replace(Regex("__([^_]+)__"), "$1")             // жирный __
        .replace(Regex("\\*([^*\\n]+)\\*"), "$1")        // курсив *
        .replace(Regex("~~([^~]+)~~"), "$1")             // зачёркнутый
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1") // ссылки [text](url)
        .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "• ") // маркеры списков
        .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "• ") // нумерованные списки
}
