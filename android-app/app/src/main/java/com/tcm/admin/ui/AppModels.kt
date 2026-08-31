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
    val expressTrackingNo: String = "",
    val pickupQrContent: String = "",
    val createdAt: String = "-",
    val pickedAt: String = "",
    val creatorName: String = "-",
    val verifierName: String = "",
)

internal fun packageItem(value: JSONObject): PackageItem {
    val statusCode = value.optInt("status", 0)
    val store = value.optJSONObject("store")?.displayField("name", "") ?: ""
    val methodCode = value.optInt("pickupMethod", 0)
    val method = when (methodCode) { 0 -> "自提"; 1 -> "跑腿"; 2 -> "快递"; else -> "未设置" }
    val status = when (statusCode) { 0 -> "待领取"; 1 -> "已领取"; else -> "已关闭" }
    fun operatorName(key: String): String {
        val operator = value.optJSONObject(key) ?: return ""
        return operator.displayField("nickname", "")
            .ifBlank { operator.displayField("name", "") }
            .ifBlank { operator.displayField("phone", "") }
    }
    val createdAt = serverDateTime(value.opt("createdAt"))
    val pickedAt = serverDateTime(value.opt("pickedAt"), "")
    return PackageItem(
        name = value.displayField("itemName", "包裹"),
        customer = value.displayField("receiverName", "客户"),
        code = value.displayField("pickupCode"),
        status = status,
        time = pickedAt.ifBlank { "未领取" },
        id = value.optInt("id", 0),
        phone = value.displayField("receiverPhone"),
        store = store,
        method = method,
        info = value.displayField("itemInfo", ""),
        statusCode = statusCode,
        methodCode = methodCode,
        expressTrackingNo = value.displayField("expressTrackingNo", ""),
        pickupQrContent = value.displayField("pickupQrContent", ""),
        createdAt = createdAt,
        pickedAt = pickedAt,
        creatorName = operatorName("creator").ifBlank { "-" },
        verifierName = operatorName("verifier"),
    )
}
