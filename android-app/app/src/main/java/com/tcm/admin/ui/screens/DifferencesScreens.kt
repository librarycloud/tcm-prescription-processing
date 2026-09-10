package com.tcm.admin

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tcm.admin.ui.viewmodels.DifferencesViewModel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DifferencesScreen(
    user: JSONObject? = null,
    listState: LazyListState = rememberLazyListState(),
    viewModel: DifferencesViewModel = hiltViewModel(),
) {
    val isStoreStaff = user?.optInt("role", -1) == 3
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val registerProducts by viewModel.registerProducts.collectAsStateWithLifecycle()
    val items = viewModel.differencesFlow.collectAsLazyPagingItems()

    var writeOff by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var writeOffQuantity by remember { mutableStateOf("") }
    var registerVisible by remember { mutableStateOf(false) }
    var registerType by remember { mutableStateOf("PRE_RECEIPT") }
    var registerProduct by remember { mutableStateOf<JSONObject?>(null) }
    var registerQuantity by remember { mutableStateOf("") }
    var registerKeyword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(registerVisible) {
        if (registerVisible) viewModel.loadRegisterProducts()
    }

    val isRefreshing = items.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            ApiClient.clearResponseCache(context)
            items.refresh()
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
                        SectionHeader("库存差异", "管理未入库/未销库的实货差异")
                    }
                    if (!isStoreStaff) {
                        Button(
                            onClick = { registerVisible = true },
                            modifier = Modifier.heightIn(min = CompactControlHeight),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("登记差异")
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedButton("当前差异", tab == "current", onClick = { viewModel.tab.value = "current" })
                    SegmentedButton("差异流水", tab == "logs", onClick = { viewModel.tab.value = "logs" })
                }

                Spacer(Modifier.height(14.dp))
            }

            if (items.loadState.refresh is LoadState.Error) {
                item(key = "error") {
                    val err = (items.loadState.refresh as LoadState.Error).error
                    ErrorStateView(message = err.message ?: "加载库存差异失败", onRetry = { items.retry() })
                }
            }

            if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
                item(key = "loading") {
                    AppEmptyState("加载中...")
                }
            } else if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Error && items.loadState.refresh !is LoadState.Loading) {
                item(key = "empty") {
                    AppEmptyState(if (tab == "current") "暂无未销账差异" else "暂无差异流水记录")
                }
            }

            if (tab == "current") {
                items(count = items.itemCount, key = items.itemKey { it.optInt("id", it.optString("productCode", it.toString()).hashCode()) }) { index ->
                    val product = items[index]
                    if (product != null) {
                        val preReceipt = product.optDouble("preReceiptQuantity", 0.0)
                        val preShipment = product.optDouble("preShipmentQuantity", 0.0)
                        val unit = product.displayField("unit")

                        AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "${product.displayField("productCode")} · ${product.displayField("name", "商品")}",
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        fontSize = 14.sp,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text("规格：${product.displayField("specification")} · 生产厂商：${product.displayField("manufacturer")}", color = Muted, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (preReceipt > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CardBorderColor),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text("先到货：+${quantityText(preReceipt)} $unit", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            if (!isStoreStaff) {
                                                TextButton(
                                                    onClick = { writeOff = Pair(product, "WRITE_OFF_RECEIPT"); writeOffQuantity = quantityText(preReceipt, "0") },
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                ) {
                                                    Text("入库销账", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (preShipment > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CardBorderColor),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text("先出货：-${quantityText(preShipment)} $unit", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            if (!isStoreStaff) {
                                                TextButton(
                                                    onClick = { writeOff = Pair(product, "WRITE_OFF_SHIPMENT"); writeOffQuantity = quantityText(preShipment, "0") },
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                ) {
                                                    Text("销库销账", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(count = items.itemCount, key = items.itemKey { it.optInt("id") }) { index ->
                    val log = items[index]
                    if (log != null) {
                        val product = log.optJSONObject("product") ?: JSONObject()
                        val opType = log.displayField("operationType", "")
                        val qty = log.optDouble("quantity", 0.0)

                        AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "${product.displayField("productCode")} · ${product.displayField("name", "商品")}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = "${log.displayField("businessDate").take(10)} · ${log.optJSONObject("operator")?.displayField("username") ?: "-"}",
                                        color = Muted,
                                        fontSize = 12.sp,
                                    )
                                }
                                StatusPill(diffOperationLabel(opType))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("变动数量：${quantityText(qty)} ${product.displayField("unit")}", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (items.loadState.append is LoadState.Loading) {
                item(key = "append_loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    writeOff?.let { (product, opType) ->
        val isReceipt = opType == "WRITE_OFF_RECEIPT"
        AlertDialog(
            onDismissRequest = { writeOff = null },
            title = { Text(if (isReceipt) "入库销账" else "销库销账") },
            text = {
                Column {
                    Text("${product.displayField("productCode")} · ${product.displayField("name", "商品")}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = writeOffQuantity,
                        onValueChange = { writeOffQuantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("销账数量 (${product.displayField("unit")})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = writeOffQuantity.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.writeOffDifference(
                                        JSONObject()
                                            .put("productId", product.optInt("id"))
                                            .put("quantity", qty)
                                            .put("businessDate", serverToday().toString()),
                                    )
                                }
                            }.onSuccess {
                                writeOff = null
                                items.refresh()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "销账失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = writeOffQuantity.toDoubleOrNull()?.let { it > 0 } == true,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("确认销账")
                }
            },
            dismissButton = {
                TextButton(onClick = { writeOff = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (registerVisible) {
        AlertDialog(
            onDismissRequest = { registerVisible = false },
            title = { Text("登记库存差异") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PRE_RECEIPT" to "先到货", "PRE_SHIPMENT" to "先出货").forEach { (key, label) ->
                            SegmentedButton(label, registerType == key, onClick = { registerType = key })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = registerKeyword,
                        onValueChange = { registerKeyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索商品") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    registerProducts.filter { product ->
                        val keyword = registerKeyword.trim()
                        keyword.isBlank() || product.optString("name").contains(keyword, ignoreCase = true) || product.optString("productCode").contains(keyword, ignoreCase = true)
                    }.take(12).forEach { product ->
                        OutlinedButton(
                            onClick = { registerProduct = product },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(if (registerProduct?.optInt("id") == product.optInt("id")) "已选：${product.displayField("name", "商品")}" else product.displayField("name", "商品"))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = registerQuantity,
                        onValueChange = { registerQuantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
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
                                                .put("businessDate", serverToday().toString())
                                                .put(
                                                    "items",
                                                    JSONArray().put(
                                                        JSONObject()
                                                            .put("productId", product.optInt("id"))
                                                            .put("quantity", registerQuantity.toDouble()),
                                                    ),
                                                ),
                                        )
                                    }
                                }.onSuccess {
                                    registerVisible = false
                                    registerProduct = null
                                    registerQuantity = ""
                                    registerKeyword = ""
                                    items.refresh()
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "登记差异失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("登记")
                }
            },
            dismissButton = {
                TextButton(onClick = { registerVisible = false }) {
                    Text("取消")
                }
            },
        )
    }
}

private fun diffOperationLabel(value: String): String = when (value) {
    "PRE_RECEIPT" -> "先到货未入库"
    "PRE_SHIPMENT" -> "先出货未销库"
    "WRITE_OFF_RECEIPT" -> "入库销账"
    "WRITE_OFF_SHIPMENT" -> "销库销账"
    "REVERSAL" -> "冲销"
    "IMPORT_OPENING" -> "导入期初差异"
    "IMPORT_ADJUSTMENT" -> "导入调整"
    else -> value
}
