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
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.inventory(query.trim()) } }
            .onSuccess { values -> products = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载库存失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索商品名称、编号或条码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(14.dp))
        if (products == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (products != null && products!!.isEmpty()) Text("暂无库存商品", color = Muted)
        products.orEmpty().forEach { product ->
            val inventories = product.optJSONArray("inventories") ?: JSONArray()
            val locations = (0 until inventories.length()).joinToString("、") { inventories.getJSONObject(it).optString("locationName", "-") }.ifBlank { "-" }
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.optString("name"), fontWeight = FontWeight.SemiBold)
                    Text("${product.optString("productCode")} · ${product.optString("specification").ifBlank { "未填写规格" }}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("库存 ${product.opt("totalQuantity") ?: 0} ${product.optString("unit")}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text("库位：$locations", color = Muted, fontSize = 12.sp)
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
    var candidateVisible by remember { mutableStateOf(false) }
    var candidateKeyword by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var locationItem by remember { mutableStateOf<JSONObject?>(null) }
    var locationValue by remember { mutableStateOf("") }
    var detailLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("盘点单"); Spacer(Modifier.weight(1f)); Button({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建盘点") } }
        Spacer(Modifier.height(14.dp))
        if (checks == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (checks != null && checks!!.isEmpty()) Text("暂无盘点单", color = Muted)
        checks.orEmpty().forEach { check ->
            val summary = check.optJSONObject("summary") ?: JSONObject()
            val status = check.optInt("status")
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(check.optString("checkName"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(goodsCheckStatus(status)) }
                    Spacer(Modifier.height(7.dp))
                    Text("${check.optJSONObject("store")?.optString("name") ?: "-"} · 共 ${summary.optInt("total")} 项，已盘 ${summary.optInt("counted")} 项", color = Muted, fontSize = 13.sp)
                    Text("创建于 ${check.optString("createdAt").take(10)}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton({
                        detailLoading = true
                        scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(check.optInt("id")) } }.onSuccess { selected = it; activeCheckId = check.optInt("id") }.onFailure { error = it.message ?: "加载盘点明细失败" }; detailLoading = false }
                    }, shape = RoundedCornerShape(6.dp)) { Text(if (detailLoading) "加载中..." else "查看盘点") }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建盘点") }, text = { Column { if (stores.size > 1) { Text("所属门店", color = Muted, fontSize = 12.sp); StoreSelector(stores, selectedStoreId) { selectedStoreId = it }; Spacer(Modifier.height(10.dp)) }; OutlinedTextField(checkName, { checkName = it }, Modifier.fillMaxWidth(), label = { Text("盘点名称") }, singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createGoodsCheck(checkName.trim(), storeId = selectedStoreId.toIntOrNull()) } }.onSuccess { createVisible = false; checkName = ""; reload++ }.onFailure { error = it.message ?: "创建盘点单失败" } } }, enabled = checkName.isNotBlank() && (stores.size <= 1 || selectedStoreId.isNotBlank())) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
    selected?.let { check ->
        val items = check.optJSONArray("items") ?: JSONArray()
        val completed = check.optInt("status") == 2
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(check.optString("checkName")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${check.optJSONObject("store")?.optString("name") ?: "-"} · ${goodsCheckStatus(check.optInt("status"))}", color = Muted); Spacer(Modifier.height(10.dp)); if (!completed) OutlinedButton({ candidateVisible = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text("搜索商品并录入盘点") }; if (items.length() == 0) Text("尚无盘点记录", color = Muted); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optJSONObject("product")?.optString("name") ?: "商品", fontWeight = FontWeight.SemiBold); Text("系统 ${item.opt("systemQty") ?: 0} · 实盘 ${item.opt("effectiveCount") ?: "未盘"}", color = Muted, fontSize = 12.sp); if (!completed) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton({ countItem = item; countValue = item.opt("effectiveCount")?.toString().orEmpty() }, shape = RoundedCornerShape(6.dp)) { Text(if (item.opt("effectiveCount") == null) "录入实盘" else "修改实盘") }; if (item.optInt("id") > 0 && item.opt("effectiveCount") != null) OutlinedButton({ countItem = item; countValue = item.opt("effectiveCount")?.toString().orEmpty() }, shape = RoundedCornerShape(6.dp)) { Text("复盘") } } }; if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) } } }, confirmButton = { if (completed) Button({ selected = null }) { Text("关闭") } else Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishGoodsCheck(check.optInt("id")) } }.onSuccess { selected = null; reload++ }.onFailure { error = it.message ?: "结束盘点失败" } } }) { Text("结束盘点") } }, dismissButton = { TextButton({ selected = null }) { Text("关闭") } })
    }
    countItem?.let { item ->
        AlertDialog(onDismissRequest = { countItem = null }, title = { Text("录入盘点数量") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); OutlinedTextField(countValue, { countValue = it }, Modifier.fillMaxWidth(), label = { Text("实际数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = countValue.toDoubleOrNull()?.let { it >= 0 } == true && activeCheckId > 0, onClick = { val value = countValue.toDouble(); scope.launch { runCatching { withContext(Dispatchers.IO) { if (item.opt("firstCountQty") == null && item.opt("effectiveCount") == null) ApiClient.addGoodsCheckItem(activeCheckId, JSONObject().put("productId", item.optInt("productId")).put("batchNo", item.optString("batchNo")).put("locationName", item.optString("locationName")).put("firstCountQty", value)) else ApiClient.recountGoodsCheckItem(item.optInt("id"), JSONObject().put("recountQty", value)) } }.onSuccess { countItem = null; selected = null; reload++ }.onFailure { error = it.message ?: "保存盘点数量失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ countItem = null }) { Text("取消") } })
    }
    locationItem?.let { item ->
        AlertDialog(onDismissRequest = { locationItem = null }, title = { Text("修改货位") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); OutlinedTextField(locationValue, { locationValue = it }, Modifier.fillMaxWidth(), label = { Text("货位名称") }, singleLine = true) } }, confirmButton = { Button(enabled = locationValue.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updateGoodsCheckLocation(item.optInt("id"), JSONObject().put("locationName", locationValue.trim())) } }.onSuccess { locationItem = null; selected = null; reload++ }.onFailure { error = it.message ?: "保存货位失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ locationItem = null }) { Text("取消") } })
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
                    candidates.take(30).forEach { candidate ->
                        val product = candidate.optJSONObject("product") ?: candidate
                        OutlinedButton(
                            onClick = {
                                countItem = JSONObject()
                                    .put("product", product)
                                    .put("productId", candidate.optInt("productId", product.optInt("id")))
                                    .put("batchNo", candidate.optString("batchNo"))
                                    .put("locationName", candidate.optString("locationName"))
                                countValue = ""
                                candidateVisible = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text("${product.optString("name")} · ${candidate.opt("quantity") ?: 0} ${product.optString("unit")}")
                        }
                    }
                }
            },
            confirmButton = { Button({ candidateVisible = false }) { Text("关闭") } },
        )
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
    if (registerVisible) AlertDialog(onDismissRequest = { registerVisible = false }, title = { Text("登记库存差异") }, text = { Column { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("PRE_RECEIPT" to "先到货", "PRE_SHIPMENT" to "先出货").forEach { (key, label) -> SegmentedButton(label, registerType == key) { registerType = key } } }; Spacer(Modifier.height(8.dp)); products.orEmpty().take(8).forEach { product -> OutlinedButton({ registerProduct = product }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text(if (registerProduct?.optInt("id") == product.optInt("id")) "已选：${product.optString("name")}" else product.optString("name")) } }; OutlinedTextField(registerQuantity, { registerQuantity = it }, Modifier.fillMaxWidth(), label = { Text("数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = registerProduct != null && registerQuantity.toDoubleOrNull()?.let { it > 0 } == true, onClick = { val product = registerProduct!!; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.registerDifference(JSONObject().put("operationType", registerType).put("businessDate", LocalDate.now().toString()).put("items", JSONArray().put(JSONObject().put("productId", product.optInt("id")).put("quantity", registerQuantity.toDouble()))) } }.onSuccess { registerVisible = false; registerProduct = null; registerQuantity = ""; reload++ }.onFailure { error = it.message ?: "登记差异失败" } } }) { Text("登记") } }, dismissButton = { TextButton({ registerVisible = false }) { Text("取消") } })
}

private fun diffOperationLabel(value: String): String = when (value) { "PRE_RECEIPT" -> "先到货未入库"; "PRE_SHIPMENT" -> "先出货未销库"; "WRITE_OFF_RECEIPT" -> "入库销账"; "WRITE_OFF_SHIPMENT" -> "销库销账"; "REVERSAL" -> "冲销"; "IMPORT_OPENING" -> "导入期初差异"; "IMPORT_ADJUSTMENT" -> "导入调整"; else -> value }
