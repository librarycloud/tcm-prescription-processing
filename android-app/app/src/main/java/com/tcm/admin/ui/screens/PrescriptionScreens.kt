package com.tcm.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
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

private fun isStoreStaff(user: JSONObject?): Boolean = user?.optInt("role", -1) == 3
private fun isSuperAdmin(user: JSONObject?): Boolean = user?.optInt("role", -1) == 0
private fun planStatusLabel(value: Int): String = when (value) {
    0 -> "待加工"
    1 -> "加工中"
    2 -> "加工完成"
    3 -> "待领取"
    4 -> "已领取"
    5 -> "已取消"
    else -> "未知"
}

@Composable
internal fun PrescriptionsScreen(user: JSONObject?, onNavigate: (ScreenTarget) -> Unit) {
    val readOnly = isStoreStaff(user)
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var doctorId by remember { mutableStateOf<Int?>(null) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }
    var deleteTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.doctors(), ApiClient.availableStores()) } }
            .onSuccess { (doctorValues, storeValues) ->
                doctors = (0 until doctorValues.length()).map { doctorValues.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
                if (!isSuperAdmin(user) && stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
    }
    LaunchedEffect(reload, keyword, status, doctorId, selectedStoreId, page) {
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.prescriptionsPaged(status, keyword.trim(), selectedStoreId.toIntOrNull(), doctorId, page)
            }
        }.onSuccess { data ->
            val list = data.optJSONArray("list") ?: JSONArray()
            items = (0 until list.length()).map { list.getJSONObject(it) }
            pages = data.optJSONObject("pagination")?.optInt("pages", 1)?.coerceAtLeast(1) ?: 1
        }.onFailure { error = it.message ?: "加载处方失败" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionHeader("处方管理", "患者处方、加工批次与原件") }
            if (!readOnly) Button(
                onClick = { onNavigate(ScreenTarget.PrescriptionEdit()) },
                modifier = Modifier.height(CompactControlHeight), shape = FieldShape,
            ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("新建") }
        }
        Spacer(Modifier.height(12.dp))
        SearchBarField(keyword, { keyword = it }, "搜索患者、手机、处方号或医生", onSearch = { page = 1; reload++ })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedButton("全部", status == null, onClick = { status = null; page = 1 })
            SegmentedButton("进行中", status == 0, onClick = { status = 0; page = 1 })
            SegmentedButton("已完成", status == 1, onClick = { status = 1; page = 1 })
            SegmentedButton("已取消", status == 2, onClick = { status = 2; page = 1 })
        }
        if (doctors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedButton("全部医生", doctorId == null, onClick = { doctorId = null; page = 1 })
                doctors.forEach { doctor ->
                    val doctorValue = doctor.optInt("id")
                    SegmentedButton(doctor.displayField("name", "医生"), doctorId == doctorValue, onClick = { doctorId = doctorValue; page = 1 })
                }
            }
        }
        if (isSuperAdmin(user) && stores.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            StoreChipsRow(stores, selectedStoreId, onSelectStore = { selectedStoreId = it; page = 1 })
        }
        Spacer(Modifier.height(14.dp))
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        if (items == null && error == null) AppEmptyState("加载处方中...")
        if (items != null && items!!.isEmpty()) AppEmptyState("暂无匹配处方")
        items.orEmpty().forEach { item ->
            val plans = item.optJSONArray("plans") ?: JSONArray()
            AppCard(modifier = Modifier.padding(bottom = 12.dp), onClick = { onNavigate(ScreenTarget.PrescriptionDetail(item.optInt("id"))) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.displayField("customerName", "患者"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text(item.displayField("prescriptionNo"), color = Muted, fontSize = 12.sp)
                    }
                    StatusPill(prescriptionStatusLabel(item.optInt("status")))
                }
                Spacer(Modifier.height(8.dp))
                InfoRowItem("联系电话", maskPhone(item.displayField("phone", "")))
                InfoRowItem("主治医生", item.optJSONObject("doctor")?.displayField("name") ?: "-")
                InfoRowItem("加工进度", "${quantityText(item.opt("takenDose"), "0")} / ${quantityText(item.opt("totalDose"), "0")} 剂")
                InfoRowItem("加工批次", "${plans.length()} 批")
                item.optJSONObject("store")?.displayField("name", "")?.takeIf { it.isNotBlank() }?.let { InfoRowItem("所属门店", it) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!readOnly && item.optInt("status") != 1) {
                        OutlinedButton(onClick = { onNavigate(ScreenTarget.PrescriptionEdit(item)) }, shape = FieldShape) { Text("编辑") }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (!readOnly && plans.length() == 0) OutlinedButton(
                        onClick = { deleteTarget = item }, shape = FieldShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    ) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("删除") }
                }
            }
        }
        if (items != null && pages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { page-- }, enabled = page > 1, shape = FieldShape) { Text("上一页") }
            Text("  $page / $pages  ", color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = { page++ }, enabled = page < pages, shape = FieldShape) { Text("下一页") }
        }
        Spacer(Modifier.height(20.dp))
    }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { if (!deleting) deleteTarget = null }, title = { Text("删除处方") },
        text = { Text("确认删除处方 ${target.displayField("prescriptionNo")}？删除后无法恢复。") },
        confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; scope.launch {
            runCatching { withContext(Dispatchers.IO) { ApiClient.deletePrescription(target.optInt("id")) } }
                .onSuccess { deleteTarget = null; page = 1; reload++ }
                .onFailure { error = it.message ?: "删除处方失败" }
            deleting = false
        } }) { Text(if (deleting) "删除中..." else "确认删除") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { deleteTarget = null }) { Text("取消") } },
    ) }
}

