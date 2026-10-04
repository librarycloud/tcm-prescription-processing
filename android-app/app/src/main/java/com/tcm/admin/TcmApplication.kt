package com.tcm.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import cn.jpush.android.api.JPushInterface
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TcmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "transfer_alerts",
                "调拨与归还提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "门店调拨申请及归还确认通知"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        JPushInterface.setDebugMode(true)
        @Suppress("DEPRECATION")
        cn.jiguang.api.utils.JCollectionAuth.setAuth(this, true)
        JPushInterface.init(this)

        val handler = Handler(Looper.getMainLooper())
        // 3秒后弹第一条
        handler.postDelayed({
            @Suppress("DEPRECATION")
            val connected = JPushInterface.getConnectionState(this)
            Toast.makeText(this, "极光连接: $connected", Toast.LENGTH_LONG).show()
        }, 3000)
        // 5秒后弹第二条
        handler.postDelayed({
            val regId = JPushInterface.getRegistrationID(this)
            Toast.makeText(this, "RegID: ${if (regId.isNullOrEmpty()) "空" else regId}", Toast.LENGTH_LONG).show()
        }, 5000)
        // 7秒后弹第三条
        handler.postDelayed({
            var key = "读取失败"
            try {
                val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                key = ai.metaData?.getString("JPUSH_APPKEY") ?: "未找到"
            } catch (_: Exception) {}
            Toast.makeText(this, "AppKey: $key", Toast.LENGTH_LONG).show()
        }, 7000)
        // 9秒后弹第四条
        handler.postDelayed({
            Toast.makeText(this, "包名: $packageName", Toast.LENGTH_LONG).show()
        }, 9000)
    }
}
