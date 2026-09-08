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
}
