package com.tcm.admin

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TcmFcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "TcmFcmService"
        fun registerCurrentToken(context: Context) {
            if (!PushNotificationPreference.isEnabled(context)) return
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Fetching FCM registration token failed, falling back to JPush", task.exception)
                        TcmJPushReceiver.registerCurrentToken(context)
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    Log.d(TAG, "FCM Token obtained: ${token.take(20)}...")
                    uploadToken(context, token)
                    TcmJPushReceiver.registerCurrentToken(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token request unavailable, falling back to JPush", e)
                TcmJPushReceiver.registerCurrentToken(context)
            }
        }

        suspend fun unregisterToken(context: Context, authToken: String? = null) {
            try {
                val task = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                val token = suspendCancellableCoroutine<String?> { continuation ->
                    task.addOnCompleteListener { completedTask ->
                        continuation.resume(completedTask.takeIf { it.isSuccessful }?.result)
                    }
                }
                if (!token.isNullOrBlank()) {
                    ApiClient.unregisterDeviceToken(context, token, authToken)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister FCM token: ${e.message}")
            }
        }

        private fun uploadToken(context: Context, token: String) {
            if (!PushNotificationPreference.isEnabled(context)) return
            if (!ApiClient.isAuthenticated) {
                ApiClient.loadSession(context)
            }
            if (!ApiClient.isAuthenticated) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ApiClient.registerDeviceToken(context, platform = "android", token = token, deviceId = PushNotificationPreference.deviceId(context))) {
                        Log.d(TAG, "FCM Token uploaded successfully")
                    } else {
                        Log.w(TAG, "FCM token registration failed")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload FCM Token: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM onNewToken: ${token.take(20)}...")
        uploadToken(applicationContext, token)
        TcmJPushReceiver.registerCurrentToken(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (!PushNotificationPreference.isEnabled(this)) return
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        
        // When the app is in the foreground, FCM does NOT display notifications automatically.
        // We must build and display it manually to ensure the user gets alerted (with sound).
        val notification = remoteMessage.notification
        if (!NotificationPreference.accepts(this, remoteMessage.data["eventCode"])) return
        if (notification != null) {
            val title = notification.title ?: "新通知"
            val body = notification.body ?: ""
            
            // Convert data payload to a JSON string for MainActivity to parse
            val dataMap = remoteMessage.data
            val extrasJson = if (dataMap.isNotEmpty()) {
                org.json.JSONObject(dataMap as Map<*, *>).toString()
            } else {
                "{}"
            }

            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("jpush_extras", extrasJson) // Reuse the same intent extra logic as JPush
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val channelId = "transfer_alerts"
            val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = androidx.core.app.NotificationManagerCompat.from(this)
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }
}
