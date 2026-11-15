package com.example.familibalance.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familibalance.R
import com.example.familibalance.data.update.UpdateError
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
                    stringResource(R.string.update_title, state.release.tagName),
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
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    } else {
                        Text(
                            stringResource(R.string.update_no_notes),
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onUpdate) { Text(stringResource(R.string.update_btn_update)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_btn_later)) }
            }
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading_title), fontWeight = FontWeight.Bold) },
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
            title = { Text(stringResource(R.string.update_permission_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.update_install_permission_text),
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
                }) { Text(stringResource(R.string.update_btn_open_settings)) }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )

        is UpdateUiState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_permission_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.update_permission_text),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(state.intent)
                    onDismiss()
                }) { Text(stringResource(R.string.update_btn_open_settings)) }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_error_title), fontWeight = FontWeight.Bold) },
            text = { Text(updateErrorMessage(state.error), fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(R.string.update_btn_close)) }
            }
        )
    }
}

/** Маппит типизированную ошибку data-слоя в локализованную строку. */
@Composable
private fun updateErrorMessage(error: UpdateError): String = when (error) {
    UpdateError.SignatureMismatch -> stringResource(R.string.error_signature_mismatch)
    UpdateError.DownloadFailed -> stringResource(R.string.error_download_failed)
    UpdateError.ChecksumMismatch -> stringResource(R.string.error_checksum_mismatch)
    UpdateError.NoApkInRelease -> stringResource(R.string.error_no_apk_in_release)
    UpdateError.PermissionDenied -> stringResource(R.string.error_permission_denied)
    is UpdateError.Unknown -> error.message
        ?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.error_unknown_with_detail, it) }
        ?: stringResource(R.string.error_unknown)
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
