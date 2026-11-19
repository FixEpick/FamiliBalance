package com.example.familibalance.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.familibalance.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Инфраструктура FCM-пушей: каналы уведомлений, шина deep-link навигации
 * и запрос разрешения POST_NOTIFICATIONS (Android 13+).
 */
object PushChannels {
    /** Квесты, награды, вывод средств, новые задания (для ребёнка). */
    const val QUESTS = "quests_and_rewards"
    /** Запросы на проверку: фотоотчёты, вывод, признания (для родителя). */
    const val PARENT_MODERATION = "parent_moderation"
    /** Изменения баланса и штрафы. */
    const val BALANCE = "balance_updates"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    QUESTS,
                    "Квесты и награды",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Выполнение квестов, награды, вывод средств" },
                NotificationChannel(
                    PARENT_MODERATION,
                    "Запросы на проверку",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Фотоотчёты, заявки на вывод, честные признания" },
                NotificationChannel(
                    BALANCE,
                    "Изменения баланса",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Штрафы и обновления баланса" }
            )
        )
    }
}

/**
 * Pending-навигация из пуша. FCM-сервис не может дёргать Compose напрямую,
 * поэтому кладёт сюда screen+role, а секции (ChildSection/AdminScreen)
 * забирают и выполняют переход на нужную вкладку.
 *
 * screen: "main" | "tasks" | "history" | "verification" | "progress"
 * role:   "child" | "parent"
 */
data class PendingPushNav(val screen: String, val role: String)

object PushNavBus {
    private val _pending = MutableStateFlow<PendingPushNav?>(null)
    val pending: StateFlow<PendingPushNav?> = _pending.asStateFlow()

    fun post(screen: String?, role: String?) {
        if (screen.isNullOrBlank() || role.isNullOrBlank()) return
        _pending.value = PendingPushNav(screen, role)
    }

    /** Очищает ожидающую навигацию после обработки. */
    fun consume() {
        _pending.value = null
    }
}

/** Читает deep-link extras из intent'а пуша (MainActivity onCreate/onNewIntent). */
fun handlePushIntent(intent: Intent?) {
    val screen = intent?.getStringExtra("push_screen")
    val role = intent?.getStringExtra("push_role")
    PushNavBus.post(screen, role)
}

/** Intent для открытия приложения на конкретном экране (используется FCM-сервисом). */
fun pushLaunchIntent(context: Context, screen: String, role: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra("push_screen", screen)
        putExtra("push_role", role)
    }

/**
 * Запрашивает POST_NOTIFICATIONS на Android 13+. На старых версиях — no-op.
 * Вызывать в момент целевого действия (отправка квеста / создание семьи),
 * а не при первом запуске — иначе пользователь может навсегда запретить пуши.
 */
@Composable
fun rememberPushPermissionRequester(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
