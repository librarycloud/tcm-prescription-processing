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
internal fun HerbsScreen() {
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<JSONObject?>(null) }
    var assignLocation by remember { mutableStateOf<JSONObject?>(null) }
    var moveAssignment by remember { mutableStateOf<JSONObject?>(null) }
    var editHerb by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.stores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString()
            }
            .onFailure { error = it.message ?: "加载门店失败" }
    }
    LaunchedEffect(selectedStoreId, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.herbLocations(selectedStoreId) } }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "加载斗谱失败" }
    }

    val locations = data?.optJSONArray("locations")?.let { values ->
        (0 until values.length()).map { values.getJSONObject(it) }
    }.orEmpty()
    val filtered = locations.filter { location ->
        val locationType = location.optString("type")
        val herbs = location.optJSONArray("herbs") ?: JSONArray()
        val herbText = (0 until herbs.length()).joinToString(" ") { index ->
            val herb = herbs.getJSONObject(index)
            "${herb.optString("name")} ${herb.optString("code")}".lowercase()
        }
        val searchText = "${location.optString("code")} $herbText".lowercase()
        (type.isBlank() || locationType == type) && (keyword.isBlank() || searchText.contains(keyword.trim().lowercase()))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("斗谱管理")
                Text(data?.optJSONObject("store")?.optString("name") ?: "当前门店", color = Muted, fontSize = 13.sp)
            }
            Text("${filtered.size} 个位置", color = Primary, fontWeight = FontWeight.SemiBold)
        }
        if (stores.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("选择门店", color = Muted, fontSize = 12.sp)
            stores.forEach { store ->
                val id = store.opt("id")?.toString().orEmpty()
                val selected = id == selectedStoreId
                if (selected) Button({ selectedStoreId = id }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
                else OutlinedButton({ selectedStoreId = id }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索药材名称、编码或位置") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("位置类型", color = Muted, fontSize = 12.sp)
        listOf("" to "全部位置", "D" to "斗 D", "G" to "柜 G", "F" to "冰箱 F", "C" to "仓库 C").chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    if (type == option.first) Button({ type = option.first }, Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text(option.second, fontSize = 12.sp) }
                    else OutlinedButton({ type = option.first }, Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text(option.second, fontSize = 12.sp) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (data == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (data != null && filtered.isEmpty()) Text("暂无匹配位置", color = Muted)
        filtered.forEach { location ->
            val herbs = location.optJSONArray("herbs") ?: JSONArray()
            val herbNames = (0 until herbs.length()).joinToString(" / ") { herbs.getJSONObject(it).optString("name") }.ifBlank { "未配置药材" }
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selectedLocation = location }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(location.optString("code"), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Primary, fontSize = 17.sp)
                        StatusPill(locationTypeLabel(location.optString("type")))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(positionLabel(location), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(herbNames, color = Ink, fontSize = 14.sp)
                }
            }
        }
    }
    selectedLocation?.let { location ->
        val herbs = location.optJSONArray("herbs") ?: JSONArray()
        AlertDialog(
            onDismissRequest = { selectedLocation = null },
            title = { Text(location.optString("code")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${locationTypeLabel(location.optString("type"))} · ${positionLabel(location)}", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (herbs.length() == 0) Text("当前库位未配置药材", color = Muted)
                    (0 until herbs.length()).forEach { index ->
                        val herb = herbs.getJSONObject(index)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(herb.optString("name"), fontWeight = FontWeight.SemiBold)
                                Text("${herb.optString("code").ifBlank { "-" }} · ${herb.optString("specification").ifBlank { "未填写规格" }} · 格内 ${herb.opt("slotNo") ?: "-"}", color = Muted, fontSize = 12.sp)
                            }
                            TextButton({ editHerb = herb }) { Text("编辑") }
                            TextButton({ moveAssignment = herb }) { Text("移动") }
                            TextButton({
                                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deleteHerbLocationAssignment(herb.optInt("assignmentId")) } }
                                    .onSuccess { selectedLocation = null; reload++ }
                                    .onFailure { error = it.message ?: "移除药材失败" } }
                            }) { Text("移除", color = Danger) }
                        }
                        if (index < herbs.length() - 1) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ assignLocation = location }) { Text("配置药材") }; OutlinedButton({ selectedLocation = null }) { Text("关闭") } } },
        )
    }
    assignLocation?.let { location ->
        val herbs = data?.optJSONArray("herbs")?.let { values -> (0 until values.length()).map { values.getJSONObject(it) } }.orEmpty()
        var selectedHerbId by remember(location, reload) { mutableStateOf(0) }
        var herbName by remember(location) { mutableStateOf("") }
        var herbCode by remember(location) { mutableStateOf("") }
        var specification by remember(location) { mutableStateOf("") }
        var slotNo by remember(location) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { assignLocation = null }, title = { Text("配置药材 · ${location.optString("code")}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (herbs.isNotEmpty()) {
                    Text("已有药材", color = Muted, fontSize = 12.sp)
                    herbs.take(12).forEach { herb -> SegmentedButton(herb.optString("name"), selectedHerbId == herb.optInt("id")) { selectedHerbId = herb.optInt("id"); herbName = ""; herbCode = ""; specification = "" } }
                    Spacer(Modifier.height(8.dp))
                }
                Text("或新增药材", color = Muted, fontSize = 12.sp)
                OutlinedTextField(herbName, { herbName = it }, Modifier.fillMaxWidth(), label = { Text("药材名称") }, singleLine = true)
                OutlinedTextField(herbCode, { herbCode = it }, Modifier.fillMaxWidth(), label = { Text("药材编码（可选）") }, singleLine = true)
                OutlinedTextField(specification, { specification = it }, Modifier.fillMaxWidth(), label = { Text("规格（可选）") }, singleLine = true)
                if (location.optString("type") == "D") OutlinedTextField(slotNo, { slotNo = it.filter(Char::isDigit).take(1) }, Modifier.fillMaxWidth(), label = { Text("格内序号 1-3") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        }, confirmButton = {
            Button(enabled = selectedHerbId > 0 || herbName.isNotBlank(), onClick = {
                val payload = JSONObject().put("locationCode", location.optString("code"))
                selectedStoreId?.toIntOrNull()?.let { payload.put("storeId", it) }
                if (selectedHerbId > 0) payload.put("herbId", selectedHerbId) else payload.put("name", herbName.trim()).put("code", herbCode.trim()).put("specification", specification.trim())
                slotNo.toIntOrNull()?.let { payload.put("slotNo", it) }
                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.assignHerbLocation(payload) } }
                    .onSuccess { assignLocation = null; selectedLocation = null; reload++ }
                    .onFailure { error = it.message ?: "配置药材失败" } }
            }) { Text("保存") }
        }, dismissButton = { TextButton({ assignLocation = null }) { Text("取消") } })
    }
    moveAssignment?.let { assignment ->
        var locationCode by remember(assignment) { mutableStateOf(selectedLocation?.optString("code").orEmpty()) }
        AlertDialog(onDismissRequest = { moveAssignment = null }, title = { Text("移动 ${assignment.optString("name")}") }, text = { Column { OutlinedTextField(locationCode, { locationCode = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("目标位置编号，例如 D-1-1-1") }, singleLine = true) } }, confirmButton = {
            Button(enabled = locationCode.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.moveHerbLocationAssignment(assignment.optInt("assignmentId"), JSONObject().put("locationCode", locationCode.trim())) } }
                .onSuccess { moveAssignment = null; selectedLocation = null; reload++ }
                .onFailure { error = it.message ?: "移动药材失败" } } }) { Text("移动") }
        }, dismissButton = { TextButton({ moveAssignment = null }) { Text("取消") } })
    }
    editHerb?.let { herb ->
        var name by remember(herb) { mutableStateOf(herb.optString("name")) }
        var code by remember(herb) { mutableStateOf(herb.optString("code")) }
        var specification by remember(herb) { mutableStateOf(herb.optString("specification")) }
        AlertDialog(onDismissRequest = { editHerb = null }, title = { Text("编辑药材") }, text = { Column { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("药材名称") }, singleLine = true); OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("药材编码") }, singleLine = true); OutlinedTextField(specification, { specification = it }, Modifier.fillMaxWidth(), label = { Text("规格") }, singleLine = true) } }, confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updateHerb(herb.optInt("id"), JSONObject().put("name", name.trim()).put("code", code.trim()).put("specification", specification.trim())) } }
                .onSuccess { editHerb = null; selectedLocation = null; reload++ }
                .onFailure { error = it.message ?: "保存药材失败" } } }) { Text("保存") }
        }, dismissButton = { TextButton({ editHerb = null }) { Text("取消") } })
    }
}

private fun locationTypeLabel(type: String): String = when (type) { "D" -> "药斗"; "G" -> "药柜"; "F" -> "冰箱"; "C" -> "仓库"; else -> "位置" }
private fun positionLabel(location: JSONObject): String {
    val type = location.optString("type")
    val unit = location.opt("unitNo") ?: "-"
    val layer = location.opt("layerNo") ?: "-"
    val column = location.opt("columnNo")
    return if (type == "D") "斗$unit · ${if (layer.toString() == "0") "顶层" else "${layer}行"} · ${column ?: "-"}列" else "${locationTypeLabel(type)}$unit · $layer 层"
}
