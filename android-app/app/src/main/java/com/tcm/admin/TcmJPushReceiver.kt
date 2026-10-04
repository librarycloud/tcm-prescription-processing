package com.tcm.admin

import android.content.Context
import android.util.Log
import cn.jpush.android.api.JPushInterface
import cn.jpush.android.api.JPushMessage
import cn.jpush.android.service.JPushMessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives JPush lifecycle events:
 *  - onRegister: called when JPush SDK obtains a Registration ID → upload to backend
 *  - onNotifyMessageArrived: foreground notification arrival
 *
 * Registered in AndroidManifest via <receiver> tag.
 */
class TcmJPushReceiver : JPushMessageReceiver() {

    companion object {
        private const val TAG = "TcmJPush"

        /** Call after login to register the current Registration ID with the backend. */
        fun registerCurrentToken(context: Context) {
            val regId = JPushInterface.getRegistrationID(context)
            if (regId.isNullOrEmpty()) {
                Log.d(TAG, "Registration ID not yet available, polling every 5s for up to 30s")
                // JPush may not have registered yet (first launch after install).
                // Poll until it becomes available, then upload.
                CoroutineScope(Dispatchers.IO).launch {
                    repeat(6) { attempt ->
                        delay(5_000L)
                        val retryRegId = JPushInterface.getRegistrationID(context)
                        if (!retryRegId.isNullOrEmpty()) {
                            Log.d(TAG, "Registration ID available after ${(attempt + 1) * 5}s, uploading...")
                            uploadToken(context, retryRegId)
                            TcmFcmService.unregisterToken(context)
                            return@launch
                        }
                    }
                    @Suppress("DEPRECATION")
                    val isConnected = JPushInterface.getConnectionState(context)
                    Log.w(TAG, "Registration ID still unavailable after 30s. Connected: $isConnected")
                }
                return
            }
            uploadToken(context, regId)
            TcmFcmService.unregisterToken(context)
        }

        private fun uploadToken(context: Context, regId: String) {
            // Cache the token so we can unregister it later even if JPush isn't fully initialized
            context.getSharedPreferences("push_prefs", Context.MODE_PRIVATE).edit().putString("last_jpush_token", regId).apply()

            if (!ApiClient.isAuthenticated) {
                ApiClient.loadSession(context)
            }
            if (!ApiClient.isAuthenticated) {
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerDeviceToken(context, platform = "jpush", token = regId)
                    Log.d(TAG, "JPush Registration ID uploaded: ${regId.take(20)}...")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload JPush Registration ID: ${e.message}")
                }
            }
        }

        /** Call on logout or FCM takeover to remove this device's JPush token from the backend. */
        fun unregisterToken(context: Context) {
            val prefs = context.getSharedPreferences("push_prefs", Context.MODE_PRIVATE)
            val regId = JPushInterface.getRegistrationID(context).takeIf { !it.isNullOrEmpty() } 
                ?: prefs.getString("last_jpush_token", null) 
                ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.unregisterDeviceToken(context, regId)
                    prefs.edit().remove("last_jpush_token").apply()
                    Log.d(TAG, "JPush Token unregistered from backend")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unregister JPush token: ${e.message}")
                }
            }
        }
    }

    override fun onRegister(context: Context, registrationId: String) {
        Log.d(TAG, "onRegister: ${registrationId.take(20)}...")
        uploadToken(context, registrationId)
    }

    override fun onConnected(context: Context, isConnected: Boolean) {
        if (isConnected) {
            Log.d(TAG, "JPush connected")
        }
    }

    override fun onNotifyMessageOpened(context: Context, message: cn.jpush.android.api.NotificationMessage) {
        Log.d(TAG, "Notification clicked. Extras: ${message.notificationExtras}")
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("jpush_extras", message.notificationExtras)
        }
        context.startActivity(intent)
    }
    override fun onNotifyMessageArrived(context: Context, message: cn.jpush.android.api.NotificationMessage) {
        Log.d(TAG, "JPush Message arrived: ${message.notificationTitle}")
        
        // When app is in foreground, manually build notification so sound plays
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("jpush_extras", message.notificationExtras)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "transfer_alerts"
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.notificationTitle ?: "新通知")
            .setContentText(message.notificationContent ?: "")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
