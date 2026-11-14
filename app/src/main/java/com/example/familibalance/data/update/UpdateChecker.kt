package com.example.familibalance.data.update

import android.util.Log
import com.example.familibalance.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val body: String,
    val apkUrl: String?,
    /** Ожидаемый SHA-256 APK (парсится из тела релиза). null — проверка пропускается. */
    val sha256: String? = null
)

sealed interface UpdateResult {
    data object NoUpdate : UpdateResult
    data class Available(val release: ReleaseInfo) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * Проверка обновлений через GitHub Releases API.
 * Repo берётся из BuildConfig.GITHUB_REPO (формат "owner/name").
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API_BASE = "https://api.github.com/repos"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            if (repo.isBlank() || repo == "user/familibalance") {
                Log.d(TAG, "GITHUB_REPO не настроен — проверка обновлений пропущена")
                return@withContext UpdateResult.NoUpdate
            }

            val request = Request.Builder()
                .url("$API_BASE/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FamiliBalance-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API вернул ${response.code}")
                    return@withContext UpdateResult.Error("HTTP ${response.code}")
                }
                val json = response.body?.string() ?: return@withContext UpdateResult.Error("Пустой ответ")
                val release = parseRelease(json) ?: return@withContext UpdateResult.Error("Не удалось разобрать релиз")

                if (VersionComparator.isNewer(currentVersion, release.tagName)) {
                    UpdateResult.Available(release)
                } else {
                    UpdateResult.NoUpdate
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка проверки обновлений: ${e.message}")
            UpdateResult.Error(e.message ?: "network error")
        }
    }

    /** Ищет в теле релиза строку вида "SHA256: <64 hex>" / "sha256=<hex>". */
    private fun parseSha256(body: String): String? {
        val regex = Regex("(?i)sha-?256\\s*[:=]?\\s*([a-fA-F0-9]{64})")
        return regex.find(body)?.groupValues?.get(1)?.lowercase()
    }

    private fun parseRelease(json: String): ReleaseInfo? {
        return try {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "")
            if (tag.isBlank()) return null
            val body = obj.optString("body", "")
            val assets = obj.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }
            ReleaseInfo(tagName = tag, body = body, apkUrl = apkUrl, sha256 = parseSha256(body))
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error: ${e.message}")
            null
        }
    }
}
