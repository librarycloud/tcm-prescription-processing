package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.KeyboardOptions
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
private fun e6PrescriptionStatusLabel(value: Int): String = when (value) {
    0 -> "进行中"
    1 -> "已完成"
    2 -> "已取消"
    else -> "-"
}
private fun e6CanConfirm(item: JSONObject): Boolean {
    val status = item.optInt("status", -1)
    val noPrescription = item.isNull("prescriptionId")
    val noActivePlan = item.isNull("processingPlanId") || item.optJSONObject("processingPlan")?.isNull("deletedAt") == false
    return (status in setOf(0, 1, 2) && noPrescription) || (status in setOf(3, 6) && noActivePlan)
}
private fun e6CanReview(item: JSONObject): Boolean = item.optInt("status", -1) in setOf(0, 1, 2) && item.isNull("prescriptionId")
private fun e6Money(value: Any?): String {
    if (value == null || value == JSONObject.NULL || value.toString().equals("null", ignoreCase = true)) return "-"
    return runCatching { "%.2f".format(java.util.Locale.US, value.toString().toDouble()) }.getOrDefault("-")
}
private fun e6Date(value: String): String = value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.take(16)?.replace("T", " ") ?: "-"
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
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var rejectTarget by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(reload, keyword, orderDate) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.e6Imports(keyword = keyword, orderDate = orderDate)
            }
        }.onSuccess { data ->
            val list = data.optJSONArray("list") ?: JSONArray()
            items = (0 until list.length()).map { list.getJSONObject(it) }
            selectedIds = emptySet()
        }.onFailure { error = it.message ?: "加载E6导入记录失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("E6诊所处方导入", "核对E6订单，确认后生成处方与加工计划")
        Spacer(Modifier.height(12.dp))
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索订单号、顾客、电话或医师编码",
            onSearch = { reload++ },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedButton(
                label = if (orderDate == LocalDate.now().toString()) "今日订单" else "今日",
                selected = orderDate == LocalDate.now().toString(),
                onClick = { orderDate = LocalDate.now().toString() },
            )
            SegmentedButton(
                label = "全部日期",
                selected = orderDate.isBlank(),
                onClick = { orderDate = "" },
            )
            OutlinedTextField(
                value = orderDate,
                onValueChange = { orderDate = it },
                modifier = Modifier.weight(1f).height(SearchControlHeight),
                singleLine = true,
                placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp) },
                shape = FieldShape,
            )
            OutlinedButton(
                onClick = { reload++ },
                modifier = Modifier.height(SearchControlHeight),
                shape = FieldShape,
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新", fontSize = 12.sp)
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
                        onDetail = { onNavigate(ScreenTarget.E6ImportDetail(item.optInt("id"))) }, onConfirm = { onNavigate(ScreenTarget.E6ImportConfirm(item)) },
                        onRevalidate = { runAction({ ApiClient.revalidateE6Import(item.optInt("id")) }, "已完成重新校验") },
                        onReject = { rejectTarget = item },
                    )
                    Spacer(Modifier.height(9.dp))
                }
                if (selectedIds.size >= 2) {
                    val mergeItems = currentItems.filter { selectedIds.contains(it.optInt("id")) && e6CanReview(it) }
                    Button(
                        enabled = mergeItems.size >= 2 && !actionLoading,
                        onClick = { onNavigate(ScreenTarget.E6ImportConfirm(mergeItems.first(), mergeItems.map { it.optInt("id") })) }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("合并选中订单并生成处方 (${mergeItems.size})", fontWeight = FontWeight.SemiBold) }
                }
            }
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
    val isPaid = item.optInt("isPaid", 0) == 1
    val paidText = if (isPaid) "已付款" else "未付款"
    val orderNo = item.displayField("externalOrderNo", "-")
    val phone = maskPhone(item.optString("phone"))

    AppCard(onClick = onDetail) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Checkbox(checked = selected, enabled = selectable, onCheckedChange = onSelect)
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.displayField("customerName", "未填写顾客"),
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(paidText)
                        StatusPill(e6StatusLabel(item.optInt("status", -1)))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$phone  ·  单号：$orderNo",
                    color = RegularText,
                    fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${e6Date(item.optString("sourceCreatedAt"))}  ·  ${item.optInt("doseCount", 0)}剂  ·  ¥${e6Money(item.opt("totalPrice"))}",
                    color = Muted,
                    fontSize = 12.sp,
                )
                item.optJSONObject("doctorMapping")?.optJSONObject("doctor")?.displayField("name")?.let { mapped ->
                    Text("系统医生：$mapped", color = Muted, fontSize = 12.sp)
                }
                if (item.optString("errorMessage").isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(item.optString("errorMessage"), color = Danger, fontSize = 12.sp, maxLines = 2)
                }
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

