package com.tcm.admin

import android.content.Context
import android.util.Log
import cn.jpush.android.api.JPushInterface
import cn.jpush.android.api.JPushMessage
import cn.jpush.android.service.JPushMessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                Log.d(TAG, "Registration ID not yet available, will be uploaded on onRegister callback")
                return
            }
            uploadToken(context, regId)
        }

        /** Call on logout to remove this device's JPush token from the backend. */
        fun unregisterToken(context: Context) {
            val regId = JPushInterface.getRegistrationID(context) ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.unregisterDeviceToken(context, regId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unregister JPush token: ${e.message}")
                }
            }
        }

        private fun uploadToken(context: Context, regId: String) {
            if (!ApiClient.isAuthenticated) return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerDeviceToken(context, platform = "jpush", token = regId)
                    Log.d(TAG, "JPush Registration ID uploaded: ${regId.take(20)}...")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload JPush Registration ID: ${e.message}")
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
}
