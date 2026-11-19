package com.example.familibalance.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.util.UUID

/**
 * Гарантирует, что URI фото квеста указывает на файл, который переживёт
 * перезапуск приложения (filesDir), а не на временный content:// провайдер.
 *
 * - FileProvider-URI нашего приложения уже указывают в filesDir — не трогаем.
 * - content:// от внешних источников (галерея и т.п.) и file:// из cacheDir
 *   копируются в filesDir/quest_photos.
 */
object PhotoStorage {

    private const val TAG = "PhotoStorage"
    private const val SUBDIR = "quest_photos"

    fun persistIfNeeded(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return uriString

        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return uriString
        val ourProvider = "${context.packageName}.fileprovider"

        val alreadyPersistent = when (uri.scheme) {
            "content" -> uri.authority == ourProvider
            "file" -> uri.path?.startsWith(context.filesDir.absolutePath) == true
            else -> true // http(s) и прочие — не локальные файлы, копировать нечего
        }
        if (alreadyPersistent) return uriString

        return try {
            val dir = File(context.filesDir, SUBDIR).apply { mkdirs() }
            val dest = File(
                dir,
                "quest_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return uriString

            FileProvider.getUriForFile(context, ourProvider, dest).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось сохранить фото локально, используем исходный URI: ${e.message}")
            uriString
        }
    }

    /**
     * Удаляет локальный файл фото из filesDir/quest_photos, если URI на него указывает.
     * Безопасно для http(s) и чужих content:// — просто no-op.
     * Вызывается после approve/reject/claim и после успешной загрузки в Storage,
     * чтобы filesDir не забивался обработанными фото.
     */
    fun deleteLocalFileIfPresent(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return
        val ourProvider = "${context.packageName}.fileprovider"
        val file = when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> if (uri.authority == ourProvider) {
                // FileProvider URI: content://pkg.fileprovider/quest_photos/<name>.jpg
                uri.lastPathSegment?.let { File(context.filesDir, "$SUBDIR/$it") }
            } else null
            else -> null
        } ?: return
        runCatching {
            if (file.exists()) file.delete()
        }
    }
}
