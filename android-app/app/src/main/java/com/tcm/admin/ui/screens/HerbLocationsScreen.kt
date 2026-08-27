package com.tcm.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
internal fun HerbsScreen(onNavigate: (ScreenTarget) -> Unit) {
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString()
            }
    }

    LaunchedEffect(selectedStoreId, keyword, type, reload) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.herbLocationMatrix(selectedStoreId?.toIntOrNull(), keyword, type)
            }
        }.onSuccess { data = it }
            .onFailure { error = it.message ?: "加载斗谱失败" }
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
                SectionHeader("斗谱管理", "中药斗谱布局与货位药材维护")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Store Chips
        StoreChipsRow(
            stores = stores,
            selectedStoreId = selectedStoreId.orEmpty(),
            onSelectStore = { selectedStoreId = it.ifBlank { null } },
        )

        if (stores.size > 1) Spacer(Modifier.height(10.dp))

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
                SegmentedButton(label, type == key) { type = key }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (data == null && error == null) AppEmptyState("加载斗谱数据中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)

        data?.let { root ->
            val summary = root.optJSONObject("summary") ?: JSONObject()
            StatsGrid(
                listOf(
                    "总位置数" to summary.optInt("totalLocations").toString(),
                    "已分配" to summary.optInt("assignedLocations").toString(),
                    "空置位置" to summary.optInt("emptyLocations").toString(),
                    "药材总数" to summary.optInt("totalHerbs").toString(),
                ),
            )

            Spacer(Modifier.height(16.dp))

            val units = root.optJSONArray("units") ?: JSONArray()
            if (units.length() == 0) {
                AppEmptyState("未找到匹配的货位数据")
            }

            (0 until units.length()).forEach { uIndex ->
                val unit = units.getJSONObject(uIndex)
                val unitNo = unit.opt("unitNo")?.toString() ?: "-"
                val unitType = unit.optString("type")
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
                            val code = loc.optString("code")
                            val isSelected = selectedLocation?.optString("code") == code

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedLocation = if (isSelected) null else loc },
                                shape = FieldShape,
                                color = if (isSelected) PrimarySoft else Color(0xFFF9FAFB),
                                border = BorderStroke(1.dp, if (isSelected) Primary else Color(0xFFEAECF0)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = code,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) PrimaryDark else Ink,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = positionLabel(loc),
                                                color = Muted,
                                                fontSize = 11.sp,
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        if (herbs.length() == 0) {
                                            Text("未配置药材（空置）", color = Muted, fontSize = 12.sp)
                                        } else {
                                            val herbNames = (0 until herbs.length())
                                                .map { herbs.getJSONObject(it).optString("name") }
                                                .joinToString("、")
                                            Text(
                                                text = herbNames,
                                                color = RegularText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onNavigate(ScreenTarget.HerbLocationAssign(loc, selectedStoreId?.toIntOrNull()))
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("配置", fontSize = 11.sp)
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

@Composable
internal fun HerbLocationAssignScreen(
    location: JSONObject,
    storeId: Int?,
    onSaved: () -> Unit,
) {
    var selectedHerbId by remember { mutableStateOf(0) }
    var herbName by remember { mutableStateOf("") }
    var herbCode by remember { mutableStateOf("") }
    var specification by remember { mutableStateOf("") }
    var slotNo by remember { mutableStateOf("1") }
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppCard {
            SectionHeader(
                title = "配置货位 ${location.optString("code")}",
                subtitle = "位置：${positionLabel(location)}",
            )

            Spacer(Modifier.height(14.dp))

            if (herbs.isNotEmpty()) {
                Text("从已有药材选择：", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    herbs.take(16).forEach { herb ->
                        val id = herb.optInt("id")
                        SegmentedButton(
                            label = herb.optString("name"),
                            selected = selectedHerbId == id,
                            onClick = {
                                selectedHerbId = id
                                herbName = ""
                                herbCode = ""
                                specification = ""
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text("或输入新增药材信息：", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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

            if (location.optString("type") == "D") {
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
            enabled = (selectedHerbId > 0 || herbName.isNotBlank()) && !busy,
            onClick = {
                busy = true
                val payload = JSONObject().put("locationCode", location.optString("code"))
                storeId?.let { payload.put("storeId", it) }
                if (selectedHerbId > 0) {
                    payload.put("herbId", selectedHerbId)
                } else {
                    payload.put("name", herbName.trim())
                        .put("code", herbCode.trim())
                        .put("specification", specification.trim())
                }
                slotNo.toIntOrNull()?.let { payload.put("slotNo", it) }

                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ApiClient.assignHerbLocation(payload)
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
            Text(if (busy) "正在保存..." else "确认保存货位配置", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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

private fun positionLabel(location: JSONObject): String {
    val type = location.optString("type")
    val unit = location.opt("unitNo") ?: "-"
    val layer = location.opt("layerNo") ?: "-"
    val column = location.opt("columnNo")
    return if (type == "D") {
        "斗$unit · ${if (layer.toString() == "0") "顶层" else "${layer}行"} · ${column ?: "-"}列"
    } else {
        "${locationTypeLabel(type)}$unit · $layer 层"
    }
}
