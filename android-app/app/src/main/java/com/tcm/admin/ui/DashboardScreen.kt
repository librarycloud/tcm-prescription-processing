package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Page Heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "工作台概览",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "实时掌握处方、加工和包裹最新状态",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Store Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = CardShape,
            border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = PrimarySoft,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前工作门店", color = Muted, fontSize = 12.sp)
                    Text("全部门店（管理员模式）", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Processing Stats
        SectionHeader("加工概况", "按日统计待办与完成情况")
        Spacer(Modifier.height(10.dp))
        StatsGrid(
            items = listOf(
                "今日待加工" to stat(stats, "waitingCount"),
                "逾期未开工" to stat(stats, "overdueCount"),
                "加工中" to stat(stats, "processingCount"),
                "今日完成" to stat(stats, "todayFinished"),
                "等待顾客" to stat(stats, "waitingNoticeCount"),
                "明日加工" to stat(stats, "tomorrowWaitingCount"),
            ),
            onClick = { go(Screen.Processing) },
        )

        Spacer(Modifier.height(20.dp))

        // Package Stats
        SectionHeader("包裹概况", "包裹入库与核销状态")
        Spacer(Modifier.height(10.dp))
        StatsGrid(
            items = listOf(
                "待取货" to stat(stats, "pendingCount"),
                "今日新增" to stat(stats, "todayAdded"),
                "今日已取" to stat(stats, "todayPicked"),
                "总包裹" to stat(stats, "totalCount"),
            ),
            onClick = { go(Screen.Packages) },
        )

        Spacer(Modifier.height(20.dp))

        // Business Management Quick Actions
        SectionHeader("业务管理", "快捷管理功能入口")
        Spacer(Modifier.height(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuickAction("处方管理", "查看及录入中药处方", Icons.AutoMirrored.Filled.Assignment, Primary) { go(Screen.Prescriptions) }
            QuickAction("库存查询", "扫码或搜索 E6 药店商品库存", Icons.Default.Inventory, Success) { go(Screen.Inventory) }
            QuickAction("商品盘点", "商品与库存批次实盘盘点", Icons.AutoMirrored.Filled.Assignment, Purple) { go(Screen.Stocktaking) }
            QuickAction("库存差异", "登记与处理实货多/实货少差异", Icons.Default.Tune, Warning) { go(Screen.Differences) }
            QuickAction("门店调拨", "跨门店物资借调与归还跟踪", Icons.Default.LocalShipping, Color(0xFF009688)) { go(Screen.Transfers) }
        }

        Spacer(Modifier.height(16.dp))
    }
}

internal fun stat(stats: JSONObject?, key: String): String = stats?.opt(key)?.toString() ?: "-"

@Composable
internal fun SectionTitle(text: String) {
    SectionHeader(title = text)
}

@Composable
internal fun StatsGrid(
    items: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    val columns = if (items.size % 2 == 0 && items.size <= 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, value) ->
                    val isPositive = value != "0" && value != "-"
                    val isAlert = label.contains("逾期") || label.contains("等待")
                    val valueColor = when {
                        !isPositive -> Ink
                        isAlert -> Danger
                        label.contains("完成") || label.contains("已取") -> Success
                        else -> Primary
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(78.dp)
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = CardShape,
                        border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = value,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = valueColor,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = Muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun QuickAction(
    label: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = Primary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    fontSize = 14.sp,
                )
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
