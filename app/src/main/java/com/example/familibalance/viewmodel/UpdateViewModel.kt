package com.example.familibalance.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.BuildConfig
import com.example.familibalance.data.update.ApkDownloader
import com.example.familibalance.data.update.DownloadResult
import com.example.familibalance.data.update.InstallResult
import com.example.familibalance.data.update.ReleaseInfo
import com.example.familibalance.data.update.UpdateChecker
import com.example.familibalance.data.update.UpdateInstaller
import com.example.familibalance.data.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data class ReadyToInstall(val intent: Intent) : UpdateUiState
    data class NeedPermission(val intent: Intent) : UpdateUiState
    /** Разрешение на установку нужно запросить ДО скачивания APK. */
    data object NeedInstallPermission : UpdateUiState
    data class Error(val message: String) : UpdateUiState
    data object Dismissed : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var checked = false
    /** Релиз, для которого ждём разрешения на установку (скачивание ещё не начато). */
    private var pendingRelease: ReleaseInfo? = null

    /** Проверка при запуске: ошибки не показываем пользователю — только лог в UpdateChecker. */
    fun checkOnLaunch() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateResult.Available -> _uiState.value = UpdateUiState.Available(result.release)
                UpdateResult.NoUpdate -> _uiState.value = UpdateUiState.Idle
                is UpdateResult.Error -> _uiState.value = UpdateUiState.Idle // тихо, уже залогировано
            }
        }
    }

    fun startDownload() {
        val state = _uiState.value
        if (state !is UpdateUiState.Available) return
        val release = state.release
        val url = release.apkUrl
        if (url.isNullOrBlank()) {
            _uiState.value = UpdateUiState.Error("В релизе нет APK-файла")
            return
        }
        // Строгий порядок: разрешение на установку запрашиваем ДО скачивания.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !getApplication<Application>().packageManager.canRequestPackageInstalls()
        ) {
            pendingRelease = release
            _uiState.value = UpdateUiState.NeedInstallPermission
            return
        }
        pendingRelease = release
        downloadAndInstall(release)
    }

    /** Вызывать из onResume: если ждали разрешения и оно выдано — продолжаем скачивание. */
    fun onResume() {
        val pending = pendingRelease ?: return
        if (_uiState.value !is UpdateUiState.NeedInstallPermission) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !getApplication<Application>().packageManager.canRequestPackageInstalls()
        ) return
        pendingRelease = null
        downloadAndInstall(pending)
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        val url = release.apkUrl ?: return
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0)
            val fileName = "familibalance-${release.tagName}.apk"
            when (val result = ApkDownloader.download(
                getApplication(), url, fileName,
                expectedSha256 = release.sha256
            ) { progress ->
                _uiState.value = UpdateUiState.Downloading(progress)
            }) {
                is DownloadResult.Success -> {
                    when (val install = UpdateInstaller.install(getApplication(), result.file)) {
                        is InstallResult.ReadyToInstall ->
                            _uiState.value = UpdateUiState.ReadyToInstall(install.intent)
                        is InstallResult.NeedPermission ->
                            _uiState.value = UpdateUiState.NeedPermission(install.intent)
                    }
                }
                is DownloadResult.Error ->
                    _uiState.value = UpdateUiState.Error(result.message)
            }
        }
    }

    fun dismiss() {
        pendingRelease = null
        _uiState.value = UpdateUiState.Dismissed
    }
}
