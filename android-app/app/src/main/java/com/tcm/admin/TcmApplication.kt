package com.tcm.admin

import android.app.Application
import cn.jpush.android.api.JPushInterface
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TcmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 极光推送初始化 — AppKey 在 AndroidManifest meta-data 中配置
        JPushInterface.setDebugMode(BuildConfig.DEBUG)
        JPushInterface.init(this)
        TcmFirebaseMessagingService.createNotificationChannel(this)
    }
}
