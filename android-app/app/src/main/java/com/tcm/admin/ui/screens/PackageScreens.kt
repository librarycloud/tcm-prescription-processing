package com.tcm.admin

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
private fun FakeQr(value: String) {
    val bitmap = remember(value) {
        runCatching {
            val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { result ->
                for (x in 0 until matrix.width) {
                    for (y in 0 until matrix.height) {
                        result.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "取货二维码", modifier = Modifier.size(140.dp))
    } else {
        Box(Modifier.size(140.dp).background(Color.White, RoundedCornerShape(8.dp)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PackagesScreen(
    user: JSONObject?,
    onNavigate: (ScreenTarget) -> Unit,
    scrollState: ScrollState,
) {
    val listOwner = "packages"
    val showStore = user?.optInt("role", -1) == 0
    var status by rememberRetainedListValue(listOwner, "status") { null as Int? } // null=全部, 0=待取, 1=已取
    var sortBy by rememberRetainedListValue(listOwner, "sortBy") { "createdAt" } // "createdAt" | "pickedAt"
    var keyword by rememberRetainedListValue(listOwner, "keyword") { "" }
    var stores by rememberRetainedListValue(listOwner, "stores") { emptyList<JSONObject>() }
    var selectedStoreId by rememberRetainedListValue(listOwner, "selectedStoreId") { "" }
    var items by rememberRetainedListValue(listOwner, "items") { null as List<PackageItem>? }
    var error by rememberRetainedListValue(listOwner, "error") { null as String? }
    var loading by remember { mutableStateOf(false) }
    var reload by rememberRetainedListValue(listOwner, "reload") { 0 }
    var page by rememberRetainedListValue(listOwner, "page") { 1 }
    var pages by rememberRetainedListValue(listOwner, "pages") { 1 }
    var loadedQueryKey by rememberRetainedListValue(listOwner, "loadedQueryKey") { null as String? }
    var storesLoaded by rememberRetainedListValue(listOwner, "storesLoaded") { false }
    var refreshing by remember { mutableStateOf(false) }
    var lastAutoKeyword by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            onNavigate(ScreenTarget.PackageVerify(value))
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

    LaunchedEffect(status, sortBy, selectedStoreId, reload, page) {
        kotlinx.coroutines.delay(300)
        val queryKey = listOf(status, sortBy, keyword, selectedStoreId, reload, page).joinToString("|")
        if (loadedQueryKey == queryKey && items != null) return@LaunchedEffect
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
                    pageSize = 10,
                )
            }
        }.onSuccess { root ->
            val list = root.optJSONArray("list")
            pages = root.optJSONObject("pagination")?.optInt("pages", 1)?.coerceAtLeast(1) ?: 1
            if (list != null) {
                items = (0 until list.length()).map { packageItem(list.getJSONObject(it)) }
            } else {
                items = emptyList()
            }
            loading = false
            refreshing = false
            loadedQueryKey = queryKey
        }.onFailure {
            error = it.message ?: "加载包裹列表失败"
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(keyword) {
        val term = keyword.trim()
        if (term.isBlank() || !shouldAutoSearchQuery(term)) {
            lastAutoKeyword = ""
            if (term.isBlank()) reload++
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        if (keyword.trim() == term && lastAutoKeyword != term) {
            lastAutoKeyword = term
            page = 1
            reload++
        }
    }

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
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionHeader("包裹工作台", "管理待领取、自提与跑腿核销")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onNavigate(ScreenTarget.PackageVerify("")) },
                    modifier = Modifier.height(CompactControlHeight),
                    shape = FieldShape,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("核销")
                }
                Button(
                    onClick = { onNavigate(ScreenTarget.PackageForm(null)) },
                    modifier = Modifier.height(CompactControlHeight),
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        SearchBarField(
            value = keyword,
            onValueChange = {
                keyword = it
                page = 1
            },
            placeholder = "搜索姓名、手机号或取货码",
            onSearch = { page = 1; lastAutoKeyword = keyword.trim(); reload++ },
            onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
        )

        Spacer(Modifier.height(10.dp))

        // Status filter tabs
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedButton("全部包裹", status == null, onClick = { status = null; page = 1 })
            SegmentedButton("待领取", status == 0, onClick = { status = 0; page = 1 })
            SegmentedButton("已领取", status == 1, onClick = { status = 1; page = 1 })
        }

        if (showStore && stores.size > 1) {
            Spacer(Modifier.height(8.dp))
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it; page = 1 },
            )
        }

        Spacer(Modifier.height(10.dp))

        // Sort switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SwapVert, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (sortBy == "createdAt") "按创建时间" else "按领取时间",
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    sortBy = if (sortBy == "createdAt") "pickedAt" else "createdAt"
                },
            )
        }

        Spacer(Modifier.height(10.dp))

        if (error != null) {
            Text(error!!, color = Danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (loading && items == null) {
            AppEmptyState("加载包裹列表中...")
        } else if (items != null && items!!.isEmpty()) {
            AppEmptyState("暂无匹配包裹")
        } else {
            items.orEmpty().forEach { item ->
                key(item.id) {
                    PackageSummaryCard(
                        item = item,
                        showStore = showStore,
                        modifier = Modifier.padding(bottom = 12.dp),
                        onClick = { onNavigate(ScreenTarget.PackageDetail(item)) },
                        onVerify = { onNavigate(ScreenTarget.PackageVerify(item.code)) },
                    )
                }
            }

            if (pages > 1) {
                AppPagination(
                    page = page,
                    pages = pages,
                    onPrev = { if (page > 1) page-- },
                    onNext = { if (page < pages) page++ },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
internal fun PackageSummaryCard(
    item: PackageItem,
    showStore: Boolean,
    onClick: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Ink,
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(text = item.method)
                StatusPill(text = item.status)
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = FieldShape,
            border = BorderStroke(1.dp, CardBorderColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("取货码", color = Muted, fontSize = 11.sp)
                    Text(
                        text = formatPickupCode(item.code),
                        color = PrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("收件人", color = Muted, fontSize = 11.sp)
                    Text(
                        text = "${item.customer} · ${maskPhone(item.phone)}",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        if (showStore && item.store.isNotBlank()) {
            InfoRowItem(label = "所属门店", value = item.store)
        }
        InfoRowItem(label = "领取时间", value = item.time)
        item.info.takeIf { it.isNotBlank() }?.let {
            InfoRowItem(label = "备注", value = it)
        }

        if (item.statusCode == 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onVerify,
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("快速核销", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun PackageDetailPage(
    pkg: PackageItem,
    showStore: Boolean,
    onNavigate: (ScreenTarget) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pkg.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = pkg.method)
                    StatusPill(text = pkg.status)
                }
            }

            Spacer(Modifier.height(12.dp))

            // QR Code Center Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = FieldShape,
                border = BorderStroke(1.dp, CardBorderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FakeQr(pkg.pickupQrContent.ifBlank { pkg.code })
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "取货码：${formatPickupCode(pkg.code)}",
                        color = PrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                    Text("请向工作人员出示此取货码或二维码", color = Muted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            InfoRowItem(label = "收件客户", value = pkg.customer)
            InfoRowItem(label = "联系电话", value = maskPhone(pkg.phone))
            InfoRowItem(label = "取货方式", value = pkg.method)
            if (showStore && pkg.store.isNotBlank()) InfoRowItem(label = "所属门店", value = pkg.store)
            if (pkg.expressTrackingNo.isNotBlank()) InfoRowItem(label = "快递单号", value = pkg.expressTrackingNo)
            InfoRowItem(label = "包裹状态", value = pkg.status)
            InfoRowItem(label = "录入时间", value = pkg.createdAt)
            InfoRowItem(label = "取货时间", value = pkg.pickedAt.ifBlank { "未领取" })
            InfoRowItem(label = "录入人", value = pkg.creatorName)
            InfoRowItem(label = "核销人", value = pkg.verifierName.ifBlank { "未核销" })

            if (pkg.info.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "备注：${pkg.info}",
                        color = RegularText,
                        fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pkg.statusCode == 0) {
                OutlinedButton(
                    onClick = { onNavigate(ScreenTarget.PackageForm(pkg)) },
                    shape = FieldShape,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑包裹")
                }
            }

            if (pkg.statusCode == 0) {
                Button(
                    onClick = { onNavigate(ScreenTarget.PackageVerify(pkg.code)) },
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("立即核销")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun PackageFormScreen(
    initial: PackageItem?,
    onSaved: () -> Unit,
) {
    val isEdit = initial != null && initial.id > 0
    var itemName by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var itemInfo by remember(initial) { mutableStateOf(initial?.info.orEmpty()) }
    var receiverName by remember(initial) { mutableStateOf(initial?.customer.orEmpty()) }
    var receiverPhone by remember(initial) { mutableStateOf(initial?.phone?.takeIf { it != "-" }.orEmpty()) }
    var method by remember(initial) { mutableStateOf(initial?.methodCode ?: 0) }
    var tracking by remember(initial) { mutableStateOf(initial?.expressTrackingNo.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppCard {
            SectionHeader(
                title = if (isEdit) "编辑包裹信息" else "创建自提/代发包裹",
                subtitle = "录入物品、备注与收件人联系方式",
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("物品名称 *") },
                placeholder = { Text("如：中药汤剂 14袋") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = itemInfo,
                onValueChange = { itemInfo = it.take(500) },
                label = { Text("备注") },
                placeholder = { Text("如：请冷藏、分装或配送说明") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = receiverName,
                onValueChange = { receiverName = it },
                label = { Text("收件人姓名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = receiverPhone,
                onValueChange = { receiverPhone = it.filter(Char::isDigit).take(11) },
                label = { Text("收件人手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(14.dp))

            Text("取货方式 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) ->
                    SegmentedButton(label, method == key, onClick = { method = key })
                }
            }

            if (method == 2) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text("快递单号 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            enabled = itemName.isNotBlank() && receiverName.isNotBlank() && !busy && (method != 2 || tracking.isNotBlank()),
            onClick = {
                busy = true
                val payload = JSONObject()
                    .put("itemName", itemName.trim())
                    .put("itemInfo", itemInfo.trim())
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(if (busy) "正在保存..." else if (isEdit) "确认修改" else "保存并生成取货码", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun PackageVerifyScreen(
    initialCode: String,
    onVerified: () -> Unit,
) {
    var signedQrContent by remember(initialCode) {
        mutableStateOf(initialCode.takeIf { it.startsWith("TCM:PICKUP:1:") })
    }
    var code by remember(initialCode) {
        val signedCode = Regex("^TCM:PICKUP:1:\\d+:(\\d{6}):[A-Za-z0-9_-]+$")
            .matchEntire(initialCode)?.groupValues?.getOrNull(1)
        mutableStateOf(signedCode ?: initialCode.filter(Char::isDigit).take(6))
    }
    var method by remember { mutableStateOf(0) }
    var tracking by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppCard {
            SectionHeader(
                title = "取货码核销",
                subtitle = "输入顾客提供的 6 位取货码进行包裹核销与出库",
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter(Char::isDigit).take(6)
                    signedQrContent = null
                },
                label = { Text("6 位取货码 *") },
                placeholder = { Text("例如：891234") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(14.dp))

            Text("确认最终取货方式", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) ->
                    SegmentedButton(label, method == key, onClick = { method = key })
                }
            }

            if (method == 2) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tracking,
                    onValueChange = { tracking = it },
                    label = { Text("快递单号 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            enabled = code.length == 6 && !busy && (method != 2 || tracking.isNotBlank()),
            onClick = {
                busy = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ApiClient.verifyPackage(code, method, tracking.trim(), signedQrContent)
                        }
                    }.onSuccess {
                        onVerified()
                    }.onFailure {
                        error = it.message ?: "核销失败"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Success),
        ) {
            Text(if (busy) "正在核销..." else "确认核销出库", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
    }
}
