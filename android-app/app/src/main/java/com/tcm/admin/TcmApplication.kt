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
        // 创建通知渠道 (Android 8+)
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

        // 极光推送初始化
        JPushInterface.setDebugMode(true) // 强制开启日志以便排查

        @Suppress("DEPRECATION")
        cn.jiguang.api.utils.JCollectionAuth.setAuth(this, true)

        JPushInterface.init(this)

        // 诊断：启动后 3 秒检查极光初始化状态
        Handler(Looper.getMainLooper()).postDelayed({
            val connected = JPushInterface.getConnectionState(this)
            val regId = JPushInterface.getRegistrationID(this)

            // 读取 Manifest 中实际注入的 JPUSH_APPKEY 值
            var manifestAppKey = "读取失败"
            try {
                val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                manifestAppKey = ai.metaData?.getString("JPUSH_APPKEY") ?: "未找到"
            } catch (e: Exception) {
                manifestAppKey = "异常: ${e.message}"
            }

            val msg = "极光诊断:\n连接=${connected}\nRegID=${if (regId.isNullOrEmpty()) "空" else regId.take(16) + "..."}\nAppKey=${manifestAppKey}\n包名=${packageName}"
            Log.w("TcmJPush", msg)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }, 3000)
    }
}