private data class E6BatchDraft(val key: Int, val dose: String, val date: String)

private fun e6DraftBatches(totalDose: Int, count: Int): List<E6BatchDraft> {
    val source = e6Batches(totalDose.coerceAtLeast(1), count.coerceIn(1, totalDose.coerceAtLeast(1)))
    return (0 until source.length()).map { index ->
        val batch = source.getJSONObject(index)
        E6BatchDraft(index, batch.optInt("totalDose").toString(), batch.optString("processDate"))
    }
}

@Composable
internal fun E6ImportDetailScreen(
    id: Int,
    onConfirm: (JSONObject) -> Unit,
    onPrescription: (Int) -> Unit,
) {
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { ApiClient.e6ImportDetail(id) } }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "加载E6订单详情失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("订单详情", "核对原始订单、处方和加工计划")
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        when {
            loading -> LoadingState("正在加载订单详情")
            detail == null -> EmptyState("暂无订单详情")
            else -> {
                val value = detail!!
                AppCard {
                    DetailLine("E6订单号", value.displayField("externalOrderNo"))
                    DetailLine("订单时间", e6Date(value.optString("sourceCreatedAt")))
                    DetailLine("顾客", value.displayField("customerName"))
                    DetailLine("手机号", maskPhone(value.optString("phone")))
                    DetailLine("操作员", value.displayField("cashierName"))
                    DetailLine("医师编码", value.displayField("e6DoctorCode"))
                    DetailLine("系统医生", value.optJSONObject("prescription")?.optJSONObject("doctor")?.displayField("name") ?: value.optJSONObject("doctorMapping")?.optJSONObject("doctor")?.displayField("name") ?: "-")
                    DetailLine("剂数", "${value.optInt("doseCount", 0)}剂")
                    DetailLine("付款", if (value.optInt("isPaid") == 1) "已付款" else "未付款")
                    DetailLine("总价", "¥${e6Money(value.opt("totalPrice"))}")
                    DetailLine("备注", value.displayField("remark"))
                    value.optString("errorMessage").takeIf { it.isNotBlank() }?.let { DetailLine("错误信息", it, Danger) }
                }
                Spacer(Modifier.height(10.dp))
                value.optJSONObject("prescription")?.let { prescription ->
                    AppCard {
                        Text("对应处方", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        DetailLine("处方号", prescription.displayField("prescriptionNo"))
                        DetailLine("状态", e6PrescriptionStatusLabel(prescription.optInt("status", 0)))
                        if (prescription.optInt("id") > 0) Button(onClick = { onPrescription(prescription.optInt("id")) }, shape = FieldShape, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("查看对应处方") }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                value.optJSONObject("processingPlan")?.let { plan ->
                    AppCard {
                        Text("加工计划", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        DetailLine("计划状态", planStatus(plan.optInt("status", 0)))
                        DetailLine("加工剂数", "${plan.optInt("totalDose", 0)}剂")
                        DetailLine("加工备注", plan.displayField("processRemark"))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                value.optJSONObject("rawPayload")?.optJSONArray("items")?.let { items ->
                    AppCard {
                        Text("E6处方明细（${items.length()}项）", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        (0 until items.length()).take(50).forEach { index ->
                            val item = items.optJSONObject(index) ?: return@forEach
                            DetailLine("${index + 1}", "${item.displayField("name", "药材")}  ${quantityText(item.opt("quantity"))}${item.displayField("unit", "")}")
                        }
                    }
                }
                if (e6CanConfirm(value)) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onConfirm(value) }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = FieldShape, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text(if (value.isNull("prescriptionId")) "确认导入并生成加工计划" else "重新生成加工计划", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun E6ImportConfirmScreen(
    initial: JSONObject,
    mergeIds: List<Int>,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hasPrescription = !initial.isNull("prescriptionId")
    var customer by remember(initial) { mutableStateOf(initial.optString("customerName")) }
    var phone by remember(initial) { mutableStateOf(initial.optString("phone")) }
    var dose by remember(initial) { mutableStateOf(initial.optInt("doseCount", 1).toString()) }
    var batchCount by remember(initial) { mutableStateOf("1") }
    var autoAllocationEnabled by remember(initial) { mutableStateOf(true) }
    var bagsPerDose by remember(initial) { mutableStateOf("2") }
    var volumeMl by remember(initial) { mutableStateOf("200") }
    var doctorId by remember(initial) { mutableStateOf(initial.optJSONObject("doctorMapping")?.optJSONObject("doctor")?.optInt("id") ?: initial.optJSONObject("prescription")?.optJSONObject("doctor")?.optInt("id") ?: 0) }
    var processTypeId by remember { mutableStateOf(0) }
    var pickupMethod by remember { mutableStateOf(0) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var processTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var batches by remember(initial) { mutableStateOf(e6DraftBatches(initial.optInt("doseCount", 1), 1)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.doctors(), ApiClient.processTypes()) } }
            .onSuccess { (doctorData, typeData) ->
                doctors = (0 until doctorData.length()).map { doctorData.getJSONObject(it) }
                processTypes = (0 until typeData.length()).map { typeData.getJSONObject(it) }
                if (processTypeId == 0) processTypeId = processTypes.firstOrNull()?.optInt("id") ?: 0
            }
            .onFailure { error = it.message ?: "加载基础数据失败" }
    }
    LaunchedEffect(dose, batchCount, autoAllocationEnabled) {
        val total = dose.toIntOrNull()
        val count = batchCount.toIntOrNull()
        if (autoAllocationEnabled && total != null && total > 0 && count != null && count in 1..total) {
            batches = e6DraftBatches(total, count)
            autoAllocationEnabled = false
        }
    }

    val selectedType = processTypes.firstOrNull { it.optInt("id") == processTypeId }
    val isDecoction = selectedType?.optString("code") == "DECOCTION" || selectedType?.optString("name") == "代煎"
    val totalDose = dose.toIntOrNull() ?: 0
    val allocatedDose = batches.sumOf { it.dose.toIntOrNull() ?: 0 }
    val validBatches = totalDose > 0 && allocatedDose == totalDose && batches.isNotEmpty() && batches.all { it.dose.toIntOrNull()?.let { value -> value > 0 } == true && it.date.isNotBlank() }
    val canSubmit = !loading && (hasPrescription || doctorId > 0) && processTypeId > 0 && validBatches && (!isDecoction || (bagsPerDose.toIntOrNull()?.let { it > 0 } == true && volumeMl.toIntOrNull()?.let { it > 0 } == true))

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(if (mergeIds.size > 1) "合并订单并生成处方" else "确认导入并生成加工计划", if (mergeIds.size > 1) "已选择 ${mergeIds.size} 个E6订单" else "核对信息后提交，生成处方和加工计划")
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        AppCard {
            OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("顾客姓名") }, singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(10.dp)); OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = FieldShape)
            Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dose, { dose = it.filter(Char::isDigit); autoAllocationEnabled = true }, Modifier.weight(1f), label = { Text("总剂数 *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
                OutlinedTextField(batchCount, { batchCount = it.filter(Char::isDigit); autoAllocationEnabled = true }, Modifier.weight(1f), label = { Text("批次数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
            }
            Spacer(Modifier.height(14.dp)); Text("系统医生${if (hasPrescription) "" else " *"}", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { doctors.forEach { doctor -> SegmentedButton(doctor.displayField("name", "医生"), doctorId == doctor.optInt("id"), { doctorId = doctor.optInt("id") }) } }
            Spacer(Modifier.height(14.dp)); Text("加工方式 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { processTypes.forEach { type -> SegmentedButton(type.displayField("name", "加工"), processTypeId == type.optInt("id"), { processTypeId = type.optInt("id") }) } }
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("加工批次", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text("系统已自动分配，可逐批修改剂数和日期", color = Muted, fontSize = 12.sp) }
                TextButton(onClick = { if (totalDose > 0) { batchCount = batches.size.toString(); autoAllocationEnabled = true } }) { Text("重新自动分配") }
            }
            Spacer(Modifier.height(8.dp))
            batches.forEachIndexed { index, batch ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第${index + 1}批", color = Ink, fontSize = 12.sp, modifier = Modifier.width(42.dp))
                    OutlinedTextField(batch.dose, { value -> batches = batches.toMutableList().also { it[index] = batch.copy(dose = value.filter(Char::isDigit)) } }, Modifier.weight(0.8f), label = { Text("剂数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
                    OutlinedTextField(batch.date, { value -> batches = batches.toMutableList().also { it[index] = batch.copy(date = value) } }, Modifier.weight(1.2f), label = { Text("加工日期") }, singleLine = true, shape = FieldShape)
                    if (batches.size > 1) TextButton(onClick = { batches = batches.toMutableList().also { it.removeAt(index) }; batchCount = batches.size.toString() }) { Text("删", color = Danger) }
                }
                if (index < batches.lastIndex) Spacer(Modifier.height(7.dp))
            }
            Spacer(Modifier.height(8.dp)); Text("已分配 $allocatedDose / $totalDose 剂", color = if (allocatedDose == totalDose) Success else Danger, fontSize = 12.sp)
            if (totalDose > 0 && batches.size < totalDose) TextButton(onClick = { batches = batches + E6BatchDraft(batches.size, "1", LocalDate.now().toString()); batchCount = batches.size.toString() }) { Text("新增批次") }
        }
        if (isDecoction) {
            Spacer(Modifier.height(12.dp)); AppCard {
                Text("代煎参数", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(bagsPerDose, { bagsPerDose = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("每剂袋数 *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
                    OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("每袋毫升 *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
                }
            }
        }
        Spacer(Modifier.height(12.dp)); AppCard {
            Text("取货方式 *", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("自提", "跑腿", "快递").forEachIndexed { index, label -> SegmentedButton(label, pickupMethod == index, { pickupMethod = index }) } }
        }
        Spacer(Modifier.height(16.dp))
        LoadingButton(
            enabled = canSubmit,
            loading = loading,
            text = if (mergeIds.size > 1) "确认合并并生成" else "确认导入并生成加工计划",
            loadingText = "正在生成...",
            onClick = {
                loading = true
                error = null
                val batchPayloads = JSONArray()
                batches.forEach { batch ->
                    val batchDose = batch.dose.toIntOrNull() ?: 0
                    val batchPayload = JSONObject()
                        .put("totalDose", batchDose)
                        .put("scheduleType", 1)
                        .put("processDate", batch.date.trim())
                    if (isDecoction) {
                        batchPayload
                            .put("bagCount", batchDose * (bagsPerDose.toIntOrNull() ?: 2))
                            .put("volumeMl", volumeMl.toIntOrNull() ?: 200)
                    }
                    batchPayloads.put(batchPayload)
                }
                val payload = JSONObject()
                    .put("customerName", customer.trim())
                    .put("phone", phone.trim())
                    .put("doseCount", totalDose)
                    .put("processTypeId", processTypeId)
                    .put("pickupMethod", pickupMethod)
                    .put("batches", batchPayloads)
                if (isDecoction) {
                    payload
                        .put("bagsPerDose", bagsPerDose.toIntOrNull() ?: 0)
                        .put("volumeMl", volumeMl.toIntOrNull() ?: 0)
                }
                if (doctorId > 0) payload.put("doctorId", doctorId)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (mergeIds.size > 1) {
                                ApiClient.mergeE6Imports(payload.put("ids", JSONArray().also { ids -> mergeIds.forEach { ids.put(it) } }))
                            } else {
                                ApiClient.confirmE6Import(initial.optInt("id"), payload)
                            }
                        }
                    }.onSuccess { onDone() }
                    .onFailure { error = it.message ?: "生成处方和加工计划失败" }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color = RegularText) {
    val display = value.trim().takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) } ?: "-"
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(display, color = color, fontSize = 13.sp)
    }
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
