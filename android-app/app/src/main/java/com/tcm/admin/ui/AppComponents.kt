package com.tcm.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal fun planStatus(status: Int): String = when (status) { 0 -> "待加工"; 1 -> "加工中"; 2 -> "加工完成"; 3 -> "待领取"; 4 -> "已领取"; 5 -> "已取消"; else -> "未知" }
internal fun transferStatusLabel(status: Int, outboundStatus: Int): String = when {
    status == 3 -> "已取消"
    status == 2 -> "已调平"
    status == 1 -> "部分归还"
    outboundStatus == 0 -> "待出库"
    else -> "借出中"
}

@Composable
internal fun StatusPill(text: String) {
    val color = when { text in listOf("加工完成", "已领取", "已完成", "已调平") -> Success; text in listOf("实货少", "已取消") -> Danger; text in listOf("加工中", "实货多") -> Primary; else -> Warning }
    Surface(color = color.copy(alpha = .12f), shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp) }
}

@Composable
internal fun StoreSelector(stores: List<JSONObject>, selectedId: String, onSelect: (String) -> Unit) {
    if (stores.isEmpty()) Text("暂无可用门店", color = Muted, fontSize = 12.sp)
    stores.forEach { store ->
        val id = store.opt("id")?.toString().orEmpty()
        if (id == selectedId) Button({ onSelect(id) }, Modifier.fillMaxWidth().padding(top = 5.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
        else OutlinedButton({ onSelect(id) }, Modifier.fillMaxWidth().padding(top = 5.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
    }
}
