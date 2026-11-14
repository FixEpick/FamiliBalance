package com.example.familibalance.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мониторит доступность сети через ConnectivityManager.
 * Уведомляет слушателей только при переходе Offline -> Online,
 * чтобы можно было автоматически повторить синхронизацию.
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val listeners = mutableSetOf<(Boolean) -> Unit>()
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true

        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return

        _isOnline.value = connectivityManager.activeNetwork != null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setOnline(true)
            }

            override fun onLost(network: Network) {
                setOnline(false)
            }

            override fun onUnavailable() {
                setOnline(false)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun setOnline(online: Boolean) {
        val wasOnline = _isOnline.value
        _isOnline.value = online
        // Уведомляем только о восстановлении сети
        if (online && !wasOnline) {
            listeners.forEach { it(true) }
        }
    }

    @Synchronized
    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }
}
