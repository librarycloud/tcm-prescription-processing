package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

// ==================== Color Palette ====================
internal val PageBackground: Color @Composable get() = MaterialTheme.colorScheme.background
internal val Primary: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val PrimaryDark: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
internal val PrimarySoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val RegularText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Border: Color @Composable get() = MaterialTheme.colorScheme.outline
internal val Success: Color @Composable get() = MaterialTheme.colorScheme.tertiary
internal val SuccessSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
internal val Warning: Color @Composable get() = MaterialTheme.colorScheme.secondary
internal val WarningSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
internal val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
internal val DangerSoft: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
internal val Purple: Color @Composable get() = MaterialTheme.colorScheme.tertiary
internal val PurpleSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
internal val CardShape = RoundedCornerShape(10.dp)
internal val FieldShape = RoundedCornerShape(8.dp)
internal val CompactControlHeight = 40.dp
internal val SearchControlHeight = 48.dp

internal fun displayText(value: Any?, fallback: String = "-"): String {
    val text = value?.toString()?.trim().orEmpty()
    return if (text.isBlank() || text.equals("null", ignoreCase = true)) fallback else text
}

internal fun JSONObject.displayField(key: String, fallback: String = "-"): String = displayText(opt(key), fallback)

internal fun quantityText(value: Any?, fallback: String = "-"): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.isBlank() || raw == "null") return fallback
    return runCatching { BigDecimal(raw).stripTrailingZeros().toPlainString() }.getOrDefault(fallback)
}

internal fun priceText(value: Any?, fallback: String = "-"): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.isBlank() || raw == "null") return fallback
    return runCatching { BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toPlainString() }.getOrDefault(fallback)
}

// ==================== Helper Mappings ====================
internal fun planStatus(status: Int): String = when (status) {
    0 -> "待加工"
    1 -> "加工中"
    2 -> "加工完成"
    3 -> "待领取"
    4 -> "已领取"
    5 -> "已取消"
    else -> "未知"
}

internal fun pickupMethodLabel(method: Int): String = when (method) {
    0 -> "自提"
    1 -> "跑腿"
    2 -> "快递"
    else -> "其他"
}

internal fun transferStatusLabel(status: Int, outboundStatus: Int): String = when {
    status == 3 -> "已取消"
    status == 2 -> "已调平"
    status == 1 -> "部分归还"
    outboundStatus == 0 -> "待出库"
    else -> "借出中"
}

internal fun maskPhone(phone: String?): String {
    if (phone.isNullOrBlank()) return "-"
    val cleaned = phone.trim()
    return if (cleaned.length == 11) {
        cleaned.substring(0, 3) + "****" + cleaned.substring(7)
    } else {
        cleaned
    }
}

// ==================== Common Components ====================

@Composable
internal fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val (color, bgColor) = when {
        text in listOf("加工完成", "已领取", "已完成", "已调平", "正常", "盘点完成", "已核销") ->
            Pair(Success, SuccessSoft)
        text in listOf("实货少", "已取消", "逾期", "已逾期", "紧急", "特急", "加急") ->
            Pair(Danger, DangerSoft)
        text in listOf("加工中", "实货多", "自提", "进行中", "待盘点", "盘点中") ->
            Pair(Primary, PrimarySoft)
        text in listOf("跑腿", "快递") ->
            Pair(Purple, PurpleSoft)
        else ->
            Pair(Warning, WarningSoft)
    }
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun UrgentBadge(
    text: String = "加急",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = DangerSoft,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, Danger.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            color = Danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun SegmentedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = Primary,
            shadowElevation = 1.dp,
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Border),
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = RegularText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Ink,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
internal fun SearchBarField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScan: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchControlHeight)
            .background(Color.White, FieldShape)
            .border(BorderStroke(1.dp, Border), FieldShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 13.sp, color = Ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onScan != null) {
                        IconButton(
                            onClick = { onScan.invoke() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "扫码",
                                tint = Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = Muted, fontSize = 13.sp, maxLines = 1)
                        }
                        innerTextField()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = { onValueChange("") },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = Muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = onSearch,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun StoreChipsRow(
    stores: List<JSONObject>,
    selectedStoreId: String,
    onSelectStore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stores.size > 1) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedButton(
                label = "全部门店",
                selected = selectedStoreId.isBlank(),
                onClick = { onSelectStore("") },
            )
            stores.forEach { store ->
                val id = store.opt("id")?.toString().orEmpty()
                val name = store.optString("name", "门店")
                SegmentedButton(
                    label = name,
                    selected = selectedStoreId == id,
                    onClick = { onSelectStore(id) },
                )
            }
        }
    }
}

@Composable
internal fun AppEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = Color(0xFFF0F2F5),
            shape = CircleShape,
            modifier = Modifier.size(54.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            color = Muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun AppPagination(
    page: Int,
    pages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPrev,
            enabled = page > 1,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("上一页", fontSize = 12.sp)
        }
        Text(
            text = "$page / ${if (pages < 1) 1 else pages}",
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        OutlinedButton(
            onClick = onNext,
            enabled = page < pages,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        ) {
            Text("下一页", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun InfoRowItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
    isBold: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Muted, fontSize = 12.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
