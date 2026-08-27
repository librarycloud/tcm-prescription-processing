package com.tcm.admin

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
@Composable
internal fun ProcessingScreenV2() {
    var mode by remember { mutableStateOf("plans") }
    var view by remember { mutableStateOf("today-all") }
    var keyword by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf<List<JSONObject>?>(null) }
    var pickupTasks by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailPlan by remember { mutableStateOf<JSONObject?>(null) }
    var workflowPlan by remember { mutableStateOf<JSONObject?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var prescriptions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var processTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var prescriptionId by remember { mutableStateOf(0) }
    var processTypeId by remember { mutableStateOf(0) }
    var totalDose by remember { mutableStateOf("1") }
    var processDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var bagCount by remember { mutableStateOf("1") }
    var volumeMl by remember { mutableStateOf("200") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.prescriptions(status = 0), ApiClient.dictionaries("ProcessType")) } }.onSuccess { (p, types) -> prescriptions = (0 until p.length()).map { p.getJSONObject(it) }; processTypes = (0 until types.length()).map { types.getJSONObject(it) }; prescriptionId = prescriptions.firstOrNull()?.optInt("id", 0) ?: 0; processTypeId = processTypes.firstOrNull()?.optInt("id", 0) ?: 0 }.onFailure { error = it.message ?: "加载加工计划选项失败" } }
    LaunchedEffect(mode, view, keyword, reload) {
        error = null
        if (mode == "plans") {
            runCatching { withContext(Dispatchers.IO) { ApiClient.plans(view, keyword) } }.onSuccess { a -> plans = (0 until a.length()).map { a.getJSONObject(it) } }.onFailure { error = it.message ?: "加载加工计划失败" }
        } else {
            runCatching { withContext(Dispatchers.IO) { ApiClient.packages(source = "processing", dateScope = "pickup-workbench", keyword = keyword) } }.onSuccess { a -> pickupTasks = (0 until a.length()).map { packageItem(a.getJSONObject(it)) } }.onFailure { error = it.message ?: "加载待领取任务失败" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { SegmentedButton("加工计划", mode == "plans") { mode = "plans"; view = "today-all" }; SegmentedButton("待领取", mode == "pickup") { mode = "pickup" }; Spacer(Modifier.weight(1f)); if (mode == "plans") OutlinedButton({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建计划") } }
        Spacer(Modifier.height(12.dp)); OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("姓名、手机号或备注") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (mode == "plans") { Spacer(Modifier.height(10.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("today-all" to "今日全部", "today-waiting" to "待加工", "overdue" to "逾期", "processing" to "加工中", "today-finished" to "今日完成", "tomorrow" to "明日").forEach { (key, label) -> SegmentedButton(label, view == key) { view = key } } } }
        Spacer(Modifier.height(14.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }; if (plans == null && pickupTasks == null && error == null) Text("加载中...", color = Muted)
        if (mode == "plans") {
            val values = plans.orEmpty(); if (plans != null && values.isEmpty()) Text("暂无加工计划", color = Muted)
            values.forEach { plan ->
                val id = plan.optInt("id", 0); val status = plan.optInt("status", 0); val prescription = plan.optJSONObject("prescription"); val customer = prescription?.optString("customerName", "客户") ?: plan.optString("customerName", "客户"); val phone = prescription?.optString("phone", "-") ?: plan.optString("customerPhone", "-"); val processType = plan.optJSONObject("processType")?.optString("name", "加工") ?: "加工"; val store = plan.optJSONObject("store")?.optString("name", "") ?: ""
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("$customer · $processType", fontWeight = FontWeight.SemiBold); Text("$phone${if (store.isNotBlank()) " · $store" else ""}", color = Muted, fontSize = 12.sp) }; StatusPill(planStatus(status)) }; Spacer(Modifier.height(8.dp)); Text("第 ${plan.optInt("batchNo", 1)} 批 · ${plan.optInt("totalDose", 0)} 剂 · ${plan.optString("processDate", "等待通知").take(10)}", color = Muted, fontSize = 13.sp); plan.optString("processRemark", "").takeIf { it.isNotBlank() }?.let { Text("备注：$it", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ detailPlan = plan }, shape = RoundedCornerShape(6.dp)) { Text("详情") }; when { status == 0 -> Button(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(id, 1) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "开始加工失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("开始加工") }; status in 1..4 -> OutlinedButton({ workflowPlan = plan }, shape = RoundedCornerShape(6.dp)) { Text("工序详情") } }; if (status == 2) Button(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.generatePackage(id) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "生成包裹失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("生成包裹") }; if (status == 1) OutlinedButton(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(id, 5) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "取消失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("取消加工") } } } }
            }
        } else {
            val values = pickupTasks.orEmpty(); if (pickupTasks != null && values.isEmpty()) Text("暂无待领取任务", color = Muted)
            values.filter { keyword.isBlank() || it.customer.contains(keyword) || it.code.contains(keyword) }.forEach { item ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.customer} · ${item.name}", fontWeight = FontWeight.SemiBold)
                                Text("${item.phone} · ${item.time}", color = Muted, fontSize = 12.sp)
                            }
                            StatusPill(item.status)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("取货码：${item.code}", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                {
                                    detailPlan = JSONObject().put("packageId", item.id).put("pickupCode", item.code).put("receiverName", item.customer).put("receiverPhone", item.phone)
                                },
                                shape = RoundedCornerShape(6.dp),
                            ) { Text("详情") }
                            if (item.statusCode == 0) {
                                Button(
                                    { workflowPlan = JSONObject().put("packageId", item.id).put("packageCode", item.code) },
                                    shape = RoundedCornerShape(6.dp),
                                ) { Text("核销") }
                            }
                        }
                    }
                }
            }
        }
    }
    detailPlan?.let { detail ->
        if (detail.has("packageId")) {
            PackageDetailDialogV2(
                packageItem(JSONObject().put("id", detail.optInt("packageId")).put("pickupCode", detail.optString("pickupCode")).put("receiverName", detail.optString("receiverName")).put("receiverPhone", detail.optString("receiverPhone"))),
                onClose = { detailPlan = null },
            )
        } else {
            PlanDetailDialog(
                detail,
                onClose = { detailPlan = null },
                onDelete = { id ->
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { ApiClient.deleteProcessingPlan(id) } }
                            .onSuccess { detailPlan = null; reload++ }
                            .onFailure { error = it.message ?: "删除加工计划失败" }
                    }
                },
            )
        }
    }
    workflowPlan?.let { plan ->
        if (plan.has("packageId")) {
            PackageDetailDialogV2(
                packageItem(JSONObject().put("id", plan.optInt("packageId")).put("pickupCode", plan.optString("packageCode")).put("receiverName", "客户")),
                onClose = { workflowPlan = null },
            )
        } else {
            WorkflowDialog(plan, onClose = { workflowPlan = null })
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建加工计划") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("处方", color = Muted, fontSize = 12.sp); prescriptions.take(8).forEach { prescription -> SegmentedButton("${prescription.optString("prescriptionNo")} · ${prescription.optString("customerName")}", prescriptionId == prescription.optInt("id")) { prescriptionId = prescription.optInt("id") } }; Spacer(Modifier.height(8.dp)); Text("加工类型", color = Muted, fontSize = 12.sp); processTypes.take(6).forEach { type -> SegmentedButton(type.optString("name"), processTypeId == type.optInt("id")) { processTypeId = type.optInt("id") } }; OutlinedTextField(totalDose, { totalDose = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("总剂数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(processDate, { processDate = it }, Modifier.fillMaxWidth(), label = { Text("加工日期 YYYY-MM-DD") }, singleLine = true); val decoction = processTypes.firstOrNull { it.optInt("id") == processTypeId }?.let { it.optString("code") == "DECOCTION" || it.optString("name") == "代煎" } == true; if (decoction) { OutlinedTextField(bagCount, { bagCount = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("袋数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("每袋毫升数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } } }, confirmButton = { val decoction = processTypes.firstOrNull { it.optInt("id") == processTypeId }?.let { it.optString("code") == "DECOCTION" || it.optString("name") == "代煎" } == true; Button(enabled = prescriptionId > 0 && processTypeId > 0 && totalDose.toIntOrNull()?.let { it > 0 } == true && (!decoction || (bagCount.toIntOrNull()?.let { it > 0 } == true && volumeMl.toIntOrNull()?.let { it > 0 } == true)), onClick = { val payload = JSONObject().put("prescriptionId", prescriptionId).put("processTypeId", processTypeId).put("totalDose", totalDose.toInt()).put("batchNo", 1).put("scheduleType", 1).put("processDate", processDate).put("pickupMethod", 0); if (decoction) { payload.put("bagCount", bagCount.toInt()); payload.put("volumeMl", volumeMl.toInt()) }; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createProcessingPlan(payload) } }.onSuccess { createVisible = false; reload++ }.onFailure { error = it.message ?: "创建加工计划失败" } } }) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
}

