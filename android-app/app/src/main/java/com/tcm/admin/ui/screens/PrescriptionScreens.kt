package com.tcm.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
            if (!readOnly) {
                Button(
                    onClick = { onNavigate(ScreenTarget.PrescriptionEdit()) },
                    shape = FieldShape,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建处方", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchBarField(keyword, { keyword = it }, "搜索患者姓名、手机号、处方号或医生", onSearch = { page = 1; reload++ })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedButton("全部状态", status == null, onClick = { status = null; page = 1 })
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
        if (items == null && error == null) AppEmptyState("正在加载处方列表...")
        if (items != null && items!!.isEmpty()) AppEmptyState("暂无匹配处方", onRetry = { reload++ })
        items.orEmpty().forEach { item ->
            val plans = item.optJSONArray("plans") ?: JSONArray()
            val remainingDose = (item.optInt("totalDose", 0) - item.optInt("takenDose", 0)).coerceAtLeast(0)
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
                InfoRowItem("剂数进度", "${quantityText(item.opt("takenDose"), "0")} / ${quantityText(item.opt("totalDose"), "0")} 剂（余 $remainingDose 剂）")
                InfoRowItem("加工批次", "${plans.length()} 批")
                if (isSuperAdmin(user)) {
                    item.optJSONObject("store")?.displayField("name", "")?.takeIf { it.isNotBlank() }?.let { InfoRowItem("所属门店", it) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onNavigate(ScreenTarget.PrescriptionDetail(item.optInt("id"))) },
                        shape = FieldShape,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text("详情", fontSize = 12.sp) }

                    if (!readOnly && item.optInt("status") == 0) {
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject().put("prescriptionId", item.optInt("id")).put("prescription", item))) },
                            shape = FieldShape,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) { Text("新增加工", fontSize = 12.sp) }
                    }

                    if (!readOnly && item.optInt("status") != 1) {
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { onNavigate(ScreenTarget.PrescriptionEdit(item)) },
                            shape = FieldShape,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("编辑", fontSize = 12.sp) }
                    }
                    if (!readOnly && plans.length() == 0) {
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { deleteTarget = item },
                            shape = FieldShape,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("删除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (items != null && pages > 1) {
            AppPagination(page = page, pages = pages, onPrev = { page-- }, onNext = { page++ })
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
            val totalDose = p.optInt("totalDose", 0)
            val takenDose = p.optInt("takenDose", 0)
            val remainingDose = (totalDose - takenDose).coerceAtLeast(0)

            // Header Card
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.displayField("customerName", "患者"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                        Text(p.displayField("prescriptionNo"), color = Muted, fontSize = 12.sp)
                    }
                    StatusPill(prescriptionStatusLabel(p.optInt("status")))
                }
                if (!readOnly && p.optInt("status") != 1) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onNavigate(ScreenTarget.PrescriptionEdit(p)) },
                        shape = FieldShape,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("编辑处方", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))
                InfoRowItem("联系电话", maskPhone(p.displayField("phone", "")))
                InfoRowItem("所属门店", p.optJSONObject("store")?.displayField("name") ?: "-")
                InfoRowItem("主治医生", p.optJSONObject("doctor")?.displayField("name") ?: "-")
                InfoRowItem("处方来源", p.optJSONObject("source")?.displayField("name") ?: "-")
                InfoRowItem("处方类型", if (p.optBoolean("isExternal")) "外方" else "本方")
                InfoRowItem("剂数进度", "$takenDose / $totalDose 剂，剩余 $remainingDose 剂", isBold = true, valueColor = PrimaryDark)
                InfoRowItem("录入时间", p.displayField("createdAt", "").take(16).replace("T", " "))
                p.optJSONObject("creator")?.displayField("nickname")?.takeIf { it.isNotBlank() }?.let { InfoRowItem("录入人", it) }
                p.displayField("remark", "").takeIf { it.isNotBlank() }?.let { InfoRowItem("备注", it) }
            }

            // Prescription Attachment Card
            Spacer(Modifier.height(14.dp))
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("处方原件", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    if (!readOnly) {
                        Button(
                            enabled = !busy,
                            onClick = { attachmentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                            shape = FieldShape,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.UploadFile, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (attachment != null) "重新上传" else "上传原件", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (attachment != null) {
                    Surface(color = Color(0xFFF8FAFC), shape = FieldShape, border = BorderStroke(1.dp, CardBorderColor), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(attachment.displayField("originalName", "处方原件"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
                                Text(attachmentSizeText(attachment.optLong("fileSize")), color = Muted, fontSize = 11.sp)
                            }
                            if (!readOnly) {
                                TextButton(enabled = !busy, onClick = { deleteAttachment = true }) {
                                    Text("删除原件", color = Danger, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Text("暂未上传处方原件", color = Muted, fontSize = 12.sp)
                }
            }

            // Processing Plans (加工批次)
            Spacer(Modifier.height(14.dp))
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("加工批次", "共 ${plans.length()} 批", modifier = Modifier.weight(1f))
                    if (!readOnly && p.optInt("status") == 0) {
                        Button(
                            onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject().put("prescriptionId", id).put("prescription", p))) },
                            shape = FieldShape,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) { Text("新增批次", fontSize = 12.sp) }
                    }
                }
                if (plans.length() == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("暂无加工批次", color = Muted, fontSize = 13.sp)
                }
                (0 until plans.length()).forEach { index ->
                    val plan = plans.getJSONObject(index)
                    val processType = plan.optJSONObject("processType")
                    val isDecoction = processType?.displayField("name", "")?.contains("煎") == true || plan.displayField("processTypeName", "").contains("煎")
                    val pkg = plan.optJSONObject("package")

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = FieldShape,
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("第 ${plan.optInt("batchNo", index + 1)} 批 · ${processType?.displayField("name") ?: "代煎"}", fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
                                StatusPill(planStatus(plan.optInt("status")))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("剂数：${quantityText(plan.opt("totalDose"), "0")} 剂  ·  安排：${plan.displayField("processDate", "等待安排").take(10)}", color = RegularText, fontSize = 12.sp)
                            if (isDecoction) {
                                Text("规格：${plan.optInt("bagCount", 0)} 袋 · ${plan.optInt("volumeMl", 0)} ml", color = Muted, fontSize = 12.sp)
                            }
                            pkg?.let {
                                Text("取货码：${it.displayField("pickupCode")}", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                // 工序详情 / 流程操作
                                OutlinedButton(
                                    onClick = { onNavigate(ScreenTarget.WorkflowOperation(plan, "", "open")) },
                                    shape = FieldShape,
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("工序详情", fontSize = 11.5.sp)
                                }

                                pkg?.let { pItem ->
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        packageItem(ApiClient.packageDetail(pItem.optInt("id")))
                                                    }
                                                }.onSuccess { onNavigate(ScreenTarget.PackageDetail(it)) }
                                                    .onFailure { error = it.message ?: "加载包裹失败" }
                                            }
                                        },
                                        shape = FieldShape,
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) { Text("包裹", fontSize = 11.5.sp) }
                                }

                                if (!readOnly && plan.optInt("status") in 0..1) {
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { onNavigate(ScreenTarget.ProcessingPlanForm(JSONObject(plan.toString()).put("prescription", p))) },
                                        shape = FieldShape,
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) { Text("编辑", fontSize = 11.5.sp) }
                                }

                                if (!readOnly && plan.optInt("status") == 0) {
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { deletePlan = plan },
                                        shape = FieldShape,
                                        modifier = Modifier.height(30.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) { Text("删除", fontSize = 11.5.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp), shape = FieldShape) { Text("返回处方列表") }
        Spacer(Modifier.height(16.dp))
    }
    if (deleteAttachment) AlertDialog(
        onDismissRequest = { deleteAttachment = false },
        title = { Text("删除处方原件") },
        text = { Text("确认删除该处方原件？") },
        confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { ApiClient.deletePrescriptionAttachment(id) } }.onSuccess { deleteAttachment = false; reload++ }.onFailure { error = it.message ?: "删除失败" }; busy = false } }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteAttachment = false }) { Text("取消") } },
    )
    deletePlan?.let { plan -> AlertDialog(
        onDismissRequest = { deletePlan = null },
        title = { Text("删除加工批次") },
        text = { Text("确认删除该加工批次？历史操作记录仍会保留。") },
        confirmButton = { Button(onClick = { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { ApiClient.deleteProcessingPlan(plan.optInt("id")) } }.onSuccess { deletePlan = null; reload++ }.onFailure { error = it.message ?: "删除失败" }; busy = false } }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deletePlan = null }) { Text("取消") } },
    ) }
}

