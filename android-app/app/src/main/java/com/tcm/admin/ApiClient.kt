package com.tcm.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool

data class AdminSession(val token: String, val user: JSONObject)

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private const val SESSION_PREFS = "admin_session"
    private const val TOKEN_KEY = "token"
    private const val USER_KEY = "user"
    private var token: String? = null

    fun setToken(value: String?) { token = value }

    fun saveSession(context: Context, session: AdminSession) {
        token = session.token
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TOKEN_KEY, session.token)
            .putString(USER_KEY, session.user.toString())
            .apply()
    }

    fun loadSession(context: Context): AdminSession? {
        val preferences = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val savedToken = preferences.getString(TOKEN_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        val savedUser = preferences.getString(USER_KEY, null) ?: return null
        val user = runCatching { JSONObject(savedUser) }.getOrNull() ?: return null
        token = savedToken
        return AdminSession(savedToken, user)
    }

    fun clearSession(context: Context) {
        token = null
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun login(identifier: String, password: String): AdminSession {
        val data = request("/auth/login", "POST", JSONObject().put("identifier", identifier).put("password", password))
        val result = data.getJSONObject("data")
        return AdminSession(result.getString("token"), result.getJSONObject("user")).also { token = it.token }
    }

    fun updateMe(payload: JSONObject): AdminSession {
        val data = request("/user/me", "PUT", payload).getJSONObject("data")
        val newToken = data.getString("token")
        val newUser = data.getJSONObject("user")
        return AdminSession(newToken, newUser).also { token = it.token }
    }

    fun me(): JSONObject = request("/user/me").getJSONObject("data")

    fun stats(storeId: Int? = null): JSONObject = request("/admin/stats${storeId?.let { "?storeId=$it" } ?: ""}").getJSONObject("data")
    fun prescriptions(status: Int? = null, keyword: String = "", storeId: Int? = null): JSONArray {
        val query = buildList {
            add("page=1"); add("pageSize=100")
            status?.let { add("status=$it") }; storeId?.let { add("storeId=$it") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return list(request("/admin/prescriptions?$query").getJSONObject("data"))
    }
    fun prescriptionDetail(id: Int): JSONObject = request("/admin/prescriptions/$id").getJSONObject("data")
    fun createPrescription(payload: JSONObject): JSONObject = request("/admin/prescriptions", "POST", payload).getJSONObject("data")
    fun updatePrescription(id: Int, payload: JSONObject): JSONObject = request("/admin/prescriptions/$id", "PUT", payload).getJSONObject("data")
    fun deletePrescription(id: Int): JSONObject = request("/admin/prescriptions/$id", "DELETE").getJSONObject("data")
    fun doctors(): JSONArray = arrayData(request("/admin/doctors?page=1&pageSize=100").opt("data"))
    fun dictionaries(type: String): JSONArray = arrayData(request("/admin/dictionaries?type=${java.net.URLEncoder.encode(type, "UTF-8")}").opt("data"))
    fun plans(view: String = "today-all", keyword: String = "", storeId: Int? = null): JSONArray {
        val query = buildList {
            add("view=${java.net.URLEncoder.encode(view, "UTF-8")}")
            add("page=1")
            add("pageSize=100")
            storeId?.let { add("storeId=$it") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return list(request("/admin/processing-plans?$query").getJSONObject("data"))
    }
    fun processingWorkflow(id: Int): JSONObject = request("/admin/processing-plans/$id/workflow").getJSONObject("data")
    // Compatibility helpers for screen modules that use descriptive API names.
    fun processingStats(storeId: Int? = null): JSONObject = stats(storeId)
    fun processingPlansPaged(view: String = "today-all", keyword: String = "", storeId: Int? = null, page: Int = 1, pageSize: Int = 20): JSONObject {
        val values = plans(view, keyword, storeId)
        return JSONObject().put("list", values).put("pagination", JSONObject().put("page", page).put("pages", 1).put("pageSize", pageSize).put("total", values.length()))
    }
    fun pickupTasks(keyword: String = "", storeId: Int? = null): JSONArray = packages(keyword = keyword, storeId = storeId)
    fun createPlan(payload: JSONObject): JSONObject = createProcessingPlan(payload)
    fun updatePlan(id: Int, payload: JSONObject): JSONObject = updateProcessingPlan(id, payload)
    fun cancelPlan(id: Int, reason: String = ""): JSONObject = transitionPlan(id, 5)
    fun generatePlanPackage(id: Int): JSONObject = generatePackage(id)
    fun delayPlan(planId: Int, days: Int): JSONObject = delayPlan(planId, JSONObject().put("days", days))
    fun createProcessingPlan(payload: JSONObject): JSONObject = request("/admin/processing-plans", "POST", payload).getJSONObject("data")
    fun updateProcessingPlan(id: Int, payload: JSONObject): JSONObject = request("/admin/processing-plans/$id", "PUT", payload).getJSONObject("data")
    fun deleteProcessingPlan(id: Int): JSONObject = request("/admin/processing-plans/$id", "DELETE").getJSONObject("data")
    fun completeDispensing(id: Int, filename: String, mimeType: String, bytes: ByteArray): JSONObject = requestMultipart("/admin/processing-plans/$id/dispensing-complete", "file", filename, mimeType, bytes).getJSONObject("data")
    fun packages(status: Int? = null, source: String? = null, dateScope: String? = null, keyword: String = "", storeId: Int? = null, sortBy: String = "createdAt"): JSONArray {
        val query = buildList {
            add("page=1")
            add("pageSize=100")
            add("sortBy=${java.net.URLEncoder.encode(sortBy, "UTF-8")}")
            add("sortOrder=desc")
            status?.let { add("status=$it") }
            storeId?.let { add("storeId=$it") }
            source?.let { add("source=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            dateScope?.let { add("dateScope=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return list(request("/admin/packages?$query").getJSONObject("data"))
    }
   fun packageDetail(id: Int): JSONObject = request("/admin/packages/$id").getJSONObject("data")
    fun packageByCode(code: String): JSONObject = request("/admin/packages/by-code/${java.net.URLEncoder.encode(code, "UTF-8")}").getJSONObject("data")
    fun createPackage(payload: JSONObject): JSONObject = request("/admin/packages", "POST", payload).getJSONObject("data")
    fun updatePackage(id: Int, payload: JSONObject): JSONObject = request("/admin/packages/$id", "PUT", payload).getJSONObject("data")
    fun deletePackage(id: Int): JSONObject = request("/admin/packages/$id", "DELETE").getJSONObject("data")
    fun inventory(keyword: String = "", storeId: Int? = null): JSONArray = list(request(
        "/admin/e6-pharmacy/products?page=1&pageSize=50" +
            (storeId?.let { "&storeId=$it" } ?: "") +
            (keyword.takeIf { it.isNotBlank() }?.let { "&keyword=${java.net.URLEncoder.encode(it.trim(), "UTF-8")}" } ?: "")
    ).getJSONObject("data"))
    fun availableStores(): JSONArray = arrayData(request("/stores?page=1&pageSize=100&status=1").opt("data"))
    fun differences(): JSONObject = request("/admin/product-differences/stats").getJSONObject("data")
    fun differenceSummary(): JSONObject = differences()
    fun differenceProducts(): JSONArray = list(request("/admin/products?onlyDifference=1&page=1&pageSize=30").getJSONObject("data"))
    fun differenceLogs(): JSONArray = list(request("/admin/product-differences/logs?page=1&pageSize=30").getJSONObject("data"))
    fun stocktakings(): JSONArray = list(request("/admin/yd-goods-check?page=1&pageSize=30").getJSONObject("data"))
    fun prescriptionSources(): JSONArray = dictionaries("prescription-source")
    fun herbLocationMatrix(storeId: Int? = null, keyword: String = "", type: String = ""): JSONObject = herbLocations(storeId?.toString())
    fun stocktaking(storeId: Int? = null): JSONArray = stocktakings()
    fun recordCheckItemCount(checkId: Int, itemId: Int, payload: JSONObject): JSONObject = recountGoodsCheckItem(itemId, payload)
    fun updateCheckItemLocation(checkId: Int, itemId: Int, payload: JSONObject): JSONObject = updateGoodsCheckLocation(itemId, payload)
    fun searchGoodsCheckCandidates(checkId: Int, keyword: String = ""): JSONArray = goodsCheckCandidates(checkId, keyword)
    fun transfers(keyword: String = "", status: Int? = null, storeId: Int? = null, overdue: Boolean = false): JSONArray {
        val query = buildList {
            add("page=1"); add("pageSize=100")
            keyword.takeIf { it.isNotBlank() }?.let { add("keyword=${java.net.URLEncoder.encode(it.trim(), "UTF-8")}") }
            status?.let { add("status=$it") }; storeId?.let { add("storeId=$it") }
            if (overdue) add("overdue=1")
        }.joinToString("&")
        return list(request("/admin/store-transfers?$query").getJSONObject("data"))
    }
    fun transferStats(storeId: Int? = null): JSONObject = request("/admin/store-transfers/stats${storeId?.let { "?storeId=$it" } ?: ""}").getJSONObject("data")
    fun herbLocations(storeId: String? = null): JSONObject = request("/admin/herb-locations${storeId?.let { "?storeId=$it" } ?: ""}").getJSONObject("data")
    fun stores(): JSONArray = request("/admin/herb-locations/stores").getJSONArray("data")
    fun assignHerbLocation(payload: JSONObject): JSONObject = request("/admin/herb-locations/assignments", "POST", payload).getJSONObject("data")
    fun updateHerb(id: Int, payload: JSONObject): JSONObject = request("/admin/herb-locations/herbs/$id", "PUT", payload).getJSONObject("data")
    fun moveHerbLocationAssignment(id: Int, payload: JSONObject): JSONObject = request("/admin/herb-locations/assignments/$id", "PUT", payload).getJSONObject("data")
    fun deleteHerbLocationAssignment(id: Int): JSONObject = request("/admin/herb-locations/assignments/$id", "DELETE").getJSONObject("data")
    fun transitionPlan(id: Int, status: Int, createPackage: Boolean = false): JSONObject = request("/admin/processing-plans/$id/transition", "POST", JSONObject().put("status", status).put("createPackage", createPackage)).getJSONObject("data")
    fun generatePackage(id: Int): JSONObject = request("/admin/processing-plans/$id/generate-package", "POST").getJSONObject("data")
    fun verifyPackage(code: String, pickupMethod: Int = 0, expressTrackingNo: String = ""): JSONObject = request("/admin/packages/verify", "POST", JSONObject().put("pickupCode", code).put("pickupMethod", pickupMethod).put("expressTrackingNo", expressTrackingNo)).getJSONObject("data")
    fun createGoodsCheck(name: String, type: Int = 1, storeId: Int? = null): JSONObject = request("/admin/yd-goods-check", "POST", JSONObject().put("checkName", name).put("checkType", type).also { if (storeId != null) it.put("storeId", storeId) }).getJSONObject("data")
    fun goodsCheck(id: Int): JSONObject = request("/admin/yd-goods-check/$id").getJSONObject("data")
    fun goodsCheckCandidates(id: Int, keyword: String = ""): JSONArray = arrayData(request("/admin/yd-goods-check/$id/candidates?page=1&pageSize=100${keyword.takeIf { it.isNotBlank() }?.let { "&keyword=${java.net.URLEncoder.encode(it.trim(), "UTF-8")}" } ?: ""}").opt("data"))
    fun addGoodsCheckItem(checkId: Int, payload: JSONObject): JSONObject = request("/admin/yd-goods-check/$checkId/items", "POST", payload).getJSONObject("data")
    fun recountGoodsCheckItem(itemId: Int, payload: JSONObject): JSONObject = request("/admin/yd-goods-check/items/$itemId/recount", "PUT", payload).getJSONObject("data")
    fun updateGoodsCheckLocation(itemId: Int, payload: JSONObject): JSONObject = request("/admin/yd-goods-check/items/$itemId/location", "PUT", payload).getJSONObject("data")
    fun reviewGoodsCheckItem(itemId: Int, payload: JSONObject = JSONObject()): JSONObject = request("/admin/yd-goods-check/items/$itemId/review", "POST", payload).getJSONObject("data")
    fun finishGoodsCheck(id: Int): JSONObject = request("/admin/yd-goods-check/$id/finish", "POST").getJSONObject("data")
    fun registerDifference(payload: JSONObject): JSONObject = request("/admin/product-differences/register", "POST", payload).getJSONObject("data")
    fun writeOffDifference(payload: JSONObject): JSONObject = request("/admin/product-differences/write-off", "POST", payload).getJSONObject("data")
    fun reverseDifference(logId: Int, reason: String): JSONObject = request("/admin/product-differences/logs/$logId/reverse", "POST", JSONObject().put("reason", reason)).getJSONObject("data")
    fun cancelTransfer(id: Int, reason: String): JSONObject = request("/admin/store-transfers/$id/cancel", "POST", JSONObject().put("reason", reason)).getJSONObject("data")
    fun confirmOutbound(id: Int): JSONObject = request("/admin/store-transfers/$id/confirm-outbound", "POST").getJSONObject("data")
    fun confirmReturn(id: Int, returnId: Int): JSONObject = request("/admin/store-transfers/$id/returns/$returnId/confirm", "POST").getJSONObject("data")
    fun addTransferReturns(id: Int, payload: JSONObject): JSONObject = request("/admin/store-transfers/$id/returns", "POST", payload).getJSONObject("data")
    fun updateTransferReturn(id: Int, returnId: Int, payload: JSONObject): JSONObject = request("/admin/store-transfers/$id/returns/$returnId", "PUT", payload).getJSONObject("data")
    fun transferDetail(id: Int): JSONObject = request("/admin/store-transfers/$id").getJSONObject("data")
    fun transferStores(): JSONArray = request("/admin/store-transfers/stores").getJSONArray("data")
    fun createTransfer(payload: JSONObject): JSONObject = request("/admin/store-transfers", "POST", payload).getJSONObject("data")
    fun updateTransfer(id: Int, payload: JSONObject): JSONObject = request("/admin/store-transfers/$id", "PUT", payload).getJSONObject("data")
    fun updateExpectedReturnDate(id: Int, date: String): JSONObject = request("/admin/store-transfers/$id/expected-return-date", "PUT", JSONObject().put("expectedReturnDate", date)).getJSONObject("data")
    fun startEquipmentUsage(planId: Int, payload: JSONObject): JSONObject = request("/admin/processing-plans/$planId/equipment-usages", "POST", payload).getJSONObject("data")
    fun startPackaging(planId: Int, usageId: Int, payload: JSONObject = JSONObject()): JSONObject = request("/admin/processing-plans/$planId/equipment-usages/$usageId/start-packaging", "POST", payload).getJSONObject("data")
    fun finishEquipmentUsage(planId: Int, usageId: Int): JSONObject = request("/admin/processing-plans/$planId/equipment-usages/$usageId/finish", "POST").getJSONObject("data")
    fun voidEquipmentUsage(planId: Int, usageId: Int, reason: String): JSONObject = request("/admin/processing-plans/$planId/equipment-usages/$usageId/void", "POST", JSONObject().put("reason", reason)).getJSONObject("data")
    fun transferFaultyEquipment(planId: Int, usageId: Int, payload: JSONObject): JSONObject = request("/admin/processing-plans/$planId/equipment-usages/$usageId/fault-transfer", "POST", payload).getJSONObject("data")
    fun delayPlan(planId: Int, payload: JSONObject): JSONObject = request("/admin/processing-plans/$planId/delay", "POST", payload).getJSONObject("data")
    fun receiveNotice(planId: Int, payload: JSONObject = JSONObject()): JSONObject = request("/admin/processing-plans/$planId/receive-notice", "POST", payload).getJSONObject("data")

    private fun list(data: JSONObject): JSONArray = when {
        data.has("list") -> data.optJSONArray("list") ?: JSONArray()
        data.has("items") -> data.optJSONArray("items") ?: JSONArray()
        else -> JSONArray()
    }
    private fun arrayData(value: Any?): JSONArray = when (value) {
        is JSONArray -> value
        is JSONObject -> list(value)
        else -> JSONArray()
    }

    private fun requestMultipart(path: String, fieldName: String, filename: String, mimeType: String, bytes: ByteArray): JSONObject {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                fieldName,
                filename,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()
        
        val requestBuilder = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + path)
            .post(requestBody)
            .header("Accept", "application/json")
            
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }
        
        val response = client.newCall(requestBuilder.build()).execute()
        val responseBodyString = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(responseBodyString) }.getOrElse { JSONObject().put("code", -1).put("message", "服务器响应格式错误") }
        if (response.code == 401) token = null
        if (json.optInt("code", -1) != 0) throw IllegalStateException(json.optString("message", "上传失败"))
        return json
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val requestBuilder = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + path)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }
        
        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaTypeOrNull())
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: "".toRequestBody(null))
            "PUT" -> requestBuilder.put(requestBody ?: "".toRequestBody(null))
            "DELETE" -> requestBuilder.delete(requestBody)
            else -> requestBuilder.method(method, requestBody)
        }
        
        val response = client.newCall(requestBuilder.build()).execute()
        val responseBodyString = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(responseBodyString) }.getOrElse { JSONObject().put("code", -1).put("message", "服务器响应格式错误") }
        if (response.code == 401) token = null
        if (json.optInt("code", -1) != 0) throw IllegalStateException(json.optString("message", "请求失败"))
        return json
    }
}
