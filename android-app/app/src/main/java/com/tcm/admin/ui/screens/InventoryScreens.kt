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
internal fun InventoryScreen() {
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchRequest by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            query = value
            searchRequest++
        }
    }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
    }
    LaunchedEffect(searchRequest, selectedStoreId) {
        if (searchRequest == 0) return@LaunchedEffect
        error = null
        selectedProduct = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.inventory(query.trim(), selectedStoreId.toIntOrNull()) } }
            .onSuccess { values -> products = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载库存失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(if (selectedProduct == null) "库存查询" else "商品库存")
                Text(if (selectedProduct == null) "按名称、商品编码或条码查询库存" else "按库存批次查看货位和有效期", color = Muted, fontSize = 13.sp)
            }
            if (selectedProduct != null) {
                OutlinedButton({ selectedProduct = null }, shape = RoundedCornerShape(6.dp)) { Text("返回结果") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (stores.size > 1) {
            Text("查询门店", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedButton("全部门店", selectedStoreId.isBlank()) { selectedStoreId = "" }
                stores.forEach { store ->
                    val id = store.opt("id")?.toString().orEmpty()
                    SegmentedButton(store.optString("name", "门店"), selectedStoreId == id) { selectedStoreId = id }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (selectedProduct == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码查询", tint = Primary)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("商品名称、编码或条码") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { searchRequest++ }, enabled = query.trim().length >= 2, shape = RoundedCornerShape(6.dp)) { Text("查询") }
            }
            Spacer(Modifier.height(8.dp))
            Text("也可使用扫码枪或相机扫描条码", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            error?.let { Text(it, color = Danger, fontSize = 13.sp) }
            if (products == null && searchRequest > 0 && error == null) Text("查询中...", color = Muted)
            if (products != null && products!!.isEmpty()) Text("未找到匹配商品", color = Muted)
            products.orEmpty().forEach { product ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selectedProduct = product },
                    colors = CardDefaults.cardColors(Color.White),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${product.optString("productCode", "-")} · ${product.optString("name", "商品")}", fontWeight = FontWeight.SemiBold)
                                Text("单位：${product.optString("unit", "-")}  规格：${product.optString("specification", "-")}", color = Muted, fontSize = 12.sp)
                            }
                            Text("¥${product.opt("retailPrice") ?: "-"}", color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("厂商：${product.optString("manufacturer", "-")}  条码：${product.optString("barcode", "无")}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val product = selectedProduct!!
            val inventories = product.optJSONArray("inventories") ?: JSONArray()
            Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.optString("name", "商品"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("编码：${product.optString("productCode", "-")}  条码：${product.optString("barcode", "-")}", color = Muted, fontSize = 12.sp)
                    Text("规格：${product.optString("specification", "-")}  单位：${product.optString("unit", "-")}", color = Muted, fontSize = 12.sp)
                    Text("生产厂商：${product.optString("manufacturer", "-")}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("总库存 ${product.opt("totalQuantity") ?: 0} ${product.optString("unit")}", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text("共 ${inventories.length()} 个库存批次", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("库存批次", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (inventories.length() == 0) Text("该商品暂无库存", color = Muted)
            for (index in 0 until inventories.length()) {
                val inventory = inventories.getJSONObject(index)
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        val storeName = inventory.optJSONObject("store")?.optString("name").orEmpty()
                        if (storeName.isNotBlank()) Text("门店：$storeName", color = Muted, fontSize = 12.sp)
                        Text("批号：${inventory.optString("batchNo", "-")}", color = Muted, fontSize = 13.sp)
                        Text("货位：${inventory.optString("locationName", "-")}", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("库存数量 ${inventory.opt("quantity") ?: 0} ${product.optString("unit")}", fontWeight = FontWeight.Bold, color = Primary)
                        val productionDate = inventory.optString("productionDate").take(10).ifBlank { "-" }
                        val expiryDate = inventory.optString("expiryDate").take(10).ifBlank { "-" }
                        val inboundDate = inventory.optString("inboundDate").take(10).ifBlank { "-" }
                        Text("生产：$productionDate  有效期：$expiryDate", color = Muted, fontSize = 12.sp)
                        Text("入库：$inboundDate", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StocktakingScreen() {
    var checks by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var checkName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var activeCheckId by remember { mutableStateOf(0) }
    var countItem by remember { mutableStateOf<JSONObject?>(null) }
    var countValue by remember { mutableStateOf("") }
    var countBatchNo by remember { mutableStateOf("") }
    var candidateVisible by remember { mutableStateOf(false) }
    var candidateKeyword by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var locationItem by remember { mutableStateOf<JSONObject?>(null) }
    var locationValue by remember { mutableStateOf("") }
    var detailLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val refreshSelectedCheck: () -> Unit = {
        if (activeCheckId > 0) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(activeCheckId) } }
                    .onSuccess { selected = it; reload++ }
                    .onFailure { error = it.message ?: "刷新盘点明细失败" }
            }
        }
    }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            candidateKeyword = value
            candidateVisible = true
        }
    }
    LaunchedEffect(reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.stocktakings(), ApiClient.stores()) } }
            .onSuccess { (values, storeValues) ->
                checks = (0 until values.length()).map { values.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
                if (selectedStoreId.isBlank() && stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
            .onFailure { error = it.message ?: "加载盘点单失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (selected == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("商品盘点")
                    Text("选择盘点单后，按商品和库存批次完成盘点", color = Muted, fontSize = 13.sp)
                }
                Button({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建盘点") }
            }
            Spacer(Modifier.height(16.dp))
            if (checks == null && error == null) Text("加载中...", color = Muted)
            if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
            if (checks != null && checks!!.isEmpty()) Text("暂无进行中的盘点单", color = Muted)
            checks.orEmpty().forEach { check ->
                val summary = check.optJSONObject("summary") ?: JSONObject()
                val status = check.optInt("status")
                val total = summary.optInt("total")
                val counted = summary.optInt("counted")
                val progress = if (total > 0) counted.toFloat() / total else 0f
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(check.optString("checkName", "未命名盘点"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(check.optJSONObject("store")?.optString("name") ?: "-", color = Muted, fontSize = 12.sp)
                            }
                            StatusPill(goodsCheckStatus(status))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$counted", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Primary)
                            Text(" / $total 条已盘", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
                            Spacer(Modifier.weight(1f))
                            Text("创建于 ${check.optString("createdAt").take(10)}", color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Primary, trackColor = PrimarySoft)
                        Spacer(Modifier.height(12.dp))
                        Button({
                            detailLoading = true
                            scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(check.optInt("id")) } }
                                .onSuccess { selected = it; activeCheckId = check.optInt("id"); candidateKeyword = ""; candidates = emptyList() }
                                .onFailure { error = it.message ?: "加载盘点明细失败" }
                            detailLoading = false
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text(if (detailLoading) "加载中..." else "进入盘点") }
                    }
                }
            }
        } else {
            val check = selected!!
            val items = check.optJSONArray("items") ?: JSONArray()
            val completed = check.optInt("status") == 2
            val counted = (0 until items.length()).count { jsonNullable(items.getJSONObject(it).opt("effectiveCount")) != null }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(check.optString("checkName", "商品盘点"))
                    Text(check.optJSONObject("store")?.optString("name") ?: "-", color = Muted, fontSize = 13.sp)
                }
                StatusPill(goodsCheckStatus(check.optInt("status")))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ selected = null; candidateKeyword = ""; candidates = emptyList() }, shape = RoundedCornerShape(6.dp)) { Text("切换盘点单") }
                if (!completed) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishGoodsCheck(activeCheckId) } }.onSuccess { selected = null; reload++ }.onFailure { error = it.message ?: "结束盘点失败" } } }, shape = RoundedCornerShape(6.dp)) { Text("完成盘点") }
            }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.QrCodeScanner, "扫码查询", tint = Primary) }
                        OutlinedTextField(candidateKeyword, { candidateKeyword = it }, modifier = Modifier.weight(1f), placeholder = { Text("名称、编码或条码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                        Button({ candidateVisible = true }, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("查询") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("输入商品后选择批次，记录实际数量；条码可直接扫码", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("盘点记录")
                Spacer(Modifier.weight(1f))
                Text("已盘 $counted 条", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (items.length() == 0) Text("尚无盘点记录，请先查询商品", color = Muted)
            (0 until items.length()).forEach { index ->
                val item = items.getJSONObject(index)
                val product = item.optJSONObject("product") ?: JSONObject()
                val systemQty = item.opt("systemQty") ?: 0
                val effective = jsonNullable(item.opt("effectiveCount"))
                val difference = jsonNullable(item.opt("difference"))
                val needsReview = item.optInt("checkStatus") in listOf(2, 4)
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.optString("name", "商品"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("${product.optString("productCode", "-")} · 批号 ${item.optString("batchNo").ifBlank { "-" }}", color = Muted, fontSize = 12.sp)
                            }
                            StatusPill(if (needsReview) "待复盘" else if (effective == null) "待盘点" else "已盘点")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCell("系统数量", "$systemQty ${product.optString("unit")}", Modifier.weight(1f))
                            MetricCell("实际数量", effective?.toString() ?: "未录入", Modifier.weight(1f))
                            MetricCell("差异", difference?.toString() ?: "-", Modifier.weight(1f), difference != null && difference.toString() != "0")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("系统货位：${item.optString("systemLocationName").ifBlank { "-" }} · 盘点货位：${item.optString("countLocationName").ifBlank { "同系统" }}", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!completed && !needsReview) OutlinedButton({ countItem = item; countValue = effective?.toString().orEmpty(); countBatchNo = item.optString("batchNo") }, shape = RoundedCornerShape(6.dp)) { Text(if (effective == null) "录入实盘" else "修改实盘") }
                            if (!completed && needsReview) Button({ countItem = item; countValue = jsonNullable(item.opt("recountQty"))?.toString().orEmpty(); countBatchNo = item.optString("batchNo") }, shape = RoundedCornerShape(6.dp)) { Text("复盘") }
                            if (!completed) TextButton({ locationItem = item; locationValue = item.optString("countLocationName").ifBlank { item.optString("systemLocationName") } }) { Text("修改货位") }
                            if (!completed && !needsReview && effective != null && item.optInt("reviewStatus") == 0) TextButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.reviewGoodsCheckItem(item.optInt("id")) } }.onSuccess { refreshSelectedCheck() }.onFailure { error = it.message ?: "复核失败" } } }) { Text("复核") }
                        }
                    }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建盘点") }, text = { Column { if (stores.size > 1) { Text("所属门店", color = Muted, fontSize = 12.sp); StoreSelector(stores, selectedStoreId) { selectedStoreId = it }; Spacer(Modifier.height(10.dp)) }; OutlinedTextField(checkName, { checkName = it }, Modifier.fillMaxWidth(), label = { Text("盘点名称") }, singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createGoodsCheck(checkName.trim(), storeId = selectedStoreId.toIntOrNull()) } }.onSuccess { createVisible = false; checkName = ""; reload++ }.onFailure { error = it.message ?: "创建盘点单失败" } } }, enabled = checkName.isNotBlank() && (stores.size <= 1 || selectedStoreId.isNotBlank())) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
    countItem?.let { item ->
        val initialCount = jsonNullable(item.opt("firstCountQty")) == null && jsonNullable(item.opt("effectiveCount")) == null
        val manualBatch = item.optBoolean("manualBatch")
        AlertDialog(onDismissRequest = { countItem = null }, title = { Text(if (initialCount) "初盘记录" else "复盘记录") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); Text("系统货位：${item.optString("locationName").ifBlank { item.optString("systemLocationName").ifBlank { "-" } }}", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(8.dp)); if (manualBatch) OutlinedTextField(countBatchNo, { countBatchNo = it }, Modifier.fillMaxWidth(), label = { Text("新增批号") }, singleLine = true) else Text("批号：${item.optString("batchNo").ifBlank { "-" }}", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); OutlinedTextField(countValue, { countValue = it }, Modifier.fillMaxWidth(), label = { Text(if (initialCount) "实际数量" else "复盘数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = countValue.toDoubleOrNull()?.let { it >= 0 } == true && activeCheckId > 0 && (!manualBatch || countBatchNo.isNotBlank()), onClick = { val value = countValue.toDouble(); scope.launch { runCatching { withContext(Dispatchers.IO) { if (initialCount) ApiClient.addGoodsCheckItem(activeCheckId, JSONObject().put("productId", item.optInt("productId")).put("batchNo", if (manualBatch) countBatchNo.trim() else item.optString("batchNo")).put("locationName", item.optString("locationName").ifBlank { item.optString("systemLocationName") }).put("firstCountQty", value)) else ApiClient.recountGoodsCheckItem(item.optInt("id"), JSONObject().put("recountQty", value)) } }.onSuccess { countItem = null; refreshSelectedCheck() }.onFailure { error = it.message ?: "保存盘点数量失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ countItem = null }) { Text("取消") } })
    }
    locationItem?.let { item ->
        AlertDialog(onDismissRequest = { locationItem = null }, title = { Text("修改货位") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); OutlinedTextField(locationValue, { locationValue = it }, Modifier.fillMaxWidth(), label = { Text("盘点货位") }, placeholder = { Text("留空则使用系统货位") }, singleLine = true) } }, confirmButton = { Button(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updateGoodsCheckLocation(item.optInt("id"), JSONObject().put("locationName", locationValue.trim())) } }.onSuccess { locationItem = null; refreshSelectedCheck() }.onFailure { error = it.message ?: "保存货位失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ locationItem = null }) { Text("取消") } })
    }
    LaunchedEffect(candidateVisible, candidateKeyword, activeCheckId) { if (candidateVisible && activeCheckId > 0) runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheckCandidates(activeCheckId, candidateKeyword) } }.onSuccess { values -> candidates = (0 until values.length()).map { values.getJSONObject(it) } }.onFailure { error = it.message ?: "加载候选商品失败" } }
    if (candidateVisible) {
        AlertDialog(
            onDismissRequest = { candidateVisible = false },
            title = { Text("选择盘点商品") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(candidateKeyword, { candidateKeyword = it }, Modifier.fillMaxWidth(), label = { Text("商品名、编码或条码") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) Text("暂无匹配的商品或库存批次", color = Muted, fontSize = 13.sp)
                    candidates.take(30).forEach { candidate ->
                        val product = candidate.optJSONObject("product") ?: candidate
                        OutlinedButton(
                            onClick = {
                                countItem = JSONObject()
                                    .put("product", product)
                                    .put("productId", candidate.optInt("productId", product.optInt("id")))
                                    .put("id", candidate.optInt("checkItemId", 0))
                                    .put("batchNo", candidate.optString("batchNo"))
                                    .put("locationName", candidate.optString("locationName"))
                                    .put("systemLocationName", candidate.optString("locationName"))
                                    .put("systemQty", jsonNullable(candidate.opt("systemQty")) ?: jsonNullable(candidate.opt("quantity")) ?: 0)
                                    .put("firstCountQty", candidate.opt("firstCountQty"))
                                    .put("recountQty", candidate.opt("recountQty"))
                                    .put("effectiveCount", candidate.opt("recountQty") ?: candidate.opt("firstCountQty"))
                                    .put("manualBatch", candidate.optBoolean("manualBatch"))
                                countValue = ""
                                countBatchNo = candidate.optString("batchNo")
                                candidateVisible = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text("${product.optString("productCode", "-")} · ${product.optString("name", "商品")}", fontWeight = FontWeight.SemiBold)
                                Text("批号：${candidate.optString("batchNo").ifBlank { "-" }} · 系统货位：${candidate.optString("locationName").ifBlank { "-" }}", color = Muted, fontSize = 12.sp)
                                Text("库存：${jsonNullable(candidate.opt("quantity")) ?: jsonNullable(candidate.opt("systemQty")) ?: 0} ${product.optString("unit")}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button({ candidateVisible = false }) { Text("关闭") } },
        )
    }
}

private fun jsonNullable(value: Any?): Any? = if (value == null || value == JSONObject.NULL) null else value

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Surface(modifier = modifier, color = PrimarySoft.copy(alpha = .55f), shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = if (danger) Danger else PrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

private fun goodsCheckStatus(status: Int): String = when (status) { 0 -> "待盘点"; 1 -> "盘点中"; 2 -> "已完成"; else -> "未知" }

@Composable
internal fun DifferencesScreen() {
    var tab by remember { mutableStateOf("current") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var products by remember { mutableStateOf<List<JSONObject>?>(null) }
    var logs by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var writeOff by remember { mutableStateOf<JSONObject?>(null) }
    var quantity by remember { mutableStateOf("") }
    var registerVisible by remember { mutableStateOf(false) }
    var registerProduct by remember { mutableStateOf<JSONObject?>(null) }
    var registerQuantity by remember { mutableStateOf("") }
    var registerType by remember { mutableStateOf("PRE_RECEIPT") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab, reload) {
        error = null
        if (tab == "current") runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.differences(), ApiClient.differenceProducts()) } }
            .onSuccess { (summary, values) -> stats = summary; products = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载库存差异失败" }
        else runCatching { withContext(Dispatchers.IO) { ApiClient.differenceLogs() } }
            .onSuccess { values -> logs = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载差异流水失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("当前差异", tab == "current") { tab = "current" }; SegmentedButton("差异流水", tab == "logs") { tab = "logs" } }; Spacer(Modifier.weight(1f)); if (tab == "current") OutlinedButton({ registerVisible = true }) { Text("登记差异") } }
        Spacer(Modifier.height(14.dp))
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (tab == "current") {
            StatsGrid(listOf("有差异货品" to (stats?.opt("total")?.toString() ?: "-"), "实货多" to (stats?.opt("more")?.toString() ?: "-"), "实货少" to (stats?.opt("less")?.toString() ?: "-")))
            Spacer(Modifier.height(14.dp))
            if (products == null && error == null) Text("加载中...", color = Muted)
            if (products != null && products!!.isEmpty()) Text("当前没有库存差异", color = Muted)
            products.orEmpty().forEach { product ->
                val diff = product.optDouble("diffQuantity")
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${product.optString("name")} · ${product.optString("productCode")}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(if (diff > 0) "实货多" else "实货少") }; Spacer(Modifier.height(8.dp)); Text("当前差异：${if (diff > 0) "+" else ""}$diff ${product.optString("unit")}", color = if (diff > 0) Primary else Danger, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Button({ writeOff = product; quantity = kotlin.math.abs(diff).toString() }, shape = RoundedCornerShape(6.dp)) { Text("销账") } } }
            }
        } else {
            if (logs == null && error == null) Text("加载中...", color = Muted)
            if (logs != null && logs!!.isEmpty()) Text("暂无差异流水", color = Muted)
            logs.orEmpty().forEach { log -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Text(diffOperationLabel(log.optString("operationType")), fontWeight = FontWeight.SemiBold); Text("${log.optJSONObject("product")?.optString("productCode") ?: "-"} · ${log.optJSONObject("product")?.optString("name") ?: "商品"}", color = Muted, fontSize = 13.sp); val change = log.optDouble("changeQuantity"); Text("数量变化：${if (change > 0) "+" else ""}$change · ${log.optString("businessDate").take(10)}", color = if (change >= 0) Primary else Danger, fontSize = 12.sp); if (log.optString("operationType") != "REVERSAL" && log.optInt("id") > 0) TextButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.reverseDifference(log.optInt("id"), "安卓端冲销") } }.onSuccess { reload++ }.onFailure { error = it.message ?: "冲销失败" } } }) { Text("冲销") } } } }
        }
    }
    writeOff?.let { product -> AlertDialog(onDismissRequest = { writeOff = null }, title = { Text("差异销账") }, text = { Column { Text("${product.optString("name")} · 当前差异 ${product.optDouble("diffQuantity")}", color = Muted); Spacer(Modifier.height(10.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("销账数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.writeOffDifference(JSONObject().put("productId", product.optInt("id")).put("quantity", quantity.toDouble()).put("businessDate", LocalDate.now().toString())) } }.onSuccess { writeOff = null; reload++ }.onFailure { error = it.message ?: "销账失败" } } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text("确认销账") } }, dismissButton = { TextButton({ writeOff = null }) { Text("取消") } }) }
    if (registerVisible) {
        AlertDialog(
            onDismissRequest = { registerVisible = false },
            title = { Text("登记库存差异") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PRE_RECEIPT" to "先到货", "PRE_SHIPMENT" to "先出货").forEach { (key, label) ->
                            SegmentedButton(label, registerType == key) { registerType = key }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    products.orEmpty().take(8).forEach { product ->
                        OutlinedButton(
                            { registerProduct = product },
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(if (registerProduct?.optInt("id") == product.optInt("id")) "已选：${product.optString("name")}" else product.optString("name"))
                        }
                    }
                    OutlinedTextField(
                        registerQuantity,
                        { registerQuantity = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = registerProduct != null && registerQuantity.toDoubleOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val product = registerProduct
                        if (product != null) {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.registerDifference(
                                            JSONObject()
                                                .put("operationType", registerType)
                                                .put("businessDate", LocalDate.now().toString())
                                                .put("items", JSONArray().put(JSONObject().put("productId", product.optInt("id")).put("quantity", registerQuantity.toDouble())))
                                        )
                                    }
                                }.onSuccess { registerVisible = false; registerProduct = null; registerQuantity = ""; reload++ }
                                    .onFailure { error = it.message ?: "登记差异失败" }
                            }
                        }
                    },
                ) { Text("登记") }
            },
            dismissButton = { TextButton({ registerVisible = false }) { Text("取消") } },
        )
    }
}

private fun diffOperationLabel(value: String): String = when (value) { "PRE_RECEIPT" -> "先到货未入库"; "PRE_SHIPMENT" -> "先出货未销库"; "WRITE_OFF_RECEIPT" -> "入库销账"; "WRITE_OFF_SHIPMENT" -> "销库销账"; "REVERSAL" -> "冲销"; "IMPORT_OPENING" -> "导入期初差异"; "IMPORT_ADJUSTMENT" -> "导入调整"; else -> value }