@Composable
internal fun PrescriptionFormScreen(initial: JSONObject, user: JSONObject?, onSaved: () -> Unit) {
    val isEdit = initial.optInt("id") > 0
    var customer by remember(initial) { mutableStateOf(initial.displayField("customerName", "")) }
    var phone by remember(initial) { mutableStateOf(initial.displayField("phone", "")) }
    var remark by remember(initial) { mutableStateOf(initial.displayField("remark", "")) }
    var totalPrice by remember(initial) { mutableStateOf(initial.displayField("totalPrice", "")) }
    var totalDose by remember(initial) { mutableStateOf(initial.displayField("totalDose", "1")) }
    var doctorId by remember(initial) { mutableStateOf<Int?>(initial.optJSONObject("doctor")?.optInt("id") ?: initial.optInt("doctorId").takeIf { it > 0 }) }
    var sourceId by remember(initial) { mutableStateOf<Int?>(initial.optJSONObject("source")?.optInt("id") ?: initial.optInt("sourceId").takeIf { it > 0 }) }
    var storeId by remember(initial) { mutableStateOf<Int?>(initial.optJSONObject("store")?.optInt("id") ?: initial.optInt("storeId").takeIf { it > 0 }) }
    var external by remember(initial) { mutableStateOf(initial.optBoolean("isExternal")) }
    var externalHospital by remember(initial) { mutableStateOf(initial.displayField("externalHospital", "")) }
    var externalDoctor by remember(initial) { mutableStateOf(initial.displayField("externalDoctor", "")) }
    var externalRemark by remember(initial) { mutableStateOf(initial.displayField("externalRemark", "")) }
    var status by remember(initial) { mutableStateOf(initial.optInt("status", 0)) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Triple(ApiClient.doctors(), ApiClient.prescriptionSources(), if (isSuperAdmin(user)) ApiClient.availableStores() else JSONArray()) } }
            .onSuccess { (docList, srcList, strList) ->
                doctors = (0 until docList.length()).map { docList.getJSONObject(it) }
                sources = (0 until srcList.length()).map { srcList.getJSONObject(it) }
                stores = (0 until strList.length()).map { strList.getJSONObject(it) }
                if (doctorId == null && doctors.isNotEmpty()) doctorId = doctors.first().optInt("id")
                if (sourceId == null && sources.isNotEmpty()) sourceId = sources.first().optInt("id")
                if (storeId == null && stores.isNotEmpty()) storeId = stores.first().optInt("id")
            }.onFailure { error = it.message ?: "加载处方基础数据失败" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(if (isEdit) "编辑处方" else "新建处方")
        Spacer(Modifier.height(14.dp))
        error?.let { Text(it, color = Danger, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
        AppCard {
            OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("患者姓名 *") }, singleLine = true, shape = FieldShape)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("联系电话") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = FieldShape)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(totalDose, { totalDose = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("处方剂数 *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = FieldShape)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(totalPrice, { totalPrice = it }, Modifier.fillMaxWidth(), label = { Text("处方金额（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = FieldShape)
            if (isSuperAdmin(user) && stores.isNotEmpty()) {
                Spacer(Modifier.height(14.dp)); Text("所属门店", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stores.forEach { store ->
                        val id = store.optInt("id")
                        SegmentedButton(store.displayField("name", "门店"), storeId == id, onClick = { storeId = id })
                    }
                }
            }
            if (doctors.isNotEmpty()) {
                Spacer(Modifier.height(14.dp)); Text("主治医生", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    doctors.forEach { doc ->
                        val id = doc.optInt("id")
                        SegmentedButton(doc.displayField("name", "医生"), doctorId == id, onClick = { doctorId = id })
                    }
                }
            }
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(14.dp)); Text("处方来源", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { src ->
                        val id = src.optInt("id")
                        SegmentedButton(src.displayField("name", "来源"), sourceId == id, onClick = { sourceId = id })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Color(0xFFF9FAFB), shape = FieldShape, border = BorderStroke(1.dp, CardBorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("外方处方", color = Ink, fontWeight = FontWeight.Medium)
                        Text("由外部医院或诊所开具", color = Muted, fontSize = 12.sp)
                    }
                    Switch(external, { external = it })
                }
            }
            if (external) {
                Spacer(Modifier.height(10.dp)); OutlinedTextField(externalHospital, { externalHospital = it }, Modifier.fillMaxWidth(), label = { Text("外方医院") }, singleLine = true, shape = FieldShape)
                Spacer(Modifier.height(10.dp)); OutlinedTextField(externalDoctor, { externalDoctor = it }, Modifier.fillMaxWidth(), label = { Text("外方医生") }, singleLine = true, shape = FieldShape)
                Spacer(Modifier.height(10.dp)); OutlinedTextField(externalRemark, { externalRemark = it }, Modifier.fillMaxWidth(), label = { Text("外方备注") }, shape = FieldShape)
            }
            if (isEdit) {
                Spacer(Modifier.height(14.dp)); Text("处方状态", color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedButton("进行中", status == 0, onClick = { status = 0 })
                    SegmentedButton("已完成", status == 1, onClick = { status = 1 })
                    SegmentedButton("已取消", status == 2, onClick = { status = 2 })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(remark, { remark = it }, Modifier.fillMaxWidth(), label = { Text("处方备注") }, shape = FieldShape)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy && customer.isNotBlank() && totalDose.toIntOrNull()?.let { it > 0 } == true,
            onClick = {
                busy = true
                val payload = JSONObject()
                    .put("customerName", customer.trim())
                    .put("phone", phone.trim())
                    .put("totalDose", totalDose.toInt())
                    .put("remark", remark.trim())
                    .put("isExternal", external)
                totalPrice.toDoubleOrNull()?.let { payload.put("totalPrice", it) }
                doctorId?.let { payload.put("doctorId", it) }
                sourceId?.let { payload.put("sourceId", it) }
                storeId?.let { payload.put("storeId", it) }
                if (external) {
                    payload.put("externalHospital", externalHospital.trim())
                        .put("externalDoctor", externalDoctor.trim())
                        .put("externalRemark", externalRemark.trim())
                }
                if (isEdit) payload.put("status", status)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (isEdit) ApiClient.updatePrescription(initial.optInt("id"), payload)
                            else ApiClient.createPrescription(payload)
                        }
                    }.onSuccess { onSaved() }.onFailure { error = it.message ?: "保存处方失败" }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(if (busy) "保存中..." else "确认保存处方", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun uploadAttachment(context: Context, prescriptionId: Int, uri: Uri) {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    val name = cursor?.use {
        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
    } ?: "prescription_${System.currentTimeMillis()}"
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
    ApiClient.uploadPrescriptionAttachment(prescriptionId, name, mimeType, bytes)
}

private fun attachmentSizeText(size: Long): String = when {
    size <= 0 -> "-"
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", size.toDouble() / (1024 * 1024))
}
