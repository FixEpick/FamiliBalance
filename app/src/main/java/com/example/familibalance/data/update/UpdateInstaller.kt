package com.example.familibalance.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

sealed interface InstallResult {
    data class ReadyToInstall(val intent: Intent) : InstallResult
    data class NeedPermission(val intent: Intent) : InstallResult
    data class Error(val error: UpdateError) : InstallResult
}

object UpdateInstaller {

    fun install(context: Context, apk: File): InstallResult {
        // Android 8+ требует разрешения «установка из неизвестных источников»
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return InstallResult.NeedPermission(settingsIntent)
        }

        // Проверка подписи: обновление обязано быть подписано тем же ключом,
        // что и установленное приложение. Иначе — удаляем файл и прерываемся.
        if (!hasSameSignature(context, apk)) {
            apk.delete()
            return InstallResult.Error(UpdateError.SignatureMismatch)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return InstallResult.ReadyToInstall(intent)
    }

    /**
     * Сравнивает сертификаты подписанта скачанного APK и текущего приложения.
     * На API 28+ используется GET_SIGNING_CERTIFICATES, на более старых — GET_SIGNATURES.
     */
    @Suppress("DEPRECATION")
    private fun hasSameSignature(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        // На API 28+ запрашиваем оба флага: signingInfo может быть null
        // (например, для архивного APK), тогда подписанты берём из signatures.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = runCatching {
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
        }.getOrNull() ?: return false

        val currentInfo = runCatching {
            pm.getPackageInfo(context.packageName, flags)
        }.getOrNull() ?: return false

        val archiveSigners = signingCertificates(archiveInfo)
        val currentSigners = signingCertificates(currentInfo)
        if (archiveSigners.isEmpty() || currentSigners.isEmpty()) return false

        // Обновление валидно, если хотя бы один подписант совпадает.
        return archiveSigners.any { archive ->
            currentSigners.any { current ->
                archive.toByteArray().contentEquals(current.toByteArray())
            }
        }
    }

    private fun signingCertificates(info: PackageInfo): List<Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
                ?.toList()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        // Fallback: signingInfo отсутствует (архивный APK на части устройств) —
        // берём устаревшее поле signatures, чтобы не получить ложный mismatch.
        @Suppress("DEPRECATION")
        return info.signatures?.toList().orEmpty()
    }
}
