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
private fun FakeQr(value: String) { Canvas(Modifier.size(150.dp).background(Color.White)) { val cells = 21; val cell = size.minDimension / cells; for (x in 0 until cells) for (y in 0 until cells) if (((x * 31 + y * 17 + value.length * 13) % 7) < 3 || (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13)) drawRect(if ((x + y) % 3 == 0) Color.Black else Color.DarkGray, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell)) } }
@Composable
private fun PackagesScreenV2(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }; var statusFilter by remember { mutableStateOf<Int?>(null) }; var list by remember { mutableStateOf<List<PackageItem>?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var reload by remember { mutableStateOf(0) }
    LaunchedEffect(keyword, statusFilter, reload) { error = null; runCatching { withContext(Dispatchers.IO) { ApiClient.packages(status = statusFilter, keyword = keyword) } }.onSuccess { a -> list = (0 until a.length()).map { packageItem(a.getJSONObject(it)) } }.onFailure { error = it.message ?: "加载包裹失败" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(
            keyword,
            { keyword = it },
            Modifier.fillMaxWidth(),
            placeholder = { Text("取货码、手机号、姓名、物品") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedButton("全部", statusFilter == null) { statusFilter = null }
            SegmentedButton("待取", statusFilter == 0) { statusFilter = 0 }
            SegmentedButton("已取", statusFilter == 1) { statusFilter = 1 }
        }
        Spacer(Modifier.height(14.dp))
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        if (list == null && error == null) Text("加载中...", color = Muted)
        if (list != null && list!!.isEmpty()) Text("暂无包裹", color = Muted)
        list.orEmpty()
            .filter { keyword.isBlank() || it.customer.contains(keyword) || it.phone.contains(keyword) || it.code.contains(keyword) || it.name.contains(keyword) }
            .forEach { item ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) },
                    colors = CardDefaults.cardColors(Color.White),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text("${item.customer} · ${item.phone}", color = Muted, fontSize = 12.sp)
                            }
                            StatusPill(item.status)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("取货码：${item.code}", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${item.method}${if (item.store.isNotBlank()) " · ${item.store}" else ""} · ${item.time}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
    }
}

@Composable
internal fun PackageDetailDialogV2(item: PackageItem, onClose: () -> Unit) {
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(item.name) }
    var editReceiver by remember { mutableStateOf(item.customer) }
    var editPhone by remember { mutableStateOf(item.phone) }
    var editInfo by remember { mutableStateOf(item.info) }
    var editMethod by remember { mutableStateOf(item.methodCode) }
    var tracking by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) { if (item.id > 0) runCatching { withContext(Dispatchers.IO) { ApiClient.packageDetail(item.id) } }.onSuccess { detail = it }.onFailure { error = it.message ?: "详情加载失败" } }
    val current = detail
    if (editing) {
        AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text("编辑包裹") }, text = { Column { OutlinedTextField(editName, { editName = it }, Modifier.fillMaxWidth(), label = { Text("物品名称") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(editReceiver, { editReceiver = it }, Modifier.fillMaxWidth(), label = { Text("收件人") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(editPhone, { editPhone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) -> SegmentedButton(label, editMethod == key) { editMethod = key } } }; OutlinedTextField(editInfo, { editInfo = it }, Modifier.fillMaxWidth(), label = { Text("备注") }) } }, confirmButton = { Button(enabled = editName.isNotBlank() && editReceiver.isNotBlank() && !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updatePackage(item.id, JSONObject().put("itemName", editName.trim()).put("receiverName", editReceiver.trim()).put("receiverPhone", editPhone.trim()).put("pickupMethod", editMethod).put("itemInfo", editInfo.trim())) } }.onSuccess { editing = false }.onFailure { error = it.message ?: "保存包裹失败" }; busy = false } }) { Text(if (busy) "保存中..." else "保存") } }, dismissButton = { TextButton({ if (!busy) editing = false }) { Text("取消") } })
    } else {
        AlertDialog(onDismissRequest = onClose, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (item.statusCode == 0) Button(enabled = !busy && (item.methodCode != 2 || tracking.isNotBlank()), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(item.code, item.methodCode, tracking.trim()) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "核销失败" }; busy = false } }) { Text(if (busy) "核销中..." else "确认核销") }; if (item.statusCode == 0) OutlinedButton({ editing = true }) { Text("编辑") }; if (item.statusCode == 0) OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deletePackage(item.id) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "删除包裹失败" }; busy = false } }) { Text("删除") }; OutlinedButton(onClose) { Text("关闭") } } }, title = { Text("包裹详情") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { Text(item.code, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary); Spacer(Modifier.height(10.dp)); FakeQr(item.code); Spacer(Modifier.height(10.dp)); Text("物品：${current?.optString("itemName", item.name) ?: item.name}\n客户：${current?.optString("receiverName", item.customer) ?: item.customer}\n手机号：${current?.optString("receiverPhone", item.phone) ?: item.phone}\n取货方式：${item.method}\n状态：${item.status}\n门店：${current?.optJSONObject("store")?.optString("name", item.store) ?: item.store}\n备注：${current?.optString("itemInfo", item.info) ?: item.info}", color = Ink); if (item.statusCode == 0 && item.methodCode == 2) { Spacer(Modifier.height(8.dp)); OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号（核销必填）") }, singleLine = true) }; error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 12.sp) } } })
    }
}

@Composable
internal fun PackagesScreenV3(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf(false) }
    var verify by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var receiverPhone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(0) }
    var tracking by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(keyword, status, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.packages(status = status, keyword = keyword) } }
            .onSuccess { array -> items = (0 until array.length()).map { packageItem(array.getJSONObject(it)) } }
            .onFailure { error = it.message ?: "加载包裹失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ form = true }) { Text("新增包裹") }; OutlinedButton({ verify = true }) { Text("取货码核销") } }
        Spacer(Modifier.height(10.dp)); OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("取货码、手机号、姓名、物品") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", status == null) { status = null }; SegmentedButton("待取", status == 0) { status = 0 }; SegmentedButton("已取", status == 1) { status = 1 } }
        Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }; if (items == null && error == null) Text("加载中...", color = Muted); if (items != null && items!!.isEmpty()) Text("暂无包裹", color = Muted)
        items.orEmpty().forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.customer} · ${item.phone}", color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(8.dp)); Text("取货码：${item.code}", fontWeight = FontWeight.Bold, color = Primary, fontSize = 17.sp); Text("${item.method} · ${item.time}", color = Muted, fontSize = 12.sp) } } }
    }
    if (form) AlertDialog(onDismissRequest = { if (!busy) form = false }, title = { Text("新增包裹") }, text = { Column { OutlinedTextField(itemName, { itemName = it }, Modifier.fillMaxWidth(), label = { Text("物品名称") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(receiverName, { receiverName = it }, Modifier.fillMaxWidth(), label = { Text("收件人") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(receiverPhone, { receiverPhone = it }, Modifier.fillMaxWidth(), label = { Text("手机号（可选）") }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) -> SegmentedButton(label, method == key) { method = key } }; }; if (method == 2) OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号") }, singleLine = true) } }, confirmButton = { Button(enabled = itemName.isNotBlank() && receiverName.isNotBlank() && !busy && (method != 2 || tracking.isNotBlank()), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createPackage(JSONObject().put("itemName", itemName.trim()).put("receiverName", receiverName.trim()).put("receiverPhone", receiverPhone.trim()).put("pickupMethod", method).put("expressTrackingNo", tracking.trim())) } }.onSuccess { form = false; itemName = ""; receiverName = ""; receiverPhone = ""; tracking = ""; reload++ }.onFailure { error = it.message ?: "新增包裹失败" }; busy = false } }) { Text(if (busy) "提交中..." else "创建") } }, dismissButton = { TextButton({ if (!busy) form = false }) { Text("取消") } })
    if (verify) {
        AlertDialog(
            onDismissRequest = { if (!busy) verify = false },
            title = { Text("取货码核销") },
            text = {
                Column {
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6 位取货码") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) ->
                            SegmentedButton(label, method == key) { method = key }
                        }
                    }
                    if (method == 2) OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = code.length == 6 && !busy && (method != 2 || tracking.isNotBlank()),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(code, method, tracking.trim()) } }
                                .onSuccess { verify = false; code = ""; tracking = ""; reload++ }
                                .onFailure { error = it.message ?: "核销失败" }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "核销中..." else "确认核销") }
            },
            dismissButton = { TextButton({ if (!busy) verify = false }) { Text("取消") } },
        )
    }
}
