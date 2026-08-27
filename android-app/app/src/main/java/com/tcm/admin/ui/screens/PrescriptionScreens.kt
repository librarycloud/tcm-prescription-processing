package com.tcm.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

private fun prescriptionStatusLabel(value: Int): String = when (value) {
    0 -> "进行中"
    1 -> "已完成"
    2 -> "已取消"
    else -> "未知"
}

@Composable
internal fun PrescriptionsScreen() {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, keyword, status, selectedStoreId) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                Triple(
                    ApiClient.prescriptions(status, keyword.trim(), selectedStoreId.toIntOrNull()),
                    ApiClient.doctors(),
                    ApiClient.prescriptionSources(),
                )
            }
        }.onSuccess { (prescriptions, doctorList, sourceList) ->
            items = (0 until prescriptions.length()).map { prescriptions.getJSONObject(it) }
            doctors = (0 until doctorList.length()).map { doctorList.getJSONObject(it) }
            sources = (0 until sourceList.length()).map { sourceList.getJSONObject(it) }
        }.onFailure {
            error = it.message ?: "加载处方失败"
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
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
                SectionHeader("处方管理", "查看、新建与管理中药处方")
            }
            Button(
                onClick = { editing = JSONObject() },
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建处方")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search bar
        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索患者姓名、手机号或处方号",
            onSearch = { reload++ },
        )

        Spacer(Modifier.height(10.dp))

        // Status Tabs
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedButton("全部状态", status == null) { status = null }
            SegmentedButton("进行中", status == 0) { status = 0 }
            SegmentedButton("已完成", status == 1) { status = 1 }
            SegmentedButton("已取消", status == 2) { status = 2 }
        }

        if (stores.size > 1) {
            Spacer(Modifier.height(8.dp))
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it },
            )
        }

        Spacer(Modifier.height(14.dp))

        if (items == null && error == null) AppEmptyState("加载中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (items != null && items!!.isEmpty()) AppEmptyState("暂无处方记录")

        items.orEmpty().forEach { p ->
            val doctor = p.optJSONObject("doctor")?.optString("name", "-") ?: "-"
            val source = p.optJSONObject("source")?.optString("name", "-") ?: "-"
            val store = p.optJSONObject("store")?.optString("name", "") ?: ""
            val createdAt = p.optString("createdAt").replace("T", " ").take(16)
            val statusCode = p.optInt("status")
            val isExternal = p.optInt("isExternal") == 1

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = p.optString("customerName", "患者"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Ink,
                        )
                        if (isExternal) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = WarningSoft,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = "外方",
                                    color = Warning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    StatusPill(text = prescriptionStatusLabel(statusCode))
                }

                Spacer(Modifier.height(10.dp))

                InfoRowItem(label = "联系电话", value = maskPhone(p.optString("phone")))
                InfoRowItem(label = "主治医生", value = doctor)
                InfoRowItem(label = "处方来源", value = source)
                if (store.isNotBlank()) {
                    InfoRowItem(label = "所属门店", value = store)
                }
                InfoRowItem(label = "创建时间", value = createdAt)

                val remark = p.optString("remark", "")
                if (remark.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFF9FAFB),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "备注：$remark",
                            color = RegularText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { editing = p },
                        shape = FieldShape,
                    ) {
                        Text("编辑")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        ApiClient.prescriptionDetail(p.optInt("id"))
                                    }
                                }.onSuccess { detail = it }
                                    .onFailure { error = it.message ?: "加载处方详情失败" }
                            }
                        },
                        shape = FieldShape,
                    ) {
                        Text("查看详情")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    editing?.let { initial ->
        PrescriptionFormDialog(
            initial = initial,
            doctors = doctors,
            sources = sources,
            onClose = { editing = null },
            onSaved = {
                editing = null
                reload++
            },
            onError = { error = it },
        )
    }

    detail?.let { p ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(p.optString("customerName", "处方详情"), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InfoRowItem(label = "处方号", value = p.optString("prescriptionNo", "-"))
                    InfoRowItem(label = "状态", value = prescriptionStatusLabel(p.optInt("status")))
                    InfoRowItem(label = "电话", value = maskPhone(p.optString("phone")))
                    InfoRowItem(label = "医生", value = p.optJSONObject("doctor")?.optString("name", "-") ?: "-")
                    InfoRowItem(label = "来源", value = p.optJSONObject("source")?.optString("name", "-") ?: "-")
                    val items = p.optJSONArray("items") ?: JSONArray()
                    if (items.length() > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("处方药材明细 (${items.length()}味)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        (0 until items.length()).forEach { idx ->
                            val item = items.getJSONObject(idx)
                            InfoRowItem(
                                label = item.optString("herbName", "药材"),
                                value = "${item.opt("quantity") ?: 0} ${item.optString("unit", "g")}",
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { detail = null }, shape = FieldShape) {
                    Text("关闭")
                }
            },
        )
    }
}

@Composable
private fun PrescriptionFormDialog(
    initial: JSONObject,
    doctors: List<JSONObject>,
    sources: List<JSONObject>,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    val isEdit = initial.has("id")
    var customer by remember(initial) { mutableStateOf(initial.optString("customerName")) }
    var phone by remember(initial) { mutableStateOf(initial.optString("phone")) }
    var remark by remember(initial) { mutableStateOf(initial.optString("remark")) }
    var doctorId by remember(initial) { mutableStateOf(initial.optInt("doctorId").takeIf { it > 0 } ?: doctors.firstOrNull()?.optInt("id", 0) ?: 0) }
    var sourceId by remember(initial) { mutableStateOf(initial.optInt("sourceId").takeIf { it > 0 } ?: sources.firstOrNull()?.optInt("id", 0) ?: 0) }
    var external by remember(initial) { mutableStateOf(initial.optInt("isExternal") == 1) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (isEdit) "编辑处方" else "新建处方", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("顾客姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("联系手机号（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
                Spacer(Modifier.height(10.dp))
                Text("选择医生", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    doctors.take(8).forEach { d ->
                        SegmentedButton(d.optString("name", "医生"), doctorId == d.optInt("id")) {
                            doctorId = d.optInt("id")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("处方来源", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.take(8).forEach { s ->
                        SegmentedButton(s.optString("name", "来源"), sourceId == s.optInt("id")) {
                            sourceId = s.optInt("id")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("外方处方", color = Ink, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = external, onCheckedChange = { external = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("处方备注") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = customer.isNotBlank() && doctorId > 0 && sourceId > 0 && !busy,
                onClick = {
                    busy = true
                    val payload = JSONObject()
                        .put("customerName", customer.trim())
                        .put("phone", phone.trim())
                        .put("doctorId", doctorId)
                        .put("sourceId", sourceId)
                        .put("isExternal", external)
                        .put("remark", remark.trim())

                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                if (isEdit) {
                                    ApiClient.updatePrescription(initial.optInt("id"), payload)
                                } else {
                                    ApiClient.createPrescription(payload)
                                }
                            }
                        }.onSuccess { onSaved() }
                            .onFailure { onError(it.message ?: "保存处方失败") }
                        busy = false
                    }
                },
                shape = FieldShape,
            ) {
                Text(if (busy) "保存中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onClose() }) {
                Text("取消")
            }
        },
    )
}
