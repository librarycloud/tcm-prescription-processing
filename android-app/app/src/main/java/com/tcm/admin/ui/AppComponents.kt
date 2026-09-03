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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.absoluteValue

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

private val retainedListValues = mutableMapOf<String, MutableState<Any?>>()

@Composable
internal fun <T> rememberRetainedListValue(
    owner: String,
    name: String,
    initialValue: () -> T,
): MutableState<T> {
    @Suppress("UNCHECKED_CAST")
    return remember(owner, name) {
        synchronized(retainedListValues) {
            retainedListValues.getOrPut("$owner:$name") { mutableStateOf<Any?>(initialValue()) }
        } as MutableState<T>
    }
}

internal fun invalidateRetainedList(owner: String) {
    synchronized(retainedListValues) {
        retainedListValues["$owner:loadedQueryKey"]?.value = null
    }
}

internal fun clearRetainedListValues() {
    synchronized(retainedListValues) { retainedListValues.clear() }
}

/** Automatic list searches wait for enough input to avoid a request per keystroke. */
internal fun shouldAutoSearchQuery(value: String): Boolean {
    val text = value.trim()
    if (text.isBlank()) return false
    val chineseCount = text.count { it in '\u4e00'..'\u9fff' }
    val digitCount = text.count(Char::isDigit)
    val hasLatinLetter = text.any { it in 'a'..'z' || it in 'A'..'Z' }
    return chineseCount >= 2 || digitCount >= 4 || hasLatinLetter
}

@Composable
internal fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val nestedScrollConnection = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && (available.x.absoluteValue > 10f || available.y.absoluteValue > 10f)) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = false)
                }
                return Offset.Zero
            }
        }
    }

    return this
        .nestedScroll(nestedScrollConnection)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            keyboardController?.hide()
            focusManager.clearFocus(force = false)
        }
}

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
// Method-specific accents keep logistics labels visually distinct from workflow status labels.
internal val Info: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val InfoSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val Indigo: Color @Composable get() = MaterialTheme.colorScheme.secondary
internal val IndigoSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
internal val Pink: Color @Composable get() = MaterialTheme.colorScheme.error
internal val PinkSoft: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
internal val Brown: Color @Composable get() = MaterialTheme.colorScheme.secondary
internal val BrownSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
private val RunnerColor: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val RunnerSoftColor: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
private val CourierColor: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val CourierSoftColor: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
internal val CardBorderColor: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
internal val CardShape = RoundedCornerShape(12.dp)
internal val FieldShape = RoundedCornerShape(8.dp)
internal val CompactControlHeight = 40.dp
internal val SearchControlHeight = 44.dp

internal fun displayText(value: Any?, fallback: String = "-"): String {
    val text = value?.toString()?.trim().orEmpty()
    return if (text.isBlank() || text.equals("null", ignoreCase = true)) fallback else text
}

internal fun JSONObject.displayField(key: String, fallback: String = "-"): String = displayText(opt(key), fallback)

/** Formats the six-digit pickup code for display without changing the value sent to APIs. */
internal fun formatPickupCode(value: String): String {
    val text = value.trim()
    return if (text.matches(Regex("\\d{6}"))) {
        "${text.substring(0, 3)}-${text.substring(3)}"
    } else {
        text
    }
}

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

private val ServerDisplayZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val ServerDisplayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Formats API timestamps in China Standard Time (UTC+08:00), regardless of device timezone. */
internal fun serverDateTime(value: Any?, fallback: String = "-"): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return fallback
    if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return raw

    val instant = runCatching { Instant.parse(raw) }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching {
            LocalDateTime.parse(raw.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        }
        .getOrNull()
        ?: return raw.replace("T", " ").replace("Z", "").take(16)
    return ServerDisplayFormatter.format(instant.atZone(ServerDisplayZone))
}

internal fun serverDateOnly(value: Any?, fallback: String = ""): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return raw
    return serverDateTime(value, fallback).take(10)
}

