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
private fun prescriptionStatusLabel(value: Int): String = when (value) { 0 -> "进行中"; 1 -> "已完成"; 2 -> "已取消"; else -> "未知" }

@Composable
internal fun PrescriptionsScreen() {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.doctors(), ApiClient.dictionaries("PrescriptionSource")) } }
            .onSuccess { (doctorArray, sourceArray) ->
                doctors = (0 until doctorArray.length()).map { doctorArray.getJSONObject(it) }
                sources = (0 until sourceArray.length()).map { sourceArray.getJSONObject(it) }
            }
            .onFailure { error = it.message ?: "加载处方选项失败" }
    }
    LaunchedEffect(keyword, status, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.prescriptions(status, keyword) } }
            .onSuccess { array -> items = (0 until array.length()).map { array.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载处方失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("处方管理"); Spacer(Modifier.weight(1f)); Button({ editing = JSONObject() }, shape = RoundedCornerShape(6.dp)) { Text("新建处方") } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("处方号、顾客、手机号、医生") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", status == null) { status = null }; SegmentedButton("进行中", status == 0) { status = 0 }; SegmentedButton("已完成", status == 1) { status = 1 }; SegmentedButton("已取消", status == 2) { status = 2 } }
        Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        if (items == null && error == null) Text("加载中...", color = Muted)
        if (items != null && items!!.isEmpty()) Text("暂无处方", color = Muted)
        items.orEmpty().forEach { item ->
            val prescriptionNo = item.optString("prescriptionNo", "处方")
            val state = item.optInt("status")
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { detail = item }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(prescriptionNo, fontWeight = FontWeight.SemiBold); Text("${item.optString("customerName", "未填写")} · ${item.optString("phone", "-")}", color = Muted, fontSize = 12.sp) }; StatusPill(prescriptionStatusLabel(state)) }
                    Spacer(Modifier.height(8.dp)); Text("医生：${item.optJSONObject("doctor")?.optString("name", "-") ?: "-"} · ${item.optJSONObject("source")?.optString("name", "-") ?: "-"}", color = Ink, fontSize = 13.sp)
                    Text("剂数：${item.opt("totalDose") ?: 0} · ${item.optString("createdAt").replace("T", " ").take(16)}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
    detail?.let { item ->
        val canEdit = item.optInt("status") != 1
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(item.optString("prescriptionNo", "处方详情")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("顾客：${item.optString("customerName", "-")}\n手机号：${item.optString("phone", "-")}\n医生：${item.optJSONObject("doctor")?.optString("name", "-") ?: "-"}\n来源：${item.optJSONObject("source")?.optString("name", "-") ?: "-"}\n状态：${prescriptionStatusLabel(item.optInt("status"))}\n备注：${item.optString("remark", "-")}", color = Ink); Spacer(Modifier.height(10.dp)); val plans = item.optJSONArray("plans") ?: JSONArray(); Text("加工批次：${plans.length()}", color = Muted, fontSize = 13.sp); (0 until plans.length()).forEach { i -> val plan = plans.getJSONObject(i); Text("第${plan.optInt("batchNo", i + 1)}批 · ${plan.optJSONObject("processType")?.optString("name", "加工") ?: "加工"} · ${planStatus(plan.optInt("status"))}", fontSize = 12.sp) } } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (canEdit) Button({ editing = item; detail = null }) { Text("编辑") }; if (item.optInt("status") == 0) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updatePrescription(item.optInt("id"), JSONObject().put("status", 2)) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "取消处方失败" } } }) { Text("取消处方") }; if (plansEmpty(item)) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deletePrescription(item.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "删除处方失败" } } }) { Text("删除") }; OutlinedButton({ detail = null }) { Text("关闭") } } })
    }
    editing?.let { initial -> PrescriptionFormDialog(initial, doctors, sources, onClose = { editing = null }, onSaved = { editing = null; reload++ }, onError = { error = it }) }
}

private fun plansEmpty(item: JSONObject): Boolean = (item.optJSONArray("plans")?.length() ?: 0) == 0

@Composable
private fun PrescriptionFormDialog(initial: JSONObject, doctors: List<JSONObject>, sources: List<JSONObject>, onClose: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit) {
    val isEdit = initial.has("id")
    var customer by remember(initial) { mutableStateOf(initial.optString("customerName")) }
    var phone by remember(initial) { mutableStateOf(initial.optString("phone")) }
    var remark by remember(initial) { mutableStateOf(initial.optString("remark")) }
    var doctorId by remember(initial) { mutableStateOf(initial.optInt("doctorId").takeIf { it > 0 } ?: doctors.firstOrNull()?.optInt("id", 0) ?: 0) }
    var sourceId by remember(initial) { mutableStateOf(initial.optInt("sourceId").takeIf { it > 0 } ?: sources.firstOrNull()?.optInt("id", 0) ?: 0) }
    var external by remember(initial) { mutableStateOf(initial.optInt("isExternal") == 1) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (isEdit) "编辑处方" else "新建处方") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("顾客姓名") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(), label = { Text("手机号（可选）") }, singleLine = true); Spacer(Modifier.height(8.dp)); Text("医生", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { doctors.take(4).forEach { d -> SegmentedButton(d.optString("name", "医生"), doctorId == d.optInt("id")) { doctorId = d.optInt("id") } } }; Spacer(Modifier.height(8.dp)); Text("处方来源", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sources.take(4).forEach { s -> SegmentedButton(s.optString("name", "来源"), sourceId == s.optInt("id")) { sourceId = s.optInt("id") } } }; Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("外方处方"); Spacer(Modifier.weight(1f)); androidx.compose.material3.Switch(external, { external = it }) }; OutlinedTextField(remark, { remark = it }, Modifier.fillMaxWidth(), label = { Text("备注") }) } }, confirmButton = { Button(enabled = customer.isNotBlank() && doctorId > 0 && sourceId > 0 && !busy, onClick = { busy = true; val payload = JSONObject().put("customerName", customer.trim()).put("phone", phone.trim()).put("doctorId", doctorId).put("sourceId", sourceId).put("isExternal", external).put("remark", remark.trim()); scope.launch { runCatching { withContext(Dispatchers.IO) { if (isEdit) ApiClient.updatePrescription(initial.optInt("id"), payload) else ApiClient.createPrescription(payload) } }.onSuccess { onSaved() }.onFailure { onError(it.message ?: "保存处方失败") }; busy = false } }) { Text(if (busy) "保存中..." else "保存") } }, dismissButton = { TextButton({ if (!busy) onClose() }) { Text("取消") } })
}
