package com.tcm.admin

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TcmFcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "TcmFcmService"

        fun registerCurrentToken(context: Context) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed, falling back to JPush", task.exception)
                    TcmJPushReceiver.registerCurrentToken(context)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d(TAG, "FCM Token obtained: ${token.take(20)}...")
                uploadToken(context, token)
                // If FCM is successful, proactively unregister JPush to avoid duplicates
                TcmJPushReceiver.unregisterToken(context)
            }
        }

        fun unregisterToken(context: Context) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiClient.unregisterDeviceToken(context, token)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to unregister FCM token: ${e.message}")
                        }
                    }
                }
            }
        }

        private fun uploadToken(context: Context, token: String) {
            if (!ApiClient.isAuthenticated) {
                ApiClient.loadSession(context)
            }
            if (!ApiClient.isAuthenticated) return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerDeviceToken(context, platform = "android", token = token)
                    Log.d(TAG, "FCM Token uploaded successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload FCM Token: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM onNewToken: ${token.take(20)}...")
        uploadToken(applicationContext, token)
        TcmJPushReceiver.unregisterToken(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        // Handle foreground notifications or background data payloads if needed
    }
}
