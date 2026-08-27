package com.tcm.admin

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AdminSession(val token: String, val user: JSONObject)

object ApiClient {
    private var token: String? = null

    fun setToken(value: String?) { token = value }

    fun login(identifier: String, password: String): AdminSession {
        val data = request("/auth/login", "POST", JSONObject().put("identifier", identifier).put("password", password))
        val result = data.getJSONObject("data")
        return AdminSession(result.getString("token"), result.getJSONObject("user")).also { token = it.token }
    }

    fun stats(): JSONObject = request("/admin/stats").getJSONObject("data")
    fun plans(view: String = "today-all", keyword: String = ""): JSONArray {
        val query = buildList {
            add("view=${java.net.URLEncoder.encode(view, "UTF-8")}")
            add("page=1")
            add("pageSize=100")
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return list(request("/admin/processing-plans?$query").getJSONObject("data"))
    }
    fun processingWorkflow(id: Int): JSONObject = request("/admin/processing-plans/$id/workflow").getJSONObject("data")
    fun packages(status: Int? = null, source: String? = null, dateScope: String? = null, keyword: String = ""): JSONArray {
        val query = buildList {
            add("page=1")
            add("pageSize=100")
            status?.let { add("status=$it") }
            source?.let { add("source=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            dateScope?.let { add("dateScope=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return list(request("/admin/packages?$query").getJSONObject("data"))
    }
    fun packageDetail(id: Int): JSONObject = request("/admin/packages/$id").getJSONObject("data")
    fun inventory(keyword: String = ""): JSONArray = list(request("/admin/e6-pharmacy/products?page=1&pageSize=50${keyword.takeIf { it.isNotBlank() }?.let { "&keyword=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""}").getJSONObject("data"))
    fun differences(): JSONObject = request("/admin/product-differences/stats").getJSONObject("data")
    fun differenceProducts(): JSONArray = list(request("/admin/products?onlyDifference=1&page=1&pageSize=30").getJSONObject("data"))
    fun differenceLogs(): JSONArray = list(request("/admin/product-differences/logs?page=1&pageSize=30").getJSONObject("data"))
    fun stocktakings(): JSONArray = list(request("/admin/yd-goods-check?page=1&pageSize=30").getJSONObject("data"))
    fun transfers(): JSONArray = list(request("/admin/store-transfers?page=1&pageSize=30").getJSONObject("data"))
    fun herbLocations(storeId: String? = null): JSONObject = request("/admin/herb-locations${storeId?.let { "?storeId=$it" } ?: ""}").getJSONObject("data")
    fun stores(): JSONArray = request("/admin/herb-locations/stores").getJSONArray("data")
    fun transitionPlan(id: Int, status: Int): JSONObject = request("/admin/processing-plans/$id/transition", "POST", JSONObject().put("status", status)).getJSONObject("data")
    fun generatePackage(id: Int): JSONObject = request("/admin/processing-plans/$id/generate-package", "POST").getJSONObject("data")
    fun verifyPackage(code: String): JSONObject = request("/admin/packages/verify", "POST", JSONObject().put("pickupCode", code).put("pickupMethod", 1)).getJSONObject("data")
    fun createGoodsCheck(name: String, type: Int = 1, storeId: Int? = null): JSONObject = request("/admin/yd-goods-check", "POST", JSONObject().put("checkName", name).put("checkType", type).also { if (storeId != null) it.put("storeId", storeId) }).getJSONObject("data")
    fun goodsCheck(id: Int): JSONObject = request("/admin/yd-goods-check/$id").getJSONObject("data")
    fun finishGoodsCheck(id: Int): JSONObject = request("/admin/yd-goods-check/$id/finish", "POST").getJSONObject("data")
    fun registerDifference(payload: JSONObject): JSONObject = request("/admin/product-differences/register", "POST", payload).getJSONObject("data")
    fun writeOffDifference(payload: JSONObject): JSONObject = request("/admin/product-differences/write-off", "POST", payload).getJSONObject("data")
    fun cancelTransfer(id: Int, reason: String): JSONObject = request("/admin/store-transfers/$id/cancel", "POST", JSONObject().put("reason", reason)).getJSONObject("data")
    fun confirmOutbound(id: Int): JSONObject = request("/admin/store-transfers/$id/confirm-outbound", "POST").getJSONObject("data")
    fun confirmReturn(id: Int, returnId: Int): JSONObject = request("/admin/store-transfers/$id/returns/$returnId/confirm", "POST").getJSONObject("data")
    fun transferDetail(id: Int): JSONObject = request("/admin/store-transfers/$id").getJSONObject("data")
    fun transferStores(): JSONArray = request("/admin/store-transfers/stores").getJSONArray("data")
    fun createTransfer(payload: JSONObject): JSONObject = request("/admin/store-transfers", "POST", payload).getJSONObject("data")

    private fun list(data: JSONObject): JSONArray = when {
        data.has("list") -> data.optJSONArray("list") ?: JSONArray()
        data.has("items") -> data.optJSONArray("items") ?: JSONArray()
        else -> JSONArray()
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject().put("code", -1).put("message", "服务器响应格式错误") }
        if (connection.responseCode == 401) token = null
        if (json.optInt("code", -1) != 0) throw IllegalStateException(json.optString("message", "请求失败"))
        return json
    }
}
