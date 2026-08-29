package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private data class E6Status(val value: Int?, val label: String)

private val e6Statuses = listOf(
    E6Status(null, "全部"), E6Status(0, "待确认"), E6Status(1, "待映射"),
    E6Status(2, "导入异常"), E6Status(3, "已生成处方"), E6Status(4, "已驳回"),
    E6Status(5, "已取消"), E6Status(6, "数据冲突"), E6Status(7, "处理中"),
)

private fun e6StatusLabel(value: Int): String = e6Statuses.firstOrNull { it.value == value }?.label ?: "未知状态"
private fun e6CanConfirm(item: JSONObject): Boolean {
    val status = item.optInt("status", -1)
    val noPrescription = item.isNull("prescriptionId")
    val noActivePlan = item.isNull("processingPlanId") || item.optJSONObject("processingPlan")?.isNull("deletedAt") == false
    return (status in setOf(0, 1, 2) && noPrescription) || (status in setOf(3, 6) && noActivePlan)
}
private fun e6CanReview(item: JSONObject): Boolean = item.optInt("status", -1) in setOf(0, 1, 2) && item.isNull("prescriptionId")
private fun e6Money(value: Any?): String = runCatching { "%.2f".format(java.util.Locale.US, value.toString().toDouble()) }.getOrDefault("0.00")
private fun e6Date(value: String): String = value.take(16).replace("T", " ")
private fun e6Batches(totalDose: Int, count: Int): JSONArray {
    val result = JSONArray()
    val safeCount = count.coerceIn(1, totalDose)
    val base = totalDose / safeCount
    val remainder = totalDose % safeCount
    var processDate = LocalDate.now()
    repeat(safeCount) { index ->
        val batchDose = base + if (index < remainder) 1 else 0
        result.put(JSONObject().put("totalDose", batchDose).put("scheduleType", 1).put("processDate", processDate.toString()))
        processDate = processDate.plusDays(batchDose.toLong())
    }
    return result
}

