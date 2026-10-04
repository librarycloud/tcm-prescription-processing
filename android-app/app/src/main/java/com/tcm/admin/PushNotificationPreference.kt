package com.tcm.admin

import android.content.Context
import android.provider.Settings
import java.util.UUID

object PushNotificationPreference {
    private const val DEVICE_ID_KEY = "pushDeviceId"

    fun isEnabled(context: Context): Boolean = context
        .getSharedPreferences("TcmPrefs", Context.MODE_PRIVATE)
        .getBoolean("receiveNotifications", true)

    fun deviceId(context: Context): String {
        val preferences = context.getSharedPreferences("TcmPrefs", Context.MODE_PRIVATE)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = androidId?.takeIf { it.isNotBlank() }?.let { "android:$it" }
            ?: preferences.getString(DEVICE_ID_KEY, null)
            ?: "install:${UUID.randomUUID()}"
        preferences.edit().putString(DEVICE_ID_KEY, deviceId).apply()
        return deviceId
    }
}
