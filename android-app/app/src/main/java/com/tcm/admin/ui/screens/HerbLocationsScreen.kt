package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Stable
internal class HerbsListState(val scrollState: ScrollState) {
    var stores by mutableStateOf<List<JSONObject>>(emptyList())
    var selectedStoreId by mutableStateOf<String?>(null)
    var data by mutableStateOf<JSONObject?>(null)
    var keyword by mutableStateOf("")
    var type by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var reload by mutableStateOf(0)
    var storesLoaded by mutableStateOf(false)
    var loadedQueryKey by mutableStateOf<String?>(null)

    fun invalidate() {
        loadedQueryKey = null
        reload++
    }
}

@Composable
internal fun rememberHerbsListState(): HerbsListState {
    val scrollState = rememberScrollState()
    return remember { HerbsListState(scrollState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HerbsScreen(
    user: JSONObject?,
    onNavigate: (ScreenTarget) -> Unit,
    listState: HerbsListState,
) {
    val showStore = user?.optInt("role", -1) == 0
    var stores by listState::stores
    var selectedStoreId by listState::selectedStoreId
    var data by listState::data
    var keyword by listState::keyword
    var type by listState::type
    var error by listState::error
    var reload by listState::reload
    var refreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(showStore) {
        if (listState.storesLoaded) return@LaunchedEffect
        if (!showStore) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                listState.storesLoaded = true
                if (stores.size == 1) selectedStoreId = stores.first().displayField("id", "")
            }
    }

    LaunchedEffect(selectedStoreId, keyword, type, reload) {
        val queryKey = listOf(selectedStoreId.orEmpty(), keyword, type).joinToString("|")
        if (listState.loadedQueryKey == queryKey && data != null) return@LaunchedEffect
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.herbLocationMatrix(selectedStoreId?.toIntOrNull(), keyword, type)
            }
        }.onSuccess {
            data = it
            listState.loadedQueryKey = queryKey
            refreshing = false
        }
            .onFailure {
                error = it.message ?: "加载斗谱失败"
                refreshing = false
            }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                ApiClient.clearResponseCache(context)
                listState.invalidate()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(listState.scrollState)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionHeader("斗谱管理", "中药斗谱布局与货位药材维护")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    onNavigate(ScreenTarget.HerbLocationAssign(JSONObject(), selectedStoreId?.toIntOrNull()))
                },
                shape = FieldShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("配置药材", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Store Chips
        if (showStore && stores.size > 1) {
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId.orEmpty(),
                onSelectStore = { selectedStoreId = it.ifBlank { null } },
            )
            Spacer(Modifier.height(10.dp))
        }

        // Search bar
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索药材名称、拼音或位置编码",
            onSearch = { reload++ },
        )

        Spacer(Modifier.height(10.dp))

        // Type filter chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("" to "全部区域", "D" to "药斗", "G" to "药柜", "F" to "冰箱", "C" to "仓库").forEach { (key, label) ->
                SegmentedButton(label, type == key, onClick = { type = key })
            }
        }

        Spacer(Modifier.height(14.dp))

        if (data == null && error == null) AppEmptyState("加载斗谱数据中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)

        data?.let { root ->
            val units = root.optJSONArray("units") ?: JSONArray()
            if (units.length() == 0) {
                AppEmptyState("未找到匹配的货位数据")
            }

            (0 until units.length()).forEach { uIndex ->
                val unit = units.getJSONObject(uIndex)
                val unitNo = unit.displayField("unitNo")
                val unitType = unit.displayField("type")
                val locations = unit.optJSONArray("locations") ?: JSONArray()

                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = PrimarySoft,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${locationTypeLabel(unitType)} $unitNo 组",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Ink,
                            )
                        }
                        Text(
                            text = "共 ${locations.length()} 个位置",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until locations.length()).forEach { lIndex ->
                            val loc = locations.getJSONObject(lIndex)
                            val herbs = loc.optJSONArray("herbs") ?: JSONArray()
                            val code = loc.displayField("code")
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigate(ScreenTarget.HerbLocationAssign(loc, selectedStoreId?.toIntOrNull()))
                                    },
                                shape = FieldShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, CardBorderColor),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            SearchHighlightedText(
                                                text = code,
                                                keyword = keyword,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = Ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(5.dp),
                                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
                                            ) {
                                                SearchHighlightedText(
                                                    text = positionLabel(loc),
                                                    keyword = keyword,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    color = PrimaryDark,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = locationTypeLabel(loc.displayField("type")),
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        if (herbs.length() == 0) {
                                            Text("未配置药材（空置）", color = Muted, fontSize = 12.sp)
                                        } else {
                                            Text(
                                                text = buildAnnotatedString {
                                                    (0 until herbs.length()).forEach { index ->
                                                        if (index > 0) append("、")
                                                        val name = herbs.getJSONObject(index).displayField("name")
                                                        append(searchHighlightedText(name, keyword, Primary.copy(alpha = 0.28f), PrimaryDark, matchPinyin = true))
                                                    }
                                                },
                                                color = RegularText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
private fun SearchHighlightedText(
    text: String,
    keyword: String,
    modifier: Modifier = Modifier,
    color: Color,
    fontWeight: FontWeight? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    matchPinyin: Boolean = false,
) {
    Text(
        text = searchHighlightedText(text, keyword, Primary.copy(alpha = 0.28f), PrimaryDark, matchPinyin),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun searchHighlightedText(
    value: String,
    keyword: String,
    highlightBackground: Color,
    highlightColor: Color,
    matchPinyin: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val text = value
    val query = keyword.trim()
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    val normalizedText = text.lowercase()
    val normalizedQuery = query.lowercase()
    var matchStart = normalizedText.indexOf(normalizedQuery)
    if (matchStart >= 0) {
        var cursor = 0
        while (matchStart >= 0) {
            append(text.substring(cursor, matchStart))
            pushStyle(SpanStyle(color = highlightColor, background = highlightBackground, fontWeight = FontWeight.SemiBold))
            append(text.substring(matchStart, matchStart + query.length))
            pop()
            cursor = matchStart + query.length
            matchStart = normalizedText.indexOf(normalizedQuery, cursor)
        }
        append(text.substring(cursor))
        return@buildAnnotatedString
    }

    val range = if (matchPinyin) pinyinInitialMatchRange(text, query) else null
    if (range == null) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, range.first))
    pushStyle(SpanStyle(color = highlightColor, background = highlightBackground, fontWeight = FontWeight.SemiBold))
    append(text.substring(range))
    pop()
    append(text.substring(range.last + 1))
}

@Composable
internal fun HerbLocationAssignScreen(
    location: JSONObject,
    storeId: Int?,
    onSaved: () -> Unit,
) {
    val existingLocation = location.displayField("code", "").isNotBlank()
    var selectedHerbId by remember { mutableStateOf(0) }
    var editingHerbId by remember { mutableStateOf<Int?>(null) }
    var herbName by remember { mutableStateOf("") }
    var herbCode by remember { mutableStateOf("") }
    var specification by remember { mutableStateOf("") }
    var herbKeyword by remember { mutableStateOf("") }
    var locationType by remember { mutableStateOf(location.displayField("type", "").ifBlank { "D" }) }
    var unitNo by remember { mutableStateOf(location.displayField("unitNo", "")) }
    var layerNo by remember { mutableStateOf(location.displayField("layerNo", "")) }
    var columnNo by remember { mutableStateOf(location.displayField("columnNo", "")) }
    var slotNo by remember { mutableStateOf(location.displayField("slotNo", "").ifBlank { "1" }) }
    var herbs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(storeId) {
        runCatching {
            withContext(Dispatchers.IO) { ApiClient.herbLocationMatrix(storeId, "", "") }
        }.onSuccess { root ->
            val arr = root.optJSONArray("herbs") ?: JSONArray()
            herbs = (0 until arr.length()).map { arr.getJSONObject(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (existingLocation) {
            val currentHerbs = (0 until (location.optJSONArray("herbs")?.length() ?: 0))
                .map { location.getJSONArray("herbs").getJSONObject(it) }
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("当前位置", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            location.displayField("code"),
                            color = Ink,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(positionLabel(location), color = PrimaryDark, fontSize = 12.sp)
                    }
                    Surface(
                        color = PrimarySoft,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            locationTypeLabel(location.displayField("type")),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = PrimaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("已配置药材", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                if (currentHerbs.isEmpty()) {
                    Text("当前货位为空，可在下方添加药材", color = Muted, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentHerbs.forEach { herb ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = FieldShape,
                                border = BorderStroke(1.dp, CardBorderColor),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(herb.displayField("name"), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        val detail = listOf(herb.displayField("code", ""), herb.displayField("specification", ""))
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        if (detail.isNotBlank()) Text(detail, color = Muted, fontSize = 11.sp)
                                    }
                                    herb.displayField("slotNo", "").takeIf { it.isNotBlank() }?.let { slot ->
                                        Text("第${slot}格", color = Muted, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingHerbId = herb.optInt("id").takeIf { it > 0 }
                                            selectedHerbId = 0
                                            herbKeyword = ""
                                            herbName = herb.displayField("name", "")
                                            herbCode = herb.displayField("code", "")
                                            specification = herb.displayField("specification", "")
                                        },
                                        shape = RoundedCornerShape(5.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("编辑", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        AppCard {
            SectionHeader(
                title = if (existingLocation) "配置货位 ${location.displayField("code")}" else "配置药材",
                subtitle = if (existingLocation) "位置：${positionLabel(location)}" else "先设置位置，再搜索匹配药材",
            )

            Spacer(Modifier.height(14.dp))

            if (!existingLocation) {
                Text("位置", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("D" to "药斗", "G" to "药柜", "F" to "冰箱", "C" to "仓库").forEach { (key, label) ->
                        SegmentedButton(label, locationType == key, onClick = { locationType = key })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = unitNo,
                    onValueChange = { unitNo = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (locationType == "D") "斗号" else "编号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = FieldShape,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = layerNo,
                    onValueChange = { layerNo = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("层") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = FieldShape,
                )
                if (locationType == "D") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = columnNo,
                        onValueChange = { columnNo = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("列") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = FieldShape,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                if (editingHerbId != null) "编辑已有药材" else "搜索匹配药材",
                color = Ink,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            if (editingHerbId == null) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = herbKeyword,
                    onValueChange = { herbKeyword = it; if (it.isBlank()) selectedHerbId = 0 },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("药材名称、编码") },
                    placeholder = { Text("输入关键词筛选已有药材") },
                    singleLine = true,
                    shape = FieldShape,
                )
                val needle = herbKeyword.trim().lowercase()
                val filteredHerbs = herbs.filter { herb ->
                    needle.isNotBlank() && listOf(
                        herb.displayField("name", ""),
                        pinyinInitials(herb.displayField("name", "")),
                        herb.displayField("code", ""),
                        herb.displayField("specification", ""),
                    )
                        .any { it.lowercase().contains(needle) }
                }.take(12)
                if (filteredHerbs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredHerbs.forEach { herb ->
                            val id = herb.optInt("id")
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    editingHerbId = null
                                    selectedHerbId = id
                                    herbName = herb.displayField("name", "")
                                    herbCode = herb.displayField("code", "")
                                    specification = herb.displayField("specification", "")
                                },
                                shape = FieldShape,
                                color = if (selectedHerbId == id) PrimarySoft else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (selectedHerbId == id) Primary else CardBorderColor),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        SearchHighlightedText(
                                            text = herb.displayField("name"),
                                            keyword = herbKeyword,
                                            color = Ink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            matchPinyin = true,
                                        )
                                        Text(
                                            listOf(
                                                herb.displayField("code", ""),
                                                herb.displayField("specification", ""),
                                            ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "已有药材" },
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingHerbId = id.takeIf { it > 0 }
                                            selectedHerbId = 0
                                            herbKeyword = ""
                                            herbName = herb.displayField("name", "")
                                            herbCode = herb.displayField("code", "")
                                            specification = herb.displayField("specification", "")
                                        },
                                        shape = RoundedCornerShape(5.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("编辑", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else if (herbKeyword.isNotBlank()) {
                    Text("没有匹配的已有药材，可继续填写新增药材", color = Muted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (editingHerbId == null) {
                Text("或填写新增药材信息", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = herbName,
                onValueChange = {
                    herbName = it
                    if (it.isNotBlank()) selectedHerbId = 0
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("药材名称 *") },
                singleLine = true,
                shape = FieldShape,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = herbCode,
                onValueChange = { herbCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("药材编码（可选）") },
                singleLine = true,
                shape = FieldShape,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = specification,
                onValueChange = { specification = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("规格说明（可选）") },
                singleLine = true,
                shape = FieldShape,
            )

            if (existingLocation && location.displayField("type", "") == "D") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = slotNo,
                    onValueChange = { slotNo = it.filter(Char::isDigit).take(1) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("格内序号 (1-3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
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
            enabled = (selectedHerbId > 0 || herbName.isNotBlank()) &&
                (existingLocation || buildLocationCode(locationType, unitNo, layerNo, columnNo, slotNo).isNotBlank()) && !busy,
            onClick = {
                busy = true
                val code = if (existingLocation) location.displayField("code", "") else buildLocationCode(locationType, unitNo, layerNo, columnNo, slotNo)
                val payload = JSONObject()
                storeId?.let { payload.put("storeId", it) }
                payload.put("name", herbName.trim())
                    .put("code", herbCode.trim())
                    .put("specification", specification.trim())
                if (editingHerbId == null) {
                    payload.put("locationCode", code)
                    if (selectedHerbId > 0) {
                        payload.remove("name")
                        payload.remove("code")
                        payload.remove("specification")
                        payload.put("herbId", selectedHerbId)
                    }
                    if (locationType == "D") slotNo.toIntOrNull()?.let { payload.put("slotNo", it) }
                }

                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            editingHerbId?.let { ApiClient.updateHerb(it, payload) }
                                ?: ApiClient.assignHerbLocation(payload)
                        }
                    }.onSuccess {
                        onSaved()
                    }.onFailure {
                        error = it.message ?: "配置药材失败"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(
                when {
                    busy -> "正在保存..."
                    editingHerbId != null -> "保存药材修改"
                    else -> "确认保存货位配置"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun locationTypeLabel(type: String): String = when (type) {
    "D" -> "药斗"
    "G" -> "药柜"
    "F" -> "冰箱"
    "C" -> "仓库"
    else -> "位置"
}

private fun buildLocationCode(type: String, unitNo: String, layerNo: String, columnNo: String, slotNo: String): String {
    if (unitNo.isBlank() || layerNo.isBlank()) return ""
    return if (type == "D") {
        if (columnNo.isBlank()) "" else "D-$unitNo-$layerNo-$columnNo"
    } else {
        "$type-$unitNo-$layerNo"
    }
}

private fun positionLabel(location: JSONObject): String {
    val type = location.displayField("type")
    val unit = location.displayField("unitNo")
    val layer = location.displayField("layerNo")
    val column = location.displayField("columnNo")
    return if (type == "D") {
        "斗$unit · ${if (layer == "0") "顶层" else "${layer}行"} · ${column}列"
    } else {
        "${locationTypeLabel(type)}$unit · $layer 层"
    }
}
