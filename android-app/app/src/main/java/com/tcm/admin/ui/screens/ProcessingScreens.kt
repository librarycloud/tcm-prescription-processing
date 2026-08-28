package com.tcm.admin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Composable
internal fun ProcessingScreenV2(onNavigate: (ScreenTarget) -> Unit = {}) {
    var mode by remember { mutableStateOf("plans") } // "plans" | "pickup"
    var activeView by remember { mutableStateOf("today-all") }
    var keyword by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf<List<JSONObject>?>(null) }
    var pickupTasks by remember { mutableStateOf<List<PackageItem>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }

    // Dialog states
    var selectedPlan by remember { mutableStateOf<JSONObject?>(null) }
    var editingPlan by remember { mutableStateOf<JSONObject?>(null) }
    var createPlanVisible by remember { mutableStateOf(false) }
    var workflowPlan by remember { mutableStateOf<JSONObject?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            keyword = value
            reload++
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
    }

    LaunchedEffect(reload, mode, activeView, selectedStoreId, keyword, page) {
        error = null
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                val storeIdInt = selectedStoreId.toIntOrNull()
                val summary = ApiClient.processingStats(storeIdInt)
                if (mode == "plans") {
                    val paged = ApiClient.processingPlansPaged(
                        view = activeView,
                        keyword = keyword.trim(),
                        storeId = storeIdInt,
                        page = page,
                        pageSize = 20,
                    )
                    Triple<JSONObject, JSONObject?, JSONArray?>(summary, paged, null)
                } else {
                    val pickupData = ApiClient.pickupTasks(
                        keyword = keyword.trim(),
                        storeId = storeIdInt,
                    )
                    Triple<JSONObject, JSONObject?, JSONArray?>(summary, null, pickupData)
                }
            }
        }.onSuccess { (summary, pagedPlans, pickupData) ->
            stats = summary
            if (pagedPlans != null) {
                val list = pagedPlans.optJSONArray("list") ?: JSONArray()
                plans = (0 until list.length()).map { list.getJSONObject(it) }
                pages = pagedPlans.optJSONObject("pagination")?.optInt("pages", 1) ?: 1
            }
            if (pickupData != null) {
                pickupTasks = (0 until pickupData.length()).map {
                    val obj = pickupData.getJSONObject(it)
                    val prescription = obj.optJSONObject("prescription")
                    val processType = obj.optJSONObject("processType")
                    val store = obj.optJSONObject("store")
                    PackageItem(
                        id = obj.optInt("id"),
                        name = "${obj.displayField("receiverName", "顾客")} · ${processType?.displayField("name", "代煎") ?: "加工"}",
                        customer = obj.displayField("receiverName"),
                        phone = obj.displayField("receiverPhone"),
                        code = obj.displayField("pickupCode"),
                        method = pickupMethodLabel(obj.optInt("pickupMethod", 0)),
                        status = if (obj.optInt("status") == 1) "已领取" else "待领取",
                        statusCode = obj.optInt("status"),
                        time = obj.displayField("finishDate", "").take(16).replace("T", " "),
                        store = store?.displayField("name", "") ?: "",
                        expressTrackingNo = obj.displayField("expressTrackingNo", ""),
                        pickupQrContent = obj.displayField("pickupQrContent", ""),
                    )
                }
            }
            loading = false
        }.onFailure {
            error = it.message ?: "加载加工数据失败"
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                modifier = Modifier.weight(1f).height(CompactControlHeight),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("扫码作业", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { createPlanVisible = true },
                modifier = Modifier.weight(1f).height(CompactControlHeight),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建加工计划", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mode Switch: 加工计划 vs 待领取
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedButton(
                label = "加工计划",
                selected = mode == "plans",
                onClick = { mode = "plans"; page = 1 },
                modifier = Modifier.weight(1f),
            )
            SegmentedButton(
                label = "待领取任务",
                selected = mode == "pickup",
                onClick = { mode = "pickup"; page = 1 },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        // In "plans" mode, show interactive stats grid
        if (mode == "plans") {
            val statItems = listOf(
                "今日待加工" to (stat(stats, "waitingCount") to "today-all"),
                "逾期未开工" to (stat(stats, "overdueCount") to "overdue"),
                "加工中" to (stat(stats, "processingCount") to "processing"),
                "今日完成" to (stat(stats, "todayFinished") to "today-finished"),
                "等待顾客" to (stat(stats, "waitingNoticeCount") to "waiting-notice"),
                "明日加工" to (stat(stats, "tomorrowWaitingCount") to "tomorrow-waiting"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statItems.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (label, pair) ->
                            val (value, viewKey) = pair
                            val isSelected = activeView == viewKey
                            val isPositive = value != "0" && value != "-"
                            val isAlert = label.contains("逾期") || label.contains("等待")

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                shape = CardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) PrimarySoft else Color.White,
                                ),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Primary else Color(0xFFEBEEF5),
                                ),
                                onClick = {
                                    activeView = if (activeView == viewKey) "all" else viewKey
                                    page = 1
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = value,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !isPositive -> Ink
                                            isAlert -> Danger
                                            label.contains("完成") -> Success
                                            else -> Primary
                                        },
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) Primary else Muted,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Search Field
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索顾客姓名、手机号或备注",
            onSearch = { page = 1; reload++ },
        )

        // Store chips stay compact and wrap naturally below the search field.
        if (stores.size > 1) {
            Spacer(Modifier.height(8.dp))
            val storeOptions = listOf(JSONObject().put("id", "").put("name", "全部")) + stores
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                storeOptions.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val chipWeight = Modifier.weight(1f)
                        row.forEach { store ->
                            val id = store.displayField("id", "")
                            SegmentedButton(store.displayField("name", "门店"), selectedStoreId == id, { selectedStoreId = id; page = 1; reload++ }, chipWeight)
                        }
                        repeat(3 - row.size) { Spacer(chipWeight) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Error Banner
        if (error != null) {
            Surface(
                color = DangerSoft,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Danger.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(error!!, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
        }

        // Loading
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
        }

        // Processing Plans List
        if (mode == "plans" && !loading) {
            if (plans == null || plans!!.isEmpty()) {
                AppEmptyState("暂无加工计划")
            } else {
                plans!!.forEach { plan ->
                    val prescription = plan.optJSONObject("prescription")
                    val processType = plan.optJSONObject("processType")
                    val store = plan.optJSONObject("store")
                    val customerName = plan.displayField("customerName", "").ifBlank { prescription?.displayField("customerName") ?: "-" }
                    val phone = plan.displayField("customerPhone", "").ifBlank { prescription?.displayField("phone") ?: "-" }
                    val doctorName = plan.displayField("doctorName", "").ifBlank { prescription?.optJSONObject("doctor")?.displayField("name") ?: "-" }
                    val isUrgent = plan.optBoolean("isUrgent") || plan.optInt("isUrgent") == 1
                    val status = plan.optInt("status")
                    val batchNo = plan.optInt("batchNo", 1)
                    val totalDose = plan.optInt("totalDose", 0)
                    val bagCount = plan.optInt("bagCount", 0)
                    val volumeMl = plan.optInt("volumeMl", 0)
                    val pickupMethod = plan.optInt("pickupMethod", 0)
                    val scheduleDate = plan.displayField("scheduledDate", "").take(10)
                    val isDecoction = processType?.displayField("name", "")?.contains("煎") == true || plan.displayField("processTypeName", "").contains("煎")
                    val packageCreated = plan.optBoolean("packageCreated") || plan.optInt("packageId", 0) > 0

                    AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$customerName · ${processType?.displayField("name", "加工") ?: "加工"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Ink,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${maskPhone(phone)} · 医生：$doctorName",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isUrgent) UrgentBadge()
                                StatusPill(planStatus(status))
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF2F3F5))
                        Spacer(Modifier.height(8.dp))

                        // Detail Rows
                        InfoRowItem("批次剂数", "第 $batchNo 批 · $totalDose 剂")
                        if (isDecoction && bagCount > 0) {
                            InfoRowItem("代煎规格", "$bagCount 袋 · ${volumeMl}ml")
                        }
                        InfoRowItem("取货方式", pickupMethodLabel(pickupMethod))
                        InfoRowItem("计划开工", scheduleDate.ifBlank { "未安排" })
                        store?.displayField("name", "")?.takeIf { it.isNotBlank() }?.let {
                            InfoRowItem("加工门店", it)
                        }
                        plan.displayField("startDate", "").takeIf { it.isNotBlank() }?.let {
                            InfoRowItem("实际开工", it.take(16).replace("T", " "))
                        }
                        plan.displayField("finishDate", "").takeIf { it.isNotBlank() }?.let {
                            InfoRowItem("完成时间", it.take(16).replace("T", " "))
                        }
                        plan.displayField("remark", "").takeIf { it.isNotBlank() }?.let {
                            InfoRowItem("备注", it)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Scrollable Action Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { selectedPlan = plan },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("详情", fontSize = 12.sp)
                            }

                            if (status == 0) { // 待加工
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 1) } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "开始加工失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("开始加工", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.delayPlan(plan.optInt("id"), 1) } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "延期失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("延期明天", fontSize = 12.sp)
                                }
                            }

                            if (status == 1) { // 加工中
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 2) } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "完成加工失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("加工完成", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { workflowPlan = plan },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("扫码工序", fontSize = 12.sp)
                                }
                            }

                            if (status == 2 && !packageCreated) { // 完成但未生成包裹
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.generatePlanPackage(plan.optInt("id")) } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "生成包裹失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("生成包裹", fontSize = 12.sp)
                                }
                            }

                            if (status != 5) {
                                OutlinedButton(
                                    onClick = { editingPlan = plan },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("编辑", fontSize = 12.sp)
                                }
                            }

                            if (status in listOf(0, 1)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.cancelPlan(plan.optInt("id"), "管理员取消") } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "取消失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("取消", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Pagination
                if (pages > 1) {
                    AppPagination(
                        page = page,
                        pages = pages,
                        onPrev = { if (page > 1) page-- },
                        onNext = { if (page < pages) page++ },
                    )
                }
            }
        }

        // Pickup Tasks List
        if (mode == "pickup" && !loading) {
            if (pickupTasks == null || pickupTasks!!.isEmpty()) {
                AppEmptyState("暂无待领取任务")
            } else {
                pickupTasks!!.forEach { item ->
                    AppCard(
                        modifier = Modifier.padding(bottom = 12.dp),
                        onClick = { onNavigate(ScreenTarget.PackageDetail(item)) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Ink,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${item.customer} · ${maskPhone(item.phone)}",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(item.method)
                                StatusPill(item.status)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF2F3F5))
                        Spacer(Modifier.height(8.dp))

                        // Large Pickup Code Highlight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("取货码：", color = RegularText, fontSize = 13.sp)
                            Text(
                                text = formatPickupCode(item.code),
                                color = Primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        InfoRowItem("完成时间", item.time)
                        if (item.store.isNotBlank()) {
                            InfoRowItem("门店", item.store)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onNavigate(ScreenTarget.PackageDetail(item)) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("详情", fontSize = 12.sp)
                            }

                            if (item.statusCode == 0) {
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(item.code, 0, "") } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "核销失败" }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Text("核销领取", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

            Spacer(Modifier.height(16.dp))
        }

    // Dialogs
    selectedPlan?.let { plan ->
        PlanDetailDialog(
            plan = plan,
            onClose = { selectedPlan = null },
            onReload = { selectedPlan = null; reload++ },
        )
    }

    if (createPlanVisible || editingPlan != null) {
        ProcessingPlanFormDialog(
            initial = editingPlan,
            stores = stores,
            onClose = { createPlanVisible = false; editingPlan = null },
            onSaved = { createPlanVisible = false; editingPlan = null; reload++ },
            onError = { error = it },
        )
    }

    workflowPlan?.let { plan ->
        WorkflowOperationDialog(
            plan = plan,
            onClose = { workflowPlan = null; reload++ },
        )
    }

    }
}

@Composable
internal fun PlanDetailDialog(
    plan: JSONObject,
    onClose: () -> Unit,
    onReload: () -> Unit,
) {
    val prescription = plan.optJSONObject("prescription")
    val processType = plan.optJSONObject("processType")
    val store = plan.optJSONObject("store")
    val customerName = plan.displayField("customerName", "").ifBlank { prescription?.displayField("customerName") ?: "-" }
    val phone = plan.displayField("customerPhone", "").ifBlank { prescription?.displayField("phone") ?: "-" }
    val status = plan.optInt("status")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("加工计划详情", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRowItem("顾客姓名", customerName)
                InfoRowItem("联系电话", phone)
                InfoRowItem("加工类型", processType?.displayField("name", "加工") ?: "加工")
                InfoRowItem("加工状态", planStatus(status), isBold = true, valueColor = Primary)
                InfoRowItem("批次剂数", "第 ${plan.optInt("batchNo", 1)} 批 · ${plan.optInt("totalDose", 0)} 剂")
                InfoRowItem("取货方式", pickupMethodLabel(plan.optInt("pickupMethod", 0)))
                InfoRowItem("计划开工", plan.displayField("scheduledDate").take(10))
                store?.displayField("name", "")?.takeIf { it.isNotBlank() }?.let { InfoRowItem("加工门店", it) }
                plan.displayField("startDate", "").takeIf { it.isNotBlank() }?.let { InfoRowItem("开工时间", it.take(16).replace("T", " ")) }
                plan.displayField("finishDate", "").takeIf { it.isNotBlank() }?.let { InfoRowItem("完成时间", it.take(16).replace("T", " ")) }
                plan.displayField("remark", "").takeIf { it.isNotBlank() }?.let { InfoRowItem("备注", it) }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("关闭")
            }
        },
    )
}

