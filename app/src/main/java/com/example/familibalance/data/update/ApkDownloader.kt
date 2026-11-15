package com.example.familibalance.data.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Error(val error: UpdateError) : DownloadResult
}

/** Скачивает APK в cacheDir/updates/ с отчётом о прогрессе (0..100). */
object ApkDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, fileName)

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error(UpdateError.DownloadFailed)
                }
                val body = response.body
                val total = body.contentLength()

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
            if (!target.exists() || target.length() == 0L) {
                target.delete()
                return@withContext DownloadResult.Error(UpdateError.DownloadFailed)
            }

            // Проверка целостности: если релиз публикует SHA-256 — сверяем.
            if (!expectedSha256.isNullOrBlank()) {
                val actual = sha256Hex(target)
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    target.delete()
                    android.util.Log.w("ApkDownloader", "SHA-256 mismatch: expected=$expectedSha256 actual=$actual")
                    return@withContext DownloadResult.Error(UpdateError.ChecksumMismatch)
                }
            }

            DownloadResult.Success(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ApkDownloader", "Download failed: ${e.message}")
            DownloadResult.Error(UpdateError.DownloadFailed)
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
