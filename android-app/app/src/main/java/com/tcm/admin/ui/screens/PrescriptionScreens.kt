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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
internal fun PrescriptionsScreen(onNavigate: (ScreenTarget) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload, keyword, status, selectedStoreId) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.prescriptions(status, keyword.trim(), selectedStoreId.toIntOrNull())
            }
        }.onSuccess { prescriptions ->
            items = (0 until prescriptions.length()).map { prescriptions.getJSONObject(it) }
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
                onClick = { onNavigate(ScreenTarget.PrescriptionEdit(JSONObject())) },
                modifier = Modifier.height(CompactControlHeight),
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
            SegmentedButton("全部状态", status == null, onClick = { status = null })
            SegmentedButton("进行中", status == 0, onClick = { status = 0 })
            SegmentedButton("已完成", status == 1, onClick = { status = 1 })
            SegmentedButton("已取消", status == 2, onClick = { status = 2 })
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
            val doctor = p.optJSONObject("doctor")?.displayField("name") ?: "-"
            val source = p.optJSONObject("source")?.displayField("name") ?: "-"
            val store = p.optJSONObject("store")?.displayField("name", "") ?: ""
            val createdAt = p.displayField("createdAt").replace("T", " ").take(16)
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
                            text = p.displayField("customerName", "患者"),
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

                InfoRowItem(label = "联系电话", value = maskPhone(p.displayField("phone", "")))
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
                        onClick = { onNavigate(ScreenTarget.PrescriptionEdit(p)) },
                        shape = FieldShape,
                    ) {
                        Text("编辑")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onNavigate(ScreenTarget.PrescriptionDetail(p.optInt("id"))) },
                        shape = FieldShape,
                    ) {
                        Text("查看详情")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun PrescriptionDetailScreen(
    id: Int,
    onBack: () -> Unit,
) {
    var p by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        runCatching {
            withContext(Dispatchers.IO) { ApiClient.prescriptionDetail(id) }
        }.onSuccess { p = it }
            .onFailure { error = it.message ?: "加载处方详情失败" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (p == null && error == null) AppEmptyState("正在加载处方详情...")
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)

        p?.let { detail ->
            val doctor = detail.optJSONObject("doctor")?.displayField("name") ?: "-"
            val source = detail.optJSONObject("source")?.displayField("name") ?: "-"
            val store = detail.optJSONObject("store")?.displayField("name", "") ?: ""
            val createdAt = detail.displayField("createdAt").replace("T", " ").take(16)
            val statusCode = detail.optInt("status")
            val isExternal = detail.optInt("isExternal") == 1
            val items = detail.optJSONArray("items") ?: JSONArray()

            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = detail.displayField("customerName", "患者"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Ink,
                    )
                    StatusPill(text = prescriptionStatusLabel(statusCode))
                }

                Spacer(Modifier.height(12.dp))

                InfoRowItem(label = "处方单号", value = detail.displayField("prescriptionNo"))
                InfoRowItem(label = "联系手机", value = maskPhone(detail.displayField("phone", "")))
                InfoRowItem(label = "主治医生", value = doctor)
                InfoRowItem(label = "处方来源", value = source)
                if (store.isNotBlank()) InfoRowItem(label = "所属门店", value = store)
                InfoRowItem(label = "处方属性", value = if (isExternal) "外方" else "本院方")
                InfoRowItem(label = "录入时间", value = createdAt)

                val remark = detail.optString("remark", "")
                if (remark.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF9FAFB),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "备注说明：$remark",
                            color = RegularText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Herb items card
            AppCard {
                SectionHeader("处方药材明细", "共 ${items.length()} 味药材")
                Spacer(Modifier.height(10.dp))

                if (items.length() == 0) {
                    Text("暂无药材明细记录", color = Muted, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until items.length()).forEach { idx ->
                            val item = items.getJSONObject(idx)
                            Surface(
                                color = Color(0xFFF9FAFB),
                                shape = FieldShape,
                                border = BorderStroke(1.dp, Color(0xFFEAECF0)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${item.displayField("herbName", "药材")}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Ink,
                                    )
                                    Text(
                                        text = "${quantityText(item.opt("quantity"), "0")} ${item.displayField("unit", "g")}",
                                        color = Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text("返回处方列表")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun PrescriptionFormScreen(
    initial: JSONObject,
    onSaved: () -> Unit,
) {
    val isEdit = initial.has("id") && initial.optInt("id") > 0
    var customer by remember(initial) { mutableStateOf(initial.optString("customerName")) }
    var phone by remember(initial) { mutableStateOf(initial.optString("phone")) }
    var remark by remember(initial) { mutableStateOf(initial.optString("remark")) }
    var doctorId by remember(initial) { mutableStateOf(initial.optInt("doctorId", 0)) }
    var sourceId by remember(initial) { mutableStateOf(initial.optInt("sourceId", 0)) }
    var external by remember(initial) { mutableStateOf(initial.optInt("isExternal") == 1) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                Pair(ApiClient.doctors(), ApiClient.prescriptionSources())
            }
        }.onSuccess { (doctorList, sourceList) ->
            doctors = (0 until doctorList.length()).map { doctorList.getJSONObject(it) }
            sources = (0 until sourceList.length()).map { sourceList.getJSONObject(it) }
            if (doctorId == 0 && doctors.isNotEmpty()) doctorId = doctors.first().optInt("id", 0)
            if (sourceId == 0 && sources.isNotEmpty()) sourceId = sources.first().optInt("id", 0)
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
                title = if (isEdit) "编辑处方基本信息" else "新建中药处方",
                subtitle = "填写患者信息与处方来源医生",
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = customer,
                onValueChange = { customer = it },
                label = { Text("顾客姓名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                label = { Text("联系手机号（可选）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )

            Spacer(Modifier.height(14.dp))

            Text("选择主治医生 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                doctors.forEach { d ->
                    val id = d.optInt("id")
                    SegmentedButton(
                        label = d.optString("name", "医生"),
                        selected = doctorId == id,
                        onClick = { doctorId = id },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text("处方来源渠道 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { s ->
                    val id = s.optInt("id")
                    SegmentedButton(
                        label = s.optString("name", "来源"),
                        selected = sourceId == id,
                        onClick = { sourceId = id },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                color = Color(0xFFF9FAFB),
                shape = FieldShape,
                border = BorderStroke(1.dp, Color(0xFFEAECF0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("外方处方标记", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("标记是否由外部医院/诊所开具", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = external, onCheckedChange = { external = it })
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("处方备注说明") },
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

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
                    }.onSuccess {
                        onSaved()
                    }.onFailure {
                        error = it.message ?: "保存处方失败"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(if (busy) "正在保存..." else if (isEdit) "确认修改" else "保存并创建", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
    }
}
