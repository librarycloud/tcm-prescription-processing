package com.tcm.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
                SectionHeader("处方管理", "查看、新建与编辑中药处方")
            }
            Button(
                onClick = { editing = JSONObject() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建处方")
            }
        }

        Spacer(Modifier.height(14.dp))

        SearchBarField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "搜索顾客姓名、手机号或备注",
            onSearch = { reload++ },
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SegmentedButton("全部", status == null) { status = null }
            SegmentedButton("进行中", status == 0) { status = 0 }
            SegmentedButton("已完成", status == 1) { status = 1 }
            SegmentedButton("已取消", status == 2) { status = 2 }
        }

        if (stores.size > 1) {
            Spacer(Modifier.height(10.dp))
            StoreChipsRow(
                stores = stores,
                selectedStoreId = selectedStoreId,
                onSelectStore = { selectedStoreId = it },
            )
        }

        Spacer(Modifier.height(14.dp))

        if (items == null && error == null) AppEmptyState("加载中...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (items != null && items!!.isEmpty()) AppEmptyState("暂无处方")

        items.orEmpty().forEach { item ->
            val doctorName = item.optJSONObject("doctor")?.optString("name", "-") ?: "-"
            val sourceName = item.optJSONObject("source")?.optString("name", "-") ?: "-"
            val plans = item.optJSONArray("plans") ?: JSONArray()
            val statusCode = item.optInt("status")

            AppCard(
                modifier = Modifier.padding(bottom = 10.dp),
                onClick = { detail = item },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.optString("customerName", "顾客"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${maskPhone(item.optString("phone"))} · 医生：$doctorName",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    StatusPill(prescriptionStatusLabel(statusCode))
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFF2F3F5))
                Spacer(Modifier.height(6.dp))

                InfoRowItem("处方来源", sourceName)
                InfoRowItem("加工批次", "${plans.length()} 个批次")
                if (item.optString("remark").isNotBlank()) {
                    InfoRowItem("备注", item.optString("remark"))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    detail?.let { item ->
        val plans = item.optJSONArray("plans") ?: JSONArray()
        val canEdit = item.optInt("status") == 0
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.optString("customerName", "处方详情"), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InfoRowItem("顾客姓名", item.optString("customerName", "-"))
                    InfoRowItem("联系电话", item.optString("phone", "-"))
                    InfoRowItem("医生", item.optJSONObject("doctor")?.optString("name", "-") ?: "-")
                    InfoRowItem("处方来源", item.optJSONObject("source")?.optString("name", "-") ?: "-")
                    InfoRowItem("状态", prescriptionStatusLabel(item.optInt("status")), isBold = true, valueColor = Primary)
                    InfoRowItem("备注", item.optString("remark").ifBlank { "-" })

                    Spacer(Modifier.height(10.dp))
                    Text("加工批次 (${plans.length()})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    for (i in 0 until plans.length()) {
                        val plan = plans.getJSONObject(i)
                        Text(
                            text = "第${plan.optInt("batchNo", i + 1)}批 · ${plan.optJSONObject("processType")?.optString("name", "加工") ?: "加工"} · ${planStatus(plan.optInt("status"))}",
                            fontSize = 12.sp,
                            color = RegularText,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canEdit) {
                        Button(onClick = { editing = item; detail = null }, shape = RoundedCornerShape(6.dp)) {
                            Text("编辑")
                        }
                    }
                    if (item.optInt("status") == 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            ApiClient.updatePrescription(item.optInt("id"), JSONObject().put("status", 2))
                                        }
                                    }.onSuccess { detail = null; reload++ }
                                        .onFailure { error = it.message ?: "取消处方失败" }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                        ) {
                            Text("取消处方")
                        }
                    }
                    OutlinedButton(onClick = { detail = null }, shape = RoundedCornerShape(6.dp)) {
                        Text("关闭")
                    }
                }
            },
        )
    }

    editing?.let { initial ->
        PrescriptionFormDialog(
            initial = initial,
            doctors = doctors,
            sources = sources,
            onClose = { editing = null },
            onSaved = { editing = null; reload++ },
            onError = { error = it },
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
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("联系手机号（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
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
                    shape = RoundedCornerShape(8.dp),
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
                shape = RoundedCornerShape(8.dp),
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
