package com.tcm.admin

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun DashboardScreen(go: (Screen) -> Unit, stats: JSONObject?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("今日概览", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text("实时掌握处方、加工和包裹状态", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(Color.White), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = PrimarySoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Store, null, tint = Primary, modifier = Modifier.size(20.dp)) } }; Spacer(Modifier.width(10.dp)); Column { Text("当前门店", color = Muted, fontSize = 12.sp); Text("全部门店", fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted) } }
        Spacer(Modifier.height(20.dp)); SectionTitle("加工概况")
        StatsGrid(listOf("今日待加工" to stat(stats, "waitingCount"), "逾期未开工" to stat(stats, "overdueCount"), "加工中" to stat(stats, "processingCount"), "今日完成" to stat(stats, "todayFinished"), "等待顾客" to stat(stats, "waitingNoticeCount"), "明日加工" to stat(stats, "tomorrowWaitingCount")))
        Spacer(Modifier.height(20.dp)); SectionTitle("包裹概况")
        StatsGrid(listOf("待取货" to stat(stats, "pendingCount"), "今日新增" to stat(stats, "todayAdded"), "今日已取" to stat(stats, "todayPicked"), "总包裹" to stat(stats, "totalCount")))
        Spacer(Modifier.height(20.dp)); SectionTitle("业务管理")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { QuickAction("处方管理", Icons.AutoMirrored.Filled.Assignment) { go(Screen.Prescriptions) }; QuickAction("库存查询", Icons.Default.Inventory) { go(Screen.Inventory) }; QuickAction("商品盘点", Icons.AutoMirrored.Filled.Assignment) { go(Screen.Stocktaking) }; QuickAction("库存差异", Icons.Default.Tune) { go(Screen.Differences) }; QuickAction("门店调拨", Icons.Default.LocalShipping) { go(Screen.Transfers) } }
    }
}

internal fun stat(stats: JSONObject?, key: String): String = stats?.opt(key)?.toString() ?: "-"

@Composable internal fun SectionTitle(text: String) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink) }
@Composable internal fun StatsGrid(items: List<Pair<String, String>>) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEachIndexed { index, (label, value) -> Card(Modifier.weight(1f).height(86.dp), colors = CardDefaults.cardColors(if (index == 0) PrimarySoft else Color.White), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (index == 0) PrimaryDark else Primary); Text(label, color = Muted, fontSize = 12.sp) } } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } } } }
@Composable internal fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Primary)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Icons.Default.ChevronRight, null) } }
@Composable internal fun SegmentedButton(label: String, selected: Boolean, onClick: () -> Unit) { if (selected) Button(onClick = onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(label) } else OutlinedButton(onClick = onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(label) } }
