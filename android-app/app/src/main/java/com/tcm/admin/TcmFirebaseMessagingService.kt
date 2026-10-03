package com.tcm.admin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives FCM messages (both foreground and background).
 *
 * On token refresh → uploads new token to backend via ApiClient.
 * On message received → shows a system notification.
 *
 * Notification channel CHANNEL_TRANSFER is created on first use (Android 8+).
 */
class TcmFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_TRANSFER = "transfer_alerts"

        /**
         * Creates the notification channel required on Android 8+.
         * Safe to call multiple times (no-op if channel already exists).
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_TRANSFER,
                    "调拨与归还提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "门店调拨申请及归还确认通知"
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }

        /**
         * Registers the current FCM token with the backend.
         * Called after login and on token refresh.
         */
        fun registerToken(context: Context) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiClient.registerDeviceToken(context, platform = "android", token = token)
                        } catch (e: Exception) {
                            android.util.Log.w("TcmFCM", "Failed to register FCM token: ${e.message}")
                        }
                    }
                }
        }

        /**
         * Unregisters the current FCM token from the backend (called on logout).
         */
        fun unregisterToken(context: Context) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiClient.unregisterDeviceToken(context, token = token)
                        } catch (e: Exception) {
                            android.util.Log.w("TcmFCM", "Failed to unregister FCM token: ${e.message}")
                        }
                    }
                }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token refreshed — re-register with backend if logged in
        if (ApiClient.isLoggedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerDeviceToken(applicationContext, platform = "android", token = token)
                } catch (e: Exception) {
                    android.util.Log.w("TcmFCM", "Token refresh upload failed: ${e.message}")
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val transferId = message.data["transferId"]

        createNotificationChannel(applicationContext)

        // Tap → open the app (deep link to transfers screen handled by MainActivity)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (transferId != null) {
                putExtra("push_transfer_id", transferId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            transferId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_TRANSFER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        }
    }
}