internal fun serverToday(): LocalDate = LocalDate.now(ServerDisplayZone)

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
    if (phone.isNullOrBlank() || phone.trim().equals("null", ignoreCase = true)) return "-"
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
        text in listOf("加工完成", "已领取", "已完成", "已调平", "正常", "盘点完成", "已核销", "已付款") ->
            Pair(Success, SuccessSoft)
        text == "自提" ->
            Pair(Primary, PrimarySoft)
        text == "跑腿" ->
            Pair(RunnerColor, RunnerSoftColor)
        text == "快递" ->
            Pair(CourierColor, CourierSoftColor)
        text in listOf("待加工", "待盘点") ->
            Pair(Indigo, IndigoSoft)
        text == "盘点中" ->
            Pair(Pink, PinkSoft)
        text in listOf("待出库", "借出中") ->
            Pair(Brown, BrownSoft)
        text == "未付款" ->
            Pair(Brown, BrownSoft)
        text == "部分归还" ->
            Pair(Info, InfoSoft)
        text in listOf("实货少", "已取消", "逾期", "已逾期", "紧急", "特急", "加急") ->
            Pair(Danger, DangerSoft)
        text in listOf("全局管理员", "门店管理员", "门店员工", "管理员") ->
            Pair(PrimaryDark, PrimarySoft)
        text in listOf("加工中", "实货多", "进行中") ->
            Pair(Primary, PrimarySoft)
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
    centerLabel: Boolean = false,
) {
    if (selected) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = Primary,
            shadowElevation = 1.dp,
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = if (centerLabel) {
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                } else {
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                },
                textAlign = if (centerLabel) TextAlign.Center else TextAlign.Start,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(0.5.dp, CardBorderColor),
            modifier = modifier,
        ) {
            Text(
                text = label,
                color = RegularText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = if (centerLabel) {
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                } else {
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                },
                textAlign = if (centerLabel) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier.padding(paddingValues),
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
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchControlHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, FieldShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), FieldShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = Ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearch()
            }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onScan != null) {
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                onScan.invoke()
                            },
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Muted,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val haptic = LocalHapticFeedback.current
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onValueChange("")
                                },
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
                            onClick = {
                                keyboardController?.hide()
                                onSearch()
                            },
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
    onRetry: (() -> Unit)? = null,
    retryText: String = "刷新数据",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = PrimarySoft.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = FieldShape,
                modifier = Modifier.height(34.dp),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
            ) {
                Text(retryText, fontSize = 12.sp, color = Primary)
            }
        }
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
    verticalPadding: Dp = 0.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Muted, fontSize = 12.5.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.5.sp,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun LoadingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    text: String = "确定",
    loadingText: String = "提交中...",
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(containerColor = Primary),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        shape = FieldShape,
        colors = colors,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(loadingText, fontSize = 14.sp)
        } else {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
internal fun HighlightedText(
    text: String,
    highlight: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    highlightColor: Color = Primary,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    highlightFontWeight: FontWeight = FontWeight.Bold,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val annotatedString = remember(text, highlight, color, highlightColor, fontWeight, highlightFontWeight) {
        val trimmedHighlight = highlight.trim()
        if (trimmedHighlight.isEmpty()) {
            androidx.compose.ui.text.AnnotatedString(text, androidx.compose.ui.text.SpanStyle(color = color, fontWeight = fontWeight))
        } else {
            androidx.compose.ui.text.buildAnnotatedString {
                var currentIndex = 0
                val lowerText = text.lowercase()
                val lowerHighlight = trimmedHighlight.lowercase()
                while (currentIndex < text.length) {
                    val matchIndex = lowerText.indexOf(lowerHighlight, currentIndex)
                    if (matchIndex < 0) {
                        append(text.substring(currentIndex))
                        break
                    }
                    if (matchIndex > currentIndex) {
                        append(text.substring(currentIndex, matchIndex))
                    }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = highlightColor, fontWeight = highlightFontWeight)) {
                        append(text.substring(matchIndex, matchIndex + lowerHighlight.length))
                    }
                    currentIndex = matchIndex + lowerHighlight.length
                }
            }
        }
    }
    Text(
        text = annotatedString,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecentSearchChipsRow(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近搜索",
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "清空历史",
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            history.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { onSelect(item) },
                ) {
                    Text(
                        text = item,
                        color = Ink,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
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
    onItemClick: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null,
    columns: Int? = null,
) {
    val gridColumns = columns ?: if (items.size % 2 == 0 && items.size <= 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(gridColumns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val cellWeight = Modifier.weight(1f)
                row.forEachIndexed { columnIndex, (label, value) ->
                    val itemIndex = rowIndex * gridColumns + columnIndex
                    val isSelected = selectedIndex == itemIndex
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
                            .then(
                                when {
                                    onItemClick != null -> Modifier.clickable { onItemClick(itemIndex) }
                                    onClick != null -> Modifier.clickable(onClick = onClick)
                                    else -> Modifier
                                },
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                        shape = CardShape,
                        border = BorderStroke(1.dp, if (isSelected) Primary else CardBorderColor),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
