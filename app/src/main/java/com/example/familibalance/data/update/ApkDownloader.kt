package com.example.familibalance.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

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
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, fileName)

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
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
            target.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            android.util.Log.w("ApkDownloader", "Download failed: ${e.message}")
            null
        }
    }
}
