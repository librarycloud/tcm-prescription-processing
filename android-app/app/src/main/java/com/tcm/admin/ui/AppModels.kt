package com.tcm.admin

import org.json.JSONObject

internal data class PackageItem(
    val name: String,
    val customer: String,
    val code: String,
    val status: String,
    val time: String,
    val id: Int = 0,
    val phone: String = "-",
    val store: String = "",
    val method: String = "",
    val info: String = "",
    val statusCode: Int = 0,
    val methodCode: Int = 0,
)

internal fun packageItem(value: JSONObject): PackageItem {
    val statusCode = value.optInt("status", 0)
    val store = value.optJSONObject("store")?.optString("name", "") ?: ""
    val methodCode = value.optInt("pickupMethod", 0)
    val method = when (methodCode) { 0 -> "自提"; 1 -> "跑腿"; 2 -> "快递"; else -> "未设置" }
    val status = when (statusCode) { 0 -> "待领取"; 1 -> "已领取"; else -> "已关闭" }
    val timestamp = value.optString(if (statusCode == 1) "pickedAt" else "createdAt", "").ifBlank { value.optString("createdAt", "-") }
    return PackageItem(value.optString("itemName", "包裹"), value.optString("receiverName", "客户"), value.optString("pickupCode", "-"), status, timestamp.replace("T", " ").take(16), value.optInt("id", 0), value.optString("receiverPhone", "-"), store, method, value.optString("itemInfo", ""), statusCode, methodCode)
}