@Composable
internal fun ProcessingPlanFormDialog(
    initial: JSONObject?,
    stores: List<JSONObject>,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    val isEdit = initial != null
    var customerName by remember(initial) { mutableStateOf(initial?.displayField("customerName", "").orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial?.displayField("customerPhone", "").orEmpty()) }
    var totalDose by remember(initial) { mutableStateOf(initial?.optInt("totalDose", 7)?.toString() ?: "7") }
    var bagCount by remember(initial) { mutableStateOf(initial?.optInt("bagCount", 14)?.toString() ?: "14") }
    var volumeMl by remember(initial) { mutableStateOf(initial?.optInt("volumeMl", 200)?.toString() ?: "200") }
    var pickupMethod by remember(initial) { mutableStateOf(initial?.optInt("pickupMethod", 0) ?: 0) }
    var scheduledDate by remember(initial) {
        mutableStateOf(initial?.displayField("scheduledDate", "").orEmpty().take(10).ifBlank { LocalDate.now().toString() })
    }
    var isUrgent by remember(initial) { mutableStateOf(initial?.optBoolean("isUrgent") == true || initial?.optInt("isUrgent") == 1) }
    var remark by remember(initial) { mutableStateOf(initial?.displayField("remark", "").orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (isEdit) "编辑加工计划" else "新建加工计划", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("顾客姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("联系手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalDose,
                        onValueChange = { totalDose = it.filter(Char::isDigit) },
                        label = { Text("总剂数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    OutlinedTextField(
                        value = bagCount,
                        onValueChange = { bagCount = it.filter(Char::isDigit) },
                        label = { Text("代煎袋数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = scheduledDate,
                    onValueChange = { scheduledDate = it },
                    label = { Text("计划开工日期 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("取货方式", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (method, label) ->
                        SegmentedButton(label, pickupMethod == method, onClick = { pickupMethod = method })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("加急处理", color = if (isUrgent) Danger else Ink, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isUrgent, onCheckedChange = { isUrgent = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("加工要求与备注") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && customerName.isNotBlank() && totalDose.toIntOrNull()?.let { it > 0 } == true,
                onClick = {
                    busy = true
                    val payload = JSONObject()
                        .put("customerName", customerName.trim())
                        .put("customerPhone", phone.trim())
                        .put("totalDose", totalDose.toIntOrNull() ?: 7)
                        .put("bagCount", bagCount.toIntOrNull() ?: 14)
                        .put("volumeMl", volumeMl.toIntOrNull() ?: 200)
                        .put("pickupMethod", pickupMethod)
                        .put("scheduledDate", scheduledDate.trim())
                        .put("isUrgent", isUrgent)
                        .put("remark", remark.trim())

                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                if (isEdit) {
                                    ApiClient.updatePlan(initial!!.optInt("id"), payload)
                                } else {
                                    ApiClient.createPlan(payload)
                                }
                            }
                        }.onSuccess {
                            onSaved()
                        }.onFailure {
                            onError(it.message ?: "保存计划失败")
                        }
                        busy = false
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (busy) "保存中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onClose() }) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun WorkflowOperationDialog(
    plan: JSONObject,
    onClose: () -> Unit,
) {
    var workflow by remember { mutableStateOf<JSONObject?>(null) }
    var stage by remember { mutableStateOf(3) } // 3=浸泡, 4=煎煮, 5=打包
    var equipmentCode by remember { mutableStateOf("") }
    var portionNo by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(plan) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.processingWorkflow(plan.optInt("id")) } }
            .onSuccess { workflow = it }
            .onFailure { error = it.message ?: "加载工序失败" }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("工序操作与设备关联", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(error!!, color = Danger, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Text("工序阶段选择", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SegmentedButton("浸泡", stage == 3, onClick = { stage = 3 })
                    SegmentedButton("煎煮", stage == 4, onClick = { stage = 4 })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = equipmentCode,
                    onValueChange = { equipmentCode = it },
                    label = { Text("设备编号或二维码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = portionNo,
                    onValueChange = { portionNo = it.filter(Char::isDigit) },
                    label = { Text("分组编号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !busy && equipmentCode.isNotBlank() && portionNo.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.startEquipmentUsage(
                                        plan.optInt("id"),
                                        JSONObject()
                                            .put("stage", stage)
                                            .put("portionNo", portionNo.toInt())
                                            .put("equipmentCode", equipmentCode.trim())
                                            .put("requestId", "android-${System.currentTimeMillis()}"),
                                    )
                                }
                            }.onSuccess {
                                workflow = it
                                equipmentCode = ""
                            }.onFailure {
                                error = it.message ?: "开始工序失败"
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (busy) "提交中..." else "开始该阶段工序")
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("完成")
            }
        },
    )
}
