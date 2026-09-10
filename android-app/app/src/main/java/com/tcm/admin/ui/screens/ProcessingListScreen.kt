package com.tcm.admin

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tcm.admin.ui.viewmodels.ProcessingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProcessingScreenV2(
    user: JSONObject?,
    onNavigate: (Route) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    viewModel: ProcessingViewModel = hiltViewModel(),
) {
    val showStore = user?.optInt("role", -1) == 0
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val pickupStatus by viewModel.pickupStatus.collectAsStateWithLifecycle()
    val keyword = if (mode == "plans") viewModel.plansKeyword.collectAsStateWithLifecycle().value else viewModel.pickupKeyword.collectAsStateWithLifecycle().value
    val selectedStoreId = if (mode == "plans") viewModel.plansStoreId.collectAsStateWithLifecycle().value else viewModel.pickupStoreId.collectAsStateWithLifecycle().value
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val plansItems = viewModel.plansFlow.collectAsLazyPagingItems()
    val pickupItems = viewModel.pickupFlow.collectAsLazyPagingItems()

    var lastAutoKeyword by remember { mutableStateOf("") }
    var generatePackagePlan by remember { mutableStateOf<JSONObject?>(null) }
    var quickScanTargetPlan by remember { mutableStateOf<JSONObject?>(null) }
    var quickScanPromptData by remember { mutableStateOf<QuickScanPromptData?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val planQuickScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanned = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        val targetPlan = quickScanTargetPlan
        quickScanTargetPlan = null
        if (result.resultCode == Activity.RESULT_OK && scanned.isNotBlank() && targetPlan != null) {
            scope.launch {
                executeAutoQuickScan(
                    context = context,
                    plan = targetPlan,
                    scanned = scanned,
                    onSuccess = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onPrompt = { prompt ->
                        quickScanPromptData = prompt
                    },
                )
            }
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            if (mode == "plans") viewModel.plansKeyword.value = value else viewModel.pickupKeyword.value = value
        }
    }

    LaunchedEffect(showStore) {
        if (showStore) viewModel.loadStores()
    }

    LaunchedEffect(stores) {
        if (showStore && stores.size == 1 && selectedStoreId == null) {
            val sid = stores.first().optInt("id")
            viewModel.plansStoreId.value = sid
            viewModel.pickupStoreId.value = sid
        }
    }

    LaunchedEffect(selectedStoreId, activeView, pickupStatus, mode) {
        viewModel.refreshStats(selectedStoreId)
    }

    LaunchedEffect(keyword, mode) {
        val term = keyword.trim()
        if (!shouldAutoSearchQuery(term)) {
            lastAutoKeyword = ""
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        if (keyword.trim() == term && lastAutoKeyword != term) {
            lastAutoKeyword = term
            if (mode == "plans") plansItems.refresh() else pickupItems.refresh()
        }
    }

    val currentRefreshState = if (mode == "plans") plansItems.loadState.refresh else pickupItems.loadState.refresh
    val isRefreshing = currentRefreshState is LoadState.Loading

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                ApiClient.clearResponseCache(context)
                if (mode == "plans") plansItems.refresh() else pickupItems.refresh()
                viewModel.refreshStats(selectedStoreId)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                item(key = "header") {
                    // Top Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                            modifier = Modifier.weight(1f).heightIn(min = CompactControlHeight),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("扫码作业", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        if (mode != "pickup") {
                            Button(
                                onClick = { onNavigate(Route.ProcessingPlanForm(RouteParams.put(JSONObject()))) },
                                modifier = Modifier.weight(1f).heightIn(min = CompactControlHeight),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("新建加工计划", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Mode Switch: 加工计划 vs 领取列表
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SegmentedButton(
                            label = "加工计划",
                            selected = mode == "plans",
                            onClick = { viewModel.mode.value = "plans" },
                            modifier = Modifier.weight(1f),
                            centerLabel = true,
                        )
                        SegmentedButton(
                            label = "领取列表",
                            selected = mode == "pickup",
                            onClick = { viewModel.mode.value = "pickup" },
                            modifier = Modifier.weight(1f),
                            centerLabel = true,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // In "plans" mode, show interactive stats grid
                    if (mode == "plans") {
                        val statItems = listOf(
                            "今日全部" to (todayAllStat(stats) to "today-all"),
                            "今日待加工" to (stat(stats, "waitingCount") to "today-waiting"),
                            "逾期未开工" to (stat(stats, "overdueCount") to "overdue"),
                            "加工中" to (stat(stats, "processingCount") to "processing"),
                            "等待顾客" to (stat(stats, "waitingNoticeCount") to "notice"),
                            "明日加工" to (stat(stats, "tomorrowWaitingCount") to "tomorrow"),
                            "全部" to (stat(stats, "processingPlanTotalCount") to "all"),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            statItems.chunked(4).forEach { row ->
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
                                                .heightIn(min = 64.dp),
                                            shape = CardShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) PrimarySoft else MaterialTheme.colorScheme.surface,
                                            ),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) Primary else CardBorderColor,
                                            ),
                                            onClick = {
                                                if (activeView != viewKey) {
                                                    viewModel.activeView.value = viewKey
                                                }
                                            },
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 64.dp)
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
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
                                    if (row.size < 4) {
                                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    } else {
                        // In "pickup" mode, show pickup stats
                        val pickupStatItems = listOf(
                            "等待药材" to (stat(stats, "pickupWaitingCount") to 0),
                            "已领取药材" to (stat(stats, "pickupReceivedCount") to 1),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pickupStatItems.forEach { (label, pair) ->
                                val (value, status) = pair
                                val isSelected = pickupStatus == status
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 64.dp),
                                    shape = CardShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) PrimarySoft else MaterialTheme.colorScheme.surface,
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) Primary else CardBorderColor,
                                    ),
                                    onClick = {
                                        if (pickupStatus != status) {
                                            viewModel.pickupStatus.value = status
                                        }
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 64.dp)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = value,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Primary else Ink,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected) Primary else Muted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    // Search Field
                    SearchBarField(
                        value = keyword,
                        onValueChange = {
                            if (mode == "plans") viewModel.plansKeyword.value = it else viewModel.pickupKeyword.value = it
                            if (it.isBlank()) {
                                if (mode == "plans") plansItems.refresh() else pickupItems.refresh()
                            }
                        },
                        placeholder = "搜索顾客姓名、手机号或备注",
                        onSearch = {
                            lastAutoKeyword = keyword.trim()
                            if (mode == "plans") plansItems.refresh() else pickupItems.refresh()
                        },
                    )

                    // Store selection chips
                    if (showStore && stores.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        val storeOptions = listOf(JSONObject().put("id", null).put("name", "全部")) + stores
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            storeOptions.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val chipWeight = Modifier.weight(1f)
                                    row.forEach { store ->
                                        val sid = if (store.isNull("id")) null else store.optInt("id")
                                        val isSelected = selectedStoreId == sid
                                        SegmentedButton(
                                            store.displayField("name", "门店"),
                                            isSelected,
                                            {
                                                if (mode == "plans") viewModel.plansStoreId.value = sid else viewModel.pickupStoreId.value = sid
                                            },
                                            chipWeight
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(chipWeight) }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                }

                // Error Banner
                if (currentRefreshState is LoadState.Error) {
                    item(key = "error") {
                        Surface(
                            color = DangerSoft,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, Danger.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            ErrorStateView(
                                message = currentRefreshState.error.message ?: "加载失败",
                                onRetry = { if (mode == "plans") plansItems.retry() else pickupItems.retry() }
                            )
                        }
                    }
                }

                // Processing Plans List
                if (mode == "plans") {
                    if (currentRefreshState is LoadState.Loading && plansItems.itemCount == 0) {
                        item(key = "loading_plans") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (plansItems.itemCount == 0 && currentRefreshState !is LoadState.Error && currentRefreshState !is LoadState.Loading) {
                        item(key = "empty_plans") {
                            AppEmptyState("暂无加工计划")
                        }
                    }

                    items(count = plansItems.itemCount, key = plansItems.itemKey { it.optInt("id") }) { index ->
                        val plan = plansItems[index]
                        if (plan != null) {
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
                            val scheduleDate = serverDateOnly(plan.opt("processDate"), "")
                            val isDecoction = processType?.displayField("name", "")?.contains("煎") == true || plan.displayField("processTypeName", "").contains("煎")
                            val packageCreated = plan.optBoolean("packageCreated") || plan.optInt("packageId", 0) > 0

                            AppCard(
                                modifier = Modifier.padding(bottom = 12.dp),
                                onClick = { onNavigate(Route.WorkflowOperation(RouteParams.put(plan), "", "open")) },
                            ) {
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

                                Spacer(Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(5.dp))

                                // Detail Rows
                                InfoRowItem("批次剂数", "第 $batchNo 批 · $totalDose 剂", verticalPadding = 0.dp)
                                if (isDecoction && bagCount > 0) {
                                    InfoRowItem("代煎规格", "$bagCount 袋 · ${volumeMl}ml", verticalPadding = 0.dp)
                                }
                                InfoRowItem("取货方式", pickupMethodLabel(pickupMethod), verticalPadding = 0.dp)
                                InfoRowItem("计划开工", scheduleDate.ifBlank { "未安排" }, verticalPadding = 0.dp)
                                if (showStore) {
                                    store?.displayField("name", "")?.takeIf { it.isNotBlank() }?.let {
                                        InfoRowItem("加工门店", it, verticalPadding = 0.dp)
                                    }
                                }
                                plan.displayField("startDate", "").takeIf { it.isNotBlank() }?.let {
                                    InfoRowItem("实际开工", serverDateTime(it), verticalPadding = 0.dp)
                                }
                                plan.displayField("finishDate", "").takeIf { it.isNotBlank() }?.let {
                                    InfoRowItem("完成时间", serverDateTime(it), verticalPadding = 0.dp)
                                }
                                plan.displayField("remark", "").takeIf { it.isNotBlank() }?.let {
                                    InfoRowItem("备注", it, verticalPadding = 0.dp)
                                }
                                plan.displayField("processRemark", "").takeIf { it.isNotBlank() }?.let {
                                    InfoRowItem("加工备注", it, verticalPadding = 0.dp)
                                }

                                Spacer(Modifier.height(8.dp))

                                // Plan Actions
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val prescriptionId = plan.optInt("prescriptionId", plan.optJSONObject("prescription")?.optInt("id", 0) ?: 0)
                                    if (prescriptionId > 0) {
                                        OutlinedButton(
                                            onClick = { onNavigate(Route.PrescriptionDetail(prescriptionId)) },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("处方", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    if (status == ProcessingPlanStatus.WAITING.code) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 1) } }
                                                        .onSuccess { plansItems.refresh(); viewModel.refreshStats(selectedStoreId) }
                                                        .onFailure { Toast.makeText(context, it.message ?: "开始加工失败", Toast.LENGTH_SHORT).show() }
                                                }
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("开始加工", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { withContext(Dispatchers.IO) { ApiClient.delayPlan(plan.optInt("id"), 1) } }
                                                        .onSuccess { plansItems.refresh(); viewModel.refreshStats(selectedStoreId) }
                                                        .onFailure { Toast.makeText(context, it.message ?: "延期失败", Toast.LENGTH_SHORT).show() }
                                                }
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("延期明天", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    if (status == ProcessingPlanStatus.IN_PROGRESS.code) {
                                        Button(
                                            onClick = {
                                                quickScanTargetPlan = plan
                                                planQuickScanLauncher.launch(Intent(context, ScannerActivity::class.java))
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(2.dp))
                                            Text("扫码", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    if (status == ProcessingPlanStatus.COMPLETED.code && !packageCreated) {
                                        Button(
                                            onClick = { generatePackagePlan = plan },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Success),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("生成包裹", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    if (status in 0..1) {
                                        OutlinedButton(
                                            onClick = { onNavigate(Route.ProcessingPlanForm(RouteParams.put(plan))) },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("编辑", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    if (status in listOf(0, 1)) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { withContext(Dispatchers.IO) { ApiClient.cancelPlan(plan.optInt("id"), "管理员取消") } }
                                                        .onSuccess { plansItems.refresh(); viewModel.refreshStats(selectedStoreId) }
                                                        .onFailure { Toast.makeText(context, it.message ?: "取消失败", Toast.LENGTH_SHORT).show() }
                                                }
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp).defaultMinSize(minWidth = 44.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("取消", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (plansItems.loadState.append is LoadState.Loading) {
                        item(key = "append_loading_plans") {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                            }
                        }
                    }
                } else if (mode == "pickup") {
                    if (currentRefreshState is LoadState.Loading && pickupItems.itemCount == 0) {
                        item(key = "loading_pickup") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (pickupItems.itemCount == 0 && currentRefreshState !is LoadState.Error && currentRefreshState !is LoadState.Loading) {
                        item(key = "empty_pickup") {
                            AppEmptyState(if (pickupStatus == 0) "暂无待领取的药材" else "暂无已领取记录")
                        }
                    }

                    items(count = pickupItems.itemCount, key = pickupItems.itemKey { it.id }) { index ->
                        val item = pickupItems[index]
                        if (item != null) {
                            PackageSummaryCard(
                                item = item,
                                showStore = showStore,
                                modifier = Modifier.padding(bottom = 12.dp),
                                onClick = { onNavigate(Route.PackageDetail(RouteParams.put(item))) },
                                onVerify = {
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(item.code, 0, "") } }
                                            .onSuccess { pickupItems.refresh(); viewModel.refreshStats(selectedStoreId) }
                                            .onFailure { Toast.makeText(context, it.message ?: "核销失败", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                            )
                        }
                    }

                    if (pickupItems.loadState.append is LoadState.Loading) {
                        item(key = "append_loading_pickup") {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        generatePackagePlan?.let { plan ->
            var packageRemark by remember(plan) {
                mutableStateOf(
                    plan.displayField("processRemark", "")
                        .ifBlank { plan.displayField("remark", "") },
                )
            }
            AlertDialog(
                onDismissRequest = { generatePackagePlan = null },
                title = { Text("生成包裹") },
                text = {
                    Column {
                        Text("该加工计划已完成，确认生成待领取包裹吗？")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = packageRemark,
                            onValueChange = { packageRemark = it.take(500) },
                            label = { Text("包裹备注") },
                            placeholder = { Text("可填写代煎、配送或取货说明") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = FieldShape,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val planId = plan.optInt("id")
                            generatePackagePlan = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.generatePlanPackage(
                                            planId,
                                            JSONObject().put("itemInfo", packageRemark.trim()),
                                        )
                                    }
                                }
                                    .onSuccess { plansItems.refresh(); viewModel.refreshStats(selectedStoreId) }
                                    .onFailure { Toast.makeText(context, it.message ?: "生成包裹失败", Toast.LENGTH_SHORT).show() }
                            }
                        },
                    ) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { generatePackagePlan = null }) {
                        Text("取消")
                    }
                },
            )
        }

        quickScanPromptData?.let { promptData ->
            QuickScanPromptDialog(
                data = promptData,
                onDismiss = { quickScanPromptData = null },
                onOpenDetail = {
                    val p = promptData.plan
                    quickScanPromptData = null
                    onNavigate(Route.WorkflowOperation(RouteParams.put(p), "", "open"))
                },
                onEquipmentScanned = {
                    quickScanPromptData = null
                },
                onNavigatePlan = { targetPlan ->
                    quickScanPromptData = null
                    onNavigate(Route.WorkflowOperation(RouteParams.put(targetPlan), "", "open"))
                },
            )
        }
    }
}