@Composable
private fun PlanDetailDialog(plan: JSONObject, onClose: () -> Unit, onDelete: (Int) -> Unit) {
    val prescription = plan.optJSONObject("prescription")
    val customer = prescription?.optString("customerName", "客户") ?: plan.optString("customerName", "客户")
    val text = listOf(
        "顾客：$customer",
        "手机号：${prescription?.optString("phone", "-") ?: "-"}",
        "批次：第${plan.optInt("batchNo", 1)}批",
        "剂数：${plan.optInt("totalDose", 0)}剂",
        "状态：${planStatus(plan.optInt("status", 0))}",
        "备注：${plan.optString("processRemark", plan.optString("remark", "-"))}",
    ).joinToString("\n")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("加工计划详情") },
        text = { Text(text, color = Ink) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.optInt("status") == 0) OutlinedButton({ onDelete(plan.optInt("id")) }) { Text("删除", color = Danger) }
                Button(onClose) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun WorkflowDialog(plan: JSONObject, onClose: () -> Unit) {
    var workflow by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var equipmentCode by remember { mutableStateOf("") }
    var portionNo by remember { mutableStateOf("1") }
    var stage by remember { mutableStateOf(3) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input -> ApiClient.completeDispensing(plan.optInt("id"), "dispensing-${System.currentTimeMillis()}.jpg", context.contentResolver.getType(uri) ?: "image/jpeg", input.readBytes()) } ?: throw IllegalStateException("无法读取照片") } }
                .onSuccess { workflow = it }
                .onFailure { error = it.message ?: "上传调配照片失败" }
            busy = false
        }
    }
    LaunchedEffect(plan.optInt("id")) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.processingWorkflow(plan.optInt("id")) } }
            .onSuccess { workflow = it }
            .onFailure { error = it.message ?: "工序加载失败" }
    }
    val usages = workflow?.optJSONArray("equipmentUsages")
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { Button(onClose) { Text("关闭") } },
        title = { Text("工序详情") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error!!, color = Danger)
                    workflow == null -> Text("加载中...", color = Muted)
                    else -> {
                        val records = usages ?: JSONArray()
                        Text("当前阶段：${workflow!!.optInt("currentStage", 0)}", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        if (workflow!!.optString("dispensingCompletedAt").isBlank()) Button(enabled = !busy, onClick = { photoLauncher.launch("image/*") }, shape = RoundedCornerShape(6.dp)) { Text(if (busy) "上传中..." else "上传调配完成照片") }
                        Text("扫码工序操作", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SegmentedButton("浸泡", stage == 3) { stage = 3 }; SegmentedButton("煎煮", stage == 4) { stage = 4 } }
                        OutlinedTextField(equipmentCode, { equipmentCode = it }, Modifier.fillMaxWidth(), label = { Text("设备编号或二维码") }, singleLine = true)
                        OutlinedTextField(portionNo, { portionNo = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("分组编号") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Button(enabled = !busy && equipmentCode.isNotBlank() && portionNo.toIntOrNull()?.let { it > 0 } == true, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.startEquipmentUsage(plan.optInt("id"), JSONObject().put("stage", stage).put("portionNo", portionNo.toInt()).put("equipmentCode", equipmentCode.trim()).put("requestId", "android-${System.currentTimeMillis()}")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "开始工序失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text(if (busy) "提交中..." else "开始工序") }
                        Spacer(Modifier.height(8.dp))
                        if (records.length() == 0) Text("暂无设备工序记录", color = Muted)
                        for (i in 0 until records.length()) {
                            val usage = records.getJSONObject(i)
                            val usageStage = usage.optInt("stage")
                            val stageText = when (usageStage) { 3 -> "浸泡"; 4 -> "煎煮"; 5 -> "打包"; else -> "工序" }
                            val usageStatus = usage.optInt("status")
                            val state = when (usageStatus) { 1 -> "进行中"; 3 -> "已作废"; else -> "已完成" }
                            Text("第${usage.optInt("portionNo", 0)}组 · $stageText · $state", color = Ink)
                            Text(usage.optString("startedAt", "-"), color = Muted, fontSize = 12.sp)
                            if (usageStatus == 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (usageStage == 4) Button(enabled = !busy && equipmentCode.isNotBlank(), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.startPackaging(plan.optInt("id"), usage.optInt("id"), JSONObject().put("equipmentCode", equipmentCode.trim()).put("requestId", "android-${System.currentTimeMillis()}")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "开始打包失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("开始打包") }
                                if (usageStage == 5) Button(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishEquipmentUsage(plan.optInt("id"), usage.optInt("id")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "完成打包失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("完成打包") }
                                OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.voidEquipmentUsage(plan.optInt("id"), usage.optInt("id"), "安卓端撤销") } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "撤销失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("撤销") }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (workflow!!.optBoolean("canCompleteWorkflow") || workflow!!.optBoolean("canFinalizeWorkflow")) Button(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 2) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "完成加工失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("完成加工") }
                    }
                }
            }
        },
    )
}