@Composable
internal fun PrescriptionDetailScreen(id: Int, user: JSONObject?, onNavigate: (ScreenTarget) -> Unit, onBack: () -> Unit) {
    val readOnly = isStoreStaff(user)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var deletePlan by remember { mutableStateOf<JSONObject?>(null) }
    var deleteAttachment by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { uploadAttachment(context, id, uri) } }
                .onSuccess { reload++ }.onFailure { error = it.message ?: "上传处方原件失败" }
            busy = false
        }
    }
    LaunchedEffect(id, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.prescriptionDetail(id) } }
            .onSuccess { detail = it }.onFailure { error = it.message ?: "加载处方详情失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (detail == null && error == null) AppEmptyState("正在加载处方详情...")
        error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        detail?.let { p ->
            val plans = p.optJSONArray("plans") ?: JSONArray()
            val attachment = p.optJSONObject("attachment")
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p.displayField("prescriptionNo"), color = Muted, fontSize = 12.sp); Text(p.displayField("customerName", "患者"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink) }
                    StatusPill(prescriptionStatusLabel(p.optInt("status")))
                }
                if (!readOnly && p.optInt("status") != 1) { Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { onNavigate(ScreenTarget.PrescriptionEdit(p)) }, shape = FieldShape) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("编辑处方") } }
                Spacer(Modifier.height(10.dp))
                InfoRowItem("联系电话", maskPhone(p.displayField("phone", "")))
                InfoRowItem("所属门店", p.optJSONObject("store")?.displayField("name") ?: "-")
                InfoRowItem("主治医生", p.optJSONObject("doctor")?.displayField("name") ?: "-")
                InfoRowItem("处方来源", p.optJSONObject("source")?.displayField("name") ?: "-")
                InfoRowItem("处方类型", if (p.optInt("isExternal") == 1) "外方" else "本方")
                InfoRowItem("剂数进度", "${quantityText(p.opt("takenDose"), "0")} / ${quantityText(p.opt("totalDose"), "0")} 剂，剩余 ${quantityText(p.opt("remainingDose"), "0")} 剂")
                InfoRowItem("总价", priceText(p.opt("totalPrice")))
                InfoRowItem("录入人", p.optJSONObject("creator")?.displayField("nickname", "")?.ifBlank { p.optJSONObject("creator")?.displayField("phone") ?: "-" } ?: "-")
                InfoRowItem("录入时间", p.displayField("createdAt").replace("T", " ").take(16))
                if (p.optInt("isExternal") == 1) { InfoRowItem("外方医院", p.displayField("externalHospital")); InfoRowItem("外方医生", p.displayField("externalDoctor")); InfoRowItem("外方备注", p.displayField("externalRemark")) }
                InfoRowItem("备注", p.displayField("remark"))
            }
            Spacer(Modifier.height(14.dp))
            AppCard {
                val herbItems = p.optJSONArray("items") ?: JSONArray()
                SectionHeader("处方药材明细", "共 ${herbItems.length()} 味药材")
                Spacer(Modifier.height(8.dp))
                if (herbItems.length() == 0) {
                    Text("暂无药材明细记录", color = Muted, fontSize = 13.sp)
                } else {
                    (0 until herbItems.length()).forEach { index ->
                        val item = herbItems.getJSONObject(index)
                        Surface(
                            color = Color(0xFFF9FAFB),
                            shape = FieldShape,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. ${item.displayField("herbName", "药材")}", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${quantityText(item.opt("quantity"), "0")} ${item.displayField("unit", "g")}", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("处方原件", if (attachment == null) "暂无处方原件" else attachment.displayField("originalName"), modifier = Modifier.weight(1f))
                    if (!readOnly) OutlinedButton(enabled = !busy, onClick = { attachmentLauncher.launch(arrayOf("image/*", "application/pdf")) }, shape = FieldShape) { Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text(if (attachment == null) "上传" else "替换") }
                }
                attachment?.let { value -> Spacer(Modifier.height(8.dp)); InfoRowItem("文件大小", attachmentSizeText(value.optLong("fileSize"))); if (!readOnly) TextButton(enabled = !busy, onClick = { deleteAttachment = true }) { Text("删除原件", color = Danger) } }
            }
            Spacer(Modifier.height(14.dp))
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("加工批次", "共 ${plans.length()} 批", modifier = Modifier.weight(1f))
                    if (!readOnly && p.optInt("status") == 0) Button(onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject().put("prescriptionId", id).put("prescription", p))) }, shape = FieldShape) { Text("新增批次") }
                }
                if (plans.length() == 0) Text("暂无加工批次", color = Muted, fontSize = 13.sp)
                (0 until plans.length()).forEach { index ->
                    val plan = plans.getJSONObject(index)
                    Spacer(Modifier.height(10.dp))
                    Surface(color = Color(0xFFF9FAFB), shape = FieldShape, border = BorderStroke(1.dp, CardBorderColor), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("第 ${plan.optInt("batchNo", index + 1)} 批 · ${plan.optJSONObject("processType")?.displayField("name") ?: "加工"}", fontWeight = FontWeight.SemiBold, color = Ink); StatusPill(planStatusLabel(plan.optInt("status"))) }
                            Spacer(Modifier.height(5.dp)); Text("剂数：${quantityText(plan.opt("totalDose"), "0")} 剂  ·  安排：${plan.displayField("processDate", "等待顾客通知").take(10)}", color = RegularText, fontSize = 12.sp)
                            plan.optJSONObject("package")?.let { pkg -> Text("取货码：${pkg.displayField("pickupCode")}", color = Primary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                plan.optJSONObject("package")?.let { pkg -> TextButton(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { packageItem(ApiClient.packageDetail(pkg.optInt("id"))) } }.onSuccess { onNavigate(ScreenTarget.PackageDetail(it)) }.onFailure { error = it.message ?: "加载包裹失败" } } }) { Text("包裹详情") } }
                                if (!readOnly && plan.optInt("status") in 0..1) TextButton(onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject(plan.toString()).put("prescription", p))) }) { Text("编辑") }
                                if (!readOnly && plan.optInt("status") == 0) TextButton(onClick = { deletePlan = plan }) { Text("删除", color = Danger) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(46.dp), shape = FieldShape) { Text("返回处方列表") }; Spacer(Modifier.height(16.dp))
    }
    if (deleteAttachment) AlertDialog(onDismissRequest = { deleteAttachment = false }, title = { Text("删除处方原件") }, text = { Text("确认删除该处方原件？") }, confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { ApiClient.deletePrescriptionAttachment(id) } }.onSuccess { deleteAttachment = false; reload++ }.onFailure { error = it.message ?: "删除失败" }; busy = false } }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { deleteAttachment = false }) { Text("取消") } })
    deletePlan?.let { plan -> AlertDialog(onDismissRequest = { deletePlan = null }, title = { Text("删除加工批次") }, text = { Text("确认删除该加工批次？历史操作记录仍会保留。") }, confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { ApiClient.deleteProcessingPlan(plan.optInt("id")) } }.onSuccess { deletePlan = null; reload++ }.onFailure { error = it.message ?: "删除失败" }; busy = false } }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { deletePlan = null }) { Text("取消") } }) }
}

