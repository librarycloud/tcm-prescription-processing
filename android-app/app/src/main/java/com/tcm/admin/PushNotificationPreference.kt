package com.tcm.admin

import android.content.Context
import java.util.UUID

object PushNotificationPreference {
    private const val DEVICE_ID_KEY = "pushDeviceId"

    fun isEnabled(context: Context): Boolean = context
        .getSharedPreferences("TcmPrefs", Context.MODE_PRIVATE)
        .getBoolean("receiveNotifications", true)

    fun deviceId(context: Context): String {
        val preferences = context.getSharedPreferences("TcmPrefs", Context.MODE_PRIVATE)
        return preferences.getString(DEVICE_ID_KEY, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(DEVICE_ID_KEY, it).apply()
        }
    }
}
