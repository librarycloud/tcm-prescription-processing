package com.tcm.admin

import android.content.Context
import org.json.JSONObject

object NotificationPreference {
    private const val PREFS = "TcmPrefs"

    fun accepts(context: Context, eventCode: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("receiveNotifications", true)) return false
        val code = eventCode.orEmpty().uppercase()
        if (code.startsWith("TRANSFER_") || code.startsWith("STOCKTAKING") || code.startsWith("GOODS_CHECK")) {
            return prefs.getBoolean("transferNotify", true)
        }
        if (code.startsWith("PACKAGE_") || code == "PROCESSING_COMPLETED" || code.startsWith("E6") || code.startsWith("PRESCRIPTION")) {
            return prefs.getBoolean("prescriptionNotify", true)
        }
        return true
    }

    fun eventCodeFromJson(json: String?): String? = runCatching {
        json?.takeIf { it.isNotBlank() }?.let { JSONObject(it).optString("eventCode").takeIf(String::isNotBlank) }
    }.getOrNull()
}