@Composable
internal fun E6ImportsScreen(user: JSONObject?, onNavigate: (ScreenTarget) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var orderDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var cashierName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var selectedStoreId by remember { mutableStateOf("") }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var confirmTarget by remember { mutableStateOf<JSONObject?>(null) }
    var mergeTarget by remember { mutableStateOf<List<JSONObject>?>(null) }
    var rejectTarget by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val isSuperAdmin = user?.optInt("role", -1) == 0

    fun openDetail(item: JSONObject) {
        val id = item.optInt("id")
        if (id <= 0) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ApiClient.e6ImportDetail(id) } }
                .onSuccess { detail = it }
                .onFailure { detail = item; error = it.message ?: "加载详情失败" }
        }
    }

    fun runAction(action: suspend () -> Unit, success: String) {
        if (actionLoading) return
        actionLoading = true
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { notice = success; selectedIds = emptySet(); reload++ }
                .onFailure { error = it.message ?: "操作失败" }
            actionLoading = false
        }
    }

    LaunchedEffect(isSuperAdmin) {
        if (isSuperAdmin) runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values -> stores = (0 until values.length()).map { values.getJSONObject(it) } }
    }
    LaunchedEffect(reload, keyword, orderDate, cashierName, status, selectedStoreId) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.e6Imports(keyword, orderDate, status, cashierName, selectedStoreId.toIntOrNull())
            }
        }.onSuccess { data ->
            val list = data.optJSONArray("list") ?: JSONArray()
            items = (0 until list.length()).map { list.getJSONObject(it) }
            selectedIds = emptySet()
        }.onFailure { error = it.message ?: "加载E6导入记录失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("E6诊所处方导入", "核对E6订单，确认后生成处方与加工计划")
        Spacer(Modifier.height(12.dp))
        Surface(
            color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)), modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("订单号、顾客、电话或医师编码") }, shape = FieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(cashierName, { cashierName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("操作员（可选）") }, shape = FieldShape)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(orderDate, { orderDate = it }, Modifier.weight(1f), singleLine = true, label = { Text("订单日期") }, shape = FieldShape)
                    TextButton(onClick = { orderDate = "" }) { Text("全部日期") }
                    OutlinedButton(onClick = { reload++ }, modifier = Modifier.height(SearchControlHeight), shape = FieldShape) {
                        Icon(Icons.Default.Refresh, null, Modifier.width(17.dp)); Spacer(Modifier.width(4.dp)); Text("刷新")
                    }
                }
                if (isSuperAdmin && stores.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SegmentedButton("全部门店", selectedStoreId.isBlank(), { selectedStoreId = "" })
                        stores.forEach { store ->
                            val id = store.optInt("id").toString()
                            SegmentedButton(store.displayField("name", "门店"), selectedStoreId == id, { selectedStoreId = id })
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    e6Statuses.forEach { item -> SegmentedButton(item.label, status == item.value, { status = item.value }) }
                }
            }
        }
        notice?.let { Text(it, color = Success, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp)) }
        error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp)) }
        Spacer(Modifier.height(12.dp))

        val currentItems = items
        when {
            loading && currentItems == null -> LoadingState("正在加载E6导入记录")
            currentItems.isNullOrEmpty() -> EmptyState("暂无符合条件的E6处方导入记录")
            else -> {
                Text("共 ${currentItems!!.size} 条记录", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                currentItems.forEach { item ->
                    E6ImportCard(
                        item = item, selected = selectedIds.contains(item.optInt("id")), selectable = e6CanReview(item),
                        onSelect = { checked -> selectedIds = if (checked) selectedIds + item.optInt("id") else selectedIds - item.optInt("id") },
                        onDetail = { openDetail(item) }, onConfirm = { confirmTarget = item },
                        onRevalidate = { runAction({ ApiClient.revalidateE6Import(item.optInt("id")) }, "已完成重新校验") },
                        onReject = { rejectTarget = item },
                    )
                    Spacer(Modifier.height(9.dp))
                }
                if (selectedIds.size >= 2) {
                    val mergeItems = currentItems.filter { selectedIds.contains(it.optInt("id")) && e6CanReview(it) }
                    Button(
                        enabled = mergeItems.size >= 2 && !actionLoading,
                        onClick = { mergeTarget = mergeItems }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("合并选中订单并生成处方 (${mergeItems.size})", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    detail?.let { value -> E6ImportDetailDialog(value, onDismiss = { detail = null }, onPrescription = { id -> detail = null; onNavigate(ScreenTarget.PrescriptionDetail(id)) }) }
    confirmTarget?.let { value ->
        E6ConfirmDialog(value, loading = actionLoading, onDismiss = { confirmTarget = null }) { payload ->
            confirmTarget = null
            runAction({ ApiClient.confirmE6Import(value.optInt("id"), payload) }, "已生成处方并进入加工工作台")
        }
    }
    mergeTarget?.let { values ->
        E6ConfirmDialog(values.first(), title = "合并订单并生成处方", loading = actionLoading, onDismiss = { mergeTarget = null }) { payload ->
            mergeTarget = null
            payload.put("ids", JSONArray().also { ids -> values.forEach { ids.put(it.optInt("id")) } })
            runAction({ ApiClient.mergeE6Imports(payload) }, "已合并订单并生成处方")
        }
    }
    rejectTarget?.let { value ->
        RejectE6Dialog(loading = actionLoading, onDismiss = { rejectTarget = null }) { reason ->
            rejectTarget = null
            runAction({ ApiClient.rejectE6Import(value.optInt("id"), reason) }, "已驳回E6订单")
        }
    }
}

@Composable
private fun E6ImportCard(item: JSONObject, selected: Boolean, selectable: Boolean, onSelect: (Boolean) -> Unit, onDetail: () -> Unit, onConfirm: () -> Unit, onRevalidate: () -> Unit, onReject: () -> Unit) {
    AppCard(onClick = onDetail) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Checkbox(checked = selected, enabled = selectable, onCheckedChange = onSelect)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.displayField("externalOrderNo", "E6订单"), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp)); StatusPill(e6StatusLabel(item.optInt("status", -1)))
                }
                Spacer(Modifier.height(5.dp))
                Text("${item.displayField("customerName", "未填写顾客")} · ${maskPhone(item.optString("phone"))}", color = RegularText, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Text("${e6Date(item.optString("sourceCreatedAt"))}  ·  ${item.optInt("doseCount", 0)}剂  ·  ¥${e6Money(item.opt("totalPrice"))}", color = Muted, fontSize = 12.sp)
                item.optJSONObject("doctorMapping")?.optJSONObject("doctor")?.displayField("name")?.let { mapped -> Text("系统医生：$mapped", color = Muted, fontSize = 12.sp) }
                if (item.optString("errorMessage").isNotBlank()) Text(item.optString("errorMessage"), color = Danger, fontSize = 12.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (e6CanReview(item)) {
                TextButton(onClick = onRevalidate) { Icon(Icons.Default.Sync, null, Modifier.width(16.dp)); Spacer(Modifier.width(4.dp)); Text("重校验") }
                TextButton(onClick = onReject) { Icon(Icons.Default.Close, null, Modifier.width(16.dp)); Spacer(Modifier.width(4.dp)); Text("驳回", color = Danger) }
            }
            if (e6CanConfirm(item)) {
                Button(onClick = onConfirm, shape = FieldShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Icon(Icons.Default.CheckCircle, null, Modifier.width(16.dp)); Spacer(Modifier.width(4.dp)); Text("确认导入") }
            }
        }
    }
}

@Composable
private fun E6ImportDetailDialog(value: JSONObject, onDismiss: () -> Unit, onPrescription: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("E6订单详情") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DetailLine("E6订单号", value.displayField("externalOrderNo")); DetailLine("顾客", value.displayField("customerName")); DetailLine("手机号", maskPhone(value.optString("phone")))
            DetailLine("订单时间", e6Date(value.optString("sourceCreatedAt"))); DetailLine("医师编码", value.displayField("e6DoctorCode")); DetailLine("剂数", "${value.optInt("doseCount", 0)}剂")
            DetailLine("付款", if (value.optInt("isPaid") == 1) "已付款" else "未付款"); DetailLine("总价", "¥${e6Money(value.opt("totalPrice"))}")
            value.optString("errorMessage").takeIf { it.isNotBlank() }?.let { DetailLine("错误信息", it, Danger) }
            value.optJSONObject("prescription")?.let { prescription ->
                Spacer(Modifier.height(8.dp)); Text("已关联处方", color = Primary, fontWeight = FontWeight.SemiBold)
                DetailLine("处方号", prescription.displayField("prescriptionNo")); DetailLine("系统医生", prescription.optJSONObject("doctor")?.displayField("name") ?: "未映射")
                if (prescription.optInt("id") > 0) TextButton(onClick = { onPrescription(prescription.optInt("id")) }) { Text("查看对应处方") }
            }
            value.optJSONObject("processingPlan")?.let { plan ->
                Spacer(Modifier.height(8.dp)); Text("加工计划", color = Primary, fontWeight = FontWeight.SemiBold)
                DetailLine("计划状态", planStatus(plan.optInt("status", 0))); DetailLine("加工剂数", "${plan.optInt("totalDose", 0)}剂")
            }
            value.optJSONObject("rawPayload")?.optJSONArray("items")?.let { items ->
                Spacer(Modifier.height(8.dp)); Text("E6处方明细（${items.length()}项）", color = Primary, fontWeight = FontWeight.SemiBold)
                (0 until items.length()).take(30).forEach { index ->
                    val item = items.optJSONObject(index) ?: return@forEach
                    DetailLine("${index + 1}", "${item.displayField("name", "药材")}  ${quantityText(item.opt("quantity"))}${item.displayField("unit", "")}")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun DetailLine(label: String, value: String, color: Color = RegularText) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(76.dp)); Text(value.ifBlank { "-" }, color = color, fontSize = 13.sp) } }

@Composable
private fun E6ConfirmDialog(value: JSONObject, title: String = "确认导入并生成加工计划", loading: Boolean, onDismiss: () -> Unit, onSubmit: (JSONObject) -> Unit) {
    var customer by remember(value) { mutableStateOf(value.optString("customerName")) }
    var phone by remember(value) { mutableStateOf(value.optString("phone")) }
    var dose by remember(value) { mutableStateOf(value.optInt("doseCount", 1).toString()) }
    var batchCount by remember(value) { mutableStateOf("1") }
    var bagsPerDose by remember(value) { mutableStateOf("2") }
    var volumeMl by remember(value) { mutableStateOf("200") }
    var doctorId by remember(value) { mutableStateOf(value.optJSONObject("doctorMapping")?.optJSONObject("doctor")?.optInt("id") ?: value.optJSONObject("prescription")?.optJSONObject("doctor")?.optInt("id") ?: 0) }
    var processTypeId by remember { mutableStateOf(0) }
    var pickupMethod by remember { mutableStateOf(0) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var processTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.doctors(), ApiClient.processTypes()) } }.onSuccess { (d, p) -> doctors = (0 until d.length()).map { d.getJSONObject(it) }; processTypes = (0 until p.length()).map { p.getJSONObject(it) }; if (processTypeId == 0) processTypeId = processTypes.firstOrNull()?.optInt("id") ?: 0 }.onFailure { loadError = it.message ?: "基础数据加载失败" } }
    val selectedType = processTypes.firstOrNull { it.optInt("id") == processTypeId }
    val isDecoction = selectedType?.optString("code") == "DECOCTION" || selectedType?.optString("name") == "代煎"
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            loadError?.let { Text(it, color = Danger, fontSize = 12.sp) }
            OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("顾客姓名") }, singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(8.dp)); OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = FieldShape)
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dose, { dose = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("剂数 *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
                OutlinedTextField(batchCount, { batchCount = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("批次数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
            }
            val previewDose = dose.toIntOrNull()
            val previewCount = batchCount.toIntOrNull()
            if (previewDose != null && previewDose > 0 && previewCount != null && previewCount in 1..previewDose) {
                val batches = e6Batches(previewDose, previewCount)
                Text(
                    (0 until batches.length()).joinToString("  ·  ") { index ->
                        val batch = batches.getJSONObject(index)
                        "第${index + 1}批 ${batch.optInt("totalDose")}剂 / ${batch.optString("processDate")}"
                    },
                    color = Info,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(9.dp)); Text("系统医生 *", color = Ink, fontSize = 12.sp); Spacer(Modifier.height(5.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { doctors.forEach { doc -> SegmentedButton(doc.displayField("name", "医生"), doctorId == doc.optInt("id"), { doctorId = doc.optInt("id") }) } }
            Spacer(Modifier.height(9.dp)); Text("加工方式 *", color = Ink, fontSize = 12.sp); Spacer(Modifier.height(5.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { processTypes.forEach { type -> SegmentedButton(type.displayField("name", "加工"), processTypeId == type.optInt("id"), { processTypeId = type.optInt("id") }) } }
            Spacer(Modifier.height(6.dp)); Text("批次数会自动均分剂数，并按每批剂数顺延加工日期", color = Muted, fontSize = 11.sp)
            if (isDecoction) { Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(bagsPerDose, { bagsPerDose = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("每剂袋数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape); OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("每袋毫升") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape) } }
            Spacer(Modifier.height(9.dp)); Text("取货方式 *", color = Ink, fontSize = 12.sp); Spacer(Modifier.height(5.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("自提", "跑腿", "快递").forEachIndexed { index, label -> SegmentedButton(label, pickupMethod == index, { pickupMethod = index }) } }
        }
    }, confirmButton = { Button(enabled = !loading && doctorId > 0 && processTypeId > 0 && dose.toIntOrNull()?.let { it > 0 } == true && batchCount.toIntOrNull()?.let { it > 0 && dose.toIntOrNull()?.let { total -> it <= total } == true } == true && (!isDecoction || (bagsPerDose.toIntOrNull()?.let { it > 0 } == true && volumeMl.toIntOrNull()?.let { it > 0 } == true)), onClick = { val totalDose = dose.toInt(); onSubmit(JSONObject().put("customerName", customer.trim()).put("phone", phone.trim()).put("doctorId", doctorId).put("doseCount", totalDose).put("processTypeId", processTypeId).put("pickupMethod", pickupMethod).put("scheduleType", 1).put("processDate", LocalDate.now().toString()).put("batches", e6Batches(totalDose, batchCount.toInt())).also { if (isDecoction) it.put("bagsPerDose", bagsPerDose.toIntOrNull() ?: 2).put("volumeMl", volumeMl.toIntOrNull() ?: 200) }) }, shape = FieldShape) { if (loading) CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp, color = Color.White) else Text("确认生成") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } })
}

@Composable
private fun RejectE6Dialog(loading: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("驳回E6订单") }, text = { OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("驳回原因 *") }, minLines = 3, shape = FieldShape) }, confirmButton = { Button(enabled = reason.isNotBlank() && !loading, onClick = { onSubmit(reason.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = Danger), shape = FieldShape) { Text("确认驳回") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun LoadingState(label: String) { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Primary); Spacer(Modifier.height(10.dp)); Text(label, color = Muted, fontSize = 13.sp) } }

@Composable
private fun EmptyState(label: String) { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Muted, fontSize = 13.sp) } }
