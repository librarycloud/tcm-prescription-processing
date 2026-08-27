package com.tcm.admin

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
internal fun StocktakingScreen(onNavigate: (ScreenTarget) -> Unit) {
    var checks by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var checkName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, selectedStoreId) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                Pair(ApiClient.stocktaking(selectedStoreId.toIntOrNull()), ApiClient.availableStores())
            }
        }.onSuccess { (values, storeValues) ->
            checks = (0 until values.length()).map { values.getJSONObject(it) }
            stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionHeader("商品盘点", "益达商品盘点单与差异录入")
            }
            Button(
                onClick = { createVisible = true },
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建盘点")
            }
        }

        Spacer(Modifier.height(14.dp))

        if (stores.size > 1) {
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it },
            )
            Spacer(Modifier.height(14.dp))
        }

        if (checks == null && error == null) AppEmptyState("加载盘点列表中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (checks != null && checks!!.isEmpty()) AppEmptyState("暂无盘点单记录")

        checks.orEmpty().forEach { check ->
            val status = check.optInt("status")
            val total = check.optInt("totalItems", 0)
            val counted = check.optInt("countedItems", 0)
            val diff = check.optInt("diffItems", 0)
            val progress = if (total > 0) (counted.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

            AppCard(
                modifier = Modifier.padding(bottom = 12.dp),
                onClick = { onNavigate(ScreenTarget.StocktakingDetail(check.optInt("id"))) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = check.optString("checkNo"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink,
                    )
                    StatusPill(text = goodsCheckStatus(status))
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = check.optString("name", "未命名盘点"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Ink,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCell("总条目", total.toString(), Modifier.weight(1f))
                    MetricCell("已盘点", counted.toString(), Modifier.weight(1f))
                    MetricCell("有差异", diff.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Primary,
                    trackColor = Color(0xFFE5E6EB),
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(ScreenTarget.StocktakingDetail(check.optInt("id"))) },
                        shape = FieldShape,
                    ) {
                        Text("进入盘点明细")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (createVisible) {
        AlertDialog(
            onDismissRequest = { createVisible = false },
            title = { Text("新建盘点单", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = checkName,
                        onValueChange = { checkName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("盘点单名称") },
                        placeholder = { Text("如：2026年3月全店盘点") },
                        singleLine = true,
                        shape = FieldShape,
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
                                        JSONObject().put("name", checkName.trim()),
                                        selectedStoreId.toIntOrNull(),
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
                    shape = FieldShape,
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
}

@Composable
internal fun StocktakingDetailScreen(
    checkId: Int,
    onBack: () -> Unit,
) {
    var check by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
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

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            candidateKeyword = value
            candidateVisible = true
        }
    }

    LaunchedEffect(checkId, reload) {
        runCatching {
            withContext(Dispatchers.IO) { ApiClient.goodsCheck(checkId) }
        }.onSuccess { check = it }
            .onFailure { error = it.message ?: "加载盘点详情失败" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (check == null && error == null) AppEmptyState("加载盘点明细中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)

        check?.let { selected ->
            val items = selected.optJSONArray("items") ?: JSONArray()
            val total = selected.optInt("totalItems", 0)
            val counted = selected.optInt("countedItems", 0)
            val diff = selected.optInt("diffItems", 0)

            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected.optString("name", "盘点明细"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Ink,
                    )
                    StatusPill(text = goodsCheckStatus(selected.optInt("status")))
                }

                Spacer(Modifier.height(8.dp))

                Text("单号：${selected.optString("checkNo")}", color = Muted, fontSize = 12.sp)

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCell("总项数", total.toString(), Modifier.weight(1f))
                    MetricCell("已盘", counted.toString(), Modifier.weight(1f))
                    MetricCell("差异", diff.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { candidateVisible = true },
                        modifier = Modifier.weight(1f),
                        shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("录入盘点")
                    }
                    OutlinedButton(
                        onClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                        shape = FieldShape,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("盘点条目明细", "共 ${items.length()} 个商品条目")
            Spacer(Modifier.height(10.dp))

            (0 until items.length()).forEach { index ->
                val item = items.getJSONObject(index)
                val product = item.optJSONObject("product") ?: JSONObject()
                val isCounted = item.optInt("status") == 1
                val systemQty = item.optDouble("systemQuantity", 0.0)
                val actualQty = item.opt("actualQuantity")?.toString()?.takeIf { it != "null" }
                val diffQty = actualQty?.toDoubleOrNull()?.let { it - systemQty }

                AppCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${product.optString("productCode", "-")} · ${product.optString("name", "商品")}",
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "批号：${item.optString("batchNo").ifBlank { "-" }} · 货位：${item.optString("locationName").ifBlank { "未设置" }}",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "系统库存：$systemQty · 实盘数量：${actualQty ?: "未盘"}",
                                color = RegularText,
                                fontSize = 12.sp,
                            )
                        }

                        if (diffQty != null) {
                            StatusPill(
                                text = if (diffQty == 0.0) "正常" else if (diffQty > 0) "实货多" else "实货少",
                            )
                        }
                    }

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
                            shape = FieldShape,
                        ) {
                            Text("修改货位")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                countItem = item
                                countValue = actualQty ?: ""
                                countBatchNo = item.optString("batchNo")
                            },
                            shape = FieldShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Text(if (isCounted) "重录实盘" else "录入实盘")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        ) {
            Text("返回盘点列表")
        }

        Spacer(Modifier.height(16.dp))
    }

    countItem?.let { item ->
        val product = item.optJSONObject("product") ?: JSONObject()
        AlertDialog(
            onDismissRequest = { countItem = null },
            title = { Text("录入实盘数量", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "${product.optString("productCode", "-")} · ${product.optString("name", "商品")}",
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countBatchNo,
                        onValueChange = { countBatchNo = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("批号") },
                        singleLine = true,
                        shape = FieldShape,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countValue,
                        onValueChange = { countValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实盘数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = FieldShape,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = countValue.toDoubleOrNull() != null,
                    onClick = {
                        val value = countValue.toDoubleOrNull() ?: return@Button
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.recordCheckItemCount(
                                        checkId,
                                        item.optInt("id"),
                                        JSONObject().put("actualQuantity", value).put("batchNo", countBatchNo.trim()),
                                    )
                                }
                            }.onSuccess {
                                countItem = null
                                reload++
                            }.onFailure {
                                error = it.message ?: "录入实盘失败"
                            }
                        }
                    },
                    shape = FieldShape,
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { countItem = null }) { Text("取消") }
            },
        )
    }

    locationItem?.let { item ->
        AlertDialog(
            onDismissRequest = { locationItem = null },
            title = { Text("修改商品货位", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = locationValue,
                        onValueChange = { locationValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("货位名称") },
                        placeholder = { Text("例如：A-01-02") },
                        singleLine = true,
                        shape = FieldShape,
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
                                        checkId,
                                        item.optInt("id"),
                                        JSONObject().put("locationName", locationValue.trim()),
                                    )
                                }
                            }.onSuccess {
                                locationItem = null
                                reload++
                            }.onFailure {
                                error = it.message ?: "修改货位失败"
                            }
                        }
                    },
                    shape = FieldShape,
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationItem = null }) { Text("取消") }
            },
        )
    }

    if (candidateVisible) {
        AlertDialog(
            onDismissRequest = { candidateVisible = false },
            title = { Text("搜索盘点商品", fontWeight = FontWeight.Bold) },
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
                                        ApiClient.searchGoodsCheckCandidates(checkId, candidateKeyword.trim())
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
                            shape = FieldShape,
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
                Button(onClick = { candidateVisible = false }, shape = FieldShape) {
                    Text("关闭")
                }
            },
        )
    }
}

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
