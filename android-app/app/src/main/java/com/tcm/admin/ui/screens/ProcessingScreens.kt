package com.tcm.admin

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcm.admin.ui.viewmodels.ProcessingViewModel

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate

private const val MAX_PROCESSING_PHOTO_BYTES = 5 * 1024 * 1024
private const val PROCESSING_PHOTO_CACHE_TTL_MILLIS = 3 * 60 * 60 * 1000L

private suspend fun loadProcessingPhoto(context: android.content.Context, planId: Int, photoId: Int): Bitmap = withContext(Dispatchers.IO) {
    val cacheFile = java.io.File(context.cacheDir, "processing-photos/$planId-$photoId")
    val legacyFile = java.io.File(context.filesDir, "processing-photos/$planId-$photoId")
    if (legacyFile.exists()) {
        legacyFile.delete()
    }
    val cacheAge = System.currentTimeMillis() - cacheFile.lastModified()
    if (cacheFile.isFile && cacheAge in 0..PROCESSING_PHOTO_CACHE_TTL_MILLIS) {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
    }
    cacheFile.delete()

    val bytes = ApiClient.processingPhoto(planId, photoId)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("无法读取照片")
    runCatching {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(bytes)
    }
    bitmap
}

private fun todayAllStat(stats: JSONObject?): String {
    if (stats == null) return "-"
    return listOf("waitingCount", "processingCount", "todayFinished")
        .sumOf { stats.optInt(it, 0) }
        .toString()
}