@Composable
internal fun PrescriptionFormScreen(initial: JSONObject, user: JSONObject?, onSaved: () -> Unit) {
    val isEdit = initial.optInt("id") > 0
    var customer by remember(initial) { mutableStateOf(initial.displayField("customerName", "")) }
    var phone by remember(initial) { mutableStateOf(initial.displayField("phone", "")) }
    var remark by remember(initial) { mutableStateOf(initial.displayField("remark", "")) }
    var totalPrice by remember(initial) { mutableStateOf(initial.displayField("totalPrice", "")) }
    var externalHospital by remember(initial) { mutableStateOf(initial.displayField("externalHospital", "")) }
    var externalDoctor by remember(initial) { mutableStateOf(initial.displayField("externalDoctor", "")) }
    var externalRemark by remember(initial) { mutableStateOf(initial.displayField("externalRemark", "")) }
    var doctorId by remember(initial) { mutableStateOf(initial.optInt("doctorId")) }
    var sourceId by remember(initial) { mutableStateOf(initial.optInt("sourceId")) }
    var storeId by remember(initial) { mutableStateOf(initial.optInt("storeId")) }
    var external by remember(initial) { mutableStateOf(initial.optInt("isExternal") == 1) }
    var status by remember(initial) { mutableStateOf(if (initial.optInt("status") == 2) 2 else 0) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Triple(ApiClient.doctors(), ApiClient.prescriptionSources(), if (isSuperAdmin(user)) ApiClient.availableStores() else JSONArray()) } }
            .onSuccess { (doctorValues, sourceValues, storeValues) ->
                doctors = (0 until doctorValues.length()).map { doctorValues.getJSONObject(it) }
                sources = (0 until sourceValues.length()).map { sourceValues.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
                if (doctorId == 0) doctorId = doctors.firstOrNull()?.optInt("id") ?: 0
                if (sourceId == 0) sourceId = sources.firstOrNull()?.optInt("id") ?: 0
            }.onFailure { error = it.message ?: "加载表单选项失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AppCard {
            SectionHeader(if (isEdit) "编辑处方" else "新建中药处方", "填写患者、来源与外方资料")
            if (!isEdit && isSuperAdmin(user)) { Spacer(Modifier.height(14.dp)); Text("所属门店 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); SelectChips(stores, storeId) { storeId = it } }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("顾客姓名 *") }, singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(10.dp)); OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(), label = { Text("联系手机号（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(10.dp)); OutlinedTextField(totalPrice, { totalPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, Modifier.fillMaxWidth(), label = { Text("总价（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(14.dp)); Text("主治医生 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); SelectChips(doctors, doctorId) { doctorId = it }
            Spacer(Modifier.height(14.dp)); Text("处方来源 *", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); SelectChips(sources, sourceId) { sourceId = it }
            Spacer(Modifier.height(14.dp)); Surface(color = Color(0xFFF9FAFB), shape = FieldShape, border = BorderStroke(1.dp, CardBorderColor), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("外方处方", color = Ink, fontWeight = FontWeight.Medium); Text("由外部医院或诊所开具", color = Muted, fontSize = 12.sp) }; Switch(external, { external = it }) } }
            if (external) { Spacer(Modifier.height(10.dp)); OutlinedTextField(externalHospital, { externalHospital = it }, Modifier.fillMaxWidth(), label = { Text("外方医院") }, singleLine = true, shape = FieldShape); Spacer(Modifier.height(10.dp)); OutlinedTextField(externalDoctor, { externalDoctor = it }, Modifier.fillMaxWidth(), label = { Text("外方医生") }, singleLine = true, shape = FieldShape); Spacer(Modifier.height(10.dp)); OutlinedTextField(externalRemark, { externalRemark = it }, Modifier.fillMaxWidth(), label = { Text("外方备注") }, shape = FieldShape) }
            if (isEdit) { Spacer(Modifier.height(14.dp)); Text("处方状态", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("进行中", status == 0, onClick = { status = 0 }); SegmentedButton("已取消", status == 2, onClick = { status = 2 }) } }
            Spacer(Modifier.height(10.dp)); OutlinedTextField(remark, { remark = it }, Modifier.fillMaxWidth(), label = { Text("处方备注") }, shape = FieldShape)
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Danger, fontSize = 13.sp) }
        Spacer(Modifier.height(24.dp))
        Button(enabled = !busy, onClick = {
            val phoneValid = phone.isBlank() || phone.matches(Regex("1[3-9]\\d{9}"))
            val priceValid = totalPrice.isBlank() || totalPrice.matches(Regex("\\d{1,12}(\\.\\d{1,2})?"))
            error = when { customer.isBlank() -> "请输入顾客姓名"; !phoneValid -> "请输入正确手机号"; doctorId <= 0 -> "请选择主治医生"; sourceId <= 0 -> "请选择处方来源"; !isEdit && isSuperAdmin(user) && storeId <= 0 -> "请选择所属门店"; !priceValid -> "总价格式不正确"; else -> null }
            if (error == null) {
                busy = true
                val payload = JSONObject().put("customerName", customer.trim()).put("phone", phone.trim()).put("doctorId", doctorId).put("sourceId", sourceId).put("isExternal", external).put("externalHospital", externalHospital.trim()).put("externalDoctor", externalDoctor.trim()).put("externalRemark", externalRemark.trim()).put("remark", remark.trim()).put("totalPrice", totalPrice.trim()).also { if (isEdit) it.put("status", status) else if (isSuperAdmin(user)) it.put("storeId", storeId) }
                scope.launch { runCatching { withContext(Dispatchers.IO) { if (isEdit) ApiClient.updatePrescription(initial.optInt("id"), payload) else ApiClient.createPrescription(payload) } }.onSuccess { onSaved() }.onFailure { error = it.message ?: "保存处方失败" }; busy = false }
            }
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = FieldShape) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (isEdit) "确认修改" else "保存并创建", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SelectChips(values: List<JSONObject>, selectedId: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { value -> val itemId = value.optInt("id"); SegmentedButton(value.displayField("name", "未命名"), selectedId == itemId, onClick = { onSelect(itemId) }) } }
}

private fun attachmentSizeText(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0) }

private fun uploadAttachment(context: Context, id: Int, uri: Uri) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("无法读取选择的文件")
    if (bytes.size > 5 * 1024 * 1024) throw IllegalStateException("处方文件不能超过 5MB")
    ApiClient.uploadPrescriptionAttachment(id, "prescription_attachment", context.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
}
