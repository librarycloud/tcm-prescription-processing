package com.tcm.admin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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

        private fun showToast(context: Context, msg: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        /** Call after login to register the current Registration ID with the backend. */
        fun registerCurrentToken(context: Context) {
            val regId = JPushInterface.getRegistrationID(context)
            if (regId.isNullOrEmpty()) {
                Log.d(TAG, "Registration ID not yet available, polling every 5s for up to 30s")
                showToast(context, "正在获取推送ID(轮询中)...")
                // JPush may not have registered yet (first launch after install).
                // Poll until it becomes available, then upload.
                CoroutineScope(Dispatchers.IO).launch {
                    repeat(6) { attempt ->
                        delay(5_000L)
                        val retryRegId = JPushInterface.getRegistrationID(context)
                        if (!retryRegId.isNullOrEmpty()) {
                            Log.d(TAG, "Registration ID available after ${(attempt + 1) * 5}s, uploading...")
                            uploadToken(context, retryRegId)
                            return@launch
                        }
                    }
                    @Suppress("DEPRECATION")
                    val isConnected = JPushInterface.getConnectionState(context)
                    Log.w(TAG, "Registration ID still unavailable after 30s. Connected: $isConnected")
                    showToast(context, "极光异常: 超时未获取到ID (网络连通状态: $isConnected)。请检查后台包名/AppKey是否匹配")
                }
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
            if (!ApiClient.isAuthenticated) {
                showToast(context, "未登录，跳过上传极光ID")
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerDeviceToken(context, platform = "jpush", token = regId)
                    Log.d(TAG, "JPush Registration ID uploaded: ${regId.take(20)}...")
                    showToast(context, "极光推送注册成功，已绑定此设备！")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload JPush Registration ID: ${e.message}")
                    showToast(context, "上传推送ID失败：${e.message}")
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