private fun readProcessingPhoto(context: android.content.Context, uri: Uri): ByteArray {
    val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("无法读取照片")
    if (original.size <= MAX_PROCESSING_PHOTO_BYTES) return original

    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: throw IllegalStateException("无法处理照片")
    val qualities = intArrayOf(92, 84, 76, 68)
    try {
        for (quality in qualities) {
            val output = java.io.ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= MAX_PROCESSING_PHOTO_BYTES) {
                return output.toByteArray()
            }
        }
    } finally {
        bitmap.recycle()
    }
    throw IllegalStateException("照片压缩后仍超过 5MB，请选择较小的照片")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProcessingScreenV2(
    user: JSONObject?,
    onNavigate: (ScreenTarget) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    viewModel: ProcessingViewModel = hiltViewModel()
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
                    onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                    onPrompt = { prompt -> quickScanPromptData = prompt },
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

    val currentItemsRefreshState = if (mode == "plans") plansItems.loadState.refresh else pickupItems.loadState.refresh
    val isRefreshing = currentItemsRefreshState is LoadState.Loading

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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item(key = "header") {
                    // Top Action Bar
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        if (mode != "pickup") {
                            Button(
                                onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject())) },
                                modifier = Modifier.weight(1f).height(CompactControlHeight),
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

                    // Mode Switch
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (label, pair) ->
                                        val (value, viewKey) = pair
                                        val isSelected = activeView == viewKey
                                        val isPositive = value != "0" && value != "-"
                                        val isAlert = label.contains("逾期") || label.contains("等待")

                                        Card(
                                            modifier = Modifier.weight(1f).height(64.dp),
                                            shape = CardShape,
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimarySoft else MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Primary else CardBorderColor),
                                            onClick = { if (activeView != viewKey) viewModel.activeView.value = viewKey },
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp),
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
                        val pickupStatItems = listOf(
                            "等待药材" to (stat(stats, "pickupWaitingCount") to 0),
                            "已领取药材" to (stat(stats, "pickupReceivedCount") to 1),
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pickupStatItems.forEach { (label, pair) ->
                                val (value, status) = pair
                                val isSelected = pickupStatus == status
                                Card(
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    shape = CardShape,
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimarySoft else MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Primary else CardBorderColor),
                                    onClick = { if (pickupStatus != status) viewModel.pickupStatus.value = status },
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
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

                    SearchBarField(
                        value = keyword,
                        onValueChange = {
                            if (mode == "plans") viewModel.plansKeyword.value = it else viewModel.pickupKeyword.value = it
                            if (it.isBlank()) { if (mode == "plans") plansItems.refresh() else pickupItems.refresh() }
                        },
                        placeholder = "搜索顾客姓名、手机号或备注",
                        onSearch = { lastAutoKeyword = keyword.trim(); if (mode == "plans") plansItems.refresh() else pickupItems.refresh() },
                    )

                    if (showStore && stores.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        val storeOptions = listOf(JSONObject().put("id", "").put("name", "全部")) + stores
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            storeOptions.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { store ->
                                        val sid = store.opt("id")?.toString()?.takeIf { it.isNotBlank() }
                                        val isSelected = selectedStoreId?.toString() == sid
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { 
                                                val idInt = sid?.toIntOrNull()
                                                if (mode == "plans") viewModel.plansStoreId.value = idInt else viewModel.pickupStoreId.value = idInt 
                                            },
                                            label = { Text(store.displayField("name", "")) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size < 3) {
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (currentItemsRefreshState is LoadState.Error) {
                    item(key = "error") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.05f)),
                            border = BorderStroke(0.5.dp, Danger.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            ErrorStateView(message = currentItemsRefreshState.error.message ?: "加载失败", onRetry = { if (mode == "plans") plansItems.retry() else pickupItems.retry() })
                        }
                    }
                }

                if (mode == "plans") {
                    if (currentItemsRefreshState is LoadState.Loading && plansItems.itemCount == 0) {
                        item(key = "loading_plans") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (plansItems.itemCount == 0 && currentItemsRefreshState !is LoadState.Error && currentItemsRefreshState !is LoadState.Loading) {
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

                            AppCard(
                                modifier = Modifier.padding(bottom = 12.dp),
                                onClick = { onNavigate(ScreenTarget.WorkflowOperation(plan, plan.optString("currentStep", "START"), "START")) },
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isUrgent) {
                                                Surface(color = Danger, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(end = 6.dp)) {
                                                    Text("加急", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                            Text(customerName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                                            Spacer(Modifier.width(8.dp))
                                            Text(maskPhone(phone), fontSize = 13.sp, color = Muted)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text("${processType?.displayField("name") ?: "未知工艺"} / 第 $batchNo 批", color = Muted, fontSize = 12.sp)
                                    }
                                    StatusPill(processingStatusLabel(status))
                                }

                                Spacer(Modifier.height(10.dp))
                                val specs = mutableListOf<String>()
                                if (totalDose > 0) specs.add("$totalDose 剂")
                                if (bagCount > 0) specs.add("$bagCount 袋")
                                if (volumeMl > 0) specs.add("$volumeMl ml/袋")
                                InfoRowItem("加工规格", specs.joinToString(" / ").ifBlank { "-" })
                                InfoRowItem("加工时间", scheduleDate)
                                InfoRowItem("处方医生", doctorName)
                                if (showStore) InfoRowItem("门店", store?.displayField("name") ?: "-")

                                val reqType = plan.optInt("requestType", 0)
                                val pNo = plan.displayField("processingNo", "")
                                if (reqType == 1 && pNo.isNotBlank()) {
                                    InfoRowItem("外发单号", pNo)
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (pickupMethod == 1) {
                                            Icon(Icons.Default.LocalShipping, contentDescription = "邮寄", tint = Primary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("需要发货物流", color = Primary, fontSize = 12.sp)
                                        } else {
                                            Icon(Icons.Default.Storefront, contentDescription = "自提", tint = Muted, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("顾客到店自提", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (status == 1 && pickupMethod == 1) {
                                            OutlinedButton(
                                                onClick = { generatePackagePlan = plan },
                                                shape = FieldShape,
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            ) { Text("生成物流包裹", fontSize = 12.sp) }
                                        }

                                        val currentStep = plan.optString("currentStep", "")
                                        if (currentStep.isNotBlank() && currentStep != "START" && currentStep != "FINISH") {
                                            Button(
                                                onClick = { onNavigate(ScreenTarget.WorkflowOperation(plan, currentStep, "RESUME")) },
                                                shape = FieldShape,
                                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            ) { Text("继续加工", fontSize = 12.sp) }
                                        }
                                        OutlinedButton(
                                            onClick = { quickScanTargetPlan = plan; planQuickScanLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                                            shape = FieldShape,
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("扫码快速录入", fontSize = 12.sp)
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

                } else {
                    if (currentItemsRefreshState is LoadState.Loading && pickupItems.itemCount == 0) {
                        item(key = "loading_pickup") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (pickupItems.itemCount == 0 && currentItemsRefreshState !is LoadState.Error && currentItemsRefreshState !is LoadState.Loading) {
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
                                onClick = { onNavigate(ScreenTarget.PackageDetail(item)) },
                                onVerify = { onNavigate(ScreenTarget.PackageVerify(item.code)) },
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

        // Quick Scan Flow Overlays
        quickScanPromptData?.let { prompt ->
            if (prompt.options.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { quickScanPromptData = null },
                    title = { Text(prompt.message, fontSize = 16.sp) },
                    text = {
                        Column {
                            prompt.options.forEach { option ->
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            executeAutoQuickScan(
                                                context = context,
                                                plan = prompt.plan,
                                                scanned = prompt.scanned,
                                                optionId = option.optInt("id"),
                                                onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                                                onPrompt = { newPrompt -> quickScanPromptData = newPrompt },
                                            )
                                            quickScanPromptData = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(option.displayField("name"))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { quickScanPromptData = null }) { Text("取消") }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { quickScanPromptData = null },
                    title = { Text("操作确认") },
                    text = { Text(prompt.message) },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                executeAutoQuickScan(
                                    context = context,
                                    plan = prompt.plan,
                                    scanned = prompt.scanned,
                                    confirmed = true,
                                    onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                                    onPrompt = { newPrompt -> quickScanPromptData = newPrompt },
                                )
                                quickScanPromptData = null
                            }
                        }) { Text("继续") }
                    },
                    dismissButton = {
                        TextButton(onClick = { quickScanPromptData = null }) { Text("取消") }
                    }
                )
            }
        }

        generatePackagePlan?.let { plan ->
            val pid = plan.optInt("id")
            val pNo = plan.displayField("processingNo", "")
            val cid = plan.optJSONObject("prescription")?.optInt("id") ?: 0
            AlertDialog(
                onDismissRequest = { generatePackagePlan = null },
                title = { Text("生成物流包裹") },
                text = { Text("将根据此加工计划的处方及代煎信息，自动生成一个待发货的物流包裹记录。") },
                confirmButton = {
                    Button(onClick = {
                        generatePackagePlan = null
                        onNavigate(
                            ScreenTarget.PackageForm(
                                initial = PackageItem(
                                    id = 0,
                                    code = "",
                                    status = 0,
                                    type = 2,
                                    metadata = JSONObject().put("prescriptionId", cid).put("processingPlanId", pid).put("sourceNote", "加工单: $pNo"),
                                    createdAt = "",
                                )
                            )
                        )
                    }) { Text("前往生成") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { generatePackagePlan = null }) { Text("取消") }
                },
            )
        }
    }
}

