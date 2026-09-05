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

private fun loadProcessingPhoto(context: android.content.Context, planId: Int, photoId: Int): Bitmap {
    val cacheFile = java.io.File(context.filesDir, "processing-photos/$planId-$photoId")
    val cacheAge = System.currentTimeMillis() - cacheFile.lastModified()
    if (cacheFile.isFile && cacheAge in 0..PROCESSING_PHOTO_CACHE_TTL_MILLIS) {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
    }
    cacheFile.delete()

    val bytes = ApiClient.processingPhoto(planId, photoId)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("无法读取照片")
    runCatching {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(bytes)
    }
    return bitmap
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
    scrollState: ScrollState,
) {
    val listOwner = "processing"
    val showStore = user?.optInt("role", -1) == 0
    var mode by rememberRetainedListValue(listOwner, "mode") { "plans" } // "plans" | "pickup"
    var activeView by rememberRetainedListValue(listOwner, "activeView") { "today-all" }
    var pickupStatus by rememberRetainedListValue(listOwner, "pickupStatus") { 0 } // 0=待领取, 1=已领取
    var keyword by rememberRetainedListValue(listOwner, "keyword") { "" }
    var plans by rememberRetainedListValue(listOwner, "plans") { null as List<JSONObject>? }
    var pickupTasks by rememberRetainedListValue(listOwner, "pickupTasks") { null as List<PackageItem>? }
    var stores by rememberRetainedListValue(listOwner, "stores") { emptyList<JSONObject>() }
    var selectedStoreId by rememberRetainedListValue(listOwner, "selectedStoreId") { "" }
    var stats by rememberRetainedListValue(listOwner, "stats") { null as JSONObject? }
    var error by rememberRetainedListValue(listOwner, "error") { null as String? }
    var loading by remember { mutableStateOf(false) }
    var reload by rememberRetainedListValue(listOwner, "reload") { 0 }
    var page by rememberRetainedListValue(listOwner, "page") { 1 }
    var pages by rememberRetainedListValue(listOwner, "pages") { 1 }
    var loadedQueryKey by rememberRetainedListValue(listOwner, "loadedQueryKey") { null as String? }
    var storesLoaded by rememberRetainedListValue(listOwner, "storesLoaded") { false }
    var refreshing by remember { mutableStateOf(false) }
    var lastAutoKeyword by remember { mutableStateOf("") }

    // Dialog states
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
            keyword = value
            reload++
        }
    }

    LaunchedEffect(showStore) {
        if (storesLoaded) return@LaunchedEffect
        if (!showStore) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                storesLoaded = true
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
    }

    LaunchedEffect(reload, mode, activeView, pickupStatus, selectedStoreId, page) {
        val queryKey = listOf(reload, mode, activeView, pickupStatus, selectedStoreId, keyword, page).joinToString("|")
        val existingItems = if (mode == "plans") plans else pickupTasks
        if (loadedQueryKey == queryKey && existingItems != null) return@LaunchedEffect
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
                        pageSize = 10,
                    )
                    Triple<JSONObject, JSONObject?, JSONObject?>(summary, paged, null)
                } else {
                    val pickupData = ApiClient.pickupTasksPaged(
                        status = pickupStatus,
                        keyword = keyword.trim(),
                        storeId = storeIdInt,
                        page = page,
                        pageSize = 10,
                    )
                    Triple<JSONObject, JSONObject?, JSONObject?>(summary, null, pickupData)
                }
            }
        }.onSuccess { (summary, pagedPlans, pickupData) ->
            error = null
            stats = summary
            if (pagedPlans != null) {
                val list = pagedPlans.optJSONArray("list") ?: JSONArray()
                plans = (0 until list.length()).map { list.getJSONObject(it) }
                pages = pagedPlans.optJSONObject("pagination")?.optInt("pages", 1) ?: 1
            }
            if (pickupData != null) {
                val list = pickupData.optJSONArray("list") ?: JSONArray()
                pickupTasks = (0 until list.length()).map {
                    packageItem(list.getJSONObject(it))
                }
                pages = pickupData.optJSONObject("pagination")?.optInt("pages", 1)?.coerceAtLeast(1) ?: 1
            }
            loading = false
            refreshing = false
            loadedQueryKey = queryKey
        }.onFailure {
            if (it.isCancellation()) return@onFailure
            error = it.message ?: "加载加工数据失败"
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(keyword) {
        val term = keyword.trim()
        if (!shouldAutoSearchQuery(term)) {
            lastAutoKeyword = ""
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        if (keyword.trim() == term && lastAutoKeyword != term) {
            lastAutoKeyword = term
            page = 1
            reload++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (!refreshing) {
                    refreshing = true
                    ApiClient.clearResponseCache(context)
                    reload++
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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

        // Mode Switch: 加工计划 vs 领取列表
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedButton(
                label = "加工计划",
                selected = mode == "plans",
                onClick = { mode = "plans"; page = 1 },
                modifier = Modifier.weight(1f),
                centerLabel = true,
            )
            SegmentedButton(
                label = "领取列表",
                selected = mode == "pickup",
                onClick = { mode = "pickup"; page = 1 },
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
                // The API uses `tomorrow` for the next-day date filter. Using a
                // different key here leaves the server view unfiltered.
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
                                    .height(64.dp),
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
                                        error = null
                                        activeView = viewKey
                                        page = 1
                                    }
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
        } else {
            val pickupStatItems = listOf(
                "待领取" to (stat(stats, "pendingCount") to 0),
                "已领取" to (stat(stats, "pickedCount") to 1),
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
                            .height(64.dp),
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
                                error = null
                                pickupStatus = status
                                page = 1
                            }
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                keyword = it
                page = 1
                if (it.isBlank()) reload++
            },
            placeholder = "搜索顾客姓名、手机号或备注",
            onSearch = { page = 1; lastAutoKeyword = keyword.trim(); reload++ },
        )

        // Store chips stay compact and wrap naturally below the search field.
        if (showStore && stores.size > 1) {
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

        val currentPlans = plans
        val currentPickup = pickupTasks
        val hasExistingPlans = currentPlans != null
        val hasExistingPickup = currentPickup != null
        val hasExistingData = if (mode == "plans") hasExistingPlans else hasExistingPickup

        // Silent progress indicator during background refresh
        if (loading && hasExistingData) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Primary,
                trackColor = Primary.copy(alpha = 0.12f),
            )
            Spacer(Modifier.height(8.dp))
        }

        // Initial Loading (only show full spinner when there is no data at all yet)
        if (loading && !hasExistingData) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
        }

        // Processing Plans List
        if (mode == "plans" && (!loading || hasExistingPlans)) {
            if (currentPlans == null || currentPlans.isEmpty()) {
                if (!loading) AppEmptyState("暂无加工计划")
            } else {
                currentPlans.forEach { plan ->
                    key(plan.optInt("id")) {
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
                        onClick = { onNavigate(ScreenTarget.WorkflowOperation(plan, "", "open")) },
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

                        // Keep plan actions on one compact row without horizontal scrolling.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 关联处方查看
                            val prescriptionId = plan.optInt("prescriptionId", plan.optJSONObject("prescription")?.optInt("id", 0) ?: 0)
                            if (prescriptionId > 0) {
                                OutlinedButton(
                                    onClick = { onNavigate(ScreenTarget.PrescriptionDetail(prescriptionId)) },
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("处方", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
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
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("开始加工", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { ApiClient.delayPlan(plan.optInt("id"), 1) } }
                                                .onSuccess { reload++ }
                                                .onFailure { error = it.message ?: "延期失败" }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("延期明天", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }

                            if (status == 1) { // 加工中：快捷扫码（直接扫码，默认下一步）
                                Button(
                                    onClick = {
                                        quickScanTargetPlan = plan
                                        planQuickScanLauncher.launch(Intent(context, ScannerActivity::class.java))
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("扫码", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }

                            if (status == 2 && !packageCreated) { // 完成但未生成包裹
                                Button(
                                    onClick = { generatePackagePlan = plan },
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("生成包裹", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }

                            // 完成、待领取、已领取和已取消的计划不再开放完整编辑。
                            if (status in 0..1) {
                                OutlinedButton(
                                    onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(plan)) },
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("编辑", fontSize = 11.sp, maxLines = 1, softWrap = false)
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
                                    modifier = Modifier.weight(1f).height(32.dp).defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                ) {
                                    Text("取消", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
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

        // Pickup List
        if (mode == "pickup" && (!loading || hasExistingPickup)) {
            if (currentPickup == null || currentPickup.isEmpty()) {
                if (!loading) AppEmptyState(if (pickupStatus == 0) "暂无待领取记录" else "暂无已领取记录")
            } else {
                currentPickup.forEach { item ->
                    key(item.id) {
                        PackageSummaryCard(
                            item = item,
                            showStore = showStore,
                            modifier = Modifier.padding(bottom = 12.dp),
                            onClick = { onNavigate(ScreenTarget.PackageDetail(item)) },
                            onVerify = {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(item.code, 0, "") } }
                                        .onSuccess { reload++ }
                                        .onFailure { error = it.message ?: "核销失败" }
                                }
                            },
                        )
                    }
                }
                if (pages > 1) {
                    AppPagination(page = page, pages = pages, onPrev = { if (page > 1) page-- }, onNext = { if (page < pages) page++ })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
                TextButton(
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
                                .onSuccess { reload++ }
                                .onFailure { error = it.message ?: "生成包裹失败" }
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
                onNavigate(ScreenTarget.WorkflowOperation(p, "", "open"))
            },
            onEquipmentScanned = {
                quickScanPromptData = null
            },
            onNavigatePlan = { targetPlan ->
                quickScanPromptData = null
                onNavigate(ScreenTarget.WorkflowOperation(targetPlan, "", "open"))
            },
        )
    }

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProcessingPlanFormScreen(
    initial: JSONObject,
    onSaved: () -> Unit,
) {
    val isEdit = initial.has("id") && initial.optInt("id") > 0
    val editLocked = isEdit && initial.optInt("status") !in 0..1
    val initialPrescription = initial.optJSONObject("prescription")
    var prescriptionId by remember(initial) {
        mutableStateOf(initial.optInt("prescriptionId", initialPrescription?.optInt("id", 0) ?: 0))
    }
    var processTypeId by remember(initial) {
        mutableStateOf(initial.optInt("processTypeId", initial.optJSONObject("processType")?.optInt("id", 0) ?: 0))
    }
    var prescriptionKeyword by remember { mutableStateOf("") }
    var prescriptions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var prescriptionOptionsVisible by remember { mutableStateOf(false) }
    var prescriptionLoading by remember { mutableStateOf(false) }
    var processTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var notifyTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var totalDose by remember(initial) { mutableStateOf(initial.optInt("totalDose", 1).toString()) }
    var bagCount by remember(initial) { mutableStateOf(initial.optInt("bagCount", 2).toString()) }
    var volumeMl by remember(initial) { mutableStateOf(initial.optInt("volumeMl", 200).toString()) }
    var usageMethod by remember(initial) { mutableStateOf(initial.displayField("usageMethod", "")) }
    var pickupMethod by remember(initial) { mutableStateOf(initial.optInt("pickupMethod", 0)) }
    var expressAddress by remember(initial) { mutableStateOf(initial.displayField("expressAddress", "")) }
    var scheduleType by remember(initial) { mutableStateOf(initial.optInt("scheduleType", 1)) }
    var processDate by remember(initial) {
        mutableStateOf(serverDateOnly(initial.opt("processDate"), "").ifBlank { serverToday().toString() })
    }
    var priority by remember(initial) { mutableStateOf(initial.optInt("priority", 0)) }
    var notifyType by remember(initial) { mutableStateOf(initial.optInt("notifyType", 0)) }
    var notifyStatus by remember(initial) { mutableStateOf(initial.optInt("notifyStatus", 0)) }
    var paymentStatus by remember(initial) { mutableStateOf(initial.optInt("paymentStatus", 1)) }
    var processRemark by remember(initial) { mutableStateOf(initial.displayField("processRemark", "")) }
    var remark by remember(initial) { mutableStateOf(initial.displayField("remark", "")) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(if (editLocked) "该加工计划已完成或进入领取流程，不能编辑" else null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val prescriptionData = if (isEdit) {
                    JSONArray().apply {
                        initialPrescription?.let { put(it) }
                            ?: prescriptionId.takeIf { it > 0 }?.let { put(ApiClient.prescriptionDetail(it)) }
                    }
                } else JSONArray()
                Triple(prescriptionData, ApiClient.dictionaries("ProcessType"), ApiClient.dictionaries("NotifyType"))
            }
        }.onSuccess { (prescriptionData, processData, notifyData) ->
            prescriptions = (0 until prescriptionData.length()).map { prescriptionData.getJSONObject(it) }
            processTypes = (0 until processData.length()).map { processData.getJSONObject(it) }
            notifyTypes = (0 until notifyData.length()).map { notifyData.getJSONObject(it) }
            if (processTypeId == 0 && processTypes.isNotEmpty()) processTypeId = processTypes.first().optInt("id")
            if (notifyType == 0) notifyType = notifyTypes.firstOrNull { it.displayField("code", "") == "NONE" }?.optInt("id", 0) ?: 0
            loading = false
        }.onFailure {
            error = it.message ?: "加载加工计划选项失败"
            loading = false
        }
    }

    LaunchedEffect(prescriptionKeyword, prescriptionOptionsVisible, isEdit) {
        if (isEdit || !prescriptionOptionsVisible) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        prescriptionLoading = true
        runCatching {
            withContext(Dispatchers.IO) {
                val keyword = prescriptionKeyword.trim()
                ApiClient.prescriptionsPaged(
                    keyword = keyword,
                    page = 1,
                    pageSize = 50,
                    createdDate = keyword.takeIf { it.isBlank() }?.let { serverToday().toString() },
                )
            }
        }.onSuccess { data ->
            val list = data.optJSONArray("list") ?: JSONArray()
            prescriptions = (0 until list.length()).map { list.getJSONObject(it) }
        }.onFailure {
            error = it.message ?: "加载处方失败"
        }
        prescriptionLoading = false
    }

    val selectedPrescription = prescriptions.firstOrNull { it.optInt("id") == prescriptionId } ?: initialPrescription
    val selectedProcessType = processTypes.firstOrNull { it.optInt("id") == processTypeId }
    val isDecoction = selectedProcessType?.displayField("code", "") == "DECOCTION" || selectedProcessType?.displayField("name", "") == "代煎"
    val visiblePrescriptions = prescriptions.take(50)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        AppCard {
            SectionHeader(if (isEdit) "编辑加工计划" else "新建加工计划", "按照 Web 管理端字段填写加工任务")
            Spacer(Modifier.height(14.dp))
            Text("选择处方 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            if (selectedPrescription != null) {
                Surface(color = PrimarySoft, shape = FieldShape, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "已选：${selectedPrescription.displayField("prescriptionNo", "处方")} · ${selectedPrescription.displayField("customerName", "顾客")}",
                        color = PrimaryDark,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (!isEdit) {
                OutlinedTextField(
                    value = prescriptionKeyword,
                    onValueChange = { prescriptionKeyword = it },
                    label = { Text("搜索处方编号、姓名或手机号") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) prescriptionOptionsVisible = true },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { prescriptionOptionsVisible = true }),
                    shape = FieldShape,
                )
                if (prescriptionOptionsVisible) {
                    Spacer(Modifier.height(8.dp))
                    if (prescriptionLoading) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp))
                        }
                    } else if (visiblePrescriptions.isEmpty()) {
                        Text(
                            if (prescriptionKeyword.isBlank()) "今日暂无可加工处方" else "暂无匹配处方",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    } else {
                        Text(
                            if (prescriptionKeyword.isBlank()) "今日创建的处方" else "搜索结果",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            visiblePrescriptions.forEach { item ->
                                Surface(
                                    onClick = {
                                        prescriptionId = item.optInt("id")
                                        prescriptionOptionsVisible = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = FieldShape,
                                    color = if (prescriptionId == item.optInt("id")) PrimarySoft else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(0.5.dp, CardBorderColor),
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                        Text(
                                            "${item.displayField("customerName", "顾客")} · ${item.displayField("prescriptionNo", "处方")}",
                                            color = if (prescriptionId == item.optInt("id")) PrimaryDark else Ink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            "${item.displayField("phone", "未填写手机号")} · ${item.optJSONObject("doctor")?.displayField("name", "未指定医生") ?: "未指定医生"}",
                                            color = Muted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("加工方式 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                processTypes.forEach { item ->
                    SegmentedButton(item.displayField("name", "加工"), processTypeId == item.optInt("id"), { processTypeId = item.optInt("id") })
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = totalDose,
                    onValueChange = { totalDose = it.filter(Char::isDigit) },
                    label = { Text("剂数 *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = FieldShape,
                )
                if (isDecoction) {
                    OutlinedTextField(
                        value = bagCount,
                        onValueChange = { bagCount = it.filter(Char::isDigit) },
                        label = { Text("袋数 *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = FieldShape,
                    )
                }
            }
            if (isDecoction) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = volumeMl,
                    onValueChange = { volumeMl = it.filter(Char::isDigit) },
                    label = { Text("每袋毫升数 *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("取货方式 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (value, label) ->
                    SegmentedButton(label, pickupMethod == value, { pickupMethod = value })
                }
            }
            if (pickupMethod == 1 || pickupMethod == 2) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = expressAddress,
                    onValueChange = { expressAddress = it.take(500) },
                    label = { Text("配送地址（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("调度设置", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedButton("指定日期", scheduleType == 1, { scheduleType = 1 })
                SegmentedButton("等待通知", scheduleType == 2, { scheduleType = 2 })
                SegmentedButton("普通", priority == 0, { priority = 0 })
                SegmentedButton("加急", priority == 1, { priority = 1 })
            }
            if (scheduleType == 1) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = processDate,
                    onValueChange = { processDate = it },
                    label = { Text("计划开工日期（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("提醒与收费", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                notifyTypes.forEach { item ->
                    SegmentedButton(item.displayField("name", "不提醒"), notifyType == item.optInt("id"), { notifyType = item.optInt("id") })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedButton("未通知", notifyStatus == 0, { notifyStatus = 0 })
                SegmentedButton("已通知", notifyStatus == 1, { notifyStatus = 1 })
                SegmentedButton("未收费", paymentStatus == 0, { paymentStatus = 0 })
                SegmentedButton("已收费", paymentStatus == 1, { paymentStatus = 1 })
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = usageMethod,
                onValueChange = { usageMethod = it.take(200) },
                label = { Text("服用方法（可选）") },
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = processRemark,
                onValueChange = { processRemark = it.take(500) },
                label = { Text("加工备注") },
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it.take(500) },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )
        }

        if (loading) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(30.dp)) }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !editLocked && !busy && !loading && prescriptionId > 0 && processTypeId > 0 && totalDose.toIntOrNull()?.let { it > 0 } == true && (!isDecoction || (bagCount.toIntOrNull()?.let { it > 0 } == true && volumeMl.toIntOrNull()?.let { it > 0 } == true)),
            onClick = {
                if (editLocked) return@Button
                busy = true
                error = null
                val payload = JSONObject()
                    .put("prescriptionId", prescriptionId)
                    .put("processTypeId", processTypeId)
                    .put("totalDose", totalDose.toIntOrNull() ?: 1)
                    .put("bagCount", if (isDecoction) bagCount.toIntOrNull() ?: 1 else JSONObject.NULL)
                    .put("volumeMl", if (isDecoction) volumeMl.toIntOrNull() ?: 200 else JSONObject.NULL)
                    .put("usageMethod", usageMethod.trim())
                    .put("pickupMethod", pickupMethod)
                    .put("expressAddress", expressAddress.trim())
                    .put("scheduleType", scheduleType)
                    .put("processDate", if (scheduleType == 1) processDate.trim() else JSONObject.NULL)
                    .put("priority", priority)
                    .put("notifyType", if (notifyType > 0) notifyType else JSONObject.NULL)
                    .put("notifyStatus", notifyStatus)
                    .put("paymentStatus", paymentStatus)
                    .put("processRemark", processRemark.trim())
                    .put("remark", remark.trim())
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (isEdit) ApiClient.updatePlan(initial.optInt("id"), payload) else ApiClient.createPlan(payload)
                        }
                    }.onSuccess { onSaved() }.onFailure { error = it.message ?: "保存加工计划失败" }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(if (editLocked) "该计划不可编辑" else if (busy) "保存中..." else if (isEdit) "保存修改" else "创建加工计划", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
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
        mutableStateOf(serverDateOnly(initial?.opt("scheduledDate"), "").ifBlank { serverToday().toString() })
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
internal fun OccupyingPlanCard(
    plan: JSONObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planId = plan.optInt("id")
    val customerName = plan.displayField("customerName", "").ifBlank {
        plan.optJSONObject("prescription")?.displayField("customerName") ?: "患者"
    }
    val planCode = plan.displayField("planCode", "计划 #$planId")
    val processTypeName = plan.displayField("processTypeName", "").ifBlank {
        plan.optJSONObject("processType")?.displayField("name", "") ?: "加工计划"
    }
    val stage = plan.optInt("stage", 0)
    val portionNo = plan.optInt("portionNo", 0)
    val stageText = when (stage) {
        3 -> if (portionNo > 0) "第${portionNo}组浸泡中" else "浸泡中"
        4 -> if (portionNo > 0) "第${portionNo}组煎煮中" else "煎煮中"
        5 -> "打包中"
        else -> ""
    }

    Surface(
        color = Color(0xFFEFF6FF),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "设备占用计划",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1D4ED8),
                    )
                    if (stageText.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = stageText,
                                color = Color(0xFF1E40AF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$customerName · $processTypeName ($planCode)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "👉 点击直达该计划工序详情",
                    fontSize = 11.sp,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "查看占用计划",
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WorkflowOperationScreen(
    plan: JSONObject,
    onNavigatePrescription: ((Int) -> Unit)? = null,
    onPlanStatusChanged: (() -> Unit)? = null,
    onNavigatePlan: ((JSONObject) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workflow by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var occupyingPlanTarget by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewPhotoId by remember { mutableStateOf(0) }
    var photoDeleting by remember { mutableStateOf(false) }

    // Exception handling state
    var exceptionTargetUsage by remember { mutableStateOf<JSONObject?>(null) }
    var exceptionDialogType by remember { mutableStateOf<Int?>(null) } // 1: 撤销误扫, 2: 故障换机
    var exceptionReason by remember { mutableStateOf("") }
    var exceptionEquipmentCode by remember { mutableStateOf("") }

    // Active scanning state (which action triggered scanner: "soaking", "decoction_$portion", "packaging_$usageId", "fault_swap")
    var scanningAction by remember { mutableStateOf<String?>(null) }

    // Completion & Package generation dialog states
    var showFinishConfirmDialog by remember { mutableStateOf(false) }
    var createPackageImmediately by remember { mutableStateOf(true) } // 默认推荐：立即生成待取包裹
    var showGeneratePackageDialog by remember { mutableStateOf(false) }

    fun createPhotoUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tcm_dispensing_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TCM")
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    val initialPlanStatus = plan.optInt("status")
    fun reload() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ApiClient.processingWorkflow(plan.optInt("id")) } }
                .onSuccess {
                    workflow = it
                    error = null
                    occupyingPlanTarget = null
                    val s = it.optInt("status", -1)
                    if (s != -1 && s != initialPlanStatus) {
                        onPlanStatusChanged?.invoke()
                    }
                }
                .onFailure {
                    if (it.isCancellation()) return@onFailure
                    error = it.message ?: "加载工序失败"
                }
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApiClient.completeDispensing(
                        plan.optInt("id"),
                        "dispensing_${System.currentTimeMillis()}.jpg",
                        "image/jpeg",
                        readProcessingPhoto(context, uri),
                    )
                }
            }.onSuccess { reload() }.onFailure { error = it.message ?: "照片上传失败" }
            busy = false
        }
    }

    val photoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingPhotoUri = createPhotoUri()
            pendingPhotoUri?.let { photoLauncher.launch(it) } ?: run { error = "无法打开相机" }
        } else {
            error = "请允许使用相机后再拍照"
        }
    }

    fun launchPhotoCapture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pendingPhotoUri = createPhotoUri()
            pendingPhotoUri?.let { photoLauncher.launch(it) } ?: run { error = "无法打开相机" }
        } else {
            photoPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanned = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        val action = scanningAction
        scanningAction = null
        if (result.resultCode == Activity.RESULT_OK && scanned.isNotBlank() && action != null) {
            when {
                action == "soaking" -> {
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val currentUsages = workflow?.optJSONArray("equipmentUsages")?.let { arr ->
                                    (0 until arr.length()).map { arr.getJSONObject(it) }
                                }.orEmpty()
                                val usedPortions = currentUsages.filter { it.optInt("stage") == 3 }.map { it.optInt("portionNo", 1) }
                                val portionNo = if (usedPortions.isNotEmpty()) (usedPortions.maxOrNull() ?: 0) + 1 else 1
                                ApiClient.startEquipmentUsage(
                                    plan.optInt("id"),
                                    JSONObject()
                                        .put("stage", 3)
                                        .put("portionNo", portionNo)
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            error = null
                            occupyingPlanTarget = null
                            reload()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫码浸泡失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
                action.startsWith("decoction_") -> {
                    val portionNo = action.removePrefix("decoction_").toIntOrNull() ?: 1
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.startEquipmentUsage(
                                    plan.optInt("id"),
                                    JSONObject()
                                        .put("stage", 4)
                                        .put("portionNo", portionNo)
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            error = null
                            occupyingPlanTarget = null
                            reload()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫锅煎煮失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
                action.startsWith("packaging_") -> {
                    val usageId = action.removePrefix("packaging_").toIntOrNull() ?: 0
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.startPackaging(
                                    plan.optInt("id"),
                                    usageId,
                                    JSONObject()
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            error = null
                            occupyingPlanTarget = null
                            reload()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫包装机失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
                action == "fault_swap" -> {
                    exceptionEquipmentCode = scanned
                }
            }
        }
    }

    LaunchedEffect(plan) { reload() }

    val detail = workflow
    val prescription = detail?.optJSONObject("prescription") ?: plan.optJSONObject("prescription")
    val prescriptionId = plan.optInt("prescriptionId", prescription?.optInt("id", 0) ?: 0)
    val customerName = detail?.displayField("customerName", "").orEmpty().ifBlank {
        plan.displayField("customerName", "").ifBlank { prescription?.displayField("customerName") ?: "顾客" }
    }
    val planCode = detail?.displayField("planCode", "").orEmpty().ifBlank { plan.displayField("planCode", "加工计划") }
    val batchNo = detail?.optInt("batchNo", plan.optInt("batchNo", 1)) ?: 1
    val totalDose = detail?.optInt("totalDose", plan.optInt("totalDose", 0)) ?: 0
    val bagCount = detail?.optInt("bagCount", plan.optInt("bagCount", 0)) ?: 0
    val volumeMl = detail?.optInt("volumeMl", plan.optInt("volumeMl", 0)) ?: 0
    val processType = detail?.optJSONObject("processType") ?: plan.optJSONObject("processType")
    val processTypeCode = processType?.displayField("code", "") ?: plan.displayField("processTypeCode", "")
    val processTypeName = processType?.displayField("name", "") ?: plan.displayField("processTypeName", "代煎")
    val isDecoction = detail?.optBoolean("isDecoction") == true || processTypeCode == "DECOCTION" || processTypeName.contains("煎")

    val usages = detail?.optJSONArray("equipmentUsages")?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }.orEmpty()
    val activeSoakings = usages.filter { it.optInt("stage") == 3 && it.optInt("status") == 1 }
    val activeDecoctions = usages.filter { it.optInt("stage") == 4 && it.optInt("status") == 1 }
    val activePackagings = usages.filter { it.optInt("stage") == 5 && it.optInt("status") == 1 }
    val allUsageRecords = usages.sortedBy { it.displayField("startedAt", "") }

    val photos = detail?.optJSONArray("photos")
    val photoCount = photos?.length() ?: 0
    val status = detail?.optInt("status", plan.optInt("status")) ?: plan.optInt("status")
    val currentStage = detail?.optInt("currentStage", 1) ?: 1
    val canUpload = status == 1 && currentStage in listOf(1, 2) && photoCount < 3
    val canFinish = detail?.optBoolean("canCompleteWorkflow") == true || detail?.optBoolean("canFinalizeWorkflow") == true
    val exceptions = detail?.optJSONArray("workflowExceptions")?.let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.orEmpty()
    val formatWorkflowTime: (String) -> String = { value ->
        serverDateTime(value)
    }
    val latestStageCompletedAt: (Int) -> String = { stage ->
        usages
            .filter { it.optInt("stage") == stage && it.optInt("status") == 2 }
            .maxByOrNull { it.displayField("endedAt", "") }
            ?.displayField("endedAt", "")
            .orEmpty()
            .let(formatWorkflowTime)
    }
    val earliestStageStartedAt: (Int) -> String = { stage ->
        usages
            .filter { it.optInt("stage") == stage && it.optInt("status") == 1 }
            .minByOrNull { it.displayField("startedAt", "") }
            ?.displayField("startedAt", "")
            .orEmpty()
            .let(formatWorkflowTime)
    }
    val latestDispensingPhotoAt = (0 until (photos?.length() ?: 0))
        .mapNotNull { photos?.optJSONObject(it)?.displayField("createdAt", "")?.takeIf(String::isNotBlank) }
        .maxOrNull()
        .orEmpty()
    val dispensingCompletedAt = detail?.displayField("dispensingCompletedAt", "")
        .orEmpty()
        .ifBlank { latestDispensingPhotoAt }
        .let(formatWorkflowTime)
    val dispensingStartedAt = (detail?.displayField("startDate", "")
        .orEmpty()
        .ifBlank { plan.displayField("startDate", "") })
        .let(formatWorkflowTime)
    val soakingCompletedAt = latestStageCompletedAt(3)
    val decoctionCompletedAt = latestStageCompletedAt(4)
    val packagingCompletedAt = latestStageCompletedAt(5)
    val soakingStartedAt = earliestStageStartedAt(3)
    val decoctionStartedAt = earliestStageStartedAt(4)
    val packagingStartedAt = earliestStageStartedAt(5)
    val dispensingState = when {
        status == 2 || photoCount > 0 -> "已完成调配"
        status == 1 -> "调配中"
        else -> "待调配"
    }
    val soakingState = when {
        status == 2 -> "浸泡已完成"
        activeSoakings.isNotEmpty() -> "浸泡中"
        else -> "等待浸泡"
    }
    val decoctionState = when {
        status == 2 -> "煎煮已完成"
        activeDecoctions.isNotEmpty() -> "煎煮中"
        activeSoakings.isNotEmpty() -> "等待浸泡完成"
        else -> "等待煎煮"
    }
    val packagingState = when {
        status == 2 -> "全部分组已打包"
        activePackagings.isNotEmpty() -> "打包中"
        activeDecoctions.isNotEmpty() -> "等待煎煮完成"
        else -> "等待打包"
    }
    val prescriptionCardClick: (() -> Unit)? = if (prescriptionId > 0) {
        onNavigatePrescription?.let { callback -> { callback(prescriptionId) } }
    } else null
    val isDispensingCompleted = status == 2 || photoCount > 0
    val isWorkflowCompleted = status == 2
    val dispensingTimeLabel = when {
        isDispensingCompleted && dispensingCompletedAt.isNotBlank() -> dispensingCompletedAt
        status == 1 && dispensingStartedAt.isNotBlank() -> dispensingStartedAt
        else -> ""
    }
    val soakingTimeLabel = when {
        isWorkflowCompleted && soakingCompletedAt.isNotBlank() -> soakingCompletedAt
        activeSoakings.isNotEmpty() && soakingStartedAt.isNotBlank() -> soakingStartedAt
        else -> ""
    }
    val decoctionTimeLabel = when {
        isWorkflowCompleted && decoctionCompletedAt.isNotBlank() -> decoctionCompletedAt
        activeDecoctions.isNotEmpty() && decoctionStartedAt.isNotBlank() -> decoctionStartedAt
        else -> ""
    }
    val packagingTimeLabel = when {
        isWorkflowCompleted && packagingCompletedAt.isNotBlank() -> packagingCompletedAt
        activePackagings.isNotEmpty() && packagingStartedAt.isNotBlank() -> packagingStartedAt
        else -> ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Top Summary Card - 顶部显示加工方式、剂数、代煎显示袋数毫升数
        AppCard(onClick = prescriptionCardClick) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(customerName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    Text("$planCode · 第 $batchNo 批", color = Muted, fontSize = 12.sp)
                }
                StatusPill(processingStageLabel(currentStage))
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加工方式", color = Muted, fontSize = 11.sp)
                        Text(processTypeName, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("处方剂数", color = Muted, fontSize = 11.sp)
                        Text("$totalDose 剂", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                    }
                }
                if (isDecoction) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("代煎规格", color = Muted, fontSize = 11.sp)
                            Text("$bagCount 袋 / $volumeMl ml", fontWeight = FontWeight.SemiBold, color = PrimaryDark, fontSize = 13.sp)
                        }
                    }
                }
            }

        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Surface(color = DangerSoft, shape = FieldShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(error!!, color = Danger, fontSize = 13.sp)
                    occupyingPlanTarget?.let { occPlan ->
                        Spacer(Modifier.height(8.dp))
                        OccupyingPlanCard(
                            plan = occPlan,
                            onClick = {
                                onNavigatePlan?.invoke(occPlan)
                            },
                        )
                    }
                }
            }
        }

        if (status == 0) {
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 1) } }
                            .onSuccess {
                                onPlanStatusChanged?.invoke()
                                reload()
                            }.onFailure { error = it.message ?: "开始调配失败" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) { Text("开始调配", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }

        // STEP 1: 调配
        Spacer(Modifier.height(12.dp))
        AppCard {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PrimarySoft,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("1", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("调配", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                }
                if (dispensingTimeLabel.isNotBlank()) {
                    Text(
                        dispensingTimeLabel,
                        color = Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(
                    dispensingState,
                    color = when {
                        isDispensingCompleted -> Success
                        status == 1 -> Primary
                        else -> Muted
                    },
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("称量调配完成后拍照留存凭证", color = Muted, fontSize = 12.sp)

            if (photoCount > 0 && photos != null) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (0 until photos.length()).forEach { index ->
                        val photoId = photos.optJSONObject(index)?.optInt("id", 0) ?: 0
                        if (photoId > 0) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                loadProcessingPhoto(context, plan.optInt("id"), photoId)
                                            }
                                        }.onSuccess {
                                            previewBitmap = it
                                            previewPhotoId = photoId
                                        }.onFailure { error = it.message ?: "照片加载失败" }
                                        busy = false
                                    }
                                },
                                shape = FieldShape,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) { Text("查看照片 ${index + 1}", fontSize = 12.sp) }

                            if (status == 1 && currentStage in listOf(1, 2)) {
                                OutlinedButton(
                                    enabled = !photoDeleting && !busy,
                                    onClick = {
                                        photoDeleting = true
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    ApiClient.deleteProcessingPhoto(plan.optInt("id"), photoId)
                                                    ApiClient.clearProcessingPhotoCache(context, plan.optInt("id"), photoId)
                                                }
                                            }
                                                .onSuccess { reload() }
                                                .onFailure { error = it.message ?: "照片删除失败" }
                                            photoDeleting = false
                                        }
                                    },
                                    shape = FieldShape,
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                ) { Text("删除", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }

            if (canUpload) {
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !busy,
                    onClick = ::launchPhotoCapture,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = FieldShape,
                ) { Text(if (photoCount > 0) "补充调配照片" else "拍照并完成调配", fontSize = 13.sp) }
            }
        }

        // STEP 2: 浸泡（代煎流程）
        if (isDecoction) {
            Spacer(Modifier.height(12.dp))
            AppCard {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimarySoft,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("2", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("浸泡", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    }
                    if (soakingTimeLabel.isNotBlank()) {
                        Text(
                            soakingTimeLabel,
                            color = Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        soakingState,
                        color = when {
                            isWorkflowCompleted -> Success
                            activeSoakings.isNotEmpty() -> Primary
                            else -> Muted
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (activeSoakings.isNotEmpty()) {
                    activeSoakings.forEach { item ->
                        val equipment = item.optJSONObject("equipment")?.displayField("name", "浸泡设备") ?: "浸泡设备"
                        val operator = item.optJSONObject("operator")?.let { op ->
                            op.displayField("nickname", "").ifBlank { op.displayField("name", op.displayField("phone", "-")) }
                        } ?: "-"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 ${item.optInt("portionNo", 1)} 组 · $equipment", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    Text("操作人：$operator · 开始：${serverDateTime(item.opt("startedAt"))}", color = Muted, fontSize = 11.sp)
                                }
                                if (status == 1) {
                                    OutlinedButton(
                                        onClick = {
                                            exceptionTargetUsage = item
                                            exceptionReason = ""
                                            exceptionEquipmentCode = ""
                                            exceptionDialogType = 0 // show selection sheet
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = FieldShape,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning),
                                    ) { Text("异常处理", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }

                if (status == 1) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            scanningAction = "soaking"
                            scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = FieldShape,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("扫码添加浸泡桶", fontSize = 13.sp)
                    }
                }
            }

            // STEP 3: 煎煮
            Spacer(Modifier.height(12.dp))
            AppCard {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimarySoft,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("3", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("煎煮", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    }
                    if (decoctionTimeLabel.isNotBlank()) {
                        Text(
                            decoctionTimeLabel,
                            color = Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        decoctionState,
                        color = when {
                            isWorkflowCompleted -> Success
                            activeDecoctions.isNotEmpty() -> Primary
                            else -> Muted
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Waiting soaking portions ready to start decoction
                if (status == 1 && activeSoakings.isNotEmpty()) {
                    activeSoakings.forEach { item ->
                        val equipment = item.optJSONObject("equipment")?.displayField("name", "浸泡桶") ?: "浸泡桶"
                        val portion = item.optInt("portionNo", 1)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 $portion 组 · 等待转煎煮", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    Text("浸泡桶：$equipment", color = Muted, fontSize = 11.sp)
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scanningAction = "decoction_$portion"
                                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = FieldShape,
                                ) { Text("扫锅煎煮", fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // Active decoctions
                if (activeDecoctions.isNotEmpty()) {
                    activeDecoctions.forEach { item ->
                        val equipment = item.optJSONObject("equipment")?.displayField("name", "煎药机") ?: "煎药机"
                        val operator = item.optJSONObject("operator")?.let { op ->
                            op.displayField("nickname", "").ifBlank { op.displayField("name", op.displayField("phone", "-")) }
                        } ?: "-"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 ${item.optInt("portionNo", 1)} 组 · $equipment · 煎煮中", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    Text("操作人：$operator · 开始：${serverDateTime(item.opt("startedAt"))}", color = Muted, fontSize = 11.sp)
                                }
                                if (status == 1) {
                                    OutlinedButton(
                                        onClick = {
                                            exceptionTargetUsage = item
                                            exceptionReason = ""
                                            exceptionEquipmentCode = ""
                                            exceptionDialogType = 0
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = FieldShape,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning),
                                    ) { Text("异常处理", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
                if (activeSoakings.isEmpty() && activeDecoctions.isEmpty() && status != 2) {
                    Text("暂无进行中的煎煮", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            // STEP 4: 打包
            Spacer(Modifier.height(12.dp))
            AppCard {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimarySoft,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("4", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("打包", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    }
                    if (packagingTimeLabel.isNotBlank()) {
                        Text(
                            packagingTimeLabel,
                            color = Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        packagingState,
                        color = when {
                            isWorkflowCompleted -> Success
                            activePackagings.isNotEmpty() -> Primary
                            else -> Muted
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Waiting decoction portions ready to start packaging
                if (status == 1 && activeDecoctions.isNotEmpty()) {
                    activeDecoctions.forEach { item ->
                        val equipment = item.optJSONObject("equipment")?.displayField("name", "煎药机") ?: "煎药机"
                        val usageId = item.optInt("id")
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 ${item.optInt("portionNo", 1)} 组 · 等待打包", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    Text("煎药机：$equipment", color = Muted, fontSize = 11.sp)
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scanningAction = "packaging_$usageId"
                                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = FieldShape,
                                ) { Text("扫包装机打包", fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // Active packagings
                if (activePackagings.isNotEmpty()) {
                    activePackagings.forEach { item ->
                        val equipment = item.optJSONObject("equipment")?.displayField("name", "包装机") ?: "包装机"
                        val operator = item.optJSONObject("operator")?.let { op ->
                            op.displayField("nickname", "").ifBlank { op.displayField("name", op.displayField("phone", "-")) }
                        } ?: "-"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 ${item.optInt("portionNo", 1)} 组 · $equipment · 打包中", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    Text("操作人：$operator · 开始：${serverDateTime(item.opt("startedAt"))}", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                if (activeDecoctions.isEmpty() && activePackagings.isEmpty() && status != 2) {
                    Text("等待煎煮完成后扫描包装机", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        // 设备工序记录（展示进行中与历史记录）
        if (allUsageRecords.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("设备工序记录", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    Text("共 ${allUsageRecords.size} 条记录", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                allUsageRecords.forEach { item ->
                    val stage = item.optInt("stage")
                    val stageText = when (stage) { 3 -> "浸泡"; 4 -> "煎煮"; 5 -> "打包"; else -> "工序" }
                    val equipment = item.optJSONObject("equipment")?.displayField("name", "设备") ?: "设备"
                    val statusCode = item.optInt("status")
                    val isRunning = statusCode == 1
                    val isSuccess = statusCode == 2
                    val isVoid = statusCode == 3
                    val statusText = when {
                        isRunning -> "进行中"
                        isSuccess -> "已完成"
                        isVoid -> "已作废"
                        else -> "未知"
                    }
                    val operator = item.optJSONObject("operator")?.let { op ->
                        op.displayField("nickname", "").ifBlank { op.displayField("name", op.displayField("phone", "-")) }
                    } ?: "-"
                    val voidReason = item.displayField("voidReason", "")
                    val startTime = serverDateTime(item.opt("startedAt"))
                    val endTime = serverDateTime(item.opt("endedAt"), "").ifBlank { if (isRunning) "进行中..." else "-" }

                    Surface(
                        color = if (isRunning) PrimarySoft else MaterialTheme.colorScheme.surfaceVariant,
                        shape = FieldShape,
                        border = BorderStroke(1.dp, if (isRunning) Primary else CardBorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Column(Modifier.padding(9.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isRunning) Primary else if (isSuccess) Success else Danger,
                                        shape = RoundedCornerShape(3.dp),
                                        modifier = Modifier.size(width = 3.5.dp, height = 14.dp),
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "$stageText · 第 ${item.optInt("portionNo", 1)} 组 · $equipment",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink,
                                        fontSize = 13.5.sp,
                                    )
                                }
                                StatusPill(statusText)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("时段：$startTime → $endTime", color = Muted, fontSize = 11.5.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "操作人：$operator",
                                    color = Muted,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (isRunning) "已用时 " + processingDuration(item.displayField("startedAt", ""), "")
                                    else "用时 " + processingDuration(item.displayField("startedAt", ""), item.displayField("endedAt", "")),
                                    color = if (isRunning) PrimaryDark else Ink,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (voidReason.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text("作废原因：$voidReason", color = Danger, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // 异常处理记录
        if (exceptions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppCard {
                Text("异常处理记录", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                exceptions.forEach { ex ->
                    val typeText = when (ex.optInt("type")) { 1 -> "误扫撤销"; 2 -> "设备故障换机"; else -> "人工补录" }
                    val operator = ex.optJSONObject("operator")?.let { op ->
                        op.displayField("nickname", "").ifBlank { op.displayField("name", op.displayField("phone", "-")) }
                    } ?: "-"
                    Surface(
                        color = DangerSoft.copy(alpha = 0.5f),
                        shape = FieldShape,
                        border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("$typeText · ${ex.displayField("reason", "无原因说明")}", fontWeight = FontWeight.SemiBold, color = Danger, fontSize = 12.5.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("操作人：$operator · 时间：${serverDateTime(ex.opt("createdAt"))}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Finish Bar
        if (status == 1 && canFinish) {
            Spacer(Modifier.height(14.dp))
            Button(
                enabled = !busy,
                onClick = {
                    createPackageImmediately = true
                    showFinishConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Success),
                shape = FieldShape,
            ) { Text("完成加工", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }

        val packageCreated = detail?.optBoolean("packageCreated") == true || detail?.optJSONObject("package") != null || status == 3 || status == 4
        if (status == 2 && !packageCreated) {
            Spacer(Modifier.height(14.dp))
            Button(
                enabled = !busy,
                onClick = {
                    showGeneratePackageDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = FieldShape,
            ) { Text("生成待取包裹", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }

        Spacer(Modifier.height(20.dp))
    }

    // Full-screen photo preview with pinch-to-zoom.
    previewBitmap?.let { bitmap ->
        var previewScale by remember(previewPhotoId) { mutableStateOf(1f) }
        var previewOffsetX by remember(previewPhotoId) { mutableStateOf(0f) }
        var previewOffsetY by remember(previewPhotoId) { mutableStateOf(0f) }
        Dialog(
            onDismissRequest = { previewBitmap = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "调配照片 $previewPhotoId",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                scaleX = previewScale
                                scaleY = previewScale
                                translationX = previewOffsetX
                                translationY = previewOffsetY
                            }
                            .pointerInput(previewPhotoId) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    previewScale = (previewScale * zoom).coerceIn(1f, 5f)
                                    previewOffsetX += pan.x
                                    previewOffsetY += pan.y
                                }
                            },
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 32.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = {
                                previewScale = 1f
                                previewOffsetX = 0f
                                previewOffsetY = 0f
                            },
                        ) { Text("复位", color = Color.White) }
                        TextButton(onClick = { previewBitmap = null }) { Text("关闭", color = Color.White) }
                    }
                }
            }
        }
    }

    // Exception handling selection & dialogs
    if (exceptionDialogType == 0 && exceptionTargetUsage != null) {
        AlertDialog(
            onDismissRequest = { exceptionDialogType = null; exceptionTargetUsage = null },
            title = { Text("工序异常处理") },
            text = { Text("请选择需要对当前设备进行的处理操作：") },
            confirmButton = {
                Button(
                    onClick = { exceptionDialogType = 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) { Text("撤销误扫") }
            },
            dismissButton = {
                Button(
                    onClick = { exceptionDialogType = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                ) { Text("设备故障换机") }
            },
        )
    }

    // 1. 撤销误扫确认框
    if (exceptionDialogType == 1 && exceptionTargetUsage != null) {
        val target = exceptionTargetUsage!!
        AlertDialog(
            onDismissRequest = { if (!busy) { exceptionDialogType = null; exceptionTargetUsage = null } },
            title = { Text("撤销误扫记录") },
            text = {
                Column {
                    Text("请填写撤销误扫原因：", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = exceptionReason,
                        onValueChange = { exceptionReason = it },
                        placeholder = { Text("如：误扫其他批次设备") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FieldShape,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && exceptionReason.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.voidEquipmentUsage(plan.optInt("id"), target.optInt("id"), exceptionReason.trim())
                                }
                            }.onSuccess {
                                exceptionDialogType = null
                                exceptionTargetUsage = null
                                reload()
                            }.onFailure { error = it.message ?: "撤销失败" }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "提交中..." else "确认撤销") }
            },
            dismissButton = {
                TextButton(onClick = { exceptionDialogType = null; exceptionTargetUsage = null }, enabled = !busy) {
                    Text("取消")
                }
            },
        )
    }

    // 2. 设备故障换机确认框
    if (exceptionDialogType == 2 && exceptionTargetUsage != null) {
        val target = exceptionTargetUsage!!
        AlertDialog(
            onDismissRequest = { if (!busy) { exceptionDialogType = null; exceptionTargetUsage = null } },
            title = { Text("设备故障换机") },
            text = {
                Column {
                    Text("故障原因：", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = exceptionReason,
                        onValueChange = { exceptionReason = it },
                        placeholder = { Text("请填写故障原因") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FieldShape,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("新设备编号：", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exceptionEquipmentCode,
                            onValueChange = { exceptionEquipmentCode = it },
                            placeholder = { Text("扫描或输入新设备") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = FieldShape,
                        )
                        OutlinedButton(
                            onClick = {
                                scanningAction = "fault_swap"
                                scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                            },
                            shape = FieldShape,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && exceptionReason.isNotBlank() && exceptionEquipmentCode.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.transferFaultyEquipment(
                                        plan.optInt("id"),
                                        target.optInt("id"),
                                        JSONObject()
                                            .put("reason", exceptionReason.trim())
                                            .put("equipmentCode", exceptionEquipmentCode.trim())
                                            .put("requestId", "android-${System.currentTimeMillis()}"),
                                    )
                                }
                            }.onSuccess {
                                exceptionDialogType = null
                                exceptionTargetUsage = null
                                error = null
                                occupyingPlanTarget = null
                                reload()
                            }.onFailure { ex ->
                                error = ex.message ?: "换机失败"
                                occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "提交中..." else "确认换机") }
            },
            dismissButton = {
                TextButton(onClick = { exceptionDialogType = null; exceptionTargetUsage = null }, enabled = !busy) {
                    Text("取消")
                }
            },
        )
    }

    // 包裹生成确认窗（完成加工后弹出）
    if (showFinishConfirmDialog) {
        val pickupCode = detail?.displayField("pickupCode", "").orEmpty().ifBlank { plan.displayField("pickupCode", "") }
        val methodCode = detail?.optInt("pickupMethod", plan.optInt("pickupMethod", 0)) ?: 0
        val methodLabel = when (methodCode) { 1 -> "跑腿"; 2 -> "快递"; else -> "自提" }
        AlertDialog(
            onDismissRequest = { if (!busy) showFinishConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("完成加工确认", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    // 计划概要信息卡片
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                                StatusPill(methodLabel)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append(processTypeName)
                                    if (totalDose > 0) append(" · $totalDose 剂")
                                    if (bagCount > 0) append(" · $bagCount 袋")
                                    if (volumeMl > 0) append(" · ${volumeMl}ml")
                                },
                                color = Muted,
                                fontSize = 12.sp,
                            )
                            if (pickupCode.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "取货码：${formatPickupCode(pickupCode)}",
                                    color = PrimaryDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 选项勾选栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!busy) createPackageImmediately = !createPackageImmediately }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = createPackageImmediately,
                            onCheckedChange = { if (!busy) createPackageImmediately = it },
                            colors = CheckboxDefaults.colors(checkedColor = Primary),
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("立即生成待取包裹", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                    }

                    // 模式动态说明条
                    Surface(
                        color = if (createPackageImmediately) PrimarySoft else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            0.5.dp,
                            if (createPackageImmediately) Primary.copy(alpha = 0.35f) else CardBorderColor,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            if (createPackageImmediately) {
                                Text(
                                    "✔ 系统自动将状态流转至「待领取」。",
                                    color = PrimaryDark,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            } else {
                                Text(
                                    "ℹ 暂缓生成：状态将变更为「加工完成」。后续可在工作台随时手动点击「生成包裹」。",
                                    color = Muted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        showFinishConfirmDialog = false
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    activePackagings.forEach {
                                        ApiClient.finishEquipmentUsage(plan.optInt("id"), it.optInt("id"))
                                    }
                                    ApiClient.transitionPlan(
                                        plan.optInt("id"),
                                        2,
                                        createPackage = createPackageImmediately,
                                    )
                                }
                            }.onSuccess {
                                onPlanStatusChanged?.invoke()
                                Toast.makeText(
                                    context,
                                    if (createPackageImmediately) "加工已完成，已生成待取包裹" else "加工已完成（已暂缓生成包裹）",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                reload()
                            }.onFailure {
                                if (!it.isCancellation()) {
                                    error = it.message ?: "完成加工失败"
                                }
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = FieldShape,
                ) {
                    Text(if (createPackageImmediately) "完成并生成包裹" else "仅完成加工")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmDialog = false }, enabled = !busy) {
                    Text("取消")
                }
            },
        )
    }

    // 后续在工序详情手动生成包裹弹窗
    if (showGeneratePackageDialog) {
        var packageRemark by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) showGeneratePackageDialog = false },
            title = { Text("生成待取包裹", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("该加工计划已加工完成，确认为顾客生成待取包裹吗？")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "状态将流转至「待领取」。",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = packageRemark,
                        onValueChange = { packageRemark = it.take(500) },
                        label = { Text("包裹备注（可选）") },
                        placeholder = { Text("可填写代煎、配送或合并取货说明") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = FieldShape,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        val planId = plan.optInt("id")
                        showGeneratePackageDialog = false
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.generatePlanPackage(
                                        planId,
                                        JSONObject().put("itemInfo", packageRemark.trim()),
                                    )
                                }
                            }.onSuccess {
                                onPlanStatusChanged?.invoke()
                                Toast.makeText(context, "待取包裹生成成功", Toast.LENGTH_SHORT).show()
                                reload()
                            }.onFailure {
                                if (!it.isCancellation()) error = it.message ?: "生成包裹失败"
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = FieldShape,
                ) {
                    Text("确认生成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeneratePackageDialog = false }, enabled = !busy) {
                    Text("取消")
                }
            },
        )
    }
}


private fun processingStageLabel(stage: Int): String = when (stage) {
    1 -> "调配中"
    2 -> "调配完成"
    3 -> "浸泡中"
    4 -> "煎煮中"
    5 -> "打包中"
    6 -> "打包完成"
    7 -> "加工完成"
    else -> "待加工"
}

private fun processingDuration(start: String, end: String): String {
    if (start.isBlank()) return "-"
    val started = runCatching { Instant.parse(start) }
        .recoverCatching { OffsetDateTime.parse(start).toInstant() }
        .getOrNull() ?: return "-"
    val finished = if (end.isBlank()) Instant.now() else {
        runCatching { Instant.parse(end) }
            .recoverCatching { OffsetDateTime.parse(end).toInstant() }
            .getOrNull() ?: return "-"
    }
    val minutes = Duration.between(started, finished).toMinutes().coerceAtLeast(0)
    return if (minutes < 60) minutes.toString() + "分钟" else (minutes / 60).toString() + "小时" + (minutes % 60).toString() + "分钟"
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

internal data class QuickScanPromptData(
    val plan: JSONObject,
    val workflow: JSONObject?,
    val errorMessage: String,
    val needPhoto: Boolean = false,
    val scannedCode: String = "",
    val occupyingPlan: JSONObject? = null,
)

internal suspend fun executeAutoQuickScan(
    context: Context,
    plan: JSONObject,
    scanned: String,
    onSuccess: (String) -> Unit,
    onPrompt: (QuickScanPromptData) -> Unit,
) {
    val planId = plan.optInt("id")
    val processType = plan.optJSONObject("processType")
    val processTypeName = processType?.displayField("name", "") ?: plan.displayField("processTypeName", "代煎")
    val isDecoction = processTypeName.contains("煎")

    if (!isDecoction) {
        onPrompt(
            QuickScanPromptData(
                plan = plan,
                workflow = null,
                errorMessage = "该加工计划为非代煎工艺，无需扫码关联煎煮设备。",
                needPhoto = false,
                scannedCode = scanned,
            )
        )
        return
    }

    // 1. 获取工序实时状态
    val workflow = runCatching {
        withContext(Dispatchers.IO) { ApiClient.processingWorkflow(planId) }
    }.getOrElse { err ->
        onPrompt(
            QuickScanPromptData(
                plan = plan,
                workflow = null,
                errorMessage = err.message ?: "获取工序状态失败",
                needPhoto = false,
                scannedCode = scanned,
            )
        )
        return
    }

    val photos = workflow.optJSONArray("photos")
    val photoCount = photos?.length() ?: 0
    val currentStage = workflow.optInt("currentStage", 1)

    // 2. 检查调配拍照约束
    if (photoCount == 0 && currentStage == 1) {
        onPrompt(
            QuickScanPromptData(
                plan = plan,
                workflow = workflow,
                errorMessage = "需先上传调配完成照片。按规范要求，浸泡前需先完成调配审核拍照。",
                needPhoto = true,
                scannedCode = scanned,
            )
        )
        return
    }

    // 3. 提取工序设备记录
    val usages = workflow.optJSONArray("equipmentUsages")?.let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.orEmpty()
    val activeSoakings = usages.filter { it.optInt("stage") == 3 && it.optInt("status") == 1 }
    val activeDecoctions = usages.filter { it.optInt("stage") == 4 && it.optInt("status") == 1 }
    val activePackagings = usages.filter { it.optInt("stage") == 5 && it.optInt("status") == 1 }
    val allSoakings = usages.filter { it.optInt("stage") == 3 }
    val nextSoakPortion = if (allSoakings.isNotEmpty()) (allSoakings.map { it.optInt("portionNo", 1) }.maxOrNull() ?: 0) + 1 else 1

    // 4. 候选步骤判定（包装 -> 煎煮 -> 浸泡）
    class CandidateStep(
        val label: String,
        val run: suspend () -> Result<String>,
    )

    val candidates = mutableListOf<CandidateStep>()

    // 如果有正在煎煮的批次，优先尝试打包
    activeDecoctions.forEach { decoction ->
        val usageId = decoction.optInt("id")
        val portionNo = decoction.optInt("portionNo", 1)
        candidates.add(
            CandidateStep("第 $portionNo 组包装机") {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ApiClient.startPackaging(
                            planId,
                            usageId,
                            JSONObject()
                                .put("equipmentCode", scanned)
                                .put("requestId", "android-${System.currentTimeMillis()}"),
                        )
                    }
                    "第 $portionNo 组包装机开始打包"
                }
            }
        )
    }

    // 如果有正在浸泡的批次，尝试煎煮
    activeSoakings.forEach { soaking ->
        val portionNo = soaking.optInt("portionNo", 1)
        candidates.add(
            CandidateStep("第 $portionNo 组煎煮设备") {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ApiClient.startEquipmentUsage(
                            planId,
                            JSONObject()
                                .put("stage", 4)
                                .put("portionNo", portionNo)
                                .put("equipmentCode", scanned)
                                .put("requestId", "android-${System.currentTimeMillis()}"),
                        )
                    }
                    "第 $portionNo 组煎煮设备"
                }
            }
        )
    }

    // 尝试开始浸泡
    candidates.add(
        CandidateStep("第 $nextSoakPortion 组浸泡桶") {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApiClient.startEquipmentUsage(
                        planId,
                        JSONObject()
                            .put("stage", 3)
                            .put("portionNo", nextSoakPortion)
                            .put("equipmentCode", scanned)
                            .put("requestId", "android-${System.currentTimeMillis()}"),
                    )
                }
                "第 $nextSoakPortion 组浸泡桶"
            }
        }
    )

    // 5. 依次匹配并提交
    var successMsg: String? = null
    var lastError: String = ""
    var occupyingPlan: JSONObject? = null

    for (candidate in candidates) {
        val res = candidate.run()
        if (res.isSuccess) {
            successMsg = "已成功扫码记录：" + res.getOrThrow()
            break
        } else {
            val ex = res.exceptionOrNull()
            val err = ex?.message.orEmpty()
            lastError = err
            if (ex is ApiClient.ApiException) {
                val occ = ex.data?.optJSONObject("occupyingPlan")
                if (occ != null) {
                    occupyingPlan = occ
                }
            }
        }
    }

    if (occupyingPlan == null && lastError.contains("占用")) {
        runCatching {
            withContext(Dispatchers.IO) {
                val equip = ApiClient.processingEquipmentByScan(scanned)
                val curPlan = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                if (curPlan != null) {
                    occupyingPlan = curPlan
                }
            }
        }
    }

    if (successMsg != null) {
        onSuccess(successMsg)
    } else {
        val errDisplay = when {
            lastError.isNotBlank() -> lastError
            activePackagings.isNotEmpty() && activeSoakings.isEmpty() && activeDecoctions.isEmpty() ->
                "当前所有工序均已在打包中，完成后请在工序详情中点击「加工完成」"
            else -> "扫码未匹配到有效工序或设备码不正确"
        }
        onPrompt(
            QuickScanPromptData(
                plan = plan,
                workflow = workflow,
                errorMessage = errDisplay,
                needPhoto = false,
                scannedCode = scanned,
                occupyingPlan = occupyingPlan,
            )
        )
    }
}

@Composable
internal fun QuickScanPromptDialog(
    data: QuickScanPromptData,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onEquipmentScanned: () -> Unit = {},
    onNavigatePlan: ((JSONObject) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workflow by remember { mutableStateOf(data.workflow) }
    var loading by remember { mutableStateOf(data.workflow == null) }
    var error by remember { mutableStateOf<String?>(data.errorMessage) }
    var occupyingPlanTarget by remember { mutableStateOf(data.occupyingPlan) }
    var busy by remember { mutableStateOf(false) }
    var scanningAction by remember { mutableStateOf<String?>(null) }

    val plan = data.plan
    val planId = plan.optInt("id")
    val prescription = plan.optJSONObject("prescription")
    val customerName = plan.displayField("customerName", "").ifBlank { prescription?.displayField("customerName") ?: "顾客" }
    val processType = plan.optJSONObject("processType")
    val processTypeName = processType?.displayField("name", "") ?: plan.displayField("processTypeName", "代煎")
    val batchNo = plan.optInt("batchNo", 1)

    fun fetchWorkflow() {
        scope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { ApiClient.processingWorkflow(planId) } }
                .onSuccess {
                    workflow = it
                    loading = false
                }
                .onFailure {
                    if (it.isCancellation()) return@onFailure
                    error = it.message ?: "加载工序信息失败"
                    loading = false
                }
        }
    }

    LaunchedEffect(planId) {
        if (workflow == null) fetchWorkflow()
    }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanned = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        val action = scanningAction
        scanningAction = null
        if (result.resultCode == Activity.RESULT_OK && scanned.isNotBlank() && action != null) {
            when {
                action == "soaking" -> {
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val currentUsages = workflow?.optJSONArray("equipmentUsages")?.let { arr ->
                                    (0 until arr.length()).map { arr.getJSONObject(it) }
                                }.orEmpty()
                                val usedPortions = currentUsages.filter { it.optInt("stage") == 3 }.map { it.optInt("portionNo", 1) }
                                val portionNo = if (usedPortions.isNotEmpty()) (usedPortions.maxOrNull() ?: 0) + 1 else 1
                                ApiClient.startEquipmentUsage(
                                    planId,
                                    JSONObject()
                                        .put("stage", 3)
                                        .put("portionNo", portionNo)
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            Toast.makeText(context, "已成功扫码记录浸泡桶", Toast.LENGTH_SHORT).show()
                            onEquipmentScanned()
                            onDismiss()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫码浸泡失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
                action.startsWith("decoction_") -> {
                    val portionNo = action.removePrefix("decoction_").toIntOrNull() ?: 1
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.startEquipmentUsage(
                                    planId,
                                    JSONObject()
                                        .put("stage", 4)
                                        .put("portionNo", portionNo)
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            Toast.makeText(context, "已成功扫码第 $portionNo 组煎煮", Toast.LENGTH_SHORT).show()
                            onEquipmentScanned()
                            onDismiss()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫锅煎煮失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
                action.startsWith("packaging_") -> {
                    val usageId = action.removePrefix("packaging_").toIntOrNull() ?: 0
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.startPackaging(
                                    planId,
                                    usageId,
                                    JSONObject()
                                        .put("equipmentCode", scanned)
                                        .put("requestId", "android-${System.currentTimeMillis()}"),
                                )
                            }
                        }.onSuccess {
                            Toast.makeText(context, "已成功扫包装机开始打包", Toast.LENGTH_SHORT).show()
                            onEquipmentScanned()
                            onDismiss()
                        }.onFailure { ex ->
                            error = ex.message ?: "扫包装机失败"
                            occupyingPlanTarget = (ex as? ApiClient.ApiException)?.data?.optJSONObject("occupyingPlan")
                            if (occupyingPlanTarget == null && ex.message?.contains("占用") == true) {
                                scope.launch {
                                    val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(scanned) }
                                    occupyingPlanTarget = equip?.optJSONObject("currentUsage")?.optJSONObject("processingPlan")
                                }
                            }
                        }
                        busy = false
                    }
                }
            }
        }
    }

    val detail = workflow ?: plan
    val photos = detail.optJSONArray("photos")
    val photoCount = photos?.length() ?: 0
    val currentStage = detail.optInt("currentStage", 1)
    val usages = detail.optJSONArray("equipmentUsages")?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }.orEmpty()
    val activeSoakings = usages.filter { it.optInt("stage") == 3 && it.optInt("status") == 1 }
    val activeDecoctions = usages.filter { it.optInt("stage") == 4 && it.optInt("status") == 1 }
    val activePackagings = usages.filter { it.optInt("stage") == 5 && it.optInt("status") == 1 }
    val isDecoction = detail.optBoolean("isDecoction") || processTypeName.contains("煎")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (data.needPhoto) "需先拍照审核" else "工序扫码提示", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("$customerName · $processTypeName (批次 #$batchNo)", color = Muted, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (error != null) {
                    Surface(
                        color = if (data.needPhoto) WarningSoft else DangerSoft,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, (if (data.needPhoto) Warning else Danger).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                text = if (data.needPhoto) "⚠️ 需先完成调配拍照" else "⚠️ 扫码提示",
                                color = if (data.needPhoto) Warning else Danger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(error!!, color = Ink, fontSize = 12.sp)

                            occupyingPlanTarget?.let { occPlan ->
                                Spacer(Modifier.height(8.dp))
                                OccupyingPlanCard(
                                    plan = occPlan,
                                    onClick = {
                                        onDismiss()
                                        onNavigatePlan?.invoke(occPlan)
                                    },
                                )
                            }
                        }
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (!isDecoction) {
                    Text("当前计划为非代煎工艺，无需扫码关联煎煮设备。", color = Muted, fontSize = 13.sp)
                } else if (data.needPhoto) {
                    OutlinedButton(
                        onClick = onOpenDetail,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    ) {
                        Text("前往工序详情拍照", fontSize = 12.sp)
                    }
                } else {
                    Text("如需手动指定工序扫码，请点击对应按钮：", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))

                    // Step 1: 浸泡
                    Text("工序 1: 浸泡", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            scanningAction = "soaking"
                            scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("扫码添加浸泡桶", fontSize = 12.sp)
                    }

                    // Step 2: 煎煮
                    if (activeSoakings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("工序 2: 扫锅煎煮", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        activeSoakings.forEach { item ->
                            val portion = item.optInt("portionNo", 1)
                            val equip = item.optJSONObject("equipment")?.displayField("name", "浸泡桶") ?: "浸泡桶"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 $portion 组 · $equip", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
                                    Text("等待转煎煮", fontSize = 10.sp, color = Muted)
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scanningAction = "decoction_$portion"
                                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                                    },
                                    modifier = Modifier.height(30.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                ) {
                                    Text("扫锅煎煮", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Step 3: 包装
                    if (activeDecoctions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("工序 3: 扫包装机", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        activeDecoctions.forEach { item ->
                            val portion = item.optInt("portionNo", 1)
                            val equip = item.optJSONObject("equipment")?.displayField("name", "煎药机") ?: "煎药机"
                            val usageId = item.optInt("id")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 $portion 组 · $equip", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
                                    Text("等待转打包", fontSize = 10.sp, color = Muted)
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scanningAction = "packaging_$usageId"
                                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                                    },
                                    modifier = Modifier.height(30.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                ) {
                                    Text("扫包装机", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (activePackagings.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = SuccessSoft,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "当前有 ${activePackagings.size} 组正在打包中，完成后可在详情点击「加工完成」",
                                color = Success,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                occupyingPlanTarget?.let { occPlan ->
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigatePlan?.invoke(occPlan)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text("查看占用计划", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onOpenDetail) {
                    Text(if (occupyingPlanTarget != null) "当前工序" else "完整工序详情", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) {
                Text("关闭", fontSize = 12.sp)
            }
        },
    )
}
