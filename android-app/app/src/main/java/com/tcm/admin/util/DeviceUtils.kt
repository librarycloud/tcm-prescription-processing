package com.tcm.admin.util

import android.content.Context
import java.util.UUID

object DeviceUtils {
    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * 获取或生成本地匿名设备唯一标识（UUID），保存在 SharedPreferences 中，
     * 用于 App Release Hub 的独立活跃设备 (UV) 统计、版本覆盖率分析及灰度分流。
     */
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        val sp = context.applicationContext.getSharedPreferences("app_meta", Context.MODE_PRIVATE)
        var id = sp.getString("device_id", null)?.trim()
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("device_id", id).apply()
        }
        cachedDeviceId = id
        return id
    }

    /**
     * 获取设备机型，例如 "Xiaomi M2011K2C"
     */
    fun getDeviceModel(): String {
        val manufacturer = android.os.Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Unknown"
        val model = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Unknown"
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} $model"
        }
    }

    /**
     * 获取操作系统版本，例如 "Android 13 (API 33)"
     */
    fun getOsVersion(): String {
        return "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    }
}
