package com.tcm.admin

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun StocktakingScreen(
    user: JSONObject? = null,
    onNavigate: (ScreenTarget) -> Unit,
    scrollState: ScrollState,
) {
    val listOwner = "stocktaking"
    var checks by rememberRetainedListValue(listOwner, "checks") { null as List<JSONObject>? }
    var stores by rememberRetainedListValue(listOwner, "stores") { emptyList<JSONObject>() }
    var selectedStoreId by rememberRetainedListValue(listOwner, "selectedStoreId") { "" }
    var page by rememberRetainedListValue(listOwner, "page") { 1 }
    var pages by rememberRetainedListValue(listOwner, "pages") { 1 }
    var error by rememberRetainedListValue(listOwner, "error") { null as String? }
    var reload by rememberRetainedListValue(listOwner, "reload") { 0 }
    var loadedQueryKey by rememberRetainedListValue(listOwner, "loadedQueryKey") { null as String? }
    var createVisible by remember { mutableStateOf(false) }
    var checkName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val isSuperAdmin = user?.optInt("role", -1) == 0
    val isManager = isSuperAdmin || user?.optInt("role", -1) == 2
    val isStoreStaff = user?.optInt("role", -1) == 3

    LaunchedEffect(reload, selectedStoreId, page) {
        val queryKey = listOf(reload, selectedStoreId, page).joinToString("|")
        if (loadedQueryKey == queryKey && checks != null) return@LaunchedEffect
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val values = ApiClient.stocktakings(selectedStoreId.toIntOrNull(), page = page, pageSize = 10)
                val storeValues = if (isSuperAdmin) ApiClient.availableStores() else JSONArray()
                Pair(values, storeValues)
            }
        }.onSuccess { (values, storeValues) ->
            checks = (0 until (values.optJSONArray("list")?.length() ?: 0)).map { values.getJSONArray("list").getJSONObject(it) }
            pages = values.optJSONObject("pagination")?.optInt("pages", 1)?.coerceAtLeast(1) ?: 1
            stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            if (isSuperAdmin && selectedStoreId.isBlank() && stores.size == 1) {
                selectedStoreId = stores.first().optInt("id").toString()
            }
            loadedQueryKey = queryKey
        }.onFailure {
            error = it.message ?: "加载盘点单失败"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionHeader("商品盘点", "商品盘点计划与差异录入")
            }
            if (isManager) {
                Button(
                    onClick = { createVisible = true },
                    modifier = Modifier.height(CompactControlHeight),
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建盘点")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (isSuperAdmin && stores.size > 1) {
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it; page = 1 },
            )
            Spacer(Modifier.height(14.dp))
        }

        if (checks == null && error == null) AppEmptyState("加载盘点列表中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (checks != null && checks!!.isEmpty()) AppEmptyState("暂无盘点单记录")

        checks.orEmpty().forEach { check ->
            key(check.optInt("id")) {
            val status = check.optInt("status")
            val summary = check.optJSONObject("summary") ?: JSONObject()
            val total = summary.optInt("total", 0)
            val counted = summary.optInt("counted", 0)
            val diff = summary.optInt("adjustment", 0)
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
                        text = check.displayField("checkNo", check.displayField("id")),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Ink,
                    )
                    StatusPill(text = goodsCheckStatus(status))
                }

                Spacer(Modifier.height(8.dp))

                Text(
                        text = check.displayField("checkName", check.displayField("name", "未命名盘点")),
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
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

            }
            }
        }
        if (checks != null && pages > 1) {
            AppPagination(page = page, pages = pages, onPrev = { if (page > 1) page-- }, onNext = { if (page < pages) page++ })
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
                                        checkName.trim(),
                                        storeId = selectedStoreId.toIntOrNull(),
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
    user: JSONObject? = null,
    scrollState: ScrollState,
    refreshKey: Int,
) {
    val detailOwner = "stocktaking-detail-$checkId"
    var check by rememberRetainedListValue(detailOwner, "check") { null as JSONObject? }
    var error by rememberRetainedListValue(detailOwner, "error") { null as String? }
    var reload by rememberRetainedListValue(detailOwner, "reload") { 0 }
    var itemFilter by rememberRetainedListValue(detailOwner, "filter") { "all" }
    var itemPage by rememberRetainedListValue(detailOwner, "page") { 1 }
    var itemPages by rememberRetainedListValue(detailOwner, "pages") { 1 }
    var entryItem by remember { mutableStateOf<JSONObject?>(null) }
    var entryMode by remember { mutableStateOf(false) }
    val isStoreStaff = user?.optInt("role", -1) == 3

    LaunchedEffect(checkId, refreshKey, reload, itemPage, itemFilter) {
        val requestedPage = itemPage
        val requestedFilter = itemFilter
        fun requestStatus() = when (requestedFilter) {
            "missing" -> "missing"
            "recount" -> "recount"
            "mine" -> "mine"
            "counted" -> "counted"
            "diff" -> "adjustment"
            else -> ""
        }
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.goodsCheck(
                    checkId,
                    page = requestedPage,
                    pageSize = 10,
                    status = requestStatus(),
                )
            }
        }.onSuccess { result ->
            if (itemPage != requestedPage || itemFilter != requestedFilter) return@onSuccess
            check = result
            itemPages = result.optJSONObject("pagination")?.optInt("pages", 1)?.coerceAtLeast(1) ?: 1
        }
            .onFailure { error = it.message ?: "加载盘点详情失败" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        if (check == null && error == null) AppEmptyState("加载盘点明细中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)

        check?.let { selected ->
            val items = selected.optJSONArray("items") ?: JSONArray()
            val itemList = (0 until items.length()).map { items.getJSONObject(it) }
            val paginationTotal = selected.optJSONObject("pagination")?.optInt("total", 0) ?: 0
            fun selectFilter(filter: String) {
                itemFilter = if (itemFilter == filter && filter != "all") "all" else filter
                itemPage = 1
            }

            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected.displayField("checkName", "盘点明细"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Ink,
                    )
                    StatusPill(text = goodsCheckStatus(selected.optInt("status")))
                }

                Spacer(Modifier.height(8.dp))

                Text("单号：${selected.displayField("checkNo")}", color = Muted, fontSize = 12.sp)

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCell("总项数", "", Modifier.weight(1f), selected = itemFilter == "all") { selectFilter("all") }
                    MetricCell("已盘", "", Modifier.weight(1f), selected = itemFilter == "counted") { selectFilter("counted") }
                    MetricCell("我的记录", "", Modifier.weight(1f), selected = itemFilter == "mine") { selectFilter("mine") }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricCell("漏盘", "", Modifier.weight(1f), selected = itemFilter == "missing") { selectFilter("missing") }
                    MetricCell("待复盘", "", Modifier.weight(1f), selected = itemFilter == "recount") { selectFilter("recount") }
                    MetricCell("有差异", "", Modifier.weight(1f), selected = itemFilter == "diff") { selectFilter("diff") }
                }

            }

            Spacer(Modifier.height(14.dp))
            StocktakingEntryScreen(
                checkId = checkId,
                user = user,
                initialItem = entryItem,
                onModeChanged = { active -> entryMode = active || entryItem != null },
                onDismiss = {
                    entryItem = null
                    entryMode = false
                },
                onSaved = {
                    val editingExistingItem = entryItem != null
                    entryItem = null
                    if (editingExistingItem) entryMode = false
                    reload++
                },
            )

            if (!entryMode) {
                Spacer(Modifier.height(16.dp))

                SectionHeader(
                    "盘点条目明细",
                    if (itemFilter == "all") "共 $paginationTotal 个商品条目" else "当前筛选 $paginationTotal 个商品条目",
                )
                Spacer(Modifier.height(10.dp))

                itemList.forEach { item ->
                    val product = item.optJSONObject("product") ?: JSONObject()
                    val firstQty = nullableDouble(item, "firstCountQty")
                    val recountQty = nullableDouble(item, "recountQty")
                    val canEditInitial = item.optBoolean("canEditInitial", false)
                    val canEditRecount = item.optBoolean("canEditRecount", false)
                    val canStartRecount = item.optBoolean("needsRecount", false) &&
                        recountQty == null && item.optInt("id", 0) > 0
                    val canEditLocation = item.optInt("id", 0) > 0 && item.optInt("reviewStatus", 0) != 1
                    val isRecount = canEditRecount || canStartRecount
                    val effectiveQty = recountQty ?: firstQty
                    val systemQty = if (recountQty != null) item.optDouble("recountSystemQty", item.optDouble("systemQty", 0.0)) else item.optDouble("systemQty", 0.0)
                    val diffQty = if (isStoreStaff) null else effectiveQty?.let { item.optDouble("difference", it - systemQty) }
                    val systemLocation = item.displayField("systemLocationName", "")
                    val countLocation = item.displayField("countLocationName", "")
                    val retailPrice = product.opt("retailPrice")?.toString()
                        ?.takeIf { it.isNotBlank() && it != "null" }
                        ?: item.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    val actionLabel = when {
                        canEditRecount -> "修改复盘"
                        firstQty == null -> "录入初盘"
                        canStartRecount -> "录入复盘"
                        canEditInitial -> "修改初盘"
                        canEditLocation -> "修改货位"
                        else -> null
                    }

                    AppCard(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${product.displayField("productCode")} · ${product.displayField("name", "商品")}",
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "编码：${product.displayField("productCode", "-")}　条码：${product.displayField("barcode", "-")}",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                                Text("规格：${product.displayField("specification", "-")}　单位：${product.displayField("unit", "-")}", color = Muted, fontSize = 12.sp)
                                Text("厂家：${product.displayField("manufacturer", "-")}", color = Muted, fontSize = 12.sp)
                                Text(
                                    text = "批号：${item.displayField("batchNo")} · 系统货位：${systemLocation.ifBlank { "未设置" }}" +
                                        (countLocation.takeIf { it.isNotBlank() }?.let { " · 盘点货位：$it" } ?: ""),
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isStoreStaff) {
                                        if (item.optBoolean("needsRecount", false) || recountQty != null) {
                                            "初盘：${quantityText(firstQty)} · 复盘：${quantityText(recountQty)}"
                                        } else {
                                            "初盘：${quantityText(firstQty)}"
                                        }
                                    } else {
                                        if (item.optBoolean("needsRecount", false) || recountQty != null) {
                                            "系统库存：${quantityText(systemQty, "0")} · 初盘：${quantityText(firstQty)} · 复盘：${quantityText(recountQty)}"
                                        } else {
                                            "系统库存：${quantityText(systemQty, "0")} · 初盘：${quantityText(firstQty)}"
                                        }
                                    },
                                    color = RegularText,
                                    fontSize = 12.sp,
                                )
                            }

                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                retailPrice?.let { price ->
                                    Text("¥${priceText(price)}", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                if (effectiveQty != null || item.optInt("checkStatus", 0) != 0) {
                                    StatusPill(
                                        text = goodsCheckItemStatus(
                                            item.optInt("checkStatus", 0),
                                            item.optInt("reviewStatus", 0),
                                            diffQty,
                                            item.optBoolean("needsAdjustment", false),
                                        ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (actionLabel != null) {
                                Button(
                                    onClick = {
                                        entryItem = item
                                        entryMode = true
                                    },
                                    shape = FieldShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                ) {
                                    Text(actionLabel)
                                }
                            } else {
                                Text(
                                    text = when {
                                        item.optInt("reviewStatus", 0) == 2 -> "复核未通过"
                                        item.optInt("reviewStatus", 0) == 1 -> "已复核"
                                        else -> "待复核"
                                    },
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                AppPagination(
                    page = itemPage,
                    pages = itemPages,
                    onPrev = { if (itemPage > 1) itemPage-- },
                    onNext = { if (itemPage < itemPages) itemPage++ },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

}

@Composable
internal fun StocktakingEntryScreen(
    checkId: Int,
    user: JSONObject? = null,
    initialItem: JSONObject? = null,
    onModeChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val isStoreStaff = user?.optInt("role", -1) == 3
    var selectedItem by remember(initialItem) { mutableStateOf(initialItem) }
    var selectedProductGroup by remember(initialItem) { mutableStateOf<CandidateProductGroup?>(null) }
    var keyword by remember(initialItem) { mutableStateOf("") }
    var candidates by remember(initialItem) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var addingBatch by remember(initialItem) { mutableStateOf(false) }
    var batchNo by remember(initialItem) { mutableStateOf(initialItem?.displayField("batchNo", "").orEmpty()) }
    var countLocation by remember(initialItem) {
        mutableStateOf(
            initialItem?.let { item ->
                item.displayField(
                    "countLocationName",
                    item.displayField("systemLocationName", item.displayField("locationName", "")),
                )
            }.orEmpty(),
        )
    }
    var editingLocation by remember(initialItem) { mutableStateOf(false) }
    var value by remember(initialItem) {
        mutableStateOf(
            initialItem?.let {
                val recount = nullableDouble(it, "recountQty")
                val first = nullableDouble(it, "firstCountQty")
                quantityText(if (it.optInt("checkStatus", 0) == 2) recount ?: first else first, "")
            }.orEmpty(),
        )
    }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSearchedTerm by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun selectCandidate(candidate: JSONObject) {
        selectedItem = candidate
        addingBatch = false
        batchNo = candidate.displayField("batchNo", "")
        countLocation = candidate.displayField(
            "countLocationName",
            candidate.displayField("systemLocationName", candidate.displayField("locationName", "")),
        )
        editingLocation = false
        val recount = nullableDouble(candidate, "recountQty")
        val first = nullableDouble(candidate, "firstCountQty")
        value = quantityText(
            if (candidate.optBoolean("canEditRecount", false) || candidate.optBoolean("needsRecount", false)) recount ?: first else first,
            "",
        )
    }

    suspend fun search(term: String = keyword.trim(), force: Boolean = false) {
        if (term.length < 2) return
        if (!force && term == lastSearchedTerm && !loading) return
        lastSearchedTerm = term
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { ApiClient.searchGoodsCheckCandidates(checkId, term) }
        }.onSuccess { values ->
            val result = (0 until values.length()).map { values.getJSONObject(it) }
            candidates = result
            val groups = candidateProductGroups(result)
            selectedProductGroup = groups.singleOrNull()
        }.onFailure { error = it.message ?: "搜索商品失败" }
        loading = false
    }

    LaunchedEffect(keyword, selectedItem) {
        if (selectedItem != null || !shouldAutoSearchQuery(keyword) || keyword.trim() == lastSearchedTerm) return@LaunchedEffect
        delay(350)
        search(keyword.trim())
    }

    LaunchedEffect(selectedItem, selectedProductGroup, keyword, candidates, loading) {
        onModeChanged(selectedItem != null || selectedProductGroup != null || keyword.isNotBlank() || candidates.isNotEmpty() || loading)
    }

    fun manualSearch() {
        scope.launch { search(force = true) }
    }

    /* Keep scanner searches immediate, while typed searches use the debounce above. */
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanned = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && scanned.isNotBlank()) {
            keyword = scanned
            selectedProductGroup = null
            candidates = emptyList()
            scope.launch { search(scanned, force = true) }
        }
    }
    val isRecount = !addingBatch && selectedItem?.let {
        it.optBoolean("canEditRecount", false) ||
            (it.optBoolean("needsRecount", false) && it.optInt("id", it.optInt("checkItemId", 0)) > 0)
    } == true
    val isEditingInitial = selectedItem?.optBoolean("canEditInitial", false) == true
    val isEditingRecount = selectedItem?.optBoolean("canEditRecount", false) == true
    val selectedCheckItemId = selectedItem?.let { it.optInt("checkItemId", it.optInt("id", 0)) } ?: 0
    val hasCount = selectedItem?.let { nullableDouble(it, "firstCountQty") != null || nullableDouble(it, "recountQty") != null } == true
    val locationOnly = selectedCheckItemId > 0 && hasCount && !isEditingInitial && !isRecount
    val product = selectedItem?.optJSONObject("product") ?: JSONObject()

    val entryBackHandlerEnabled = selectedItem != null || selectedProductGroup != null || keyword.isNotBlank() || candidates.isNotEmpty() || loading
    BackHandler(enabled = entryBackHandlerEnabled) {
        if (selectedItem != null) {
            if (selectedProductGroup == null && keyword.isBlank() && candidates.isEmpty()) {
                onDismiss()
                return@BackHandler
            }
            selectedItem = null
            addingBatch = false
            batchNo = ""
            value = ""
        } else if (selectedItem == null && selectedProductGroup != null) {
            selectedProductGroup = null
        } else if (keyword.isNotBlank() || candidates.isNotEmpty()) {
            keyword = ""
            candidates = emptyList()
        } else {
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBarField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    lastSearchedTerm = ""
                    error = null
                    if (selectedItem != null) {
                        selectedItem = null
                        addingBatch = false
                        batchNo = ""
                        value = ""
                    }
                    selectedProductGroup = null
                    candidates = emptyList()
                },
                placeholder = "搜索商品名称、编码或条码",
                onSearch = ::manualSearch,
                onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (selectedItem == null && selectedProductGroup == null) {
            if (loading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Danger, fontSize = 12.sp)
            }
            if (!loading && candidates.isEmpty() && keyword.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                AppEmptyState("暂无匹配的盘点商品")
            }
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("匹配商品", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                candidateProductGroups(candidates).forEach { group ->
                    val candidateProduct = group.product
                    AppCard(
                        modifier = Modifier.padding(bottom = 8.dp),
                        onClick = { selectedProductGroup = group },
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("${candidateProduct.displayField("productCode")} · ${candidateProduct.displayField("name", "商品")}", fontWeight = FontWeight.Bold, color = Ink)
                                Spacer(Modifier.height(3.dp))
                                Text("规格：${candidateProduct.displayField("specification", "-")}　单位：${candidateProduct.displayField("unit", "-")}", color = Muted, fontSize = 12.sp)
                                Text("厂家：${candidateProduct.displayField("manufacturer", "-")}　条码：${candidateProduct.displayField("barcode", "-")}", color = Muted, fontSize = 12.sp)
                                Text("${group.batches.size} 个库存批次", color = Muted, fontSize = 12.sp)
                            }
                            candidateProduct.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { price ->
                                Text("¥${priceText(price)}", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        } else if (selectedItem == null && selectedProductGroup != null) {
            val group = selectedProductGroup!!
            val candidateProduct = group.product
            SectionHeader("商品盘点", "${group.batches.size} 个库存批次")
            AppCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("${candidateProduct.displayField("productCode")} · ${candidateProduct.displayField("name", "商品")}", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("编码：${candidateProduct.displayField("productCode", "-")}　条码：${candidateProduct.displayField("barcode", "-")}", color = Muted, fontSize = 12.sp)
                        Text("规格：${candidateProduct.displayField("specification", "-")}　单位：${candidateProduct.displayField("unit", "-")}", color = Muted, fontSize = 12.sp)
                        Text("厂家：${candidateProduct.displayField("manufacturer", "-")}", color = Muted, fontSize = 12.sp)
                    }
                    candidateProduct.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { price ->
                        Text("¥${priceText(price)}", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            group.batches.forEach { candidate ->
                val firstQty = nullableDouble(candidate, "firstCountQty")
                val canEditRecount = candidate.optBoolean("canEditRecount", false)
                val canStartRecount = candidate.optBoolean("needsRecount", false) && candidate.optInt("id", candidate.optInt("checkItemId", 0)) > 0
                val canEditInitial = candidate.optBoolean("canEditInitial", false)
                val status = when {
                    canEditRecount -> "修改复盘"
                    canStartRecount -> "复盘"
                    canEditInitial -> "修改初盘"
                    firstQty == null -> "盘点"
                    else -> "查看"
                }
                AppCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = { selectCandidate(candidate) },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("批号：${candidate.displayField("batchNo", "-")}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("系统货位：${candidate.displayField("systemLocationName", candidate.displayField("locationName", "未设置"))}", color = Muted, fontSize = 12.sp)
                            Text("盘点货位：${candidate.displayField("countLocationName", "未设置")}", color = Muted, fontSize = 12.sp)
                            Text("生产日期：${serverDateOnly(candidate.displayField("productionDate", ""), "-")}　有效期：${serverDateOnly(candidate.displayField("expiryDate", ""), "-")}", color = Muted, fontSize = 12.sp)
                            if (!isStoreStaff) {
                                Text("系统库存：${quantityText(candidate.opt("systemQty"), "0")} ${candidateProduct.displayField("unit", "")}", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Text(status, color = if (status == "查看") Muted else Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Surface(color = PrimarySoft, shape = FieldShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(product.displayField("name", "商品"), fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                            Spacer(Modifier.height(3.dp))
                            Text("编码：${product.displayField("productCode", "-")}　条码：${product.displayField("barcode", "-")}", color = Muted, fontSize = 12.sp)
                        }
                        product.opt("retailPrice")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { price ->
                            Text("¥${priceText(price)}", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("规格：${product.displayField("specification", "-")}　单位：${product.displayField("unit", "-")}", color = Muted, fontSize = 12.sp)
                    Text("生产厂商：${product.displayField("manufacturer", "-")}", color = Muted, fontSize = 12.sp)
                    Text("系统货位：${selectedItem!!.displayField("systemLocationName", selectedItem!!.displayField("locationName", "未设置"))}", color = Muted, fontSize = 12.sp)
                    Text("生产日期：${serverDateOnly(selectedItem!!.displayField("productionDate", ""), "-")}　有效期：${serverDateOnly(selectedItem!!.displayField("expiryDate", ""), "-")}", color = Muted, fontSize = 12.sp)
                    if (!isStoreStaff) {
                        Text("系统库存：${quantityText(selectedItem!!.optDouble("systemQty", 0.0), "0")} ${product.displayField("unit")}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (addingBatch) {
                OutlinedTextField(
                    value = batchNo,
                    onValueChange = { batchNo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("批号") },
                    singleLine = true,
                    shape = FieldShape,
                )
                Spacer(Modifier.height(10.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("批号：${batchNo.ifBlank { "-" }}", color = RegularText, fontSize = 13.sp)
                    if (!locationOnly) {
                        TextButton(onClick = { addingBatch = true; batchNo = ""; countLocation = ""; value = "" }) {
                            Text("新增批号")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = countLocation,
                    onValueChange = { countLocation = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("盘点货位") },
                    singleLine = true,
                    enabled = editingLocation,
                    shape = FieldShape,
                )
                OutlinedButton(
                    onClick = { editingLocation = !editingLocation },
                    shape = FieldShape,
                ) { Text(if (editingLocation) "完成" else "修改") }
            }
            if (!locationOnly) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isRecount) "复盘数量" else "初盘数量") },
                    supportingText = { Text("请输入实际盘点数量，支持小数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = FieldShape,
                )
            }
        }

        if (selectedItem != null) {
            Button(
                onClick = {
                    val item = selectedItem ?: return@Button
                    val amount = value.toDoubleOrNull()
                    if (!locationOnly && amount == null) return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val checkItemId = item.optInt("checkItemId", item.optInt("id", 0))
                                val systemLocation = if (addingBatch) "" else item.displayField("systemLocationName", item.displayField("locationName", ""))
                                if (locationOnly) {
                                    ApiClient.updateCheckItemLocation(
                                        checkId,
                                        checkItemId,
                                        JSONObject().put("countLocationName", countLocation.trim()),
                                    )
                                } else if (isRecount) {
                                    ApiClient.recountGoodsCheckItem(
                                        checkItemId,
                                        JSONObject()
                                            .put("recountQty", amount!!)
                                            .put("countLocationName", countLocation.trim()),
                                    )
                                } else {
                                    ApiClient.addGoodsCheckItem(
                                        checkId,
                                        JSONObject()
                                            .also { payload -> if (isEditingInitial && !addingBatch) payload.put("itemId", checkItemId) }
                                            .put("productId", item.optInt("productId"))
                                            .put("batchNo", batchNo.trim())
                                            .put("locationName", systemLocation)
                                            .put("countLocationName", countLocation.trim())
                                            .put("firstCountQty", amount!!),
                                    )
                                }
                            }
                        }.onSuccess {
                            selectedItem = null
                            addingBatch = false
                            onSaved()
                            if (selectedProductGroup != null) {
                                scope.launch { search(keyword, force = true) }
                            } else {
                                keyword = ""
                                candidates = emptyList()
                            }
                        }
                            .onFailure { error = it.message ?: "录入实盘失败" }
                        saving = false
                    }
                },
                enabled = !saving && (locationOnly || value.toDoubleOrNull() != null),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = FieldShape,
            ) {
                Text(
                    if (saving) "保存中..."
                    else if (locationOnly) "保存货位"
                    else if (isEditingRecount) "保存修改复盘"
                    else if (isEditingInitial) "保存修改初盘"
                    else "保存${if (isRecount) "复盘" else "初盘"}",
                )
            }
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
        }
    }
}

private data class CandidateProductGroup(
    val product: JSONObject,
    val batches: List<JSONObject>,
)

private fun candidateProductGroups(candidates: List<JSONObject>): List<CandidateProductGroup> {
    val groups = linkedMapOf<String, MutableList<JSONObject>>()
    val products = linkedMapOf<String, JSONObject>()

    candidates.forEach { candidate ->
        val product = candidate.optJSONObject("product") ?: JSONObject()
        val key = product.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: candidate.opt("productId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: listOf(
                product.displayField("productCode", ""),
                product.displayField("barcode", ""),
                product.displayField("name", "商品"),
            ).joinToString("|")
        groups.getOrPut(key) { mutableListOf() }.add(candidate)
        val existingProduct = products[key]
        if (existingProduct == null ||
            (existingProduct.length() == 0 && product.length() > 0)
        ) {
            products[key] = product
        }
    }

    return groups.map { (key, batches) -> CandidateProductGroup(products.getValue(key), batches) }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = if (selected) Primary else PrimarySoft,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else Muted, fontSize = 11.sp)
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else PrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun goodsCheckStatus(status: Int): String = when (status) {
    0 -> "待盘点"
    1 -> "盘点中"
    2 -> "盘点完成"
    else -> "未知"
}

private fun goodsCheckItemStatus(status: Int, reviewStatus: Int, difference: Double?, needsAdjustment: Boolean): String = when {
    reviewStatus == 2 -> "复核未通过"
    reviewStatus == 0 && (status == 3 || status == 4) -> "复盘待复核"
    reviewStatus == 0 && status != 0 -> "待复核"
    needsAdjustment && status == 4 -> "需调整库存"
    status == 2 -> "待复盘"
    status == 1 -> "待复核"
    status == 3 -> "复盘待复核"
    status == 5 -> "新增批号"
    status == 6 -> "已确认"
    difference == null -> "未盘"
    difference == 0.0 -> "正常"
    difference > 0 -> "实货多"
    else -> "实货少"
}

private fun nullableDouble(value: JSONObject, key: String): Double? {
    val raw = value.opt(key) ?: return null
    if (raw == JSONObject.NULL) return null
    return raw.toString().toDoubleOrNull()
}
