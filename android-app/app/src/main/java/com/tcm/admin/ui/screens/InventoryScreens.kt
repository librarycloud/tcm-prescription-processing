package com.tcm.admin

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tcm.admin.ui.viewmodels.InventoryViewModel

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryScreen(
    user: JSONObject?,
    initialQuery: String = "",
    scanRequestId: Long = 0L,
    scrollState: ScrollState? = null,
    listState: LazyListState = rememberLazyListState(),
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val showStore = user?.optInt("role", -1) == 0
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedStoreId by viewModel.selectedStoreId.collectAsStateWithLifecycle()
    val selectedProduct by viewModel.selectedProduct.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()

    val products = viewModel.productsFlow.collectAsLazyPagingItems()

    var listScrollPosition by remember { mutableStateOf(0) }
    var restoreListScroll by remember { mutableStateOf(false) }
    var lastAutoSearchQuery by remember { mutableStateOf(query.trim()) }
    var hasAutoNavigated by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("inventory_search_prefs", android.content.Context.MODE_PRIVATE) }
    var searchHistory by remember {
        mutableStateOf(
            prefs.getString("history", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        )
    }

    fun addSearchHistory(term: String) {
        val t = term.trim()
        if (t.isBlank()) return
        val updated = (listOf(t) + searchHistory.filter { it != t }).take(8)
        searchHistory = updated
        prefs.edit().putString("history", updated.joinToString(",")).apply()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        prefs.edit().remove("history").apply()
    }

    fun clearSearchResults() {
        viewModel.query.value = ""
        viewModel.selectedProduct.value = null
        hasAutoNavigated = false
        products.refresh()
    }

    fun searchInventory() {
        if (query.isBlank()) {
            clearSearchResults()
            return
        }
        addSearchHistory(query)
        lastAutoSearchQuery = query.trim()
        viewModel.selectedProduct.value = null
        hasAutoNavigated = false
        products.refresh()
    }

    LaunchedEffect(initialQuery, scanRequestId) {
        if (initialQuery.isNotBlank()) {
            viewModel.query.value = initialQuery
            addSearchHistory(initialQuery)
            lastAutoSearchQuery = initialQuery.trim()
            viewModel.selectedProduct.value = null
            hasAutoNavigated = false
            products.refresh()
        }
    }

    LaunchedEffect(query) {
        val searchTerm = query.trim()
        if (!shouldAutoSearchQuery(searchTerm)) {
            lastAutoSearchQuery = ""
            return@LaunchedEffect
        }
        delay(300)
        if (query.trim() == searchTerm && lastAutoSearchQuery != searchTerm) {
            lastAutoSearchQuery = searchTerm
            viewModel.selectedProduct.value = null
            hasAutoNavigated = false
            products.refresh()
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && value.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.query.value = value
            addSearchHistory(value)
            lastAutoSearchQuery = value
            viewModel.selectedProduct.value = null
            hasAutoNavigated = false
            products.refresh()
        }
    }

    // Auto navigate to product detail if exactly one item matches
    LaunchedEffect(products.loadState.refresh, products.itemCount) {
        val refreshState = products.loadState.refresh
        if (refreshState is LoadState.Loading) {
            hasAutoNavigated = false
        } else if (refreshState is LoadState.NotLoading) {
            if (!hasAutoNavigated && query.isNotBlank() && selectedProduct == null && products.itemCount == 1) {
                val singleProduct = products.itemSnapshotList.items.firstOrNull() ?: products[0]
                if (singleProduct != null) {
                    hasAutoNavigated = true
                    keyboardController?.hide()
                    focusManager.clearFocus(force = false)
                    listScrollPosition = listState.firstVisibleItemIndex
                    viewModel.selectedProduct.value = singleProduct
                }
            }
        }
    }

    LaunchedEffect(showStore) {
        if (showStore) viewModel.loadStores()
    }

    LaunchedEffect(stores) {
        if (showStore && stores.size == 1 && selectedStoreId == null) {
            viewModel.selectedStoreId.value = stores.first().optInt("id")
        }
    }

    BackHandler(enabled = selectedProduct != null) {
        viewModel.selectedProduct.value = null
        restoreListScroll = true
    }

    LaunchedEffect(selectedProduct, restoreListScroll) {
        if (selectedProduct == null && restoreListScroll) {
            withFrameNanos { }
            listState.scrollToItem(listScrollPosition)
            restoreListScroll = false
        }
    }

    val isRefreshing = products.loadState.refresh is LoadState.Loading && query.isNotBlank()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (query.isNotBlank()) {
                ApiClient.clearResponseCache(context)
                hasAutoNavigated = false
                products.refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        // Heading & Search
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "E6药店商品库存",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "搜索或扫码查看商品库存与批次详情",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SearchBarField(
                value = query,
                onValueChange = {
                    viewModel.query.value = it
                    if (it.isBlank()) clearSearchResults()
                },
                placeholder = "输入商品名称、编码或条码",
                onSearch = ::searchInventory,
                onScan = {
                    scannerLauncher.launch(
                        Intent(context, ScannerActivity::class.java)
                            .putExtra(ScannerActivity.EXTRA_ENABLE_SKU_OCR, true),
                    )
                },
            )
        }

        // Recent Search History (shown when query is empty & not viewing a detail)
        if (query.isBlank() && selectedProduct == null && searchHistory.isNotEmpty()) {
            item(key = "search_history") {
                Spacer(Modifier.height(10.dp))
                RecentSearchChipsRow(
                    history = searchHistory,
                    onSelect = { term ->
                        keyboardController?.hide()
                        focusManager.clearFocus(force = false)
                        viewModel.query.value = term
                        addSearchHistory(term)
                        lastAutoSearchQuery = term
                        viewModel.selectedProduct.value = null
                        hasAutoNavigated = false
                        products.refresh()
                    },
                    onClear = ::clearSearchHistory,
                )
            }
        }

        // Store selection filter chips
        if (showStore && stores.size > 1 && selectedProduct == null) {
            item(key = "store_chips") {
                Spacer(Modifier.height(10.dp))
                StoreChipsRow(
                    stores = stores,
                    selectedStoreId = selectedStoreId?.toString().orEmpty(),
                    onSelectStore = { id ->
                        keyboardController?.hide()
                        focusManager.clearFocus(force = false)
                        viewModel.selectedStoreId.value = id.toIntOrNull()
                        if (query.isNotBlank()) {
                            viewModel.selectedProduct.value = null
                            hasAutoNavigated = false
                            products.refresh()
                        }
                    },
                )
            }
        }

        // Error message
        if (products.loadState.refresh is LoadState.Error) {
            item(key = "error") {
                val err = (products.loadState.refresh as LoadState.Error).error
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = DangerSoft,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Danger.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        text = err.message ?: "加载库存失败",
                        color = Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Loading
        if (products.loadState.refresh is LoadState.Loading && query.isNotBlank()) {
            if (products.itemCount == 0) {
                item(key = "loading") {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                item(key = "silent_loading") {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Primary,
                        trackColor = Primary.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // When a product is selected -> show detailed breakdown
        selectedProduct?.let { product ->
            val inventories = product.optJSONArray("inventories") ?: JSONArray()
            val totalQuantity = product.optDouble("totalQuantity", 0.0)
            val unit = product.displayField("unit", "")
            val retailPrice = product.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

            item(key = "product_info_header") {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(title = "商品信息")
                    OutlinedButton(
                        onClick = {
                            viewModel.selectedProduct.value = null
                            restoreListScroll = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("返回列表", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))

                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = product.displayField("name", "商品"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                        }
                        if (!retailPrice.isNullOrBlank()) {
                            Text(
                                text = "¥${priceText(retailPrice)}",
                                color = Danger,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "商品编码",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = product.displayField("productCode").ifBlank { "-" },
                            color = Ink,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "商品条码",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = product.displayField("barcode").ifBlank { "无条码" },
                            color = Ink,
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "规格：${product.displayField("specification").ifBlank { "-" }}",
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "单位：${unit.ifBlank { "-" }}",
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "生产厂商",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = product.displayField("manufacturer").ifBlank { "-" },
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Highlighted Total Stock banner
                    Surface(
                        color = PrimarySoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("总库存：", color = RegularText, fontSize = 13.sp)
                            Text(
                                text = "${quantityText(totalQuantity)} $unit",
                                color = PrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("共 ", color = RegularText, fontSize = 13.sp)
                            Text(
                                text = "${product.optInt("batchCount", inventories.length())}",
                                color = PrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(" 个库存批次", color = RegularText, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionHeader(title = "库存批次明细")
                Spacer(Modifier.height(6.dp))
            }

            if (inventories.length() == 0) {
                item(key = "empty_batches") {
                    AppEmptyState("该商品暂无库存批次")
                }
            } else {
                items(inventories.length(), key = { idx ->
                    val itm = inventories.getJSONObject(idx)
                    "batch_${idx}_${itm.optString("batchNo")}"
                }) { i ->
                    val item = inventories.getJSONObject(i)
                    val storeName = item.optJSONObject("store")?.displayField("name", "")
                        ?: item.displayField("storeName", "")
                    val batchNo = item.displayField("batchNo")
                    val location = item.displayField("locationName", "").ifBlank { formatLocationCode(item.displayField("locationCode")) }
                    val qty = item.optDouble("quantity", 0.0)
                    val prodDate = inventoryDate(item, "productionDate")
                    val expDate = inventoryDate(item, "expiryDate", "expirationDate", "expireDate")
                    val expiringSoon = inventoryExpiryWarning(expDate)
                    val inDate = inventoryDate(item, "inboundDate", "receivedAt")

                    AppCard(modifier = Modifier.padding(bottom = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "批号：$batchNo",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink,
                                        fontSize = 14.sp,
                                    )
                                    if (showStore && storeName.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusPill(storeName)
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "货位：${location.ifBlank { "未分配" }}",
                                    color = if (location.isNotBlank()) Primary else Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${quantityText(qty)} $unit",
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDark,
                                    fontSize = 15.sp,
                                )
                                if (expiringSoon) {
                                    Text(
                                        text = "即将过期",
                                        color = Danger,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        if (prodDate.isNotBlank() || expDate.isNotBlank() || inDate.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("生产日期", color = Muted, fontSize = 9.sp)
                                    Text(prodDate.ifBlank { "-" }, color = RegularText, fontSize = 10.sp, maxLines = 1)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("有效期至", color = Muted, fontSize = 9.sp)
                                    Text(
                                        expDate.ifBlank { "-" },
                                        color = if (expiringSoon) Danger else RegularText,
                                        fontWeight = if (expiringSoon) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("入库日期", color = Muted, fontSize = 9.sp)
                                    Text(inDate.ifBlank { "-" }, color = Muted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // When multiple products match and none is selected -> show selection list
        if (selectedProduct == null) {
            if (query.isNotBlank()) {
                if (products.itemCount == 0 && products.loadState.refresh !is LoadState.Loading && products.loadState.refresh !is LoadState.Error) {
                    item(key = "empty_matches") {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppEmptyState(
                                message = "未找到与 \"$query\" 匹配的商品",
                                icon = Icons.Default.Search,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scannerLauncher.launch(
                                            Intent(context, ScannerActivity::class.java)
                                                .putExtra(ScannerActivity.EXTRA_ENABLE_SKU_OCR, true),
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("重新扫描", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        clearSearchResults()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("清空搜索", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else if (products.itemCount > 0) {
                    item(key = "matches_header") {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader(
                            title = "匹配商品",
                            subtitle = "共找到 ${products.itemCount} 个商品，点击查看库存批次",
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    items(count = products.itemCount, key = products.itemKey { it.optString("productCode", it.optString("id", it.toString())) }) { index ->
                        val product = products[index]
                        if (product != null) {
                            val retailPrice = product.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                            val unit = product.displayField("unit", "")
                            val spec = product.displayField("specification", "")
                            val barcode = product.displayField("barcode", "")
                            val manufacturer = product.displayField("manufacturer", "")

                            AppCard(
                                modifier = Modifier.padding(bottom = 10.dp),
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = false)
                                    listScrollPosition = listState.firstVisibleItemIndex
                                    viewModel.selectedProduct.value = product
                                },
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        HighlightedText(
                                            text = "${product.displayField("productCode")} · ${product.displayField("name", "商品")}",
                                            highlight = query,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (!retailPrice.isNullOrBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "¥${priceText(retailPrice)}",
                                                color = Danger,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "规格：${spec.ifBlank { "-" }}　单位：${unit.ifBlank { "-" }}",
                                        color = Muted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    HighlightedText(
                                        text = "厂家：${manufacturer.ifBlank { "-" }}　条码：${barcode.ifBlank { "无条码" }}",
                                        highlight = query,
                                        color = Muted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    if (products.loadState.append is LoadState.Loading) {
                        item(key = "append_loading") {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            } else {
                item(key = "empty_prompt") {
                    Spacer(Modifier.height(16.dp))
                    AppEmptyState(
                        message = "请输入商品名称、编码或条形码进行查询",
                        icon = Icons.Default.Inventory2,
                    )
                }
            }
        }
    }
    }
}

private fun inventoryDate(item: JSONObject, vararg keys: String): String {
    return keys.asSequence()
        .mapNotNull { key -> item.opt(key)?.takeIf { it != JSONObject.NULL }?.toString() }
        .firstOrNull { value -> value.isNotBlank() && value != "null" }
        ?.take(10)
        .orEmpty()
}

private fun inventoryExpiryWarning(value: String): Boolean = runCatching {
    LocalDate.parse(value.take(10)).isBefore(serverToday().plusMonths(6))
}.getOrDefault(false)
