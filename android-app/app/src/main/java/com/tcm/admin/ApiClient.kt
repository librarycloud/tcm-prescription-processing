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
    fun packages(): JSONArray = list(request("/admin/packages?page=1&pageSize=30").getJSONObject("data"))
    fun inventory(): JSONArray = list(request("/admin/e6-pharmacy/products?page=1&pageSize=30").getJSONObject("data"))
    fun differences(): JSONObject = request("/admin/product-differences/stats").getJSONObject("data")
    fun differenceLogs(): JSONArray = list(request("/admin/product-differences/logs?page=1&pageSize=30").getJSONObject("data"))
    fun stocktakings(): JSONArray = list(request("/admin/yd-goods-check?page=1&pageSize=30").getJSONObject("data"))
    fun transfers(): JSONArray = list(request("/admin/store-transfers?page=1&pageSize=30").getJSONObject("data"))

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
