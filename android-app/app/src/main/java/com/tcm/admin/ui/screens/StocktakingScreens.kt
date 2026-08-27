package com.tcm.admin

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val refreshSelectedCheck: () -> Unit = {
        if (activeCheckId > 0) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(activeCheckId) } }
                    .onSuccess {
                        selected = it
                        reload++
                    }.onFailure {
                        error = it.message ?: "刷新盘点明细失败"
                    }
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
        runCatching {
            withContext(Dispatchers.IO) {
                Pair(ApiClient.stocktakings(), ApiClient.stores())
            }
        }.onSuccess { (values, storeValues) ->
            checks = (0 until values.length()).map { values.getJSONObject(it) }
            stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            if (selectedStoreId.isBlank() && stores.size == 1) {
                selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
        }.onFailure {
            error = it.message ?: "加载盘点单失败"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (selected == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionHeader("商品盘点", "按商品和库存批次完成实盘记录")
                }
                Button(
                    onClick = { createVisible = true },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("新建盘点")
                }
            }
            Spacer(Modifier.height(16.dp))

            if (checks == null && error == null) AppEmptyState("加载中...")
            if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
            if (checks != null && checks!!.isEmpty()) AppEmptyState("暂无进行中的盘点单")

            checks.orEmpty().forEach { check ->
                val summary = check.optJSONObject("summary") ?: JSONObject()
                val status = check.optInt("status")
                val total = summary.optInt("total")
                val counted = summary.optInt("counted")
                val progress = if (total > 0) counted.toFloat() / total else 0f

                AppCard(
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = check.optString("checkName", "未命名盘点"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "单号：${check.optString("checkNo", "-")}",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        StatusPill(goodsCheckStatus(status))
                    }

                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Primary,
                        trackColor = Color(0xFFF2F3F5),
                    )
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("进度：$counted / $total", color = Muted, fontSize = 12.sp)
                        Text(
                            "平：${summary.optInt("match")}  多：${summary.optInt("more")}  少：${summary.optInt("less")}",
                            color = RegularText,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                activeCheckId = check.optInt("id")
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(activeCheckId) } }
                                        .onSuccess { selected = it }
                                        .onFailure { error = it.message ?: "加载盘点单详情失败" }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text("继续盘点")
                        }
                    }
                }
            }
        } else {
            val check = selected!!
            val items = check.optJSONArray("items") ?: JSONArray()
            val status = check.optInt("status")
            val isCompleted = status == 2

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected = null; activeCheckId = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = check.optString("checkName", "盘点单详情"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Ink,
                    )
                    Text(
                        text = "单号：${check.optString("checkNo", "-")}",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                StatusPill(goodsCheckStatus(status))
            }

            Spacer(Modifier.height(12.dp))

            if (!isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("扫码快速盘点")
                    }
                    Button(
                        onClick = { candidateVisible = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("搜索盘点商品")
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionHeader(
                title = "盘点明细",
                subtitle = "共 ${items.length()} 个商品批次",
            )
            Spacer(Modifier.height(8.dp))

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val product = item.optJSONObject("product") ?: JSONObject()
                val diffQty = item.optDouble("diffQty", 0.0)
                val isCounted = item.opt("countedQty") != null && item.opt("countedQty") != JSONObject.NULL

                AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${product.optString("productCode", "-")} · ${product.optString("name", "商品")}",
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "批号：${item.optString("batchNo").ifBlank { "-" }} · 货位：${item.optString("locationName").ifBlank { "-" }}",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        if (isCounted) {
                            StatusPill(
                                when {
                                    diffQty > 0 -> "实货多 +$diffQty"
                                    diffQty < 0 -> "实货少 $diffQty"
                                    else -> "盘平"
                                },
                            )
                        } else {
                            StatusPill("未盘")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricCell(
                            label = "账面库存",
                            value = "${jsonNullable(item.opt("systemQty")) ?: 0} ${product.optString("unit")}",
                            modifier = Modifier.weight(1f),
                        )
                        MetricCell(
                            label = "实盘数量",
                            value = "${jsonNullable(item.opt("countedQty")) ?: "-"} ${product.optString("unit")}",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (!isCompleted) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    locationItem = item
                                    locationValue = item.optString("locationName")
                                },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text("修改货位")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    countItem = item
                                    countValue = jsonNullable(item.opt("countedQty"))?.toString().orEmpty()
                                    countBatchNo = item.optString("batchNo")
                                },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(if (isCounted) "重盘" else "录入实盘")
                            }
                        }
                    }
                }
            }

            if (!isCompleted) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { ApiClient.completeGoodsCheck(check.optInt("id")) } }
                                .onSuccess {
                                    selected = null
                                    reload++
                                }.onFailure {
                                    error = it.message ?: "完成盘点失败"
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                ) {
                    Text("完成并提交本次盘点", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (createVisible) {
        AlertDialog(
            onDismissRequest = { createVisible = false },
            title = { Text("新建盘点单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = checkName,
                        onValueChange = { checkName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("盘点单名称") },
                        placeholder = { Text("例如：2026年8月中药饮片盘点") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("选择盘点门店", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    StoreChipsRow(
                        stores = stores,
                        selectedStoreId = selectedStoreId,
                        onSelectStore = { selectedStoreId = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = checkName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.createGoodsCheck(
                                        JSONObject()
                                            .put("checkName", checkName.trim())
                                            .put("storeId", selectedStoreId.toIntOrNull() ?: 1),
                                    )
                                }
                            }.onSuccess {
                                createVisible = false
                                checkName = ""
                                reload++
                            }.onFailure {
                                error = it.message ?: "创建盘点单失败"
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { createVisible = false }) {
                    Text("取消")
                }
            },
        )
    }

    countItem?.let { item ->
        val product = item.optJSONObject("product") ?: JSONObject()
        AlertDialog(
            onDismissRequest = { countItem = null },
            title = { Text("录入实盘数量") },
            text = {
                Column {
                    Text(
                        text = "${product.optString("productCode", "-")} · ${product.optString("name", "商品")}",
                        fontWeight = FontWeight.Bold,
                    )
                    Text("规格：${product.optString("specification", "-")} · 单位：${product.optString("unit", "-")}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = countBatchNo,
                        onValueChange = { countBatchNo = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("批号") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countValue,
                        onValueChange = { countValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实盘数量 (${product.optString("unit")})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = countValue.toDoubleOrNull() != null,
                    onClick = {
                        val qty = countValue.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.recordCount(
                                        activeCheckId,
                                        JSONObject()
                                            .put("checkItemId", item.optInt("id"))
                                            .put("countedQty", qty)
                                            .put("batchNo", countBatchNo.trim()),
                                    )
                                }
                            }.onSuccess {
                                countItem = null
                                refreshSelectedCheck()
                            }.onFailure {
                                error = it.message ?: "提交实盘失败"
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { countItem = null }) {
                    Text("取消")
                }
            },
        )
    }

    locationItem?.let { item ->
        AlertDialog(
            onDismissRequest = { locationItem = null },
            title = { Text("修改货位") },
            text = {
                Column {
                    OutlinedTextField(
                        value = locationValue,
                        onValueChange = { locationValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("货位名称或编号") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.updateCheckItemLocation(
                                        activeCheckId,
                                        item.optInt("id"),
                                        JSONObject().put("locationName", locationValue.trim()),
                                    )
                                }
                            }.onSuccess {
                                locationItem = null
                                refreshSelectedCheck()
                            }.onFailure {
                                error = it.message ?: "修改货位失败"
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationItem = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (candidateVisible) {
        AlertDialog(
            onDismissRequest = { candidateVisible = false },
            title = { Text("搜索盘点商品") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SearchBarField(
                        value = candidateKeyword,
                        onValueChange = { candidateKeyword = it },
                        placeholder = "输入商品名称、编码或条码",
                        onSearch = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.searchGoodsCheckCandidates(activeCheckId, candidateKeyword.trim())
                                    }
                                }.onSuccess { values ->
                                    candidates = (0 until values.length()).map { values.getJSONObject(it) }
                                }.onFailure {
                                    error = it.message ?: "搜索商品失败"
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    candidates.forEach { candidate ->
                        val product = candidate.optJSONObject("product") ?: JSONObject()
                        OutlinedButton(
                            onClick = {
                                countItem = candidate
                                countValue = ""
                                countBatchNo = candidate.optString("batchNo")
                                candidateVisible = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "${product.optString("productCode", "-")} · ${product.optString("name", "商品")}",
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                )
                                Text(
                                    text = "批号：${candidate.optString("batchNo").ifBlank { "-" }} · 货位：${candidate.optString("locationName").ifBlank { "-" }}",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { candidateVisible = false }, shape = RoundedCornerShape(8.dp)) {
                    Text("关闭")
                }
            },
        )
    }
}

private fun jsonNullable(value: Any?): Any? = if (value == null || value == JSONObject.NULL) null else value

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = PrimarySoft,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(
                text = value,
                color = PrimaryDark,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

private fun goodsCheckStatus(status: Int): String = when (status) {
    0 -> "待盘点"
    1 -> "盘点中"
    2 -> "盘点完成"
    else -> "未知"
}
