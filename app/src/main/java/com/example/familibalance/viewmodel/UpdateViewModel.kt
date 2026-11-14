package com.example.familibalance.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.familibalance.BuildConfig
import com.example.familibalance.data.update.ApkDownloader
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
    data class Error(val message: String) : UpdateUiState
    data object Dismissed : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var checked = false

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
        val url = state.release.apkUrl
        if (url.isNullOrBlank()) {
            _uiState.value = UpdateUiState.Error("В релизе нет APK-файла")
            return
        }
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0)
            val fileName = "familibalance-${state.release.tagName}.apk"
            val file = ApkDownloader.download(getApplication(), url, fileName) { progress ->
                _uiState.value = UpdateUiState.Downloading(progress)
            }
            if (file == null) {
                _uiState.value = UpdateUiState.Error("Не удалось скачать обновление")
                return@launch
            }
            when (val install = UpdateInstaller.install(getApplication(), file)) {
                is InstallResult.ReadyToInstall ->
                    _uiState.value = UpdateUiState.ReadyToInstall(install.intent)
                is InstallResult.NeedPermission ->
                    _uiState.value = UpdateUiState.NeedPermission(install.intent)
            }
        }
    }

    fun dismiss() {
        _uiState.value = UpdateUiState.Dismissed
    }
}
