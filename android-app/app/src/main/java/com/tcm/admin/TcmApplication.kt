package com.tcm.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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
        // 极光推送初始化 — AppKey 在 AndroidManifest meta-data 中配置
        JPushInterface.setDebugMode(BuildConfig.DEBUG)
        JPushInterface.init(this)
    }
}
