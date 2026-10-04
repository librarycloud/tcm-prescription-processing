package com.tcm.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import cn.jpush.android.api.JPushInterface
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TcmApplication : Application() {
    companion object {
        fun initializeJPush(context: Context) {
            JPushInterface.setDebugMode(BuildConfig.DEBUG)
            @Suppress("DEPRECATION")
            cn.jiguang.api.utils.JCollectionAuth.setAuth(context.applicationContext, true)
            JPushInterface.init(context.applicationContext)
        }
    }

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
                enableLights(true)
                val audioAttr = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), audioAttr)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        if (getSharedPreferences("privacy_prefs", MODE_PRIVATE).getBoolean("agreed_privacy", false)) {
            initializeJPush(this)
        }
    }
}
