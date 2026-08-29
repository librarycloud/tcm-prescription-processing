package com.tcm.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    fun androidAppVersion(): JSONObject = request("/app/version/android").getJSONObject("data")
    fun prescriptions(status: Int? = null, keyword: String = "", storeId: Int? = null): JSONArray {
        val data = prescriptionsPaged(status = status, keyword = keyword, storeId = storeId, pageSize = 100)
        return data.optJSONArray("list") ?: JSONArray()
    }
    fun prescriptionsPaged(
        status: Int? = null,
        keyword: String = "",
        storeId: Int? = null,
        doctorId: Int? = null,
        page: Int = 1,
        pageSize: Int = 15,
    ): JSONObject {
        val query = buildList {
            add("page=$page"); add("pageSize=$pageSize")
            status?.let { add("status=$it") }; storeId?.let { add("storeId=$it") }
            doctorId?.let { add("doctorId=$it") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return request("/admin/prescriptions?$query").getJSONObject("data")
    }
    fun prescriptionDetail(id: Int): JSONObject = request("/admin/prescriptions/$id").getJSONObject("data")
    fun createPrescription(payload: JSONObject): JSONObject = request("/admin/prescriptions", "POST", payload).getJSONObject("data")
    fun updatePrescription(id: Int, payload: JSONObject): JSONObject = request("/admin/prescriptions/$id", "PUT", payload).getJSONObject("data")
    fun deletePrescription(id: Int): JSONObject = request("/admin/prescriptions/$id", "DELETE").getJSONObject("data")
    fun uploadPrescriptionAttachment(id: Int, filename: String, mimeType: String, bytes: ByteArray): JSONObject =
        requestMultipart("/admin/prescriptions/$id/attachment", "file", filename, mimeType, bytes).getJSONObject("data")
    fun deletePrescriptionAttachment(id: Int): JSONObject = request("/admin/prescriptions/$id/attachment", "DELETE").getJSONObject("data")
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
        val query = buildList {
            add("view=${java.net.URLEncoder.encode(view, "UTF-8")}")
            add("page=$page"); add("pageSize=$pageSize")
            storeId?.let { add("storeId=$it") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return request("/admin/processing-plans?$query").getJSONObject("data")
    }
    fun pickupTasks(keyword: String = "", storeId: Int? = null): JSONArray = packages(keyword = keyword, storeId = storeId)
    fun createPlan(payload: JSONObject): JSONObject = createProcessingPlan(payload)
    fun updatePlan(id: Int, payload: JSONObject): JSONObject = updateProcessingPlan(id, payload)
    fun cancelPlan(id: Int, reason: String = ""): JSONObject = transitionPlan(id, 5)
    fun generatePlanPackage(id: Int, payload: JSONObject = JSONObject()): JSONObject = generatePackage(id, payload)
    fun delayPlan(planId: Int, days: Int): JSONObject = delayPlan(planId, JSONObject().put("days", days))
    fun createProcessingPlan(payload: JSONObject): JSONObject = request("/admin/processing-plans", "POST", payload).getJSONObject("data")
    fun updateProcessingPlan(id: Int, payload: JSONObject): JSONObject = request("/admin/processing-plans/$id", "PUT", payload).getJSONObject("data")
    fun deleteProcessingPlan(id: Int): JSONObject = request("/admin/processing-plans/$id", "DELETE").getJSONObject("data")
    fun completeDispensing(id: Int, filename: String, mimeType: String, bytes: ByteArray): JSONObject = requestMultipart("/admin/processing-plans/$id/dispensing-complete", "file", filename, mimeType, bytes).getJSONObject("data")
    fun processingPhoto(id: Int, photoId: Int): ByteArray = requestBytes("/admin/processing-plans/$id/photos/$photoId")
    fun deleteProcessingPhoto(id: Int, photoId: Int): JSONObject = request("/admin/processing-plans/$id/photos/$photoId", "DELETE").getJSONObject("data")
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
    fun packagesPaged(status: Int? = null, source: String? = null, dateScope: String? = null, keyword: String = "", storeId: Int? = null, sortBy: String = "createdAt", page: Int = 1, pageSize: Int = 15): JSONObject {
        val query = buildList {
            add("page=$page"); add("pageSize=$pageSize")
            add("sortBy=${java.net.URLEncoder.encode(sortBy, "UTF-8")}"); add("sortOrder=desc")
            status?.let { add("status=$it") }; storeId?.let { add("storeId=$it") }
            source?.let { add("source=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            dateScope?.let { add("dateScope=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
        }.joinToString("&")
        return request("/admin/packages?$query").getJSONObject("data")
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
    fun differences(): JSONArray = differenceProducts()
    fun productCatalog(keyword: String = ""): JSONArray = list(request(
        "/admin/products?page=1&pageSize=100" +
            (keyword.takeIf { it.isNotBlank() }?.let { "&keyword=${java.net.URLEncoder.encode(it.trim(), "UTF-8")}" } ?: "")
    ).getJSONObject("data"))
    fun differenceSummary(storeId: Int? = null): JSONObject {
        val summary = request("/admin/product-differences/stats${storeId?.let { "?storeId=$it" } ?: ""}").getJSONObject("data")
        return JSONObject()
            .put("preReceiptQuantity", summary.optInt("more", 0))
            .put("preShipmentQuantity", summary.optInt("less", 0))
            .put("affectedProducts", summary.optInt("total", 0))
            .put("total", summary.optInt("total", 0))
    }
    fun differenceProducts(): JSONArray {
        val values = list(request("/admin/products?onlyDifference=1&page=1&pageSize=30").getJSONObject("data"))
        return JSONArray().also { result ->
            for (index in 0 until values.length()) {
                val product = values.getJSONObject(index)
                val difference = product.optDouble("diffQuantity", 0.0)
                result.put(JSONObject(product.toString())
                    .put("preReceiptQuantity", if (difference > 0) difference else 0.0)
                    .put("preShipmentQuantity", if (difference < 0) -difference else 0.0))
            }
        }
    }
    fun differenceLogs(): JSONArray {
        val values = list(request("/admin/product-differences/logs?page=1&pageSize=30").getJSONObject("data"))
        return JSONArray().also { result ->
            for (index in 0 until values.length()) {
                val log = values.getJSONObject(index)
                result.put(JSONObject(log.toString())
                    .put("quantity", kotlin.math.abs(log.optDouble("changeQuantity", 0.0))))
            }
        }
    }
    fun stocktakings(storeId: Int? = null, page: Int = 1, pageSize: Int = 30): JSONObject {
        val query = buildList { add("page=$page"); add("pageSize=$pageSize"); storeId?.let { add("storeId=$it") } }.joinToString("&")
        return request("/admin/yd-goods-check?$query").getJSONObject("data")
    }
    fun prescriptionSources(): JSONArray = dictionaries("PrescriptionSource")
    fun processTypes(): JSONArray = dictionaries("ProcessType")
    fun e6Imports(
        keyword: String = "",
        orderDate: String = "",
        status: Int? = null,
        cashierName: String = "",
        storeId: Int? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): JSONObject {
        val query = buildList {
            add("page=$page")
            add("pageSize=$pageSize")
            status?.let { add("status=$it") }
            storeId?.let { add("storeId=$it") }
            if (keyword.isNotBlank()) add("keyword=${java.net.URLEncoder.encode(keyword.trim(), "UTF-8")}")
            if (orderDate.isNotBlank()) add("orderDate=${java.net.URLEncoder.encode(orderDate, "UTF-8")}")
            if (cashierName.isNotBlank()) add("cashierName=${java.net.URLEncoder.encode(cashierName.trim(), "UTF-8")}")
        }.joinToString("&")
        return request("/admin/e6/imports?$query").getJSONObject("data")
    }
    fun e6ImportDetail(id: Int): JSONObject = request("/admin/e6/imports/$id").getJSONObject("data")
    fun confirmE6Import(id: Int, payload: JSONObject): JSONObject = request("/admin/e6/imports/$id/confirm", "POST", payload).getJSONObject("data")
    fun mergeE6Imports(payload: JSONObject): JSONObject = request("/admin/e6/imports/merge", "POST", payload).getJSONObject("data")
    fun rejectE6Import(id: Int, reason: String): JSONObject = request("/admin/e6/imports/$id/reject", "POST", JSONObject().put("reason", reason)).getJSONObject("data")
    fun revalidateE6Import(id: Int): JSONObject = request("/admin/e6/imports/$id/revalidate", "POST").getJSONObject("data")
    fun herbLocationMatrix(storeId: Int? = null, keyword: String = "", type: String = ""): JSONObject {
        val root = herbLocations(storeId?.toString())
        val allLocations = root.optJSONArray("locations") ?: JSONArray()
        val units = linkedMapOf<String, JSONObject>()
        var assigned = 0
        val needle = keyword.trim().lowercase()
        for (index in 0 until allLocations.length()) {
            val location = allLocations.getJSONObject(index)
            val locationType = location.optString("type")
            val herbs = location.optJSONArray("herbs") ?: JSONArray()
            val matchesKeyword = needle.isBlank() || location.optString("code").lowercase().contains(needle) ||
                (0 until herbs.length()).any { herbIndex ->
                    herbs.getJSONObject(herbIndex).optString("name").lowercase().contains(needle) ||
                        herbs.getJSONObject(herbIndex).optString("code").lowercase().contains(needle)
                }
            if ((type.isNotBlank() && locationType != type) || !matchesKeyword) continue
            if (herbs.length() > 0) assigned++
            val key = listOf(locationType, location.optInt("unitNo")).joinToString(":")
            val unit = units.getOrPut(key) {
                JSONObject().put("type", locationType).put("unitNo", location.optInt("unitNo")).put("locations", JSONArray())
            }
            unit.getJSONArray("locations").put(location)
        }
        val visibleLocations = units.values.sumOf { it.getJSONArray("locations").length() }
        return JSONObject()
            .put("store", root.optJSONObject("store"))
            .put("herbs", root.optJSONArray("herbs") ?: JSONArray())
            .put("units", JSONArray(units.values.toList()))
            .put("summary", JSONObject()
                .put("totalLocations", visibleLocations)
                .put("assignedLocations", assigned)
                .put("emptyLocations", visibleLocations - assigned)
                .put("totalHerbs", (root.optJSONArray("herbs") ?: JSONArray()).length()))
    }
    fun stocktaking(storeId: Int? = null): JSONObject = stocktakings(storeId)
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
    fun generatePackage(id: Int, payload: JSONObject = JSONObject()): JSONObject = request("/admin/processing-plans/$id/generate-package", "POST", payload).getJSONObject("data")
    fun verifyPackage(code: String, pickupMethod: Int = 0, expressTrackingNo: String = "", pickupQrContent: String? = null): JSONObject = request("/admin/packages/verify", "POST", JSONObject().put("pickupCode", code).put("pickupMethod", pickupMethod).put("expressTrackingNo", expressTrackingNo).also { pickupQrContent?.takeIf { it.isNotBlank() }?.let { value -> it.put("pickupQrContent", value) } }).getJSONObject("data")
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

        // Only send JSON metadata when a JSON payload exists. Fastify rejects an
        // empty request body paired with application/json before reaching the route.
        if (body != null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        token?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaTypeOrNull())

        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            // OkHttp requires a non-null body for POST/PUT. A zero-byte body
            // without a media type preserves the endpoint's bodyless semantics.
            "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> requestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(null))
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

    private fun requestBytes(path: String): ByteArray {
        val requestBuilder = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + path)
            .header("Accept", "image/*")
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val response = client.newCall(requestBuilder.get().build()).execute()
        if (!response.isSuccessful) {
            val message = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(message) }.getOrNull()
            throw IllegalStateException(json?.optString("message")?.takeIf { it.isNotBlank() } ?: "照片加载失败")
        }
        return response.body?.bytes() ?: throw IllegalStateException("照片内容为空")
    }
}
