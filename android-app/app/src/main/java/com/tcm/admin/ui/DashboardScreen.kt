package com.tcm.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
internal fun DashboardScreen(
    onNavigate: (ScreenTarget) -> Unit,
    stats: JSONObject?,
    user: JSONObject? = null,
    stores: List<JSONObject> = emptyList(),
    selectedStoreId: String = "",
    onSelectStore: (String) -> Unit = {},
) {
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

        // Quick Actions
        SectionHeader("快捷功能", "快速直达管理模块")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("处方管理", "查看、新建与管理处方", Icons.AutoMirrored.Filled.Assignment, Primary) { onNavigate(ScreenTarget.Prescriptions) }
            QuickAction("E6商品库存", "查询商品批次、规格、条码与效期", Icons.Default.Inventory, Color(0xFF0F766E)) { onNavigate(ScreenTarget.Inventory) }
            QuickAction("商品盘点", "商品盘点计划与差异录入", Icons.AutoMirrored.Filled.CompareArrows, Color(0xFF722ED1)) { onNavigate(ScreenTarget.Stocktaking) }
            QuickAction("库存差异", "登记与处理实货多/实货少差异", Icons.Default.Tune, Warning) { onNavigate(ScreenTarget.Differences) }
            QuickAction("门店调拨", "跨门店物资借调与归还跟踪", Icons.Default.LocalShipping, Color(0xFF009688)) { onNavigate(ScreenTarget.Transfers) }
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
    columns: Int? = null,
) {
    val gridColumns = columns ?: if (items.size % 2 == 0 && items.size <= 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(gridColumns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val cellWeight = Modifier.weight(1f)
                row.forEach { (label, value) ->
                    val isPositive = value != "0" && value != "-"
                    val isAlert = label.contains("逾期") || label.contains("等待") || label.contains("超时")
                    val valueColor = when {
                        !isPositive -> Ink
                        isAlert -> Danger
                        label.contains("完成") || label.contains("已取") -> Success
                        else -> Primary
                    }

                    Card(
                        modifier = Modifier
                            .then(cellWeight)
                            .height(68.dp)
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = CardShape,
                        border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = value,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = valueColor,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = Muted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                repeat(gridColumns - row.size) {
                    Spacer(cellWeight)
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
