package com.tcm.admin

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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

@Composable
private fun FakeQr(value: String) {
    Canvas(
        Modifier
            .size(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
    ) {
        val cells = 21
        val cell = size.minDimension / cells
        for (x in 0 until cells) {
            for (y in 0 until cells) {
                if (((x * 31 + y * 17 + value.length * 13) % 7) < 3 ||
                    (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13)
                ) {
                    drawRect(
                        if ((x + y) % 3 == 0) Color.Black else Color(0xFF262626),
                        androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                        androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PackagesScreenV3(onOpen: (PackageItem) -> Unit) {
    var status by remember { mutableStateOf<Int?>(null) } // null=全部, 0=待取, 1=已取
    var sortBy by remember { mutableStateOf("createdAt") } // "createdAt" | "pickedAt"
    var keyword by remember { mutableStateOf("") }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }

    var formVisible by remember { mutableStateOf(false) }
    var verifyVisible by remember { mutableStateOf(false) }
    var verifyCodeInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            verifyCodeInput = value
            verifyVisible = true
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
    }

    LaunchedEffect(reload, status, sortBy, selectedStoreId, keyword, page) {
        error = null
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.packagesPaged(
                    status = status,
                    keyword = keyword.trim(),
                    storeId = selectedStoreId.toIntOrNull(),
                    sortBy = sortBy,
                    page = page,
                    pageSize = 20,
                )
            }
        }.onSuccess { data ->
            val list = data.optJSONArray("list") ?: JSONArray()
            items = (0 until list.length()).map {
                val obj = list.getJSONObject(it)
                val timeField = if (sortBy == "pickedAt" && obj.optInt("status") == 1) "pickedAt" else "createdAt"
                val rawTime = obj.optString(timeField, obj.optString("createdAt", ""))
                PackageItem(
                    id = obj.optInt("id"),
                    name = obj.optString("itemName", "包裹"),
                    customer = obj.optString("receiverName", "-"),
                    phone = obj.optString("receiverPhone", "-"),
                    code = obj.optString("pickupCode", "-"),
                    method = pickupMethodLabel(obj.optInt("pickupMethod", 0)),
                    status = if (obj.optInt("status") == 1) "已取" else if (obj.optInt("status") == 2) "已取消" else "待取",
                    statusCode = obj.optInt("status"),
                    time = rawTime.take(16).replace("T", " "),
                    store = obj.optJSONObject("store")?.optString("name").orEmpty(),
                    expressTrackingNo = obj.optString("expressTrackingNo", ""),
                )
            }
            pages = data.optJSONObject("pagination")?.optInt("pages", 1) ?: 1
            loading = false
        }.onFailure {
            error = it.message ?: "加载包裹失败"
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { formVisible = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新增包裹", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(
                onClick = { verifyVisible = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Success),
                border = BorderStroke(1.dp, Success),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("取货码核销", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search Bar
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索取货码、手机号、姓名或物品",
            onSearch = { page = 1; reload++ },
            onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
        )

        // Store Chips
        if (stores.size > 1) {
            Spacer(Modifier.height(10.dp))
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { id ->
                    selectedStoreId = id
                    page = 1
                    reload++
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Filter & Sort Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedButton("全部", status == null) { status = null; page = 1 }
                SegmentedButton("待取", status == 0) { status = 0; page = 1 }
                SegmentedButton("已取", status == 1) { status = 1; page = 1 }
            }
            OutlinedButton(
                onClick = {
                    sortBy = if (sortBy == "createdAt") "pickedAt" else "createdAt"
                    page = 1
                },
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text(if (sortBy == "createdAt") "按录入时间" else "按取货时间", fontSize = 11.sp)
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

        // Packages List
        if (!loading) {
            if (items == null || items!!.isEmpty()) {
                AppEmptyState("暂无包裹记录")
            } else {
                items!!.forEach { item ->
                    AppCard(
                        modifier = Modifier.padding(bottom = 12.dp),
                        onClick = { onOpen(item) },
                    ) {
                        // Title & Status
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
                                    text = "收件人：${item.customer} · ${maskPhone(item.phone)}",
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
                                text = item.code,
                                color = Primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        if (item.store.isNotBlank()) {
                            InfoRowItem("门店", item.store)
                        }
                        if (item.expressTrackingNo.isNotBlank()) {
                            InfoRowItem("快递单号", item.expressTrackingNo)
                        }
                        InfoRowItem(
                            if (sortBy == "pickedAt" && item.statusCode == 1) "取货时间" else "录入时间",
                            item.time,
                        )

                        if (item.statusCode == 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "点击查看详情，可编辑或扫码核销",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
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

        Spacer(Modifier.height(16.dp))
    }

    // Dialog: Create Package
    if (formVisible) {
        PackageFormDialog(
            initial = null,
            onClose = { formVisible = false },
            onSaved = { formVisible = false; reload++ },
        )
    }

    // Dialog: Verify Package
    if (verifyVisible) {
        PackageVerifyDialog(
            initialCode = verifyCodeInput,
            onClose = { verifyVisible = false; verifyCodeInput = "" },
            onVerified = { verifyVisible = false; verifyCodeInput = ""; reload++ },
        )
    }
}

@Composable
internal fun PackageDetailDialogV2(
    item: PackageItem,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
) {
    var editVisible by remember { mutableStateOf(false) }
    var verifyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                StatusPill(item.status)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // QR representation
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    FakeQr(item.code)
                }

                Surface(
                    color = PrimarySoft,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("取货码", color = Muted, fontSize = 12.sp)
                        Text(item.code, color = Primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                InfoRowItem("收件人", item.customer)
                InfoRowItem("联系电话", item.phone)
                InfoRowItem("取货方式", item.method)
                if (item.store.isNotBlank()) InfoRowItem("所在门店", item.store)
                if (item.expressTrackingNo.isNotBlank()) InfoRowItem("快递单号", item.expressTrackingNo)
                InfoRowItem("状态更新时间", item.time)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.statusCode == 0) {
                    OutlinedButton(onClick = { editVisible = true }, shape = RoundedCornerShape(6.dp)) {
                        Text("编辑")
                    }
                    Button(
                        onClick = { verifyVisible = true },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                    ) {
                        Text("核销")
                    }
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) {
                    Text("关闭")
                }
            }
        },
    )

    if (editVisible) {
        PackageFormDialog(
            initial = item,
            onClose = { editVisible = false },
            onSaved = { editVisible = false; onReload() },
        )
    }

    if (verifyVisible) {
        PackageVerifyDialog(
            initialCode = item.code,
            onClose = { verifyVisible = false },
            onVerified = { verifyVisible = false; onReload() },
        )
    }
}

@Composable
internal fun PackageFormDialog(
    initial: PackageItem?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val isEdit = initial != null
    var itemName by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var receiverName by remember(initial) { mutableStateOf(initial?.customer.orEmpty()) }
    var receiverPhone by remember(initial) { mutableStateOf(initial?.phone.orEmpty()) }
    var method by remember(initial) {
        mutableStateOf(
            when (initial?.method) {
                "跑腿" -> 1
                "快递" -> 2
                else -> 0
            },
        )
    }
    var tracking by remember(initial) { mutableStateOf(initial?.expressTrackingNo.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (isEdit) "编辑包裹" else "新增包裹", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(error!!, color = Danger, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("物品名称") },
                    placeholder = { Text("例如：中药汤剂") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = receiverName,
                    onValueChange = { receiverName = it },
                    label = { Text("收件人姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = receiverPhone,
                    onValueChange = { receiverPhone = it.filter(Char::isDigit).take(11) },
                    label = { Text("联系手机号（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("取货方式", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) ->
                        SegmentedButton(label, method == key) { method = key }
                    }
                }
                if (method == 2) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tracking,
                        onValueChange = { tracking = it },
                        label = { Text("快递单号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = itemName.isNotBlank() && receiverName.isNotBlank() && !busy && (method != 2 || tracking.isNotBlank()),
                onClick = {
                    busy = true
                    val payload = JSONObject()
                        .put("itemName", itemName.trim())
                        .put("receiverName", receiverName.trim())
                        .put("receiverPhone", receiverPhone.trim())
                        .put("pickupMethod", method)
                        .put("expressTrackingNo", tracking.trim())

                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                if (isEdit) {
                                    ApiClient.updatePackage(initial!!.id, payload)
                                } else {
                                    ApiClient.createPackage(payload)
                                }
                            }
                        }.onSuccess {
                            onSaved()
                        }.onFailure {
                            error = it.message ?: "保存包裹失败"
                        }
                        busy = false
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (busy) "提交中..." else if (isEdit) "保存" else "创建")
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
internal fun PackageVerifyDialog(
    initialCode: String,
    onClose: () -> Unit,
    onVerified: () -> Unit,
) {
    var code by remember(initialCode) { mutableStateOf(initialCode.filter(Char::isDigit).take(6)) }
    var method by remember { mutableStateOf(0) }
    var tracking by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("取货码核销", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (error != null) {
                    Text(error!!, color = Danger, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位取货码") },
                    placeholder = { Text("例如：891234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("确认取货方式", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) ->
                        SegmentedButton(label, method == key) { method = key }
                    }
                }
                if (method == 2) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tracking,
                        onValueChange = { tracking = it },
                        label = { Text("快递单号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = code.length == 6 && !busy && (method != 2 || tracking.isNotBlank()),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.verifyPackage(code, method, tracking.trim())
                            }
                        }.onSuccess {
                            onVerified()
                        }.onFailure {
                            error = it.message ?: "核销失败"
                        }
                        busy = false
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Success),
            ) {
                Text(if (busy) "核销中..." else "确认核销")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onClose() }) {
                Text("取消")
            }
        },
    )
}
