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
internal fun TransfersScreen() {
    var transfers by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var fromStoreId by remember { mutableStateOf("") }
    var toStoreId by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemSpecification by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemUnit by remember { mutableStateOf("") }
    var expectedReturnDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var returnItem by remember { mutableStateOf<JSONObject?>(null) }
    var returnQuantity by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.transfers(), ApiClient.transferStores()) } }
            .onSuccess { (transferValues, storeValues) ->
                transfers = (0 until transferValues.length()).map { transferValues.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            }
            .onFailure { error = it.message ?: "加载门店调拨失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("门店调拨"); Spacer(Modifier.weight(1f)); Button({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建调拨") } }
        Spacer(Modifier.height(14.dp))
        if (transfers == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (transfers != null && transfers!!.isEmpty()) Text("暂无调拨单", color = Muted)
        transfers.orEmpty().forEach { transfer ->
            val items = transfer.optJSONArray("items") ?: JSONArray()
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(transfer.optString("transferNo"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(transferStatusLabel(transfer.optInt("status"), transfer.optInt("outboundStatus"))) }
                    Spacer(Modifier.height(8.dp))
                    Text("${transfer.optJSONObject("fromStore")?.optString("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.optString("name") ?: "-"}", color = Ink)
                    Text("${items.length()} 项 · ${transfer.optString("transferDate").take(10)} · 预计归还 ${transfer.optString("expectedReturnDate").take(10)}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transferDetail(transfer.optInt("id")) } }.onSuccess { detail = it }.onFailure { error = it.message ?: "加载调拨详情失败" } } }, shape = RoundedCornerShape(6.dp)) { Text("查看详情") }
                }
            }
        }
    }
    if (createVisible) AlertDialog(
        onDismissRequest = { createVisible = false },
        title = { Text("新建调拨") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("调出门店", color = Muted, fontSize = 12.sp)
                StoreSelector(stores, fromStoreId) { fromStoreId = it }
                Spacer(Modifier.height(8.dp))
                Text("调入门店", color = Muted, fontSize = 12.sp)
                StoreSelector(stores, toStoreId) { toStoreId = it }
                OutlinedTextField(itemName, { itemName = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("物品名称") }, singleLine = true)
                OutlinedTextField(itemSpecification, { itemSpecification = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("规格（可选）") }, singleLine = true)
                OutlinedTextField(itemQuantity, { itemQuantity = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("借调数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(itemUnit, { itemUnit = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("单位") }, singleLine = true)
                OutlinedTextField(expectedReturnDate, { expectedReturnDate = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("预计归还日期 YYYY-MM-DD") }, singleLine = true)
            }
        },
        confirmButton = {
            val valid = fromStoreId.isNotBlank() && toStoreId.isNotBlank() && fromStoreId != toStoreId && itemName.isNotBlank() && itemUnit.isNotBlank() && itemQuantity.toDoubleOrNull()?.let { it > 0 } == true
            Button({
                val item = JSONObject().put("itemName", itemName.trim()).put("specification", itemSpecification.trim()).put("quantity", itemQuantity.toDouble()).put("unit", itemUnit.trim())
                val payload = JSONObject().put("fromStoreId", fromStoreId.toInt()).put("toStoreId", toStoreId.toInt()).put("transferDate", LocalDate.now().toString()).put("expectedReturnDate", expectedReturnDate).put("items", JSONArray().put(item))
                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createTransfer(payload) } }.onSuccess { createVisible = false; itemName = ""; itemSpecification = ""; itemQuantity = "1"; itemUnit = ""; reload++ }.onFailure { error = it.message ?: "创建调拨失败" } }
            }, enabled = valid) { Text("创建") }
        },
        dismissButton = { TextButton({ createVisible = false }) { Text("取消") } },
    )
    detail?.let { transfer ->
        val items = transfer.optJSONArray("items") ?: JSONArray()
        val canConfirm = transfer.optJSONObject("permissions")?.optBoolean("canConfirmOutbound") == true
        val pendingReturn = transfer.optJSONArray("returnRecords")?.let { records ->
            (0 until records.length()).map { records.getJSONObject(it) }.firstOrNull { it.optInt("status") == 0 }
        }
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(transfer.optString("transferNo")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${transfer.optJSONObject("fromStore")?.optString("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.optString("name") ?: "-"}", color = Muted); Spacer(Modifier.height(8.dp)); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optString("itemName"), fontWeight = FontWeight.SemiBold); Text("${item.opt("quantity") ?: 0} ${item.optString("unit")} · 已归还 ${item.opt("returnedQuantity") ?: 0}", color = Muted, fontSize = 12.sp); val available = item.optDouble("availableReturnQuantity", 0.0); if (available > 0 && transfer.optJSONObject("permissions")?.optBoolean("canSubmitReturn") == true) OutlinedButton({ returnItem = item; returnQuantity = available.toString() }, shape = RoundedCornerShape(6.dp)) { Text("申请归还") }; if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) }; pendingReturn?.let { record -> Spacer(Modifier.height(10.dp)); Text("待确认归还：${record.opt("quantity") ?: 0} · ${record.optString("returnDate").take(10)}", color = Warning, fontSize = 13.sp) } } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (canConfirm) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.confirmOutbound(transfer.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "确认调出失败" } } }) { Text("确认调出") }; if (pendingReturn != null && transfer.optJSONObject("permissions")?.optBoolean("canConfirmReturn") == true) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.confirmReturn(transfer.optInt("id"), pendingReturn.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "确认归还失败" } } }) { Text("确认归还") }; if (transfer.optJSONObject("permissions")?.optBoolean("canCancel") == true) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.cancelTransfer(transfer.optInt("id"), "安卓端取消") } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "取消调拨失败" } } }) { Text("取消") }; OutlinedButton({ detail = null }) { Text("关闭") } } }, dismissButton = { TextButton({ detail = null }) { Text("关闭") } })
    }
    returnItem?.let { item ->
        AlertDialog(onDismissRequest = { returnItem = null }, title = { Text("申请归还") }, text = { Column { Text(item.optString("itemName"), fontWeight = FontWeight.SemiBold); OutlinedTextField(returnQuantity, { returnQuantity = it }, Modifier.fillMaxWidth(), label = { Text("归还数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = returnQuantity.toDoubleOrNull()?.let { it > 0 } == true, onClick = { val transferId = detail?.optInt("id") ?: 0; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.addTransferReturns(transferId, JSONObject().put("returnDate", LocalDate.now().toString()).put("items", JSONArray().put(JSONObject().put("transferItemId", item.optInt("id")).put("quantity", returnQuantity.toDouble()))) } }.onSuccess { returnItem = null; detail = null; reload++ }.onFailure { error = it.message ?: "提交归还失败" } } }) { Text("提交") } }, dismissButton = { TextButton({ returnItem = null }) { Text("取消") } })
    }
}
