package com.tcm.admin

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tcm.admin.ui.viewmodels.TransferViewModel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransfersScreen(
    user: JSONObject?,
    onNavigate: (ScreenTarget) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val showStore = user?.optInt("role", -1) == 0
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val overdueOnly by viewModel.overdueOnly.collectAsStateWithLifecycle()
    val selectedStoreId by viewModel.selectedStoreId.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val transfers = viewModel.transfersFlow.collectAsLazyPagingItems()

    var createVisible by remember { mutableStateOf(false) }
    var fromStoreId by remember { mutableStateOf("") }
    var toStoreId by remember { mutableStateOf("") }
    var expectedReturnDate by remember { mutableStateOf(serverToday().plusDays(7).toString()) }
    var itemName by remember { mutableStateOf("") }
    var itemSpecification by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemUnit by remember { mutableStateOf("") }
    var lastAutoKeyword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(showStore) {
        if (showStore) viewModel.loadStores()
    }

    LaunchedEffect(stores) {
        if (showStore && stores.size == 1 && selectedStoreId == null) {
            val sid = stores.first().optInt("id")
            viewModel.selectedStoreId.value = sid
        }
    }

    LaunchedEffect(selectedStoreId) {
        viewModel.refreshStats(selectedStoreId)
    }

    LaunchedEffect(keyword) {
        val term = keyword.trim()
        if (term.isBlank() || !shouldAutoSearchQuery(term)) {
            lastAutoKeyword = ""
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        if (keyword.trim() == term && lastAutoKeyword != term) {
            lastAutoKeyword = term
            transfers.refresh()
        }
    }

    val isRefreshing = transfers.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            ApiClient.clearResponseCache(context)
            transfers.refresh()
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
                        selectedIndex = when {
                            overdueOnly -> 2
                            statusFilter == 0 -> 0
                            statusFilter == 1 -> 1
                            else -> null
                        },
                        onItemClick = { index ->
                            when (index) {
                                0 -> {
                                    viewModel.statusFilter.value = 0
                                    viewModel.overdueOnly.value = false
                                }
                                1 -> {
                                    viewModel.statusFilter.value = 1
                                    viewModel.overdueOnly.value = false
                                }
                                2 -> {
                                    viewModel.statusFilter.value = null
                                    viewModel.overdueOnly.value = true
                                }
                            }
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Search Bar
                SearchBarField(
                    value = keyword,
                    onValueChange = {
                        viewModel.keyword.value = it
                        if (it.isBlank()) transfers.refresh()
                    },
                    placeholder = "输入单号、门店、物品或批号",
                    onSearch = {
                        lastAutoKeyword = keyword.trim()
                        transfers.refresh()
                    },
                )

                Spacer(Modifier.height(10.dp))

                // Status Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedButton("全部状态", statusFilter == null && !overdueOnly, onClick = {
                        viewModel.statusFilter.value = null
                        viewModel.overdueOnly.value = false
                    })
                    SegmentedButton("借出中", statusFilter == 0 && !overdueOnly, onClick = {
                        viewModel.statusFilter.value = 0
                        viewModel.overdueOnly.value = false
                    })
                    SegmentedButton("部分归还", statusFilter == 1 && !overdueOnly, onClick = {
                        viewModel.statusFilter.value = 1
                        viewModel.overdueOnly.value = false
                    })
                    SegmentedButton("已逾期", overdueOnly, onClick = {
                        viewModel.statusFilter.value = null
                        viewModel.overdueOnly.value = true
                    })
                }

                if (showStore && stores.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    StoreChipsRow(
                        stores = stores,
                        selectedStoreId = selectedStoreId?.toString().orEmpty(),
                        onSelectStore = {
                            viewModel.selectedStoreId.value = it.toIntOrNull()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
            }

            if (transfers.loadState.refresh is LoadState.Error) {
                item(key = "error") {
                    val err = (transfers.loadState.refresh as LoadState.Error).error
                    ErrorStateView(message = err.message ?: "加载失败", onRetry = { transfers.retry() })
                }
            }

            if (transfers.loadState.refresh is LoadState.Loading && transfers.itemCount == 0) {
                item(key = "loading") {
                    AppEmptyState("加载中...")
                }
            } else if (transfers.itemCount == 0 && transfers.loadState.refresh !is LoadState.Error && transfers.loadState.refresh !is LoadState.Loading) {
                item(key = "empty") {
                    AppEmptyState("暂无调拨单")
                }
            }

            items(count = transfers.itemCount, key = transfers.itemKey { it.optInt("id") }) { index ->
                val transfer = transfers[index]
                if (transfer != null) {
                    val items = transfer.optJSONArray("items") ?: JSONArray()
                    val status = transfer.optInt("status")
                    val outboundStatus = transfer.optInt("outboundStatus")
                    val isOverdue = transfer.optBoolean("overdue")

                    AppCard(
                        modifier = Modifier.padding(bottom = 12.dp),
                        onClick = { onNavigate(ScreenTarget.TransferDetail(transfer.optInt("id"))) },
                    ) {
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
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

                        val count = items.length()
                        val itemNames = (0 until count)
                            .mapNotNull { items.optJSONObject(it)?.optString("itemName")?.trim() }
                            .filter { it.isNotBlank() }
                        val itemsDisplay = when {
                            itemNames.isEmpty() -> "-"
                            count > 2 -> "${itemNames.take(2).joinToString("、")} 等${count}项"
                            else -> itemNames.joinToString("、")
                        }

                        InfoRowItem(
                            label = "调拨物品",
                            value = itemsDisplay,
                        )
                        InfoRowItem(
                            label = "调拨日期",
                            value = serverDateOnly(transfer.opt("transferDate"), "-"),
                        )
                        InfoRowItem(
                            label = "预计归还",
                            value = serverDateOnly(transfer.opt("expectedReturnDate"), "-"),
                            valueColor = if (isOverdue) Danger else Ink,
                            isBold = isOverdue,
                        )

                        if (outboundStatus == 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("提示：调出方尚未确认出库", color = Warning, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            if (transfers.loadState.append is LoadState.Loading) {
                item(key = "append_loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                    }
                }
            }
        }
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
                                transfers.refresh()
                                viewModel.refreshStats(selectedStoreId)
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "创建调拨失败", Toast.LENGTH_SHORT).show()
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
}

@Composable
internal fun TransferDetailScreen(
    id: Int,
    onBack: () -> Unit,
) {
    var transfer by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var reload by remember(id) { mutableStateOf(0) }
    var returnItem by remember { mutableStateOf<JSONObject?>(null) }
    var returnQuantity by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.transferDetail(id) } }
            .onSuccess { transfer = it }
            .onFailure { error = it.message ?: "加载调拨详情失败" }
    }

    val current = transfer
    if (current == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error == null) AppEmptyState("正在加载调拨详情...")
            else ErrorStateView(message = error!!, onRetry = { reload++ })
        }
        return
    }

    val items = current.optJSONArray("items") ?: JSONArray()
    val records = current.optJSONArray("returnRecords") ?: JSONArray()
    val permissions = current.optJSONObject("permissions")
    val isOverdue = current.optBoolean("overdue")

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
                Column(Modifier.weight(1f)) {
                    Text(current.displayField("transferNo"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${current.optJSONObject("fromStore")?.displayField("name") ?: "-"}  ->  ${current.optJSONObject("toStore")?.displayField("name") ?: "-"}",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
                StatusPill(if (isOverdue) "已逾期" else transferStatusLabel(current.optInt("status"), current.optInt("outboundStatus")))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("调拨信息")
        Spacer(Modifier.height(8.dp))
        AppCard {
            InfoRowItem("调拨日期", serverDateOnly(current.opt("transferDate"), "-"))
            InfoRowItem(
                "预计归还",
                serverDateOnly(current.opt("expectedReturnDate"), "-"),
                valueColor = if (isOverdue) Danger else Ink,
                isBold = isOverdue,
            )
            InfoRowItem("创建人", transferOperatorLabel(current.optJSONObject("creator")))
            InfoRowItem("创建时间", transferDateTime(current.opt("createdAt")))
            val remark = displayText(current.opt("remark"))
            if (remark != "-") InfoRowItem("备注", remark)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("借出确认")
        Spacer(Modifier.height(8.dp))
        AppCard {
            val confirmed = current.optInt("outboundStatus") == 1
            InfoRowItem(
                "状态",
                if (confirmed) "已确认调出" else "待确认调出",
                valueColor = if (confirmed) Success else Warning,
                isBold = true,
            )
            InfoRowItem("确认人", transferOperatorLabel(current.optJSONObject("outboundConfirmer")))
            InfoRowItem("确认时间", transferDateTime(current.opt("outboundConfirmedAt")))
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("调拨明细")
        Spacer(Modifier.height(8.dp))
        (0 until items.length()).forEach { index ->
            val item = items.getJSONObject(index)
            AppCard(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.displayField("itemName", "物资"), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val meta = listOf(item.displayField("specification", ""), item.displayField("batchNo", "")).filter { it.isNotBlank() }
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        "${quantityText(item.opt("quantity"), "0")} ${item.displayField("unit")}",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                InfoRowItem("已确认归还", "${quantityText(item.opt("returnedQuantity"), "0")} ${item.displayField("unit")}")
                InfoRowItem("待确认归还", "${quantityText(item.opt("pendingReturnQuantity"), "0")} ${item.displayField("unit")}", valueColor = Warning)
                InfoRowItem("剩余待归还", "${quantityText(item.opt("remainingQuantity"), "0")} ${item.displayField("unit")}", isBold = true)
                if (permissions?.optBoolean("canSubmitReturn") == true && item.optDouble("availableReturnQuantity") > 0) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            returnItem = item
                            returnQuantity = quantityText(item.opt("availableReturnQuantity"), "0")
                        },
                        modifier = Modifier.height(34.dp),
                        shape = FieldShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text("申请归还", fontSize = 12.sp) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionHeader("归还记录")
        Spacer(Modifier.height(8.dp))
        if (records.length() == 0) {
            AppCard { AppEmptyState("暂无归还记录") }
        } else {
            (0 until records.length()).forEach { index ->
                val record = records.getJSONObject(index)
                val confirmed = record.optInt("status") == 1
                AppCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(record.displayField("itemName", "物资"), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        StatusPill(if (confirmed) "已确认" else "待确认")
                    }
                    Spacer(Modifier.height(4.dp))
                    InfoRowItem("归还数量", quantityText(record.opt("quantity"), "0"))
                    InfoRowItem("归还日期", serverDateOnly(record.opt("returnDate"), "-"))
                    InfoRowItem("发起人", transferOperatorLabel(record.optJSONObject("operator")))
                    InfoRowItem("发起时间", transferDateTime(record.opt("createdAt")))
                    InfoRowItem("确认人", transferOperatorLabel(record.optJSONObject("confirmer")))
                    InfoRowItem("确认时间", transferDateTime(record.opt("confirmedAt")))
                    val recordRemark = displayText(record.opt("remark"))
                    if (recordRemark != "-") InfoRowItem("备注", recordRemark)
                    if (!confirmed && permissions?.optBoolean("canConfirmReturn") == true) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { ApiClient.confirmReturn(id, record.optInt("id")) } }
                                        .onSuccess { saving = false; reload++ }
                                        .onFailure { saving = false; error = it.message ?: "确认归还失败" }
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            enabled = !saving,
                            shape = FieldShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("确认归还", fontSize = 12.sp) }
                    }
                }
            }
        }

        if (permissions?.optBoolean("canConfirmOutbound") == true || permissions?.optBoolean("canCancel") == true) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (permissions?.optBoolean("canConfirmOutbound") == true) {
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { ApiClient.confirmOutbound(id) } }
                                    .onSuccess { saving = false; reload++ }
                                    .onFailure { saving = false; error = it.message ?: "确认调出失败" }
                            }
                        },
                        modifier = Modifier.height(34.dp),
                        enabled = !saving,
                        shape = FieldShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text("确认调出", fontSize = 12.sp) }
                }
                if (permissions?.optBoolean("canCancel") == true) {
                    OutlinedButton(
                        onClick = {
                            saving = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { ApiClient.cancelTransfer(id, "安卓端取消") } }
                                    .onSuccess { saving = false; onBack() }
                                    .onFailure { saving = false; error = it.message ?: "取消调拨失败" }
                            }
                        },
                        modifier = Modifier.height(34.dp),
                        enabled = !saving,
                        shape = FieldShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    ) { Text("取消调拨", fontSize = 12.sp) }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorStateView(message = error!!, onRetry = { reload++ })
        }
        Spacer(Modifier.height(16.dp))
    }

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
                    enabled = !saving && returnQuantity.toDoubleOrNull()?.let { it > 0 } == true,
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.addTransferReturns(
                                        id,
                                        JSONObject()
                                            .put("returnDate", serverToday().toString())
                                            .put("items", JSONArray().put(JSONObject().put("transferItemId", item.optInt("id")).put("quantity", returnQuantity.toDouble()))),
                                    )
                                }
                            }.onSuccess { saving = false; returnItem = null; reload++ }
                                .onFailure { saving = false; error = it.message ?: "提交归还失败" }
                        }
                    },
                    shape = FieldShape,
                ) { Text("提交") }
            },
            dismissButton = { TextButton(onClick = { returnItem = null }) { Text("取消") } },
        )
    }
}

private fun transferOperatorLabel(operator: JSONObject?): String = operator?.let {
    listOf(
        it.displayField("nickname", ""),
        it.displayField("name", ""),
        it.displayField("phone", ""),
    ).firstOrNull { value -> value.isNotBlank() } ?: "-"
} ?: "-"

private fun transferDateTime(value: Any?): String {
    return serverDateTime(value)
}
