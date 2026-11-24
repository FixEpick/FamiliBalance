package com.example.familibalance.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.familibalance.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Приём FCM-пушей. Data-payload от Edge Function send-push:
 *   title, body — текст; screen, role — deep-link; channel — id канала;
 *   priority — "high"|"normal".
 */
class FamiliBalanceMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenRegistrar.onTokenRefreshed(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        // Локализация на устройстве получателя: если отправитель передал
        // title_key/body_key — резолвим string-ресурс в локали этого
        // устройства; иначе fallback на текст title/body (старые клиенты).
        val title = PushTexts.resolve(this, data["title_key"], data["title_args"])
            ?: data["title"] ?: message.notification?.title ?: return
        val body = PushTexts.resolve(this, data["body_key"], data["body_args"])
            ?: data["body"] ?: message.notification?.body ?: ""
        val screen = data["screen"].orEmpty()
        val role = data["role"].orEmpty()
        // Устройство в детском режиме не должно показывать родительские
        // пуши (например, «Новый фотоотчёт на проверке», который ребёнок
        // сам только что отправил). Роль устройства — из push_prefs;
        // на общем устройстве родителя role="parent" и фильтр не срабатывает.
        val deviceRole = PushTokenRegistrar.currentRole(this)
        if (deviceRole == "child" && role == "parent") return
        val channelId = data["channel"]?.takeIf { it.isNotBlank() } ?: PushChannels.QUESTS
        val highPriority = data["priority"] == "high"

        PushChannels.ensureChannels(this)

        val contentIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            pushLaunchIntent(this, screen, role),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (notificationsAllowed) {
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                    notification
                )
            }
        }
    }
}
