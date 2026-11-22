package com.example.familibalance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.familibalance.MainViewModel
import com.example.familibalance.data.supabase.SupabaseManager
import com.example.familibalance.viewmodel.UpdateViewModel
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Жизненный цикл синхронизации данных:
 *  - ON_START: подписка активна всегда, включая фон
 *    (startRealtimeSync идемпотентен — повторный вызов no-op);
 *  - ON_RESUME: досылаем несинхронизированные данные и обновления.
 *
 * Вынесено из MainActivity — activity только монтирует эффект.
 */
@Composable
fun AppSessionEffects(
    viewModel: MainViewModel,
    updateViewModel: UpdateViewModel,
    sessionStatus: SessionStatus
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (sessionStatus is SessionStatus.Authenticated) {
                        SupabaseManager.startRealtimeSync(context)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.forceSync()
                    // Если ждали разрешения на установку — продолжаем обновление
                    updateViewModel.onResume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
