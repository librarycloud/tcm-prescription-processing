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
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        // Quick Actions - 单列长条卡片
        SectionHeader("快捷功能", "快速直达药房业务管理模块")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                label = "处方管理",
                subtitle = "查看、新建与管理患者处方及加工批次",
                icon = Icons.AutoMirrored.Filled.Assignment,
                iconColor = Primary,
                onClick = { onNavigate(ScreenTarget.Prescriptions) }
            )
            QuickAction(
                label = "E6商品库存",
                subtitle = "查询商品批次、规格、条码与效期",
                icon = Icons.Default.Inventory,
                iconColor = Success,
                onClick = { onNavigate(ScreenTarget.Inventory()) }
            )
            QuickAction(
                label = "商品盘点",
                subtitle = "商品盘点计划发起与实物差异录入",
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                iconColor = Indigo,
                onClick = { onNavigate(ScreenTarget.Stocktaking) }
            )
            QuickAction(
                label = "库存差异",
                subtitle = "登记与处理实货多/实货少差异记录",
                icon = Icons.Default.Tune,
                iconColor = Warning,
                onClick = { onNavigate(ScreenTarget.Differences) }
            )
            QuickAction(
                label = "门店调拨",
                subtitle = "跨门店物资借调、出库与归还跟踪",
                icon = Icons.Default.LocalShipping,
                iconColor = Info,
                onClick = { onNavigate(ScreenTarget.Transfers) }
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

internal fun stat(stats: JSONObject?, key: String): String = stats?.displayField(key) ?: "-"

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
                        border = BorderStroke(1.dp, CardBorderColor),
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
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Muted.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
