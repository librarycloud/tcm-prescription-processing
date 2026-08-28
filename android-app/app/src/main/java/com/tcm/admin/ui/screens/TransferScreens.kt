package com.tcm.admin

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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
internal fun TransfersScreen() {
    var transfers by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var keyword by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<Int?>(null) }
    var overdueOnly by remember { mutableStateOf(false) }
    var selectedStoreId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var createVisible by remember { mutableStateOf(false) }
    var fromStoreId by remember { mutableStateOf("") }
    var toStoreId by remember { mutableStateOf("") }
    var expectedReturnDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var itemName by remember { mutableStateOf("") }
    var itemSpecification by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemUnit by remember { mutableStateOf("") }
    var returnItem by remember { mutableStateOf<JSONObject?>(null) }
    var returnQuantity by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, keyword, statusFilter, overdueOnly, selectedStoreId) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                Triple(
                    ApiClient.transfers(keyword, statusFilter, selectedStoreId.toIntOrNull(), overdueOnly),
                    ApiClient.transferStores(),
                    ApiClient.transferStats(selectedStoreId.toIntOrNull()),
                )
            }
        }.onSuccess { (transferValues, storeValues, summary) ->
            transfers = (0 until transferValues.length()).map { transferValues.getJSONObject(it) }
            stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            stats = summary
        }.onFailure {
            error = it.message ?: "加载门店调拨失败"
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
                SectionHeader("门店调拨", "跨门店物资借调与归还跟踪")
            }
            Button(
                onClick = { createVisible = true },
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建调拨")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Stats
        stats?.let {
            StatsGrid(
                listOf(
                    "借出中" to it.optInt("borrowing").toString(),
                    "部分归还" to it.optInt("partReturned").toString(),
                    "已逾期" to it.optInt("overdue").toString(),
                ),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Search Bar
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "输入单号、门店、物品或批号",
            onSearch = { reload++ },
        )

        Spacer(Modifier.height(10.dp))

        // Status Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedButton("全部状态", statusFilter == null && !overdueOnly, onClick = {
                statusFilter = null
                overdueOnly = false
            })
            SegmentedButton("借出中", statusFilter == 0 && !overdueOnly, onClick = {
                statusFilter = 0
                overdueOnly = false
            })
            SegmentedButton("部分归还", statusFilter == 1 && !overdueOnly, onClick = {
                statusFilter = 1
                overdueOnly = false
            })
            SegmentedButton("已逾期", overdueOnly, onClick = {
                statusFilter = null
                overdueOnly = true
            })
        }

        if (stores.size > 1) {
            Spacer(Modifier.height(8.dp))
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it },
            )
        }

        Spacer(Modifier.height(14.dp))

        if (transfers == null && error == null) AppEmptyState("加载中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (transfers != null && transfers!!.isEmpty()) AppEmptyState("暂无调拨单")

        transfers.orEmpty().forEach { transfer ->
            val items = transfer.optJSONArray("items") ?: JSONArray()
            val status = transfer.optInt("status")
            val outboundStatus = transfer.optInt("outboundStatus")
            val isOverdue = transfer.optBoolean("overdue")

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = transfer.displayField("transferNo"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink,
                    )
                    StatusPill(
                        text = if (isOverdue) "已逾期" else transferStatusLabel(status, outboundStatus),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    color = Color(0xFFF9FAFB),
                    shape = FieldShape,
                    border = BorderStroke(1.dp, Color(0xFFEAECF0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = transfer.optJSONObject("fromStore")?.displayField("name") ?: "-",
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = transfer.optJSONObject("toStore")?.displayField("name") ?: "-",
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                InfoRowItem(
                    label = "调拨物品",
                    value = "${items.length()} 项",
                )
                InfoRowItem(
                    label = "调拨日期",
                            value = transfer.displayField("transferDate").take(10),
                )
                InfoRowItem(
                    label = "预计归还",
                            value = transfer.displayField("expectedReturnDate").take(10),
                    valueColor = if (isOverdue) Danger else Ink,
                    isBold = isOverdue,
                )

                if (outboundStatus == 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("提示：调出方尚未确认出库", color = Warning, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.transferDetail(transfer.optInt("id"))
                                    }
                                }.onSuccess { detail = it }
                                    .onFailure { error = it.message ?: "加载调拨详情失败" }
                            }
                        },
                        shape = FieldShape,
                    ) {
                        Text("查看详情")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // Create Transfer Dialog
    if (createVisible) {
        val valid = fromStoreId.isNotBlank() && toStoreId.isNotBlank() && fromStoreId != toStoreId &&
            itemName.isNotBlank() && itemQuantity.toDoubleOrNull()?.let { it > 0 } == true

        AlertDialog(
            onDismissRequest = { createVisible = false },
            title = { Text("新建门店调拨", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("调出门店", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        stores.forEach { store ->
                            val id = store.opt("id")?.toString().orEmpty()
                            SegmentedButton(store.displayField("name", "门店"), fromStoreId == id, onClick = { fromStoreId = id })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("调入门店", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        stores.forEach { store ->
                            val id = store.opt("id")?.toString().orEmpty()
                            SegmentedButton(store.displayField("name", "门店"), toStoreId == id, onClick = { toStoreId = id })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = expectedReturnDate,
                        onValueChange = { expectedReturnDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("预计归还日期 (YYYY-MM-DD)") },
                        singleLine = true,
                        shape = FieldShape,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("调拨物品", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("物品名称") },
                        singleLine = true,
                        shape = FieldShape,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = itemSpecification,
                        onValueChange = { itemSpecification = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("规格（可选）") },
                        singleLine = true,
                        shape = FieldShape,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemQuantity,
                            onValueChange = { itemQuantity = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("数量") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = FieldShape,
                        )
                        OutlinedTextField(
                            value = itemUnit,
                            onValueChange = { itemUnit = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("单位（如：盒）") },
                            singleLine = true,
                            shape = FieldShape,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = JSONObject()
                            .put("itemName", itemName.trim())
                            .put("specification", itemSpecification.trim())
                            .put("quantity", itemQuantity.toDouble())
                            .put("unit", itemUnit.trim().ifBlank { "件" })
                        val payload = JSONObject()
                            .put("fromStoreId", fromStoreId.toInt())
                            .put("toStoreId", toStoreId.toInt())
                            .put("expectedReturnDate", expectedReturnDate)
                            .put("items", JSONArray().put(item))

                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { ApiClient.createTransfer(payload) }
                            }.onSuccess {
                                createVisible = false
                                itemName = ""
                                itemSpecification = ""
                                itemQuantity = "1"
                                itemUnit = ""
                                reload++
                            }.onFailure {
                                error = it.message ?: "创建调拨失败"
                            }
                        }
                    },
                    enabled = valid,
                    shape = FieldShape,
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { createVisible = false }) { Text("取消") }
            },
        )
    }

    // Transfer Detail Dialog
    detail?.let { transfer ->
        val items = transfer.optJSONArray("items") ?: JSONArray()
        val canConfirm = transfer.optJSONObject("permissions")?.optBoolean("canConfirmOutbound") == true
        val pendingReturn = transfer.optJSONArray("returnRecords")?.let { records ->
            (0 until records.length()).map { records.getJSONObject(it) }.firstOrNull { it.optInt("status") == 0 }
        }

        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(transfer.displayField("transferNo"), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${transfer.optJSONObject("fromStore")?.displayField("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.displayField("name") ?: "-"}",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    (0 until items.length()).forEach { index ->
                        val item = items.getJSONObject(index)
                        Text(item.displayField("itemName", "物资"), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${quantityText(item.opt("quantity"), "0")} ${item.displayField("unit")} · 已归还 ${quantityText(item.opt("returnedQuantity"), "0")}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        val available = item.optDouble("availableReturnQuantity", 0.0)
                        if (available > 0 && transfer.optJSONObject("permissions")?.optBoolean("canSubmitReturn") == true) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    returnItem = item
                                    returnQuantity = quantityText(available, "0")
                                },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text("申请归还")
                            }
                        }
                        if (index < items.length() - 1) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFF2F3F5))
                        }
                    }
                    pendingReturn?.let { record ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "待确认归还：${quantityText(record.opt("quantity"), "0")} · ${record.displayField("returnDate").take(10)}",
                            color = Warning,
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canConfirm) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { ApiClient.confirmOutbound(transfer.optInt("id")) }
                                    }.onSuccess { detail = null; reload++ }
                                        .onFailure { error = it.message ?: "确认调出失败" }
                                }
                            },
                            shape = FieldShape,
                        ) {
                            Text("确认调出")
                        }
                    }
                    if (pendingReturn != null && transfer.optJSONObject("permissions")?.optBoolean("canConfirmReturn") == true) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { ApiClient.confirmReturn(transfer.optInt("id"), pendingReturn.optInt("id")) }
                                    }.onSuccess { detail = null; reload++ }
                                        .onFailure { error = it.message ?: "确认归还失败" }
                                }
                            },
                            shape = FieldShape,
                        ) {
                            Text("确认归还")
                        }
                    }
                    if (transfer.optJSONObject("permissions")?.optBoolean("canCancel") == true) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) { ApiClient.cancelTransfer(transfer.optInt("id"), "安卓端取消") }
                                    }.onSuccess { detail = null; reload++ }
                                        .onFailure { error = it.message ?: "取消调拨失败" }
                                }
                            },
                            shape = FieldShape,
                        ) {
                            Text("取消")
                        }
                    }
                    OutlinedButton(onClick = { detail = null }, shape = FieldShape) {
                        Text("关闭")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) {
                    Text("关闭")
                }
            },
        )
    }

    // Return Dialog
    returnItem?.let { item ->
        AlertDialog(
            onDismissRequest = { returnItem = null },
            title = { Text("申请归还", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(item.displayField("itemName", "物资"), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = returnQuantity,
                        onValueChange = { returnQuantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("归还数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = FieldShape,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = returnQuantity.toDoubleOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val transferId = detail?.optInt("id") ?: 0
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.addTransferReturns(
                                        transferId,
                                        JSONObject()
                                            .put("returnDate", LocalDate.now().toString())
                                            .put(
                                                "items",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("transferItemId", item.optInt("id"))
                                                        .put("quantity", returnQuantity.toDouble()),
                                                ),
                                            ),
                                    )
                                }
                            }.onSuccess { returnItem = null; detail = null; reload++ }
                                .onFailure { error = it.message ?: "提交归还失败" }
                        }
                    },
                    shape = FieldShape,
                ) {
                    Text("提交")
                }
            },
            dismissButton = {
                TextButton(onClick = { returnItem = null }) { Text("取消") }
            },
        )
    }
}
