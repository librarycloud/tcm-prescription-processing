package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun ProfileScreen(
    user: JSONObject?,
    onOpenDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onEntered: () -> Unit,
    hasAppUpdate: Boolean,
    scrollState: ScrollState,
    onSessionUpdated: (AdminSession) -> Unit,
) {
    val displayName = user?.displayField("nickname", "").orEmpty().ifBlank {
        user?.displayField("username", "").orEmpty().ifBlank { "管理员" }
    }
    val role = when (user?.optInt("role", 0)) {
        0 -> "全局管理员"
        2 -> "门店管理员"
        3 -> "门店员工"
        else -> "管理员"
    }

    LaunchedEffect(Unit) { onEntered() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, CardBorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = PrimarySoft) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(displayName.take(1), color = Primary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    StatusPill(text = role)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "查看个人资料", tint = Muted)
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("设置", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("检查新版本（${BuildConfig.VERSION_NAME}）", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (hasAppUpdate) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = CircleShape,
                    color = Color(0xFFE5484D),
                ) {}
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LiveThemePreviewCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "界面实时渲染预览",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimarySoft,
                    border = BorderStroke(0.5.dp, Primary.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = "实时生效",
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Simulated Mini TCM App Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Primary,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "补中益气汤加减",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                )
                                Text(
                                    text = "处方号 RX-2026-088 · 3剂",
                                    fontSize = 10.sp,
                                    color = Muted,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = PrimarySoft,
                            border = BorderStroke(0.5.dp, Primary.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = "调剂完成",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Herb tags row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("黄芪 15g", "党参 10g", "白术 10g", "甘草 6g").forEach { herb ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    text = herb,
                                    fontSize = 10.sp,
                                    color = Ink,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("出库发药", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("打印药签", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedThemeSelector(
    selectedTheme: String,
    pureBlackMode: Boolean,
    onThemeSelected: (String) -> Unit,
) {
    val options = listOf(
        Triple("system", "跟随系统", Icons.Default.BrightnessAuto),
        Triple("light", "浅色", Icons.Default.LightMode),
        Triple("dark", "深色", Icons.Default.DarkMode),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = selectedTheme == mode
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onThemeSelected(mode) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) PrimarySoft.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (mode) {
                            "system" -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color(0xFFF4F4F6)),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (pureBlackMode) Color(0xFF000000) else Color(0xFF18181D)),
                                    )
                                }
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            "light" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFFFFFFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(5.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .background(Primary.copy(alpha = 0.35f), RoundedCornerShape(3.dp)),
                                        ) {}
                                        Row(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(4.dp)
                                                .background(Color(0xFFE2E4EB), RoundedCornerShape(2.dp)),
                                        ) {}
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            "dark" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(if (pureBlackMode) Color(0xFF000000) else Color(0xFF18181D)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(5.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .background(Primary.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                                        ) {}
                                        Row(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(4.dp)
                                                .background(Color(0xFF383842), RoundedCornerShape(2.dp)),
                                        ) {}
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Primary else Ink,
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorConfirmed: (String) -> Unit,
) {
    var currentHexInput by remember(initialHex) {
        val clean = if (initialHex.startsWith("#")) initialHex else "#$initialHex"
        mutableStateOf(clean)
    }
    val initialColor = remember(initialHex) {
        tryParseHexColor(initialHex) ?: Color(0xFF2563EB)
    }
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }

    var hue by remember { mutableStateOf(initialHsv.first) }
    var saturation by remember { mutableStateOf(initialHsv.second) }
    var value by remember { mutableStateOf(initialHsv.third) }

    val currentColor = remember(hue, saturation, value) {
        hsvToColor(hue, saturation, value)
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: 本草国风色谱, 1: 自由调色滑块

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "调色板与本草色谱",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Large Color Preview Box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = currentColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            val hexStr = formatHexColor(currentColor)
                            Text(
                                text = hexStr,
                                color = if (isLightColor(currentColor)) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            val r = (currentColor.red * 255).toInt()
                            val g = (currentColor.green * 255).toInt()
                            val b = (currentColor.blue * 255).toInt()
                            Text(
                                text = "RGB($r, $g, $b)",
                                color = if (isLightColor(currentColor)) Color(0x99000000) else Color(0xCCFFFFFF),
                                fontSize = 11.sp,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLightColor(currentColor)) Color(0x33000000) else Color(0x33FFFFFF),
                        ) {
                            Text(
                                text = "即时效果",
                                color = if (isLightColor(currentColor)) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Tab Switcher between "本草国风" and "微调滑块"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                ) {
                    listOf("本草国风色谱", "色相/明度微调").forEachIndexed { index, title ->
                        val isTabSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTabSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTabSelected) Primary else Muted,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TCM Herbal Palette
                    val categories = listOf("草木", "金石", "根茎", "花实")
                    categories.forEach { category ->
                        Text(
                            text = when (category) {
                                "草木" -> "🌿 草木本草"
                                "金石" -> "🪨 金石矿物"
                                "根茎" -> "🍂 香木根茎"
                                else -> "🌸 花实灵秀"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Muted,
                        )
                        Spacer(Modifier.height(6.dp))

                        val categoryItems = TcmHerbalPalette.filter { it.category == category }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categoryItems.forEach { herbal ->
                                val isChosen = formatHexColor(currentColor).equals(herbal.hex, ignoreCase = true)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val parsed = herbal.color
                                            val (h, s, v) = colorToHsv(parsed)
                                            hue = h
                                            saturation = s
                                            value = v
                                            currentHexInput = herbal.hex
                                        }
                                        .padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(herbal.color)
                                            .border(
                                                width = if (isChosen) 2.5.dp else 1.dp,
                                                color = if (isChosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isChosen) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isLightColor(herbal.color)) Color.Black else Color.White,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = herbal.name,
                                        fontSize = 11.sp,
                                        color = if (isChosen) Primary else Ink,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    // HSV Sliders
                    val rainbowBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000),
                        )
                    )

                    // 1. Hue Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("色相 (Hue)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("${hue.toInt()}°", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(rainbowBrush),
                    )
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            currentHexInput = formatHexColor(hsvToColor(it, saturation, value))
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )

                    // 2. Saturation Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("饱和度 (Saturation)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("${(saturation * 100).toInt()}%", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    val satBrush = Brush.horizontalGradient(
                        listOf(
                            hsvToColor(hue, 0.05f, value),
                            hsvToColor(hue, 1.0f, value),
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(satBrush),
                    )
                    Slider(
                        value = saturation,
                        onValueChange = {
                            saturation = it
                            currentHexInput = formatHexColor(hsvToColor(hue, it, value))
                        },
                        valueRange = 0.15f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )

                    // 3. Brightness/Value Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("明度 (Value)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    val valBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF202020),
                            hsvToColor(hue, saturation, 1.0f),
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(valBrush),
                    )
                    Slider(
                        value = value,
                        onValueChange = {
                            value = it
                            currentHexInput = formatHexColor(hsvToColor(hue, saturation, it))
                        },
                        valueRange = 0.35f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                // Manual HEX fine-tuning inside dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("HEX: ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                    OutlinedTextField(
                        value = currentHexInput,
                        onValueChange = { text ->
                            currentHexInput = text
                            val parsed = tryParseHexColor(text)
                            if (parsed != null) {
                                val (h, s, v) = colorToHsv(parsed)
                                hue = h
                                saturation = s
                                value = v
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalHex = formatHexColor(currentColor)
                    onColorConfirmed(finalHex)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("应用此配色")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
        },
    )
}

@Composable
private fun TextScalingCard(
    textScale: Float,
    onTextScaleChanged: (Float) -> Unit,
) {
    val scalePresets = listOf(
        0.88f to "较小",
        1.00f to "标准",
        1.12f to "中等",
        1.25f to "较大",
        1.38f to "超大",
    )

    val currentIndex = scalePresets.indexOfFirst { kotlin.math.abs(it.first - textScale) < 0.03f }
        .let { if (it >= 0) it else 1 }

    val currentLabel = scalePresets.getOrNull(currentIndex)?.second ?: "标准"
    val percentText = "${(textScale * 100).toInt()}%"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "字体大小与缩放",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (kotlin.math.abs(textScale - 1.0f) >= 0.03f) {
                        Text(
                            text = "恢复默认",
                            fontSize = 12.sp,
                            color = Primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onTextScaleChanged(1.00f) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PrimarySoft,
                    ) {
                        Text(
                            text = "$currentLabel ($percentText)",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "拖动刻度调节应用全局文字大小，即时生效并自动记忆",
                fontSize = 12.sp,
                color = Muted,
            )

            Spacer(Modifier.height(12.dp))

            // Simulated Quote / Prescription Preview Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "「补中益气汤」黄芪 15g，党参 10g，白术 10g，柴胡 6g",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "处方排版与调剂界面文字将根据所选字号等比例渲染",
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Slider with A - A icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "A",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Muted,
                )

                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { floatVal ->
                        val idx = kotlin.math.round(floatVal).toInt().coerceIn(0, scalePresets.lastIndex)
                        if (idx != currentIndex) {
                            onTextScaleChanged(scalePresets[idx].first)
                        }
                    },
                    valueRange = 0f..4f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )

                Text(
                    text = "A",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }

            // Clickable text labels under ticks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                scalePresets.forEachIndexed { index, (presetScale, label) ->
                    val isChosen = index == currentIndex
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (isChosen) Primary else Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onTextScaleChanged(presetScale) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    pureBlackMode: Boolean,
    onPureBlackModeChanged: (Boolean) -> Unit,
    themeAccentKey: String,
    customColorHex: String,
    onThemeAccentSelected: (String) -> Unit,
    onCustomColorChanged: (String) -> Unit,
    textScale: Float = 1.0f,
    onTextScaleChanged: (Float) -> Unit = {},
) {
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            initialHex = customColorHex,
            onDismiss = { showColorPicker = false },
            onColorConfirmed = { hex ->
                onCustomColorChanged(hex)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 1. Live Preview Widget
        LiveThemePreviewCard()

        Spacer(Modifier.height(18.dp))

        // 2. Appearance & Segmented Theme Mode Selector
        Text("外观风格", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        SegmentedThemeSelector(
            selectedTheme = selectedTheme,
            pureBlackMode = pureBlackMode,
            onThemeSelected = onThemeSelected,
        )

        Spacer(Modifier.height(12.dp))

        // 3. Text Scaling
        TextScalingCard(
            textScale = textScale,
            onTextScaleChanged = onTextScaleChanged,
        )

        Spacer(Modifier.height(12.dp))

        // Pure Black Mode Switch
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPureBlackModeChanged(!pureBlackMode) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "纯黑模式",
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (selectedTheme == "light") {
                            "暗色主题下背景呈现纯黑（当前为亮色，切换暗色后生效）"
                        } else {
                            "深色模式下使用纯黑背景（AMOLED 屏幕更省电）"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Switch(
                    checked = pureBlackMode,
                    onCheckedChange = onPureBlackModeChanged,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("主题配色", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(8.dp))

        // Preset Colors
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("经典风格", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DefaultThemeAccents.forEach { preset ->
                        val isSelected = themeAccentKey == preset.key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onThemeAccentSelected(preset.key) }
                                .padding(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(preset.primary)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isLightColor(preset.primary)) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = preset.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Primary else Ink,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Custom Color Card with Palette Button
        var hexInput by remember(customColorHex) { mutableStateOf(customColorHex) }
        val parsedColor = remember(hexInput) { tryParseHexColor(hexInput) }
        val isCustomActive = themeAccentKey == "custom"

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(
                1.dp,
                if (isCustomActive) Primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自定义色彩", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.weight(1f))
                    if (isCustomActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PrimarySoft,
                        ) {
                            Text(
                                "已生效",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { showColorPicker = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimarySoft,
                            contentColor = Primary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("打开调色板", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("点选快捷色块、输入 16 进制颜色代码或使用上方调色板", fontSize = 12.sp, color = Muted)

                Spacer(Modifier.height(14.dp))
                Text("快捷色彩", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickCustomColors.forEach { (_, color) ->
                        val formatted = formatHexColor(color)
                        val isCurrent = isCustomActive && customColorHex.equals(formatted, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    hexInput = formatted
                                    onCustomColorChanged(formatted)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isLightColor(color)) Color.Black else Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                Text("自定义色值（HEX）", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parsedColor ?: Color.LightGray.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (parsedColor == null) {
                            Text("?", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            hexInput = input
                            val parsed = tryParseHexColor(input)
                            if (parsed != null && (input.length == 7 || input.length == 6)) {
                                val fmt = if (input.startsWith("#")) input else "#$input"
                                onCustomColorChanged(fmt)
                            }
                        },
                        placeholder = { Text("#059669") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    )

                    Spacer(Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val parsed = tryParseHexColor(hexInput)
                            if (parsed != null) {
                                val fmt = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                                onCustomColorChanged(fmt)
                            }
                        },
                        enabled = parsedColor != null,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("应用")
                    }
                }
                if (hexInput.isNotBlank() && parsedColor == null) {
                    Spacer(Modifier.height(4.dp))
                    Text("请输入有效的颜色代码，如 #059669 或 059669", color = Danger, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ProfileDetailScreen(
    user: JSONObject?,
    onLogout: () -> Unit,
    onSessionUpdated: (AdminSession) -> Unit,
) {
    val displayName = user?.displayField("nickname", "").orEmpty().ifBlank {
        user?.displayField("username", "").orEmpty().ifBlank { "管理员" }
    }
    val role = when (user?.optInt("role", 0)) {
        0 -> "全局管理员"
        2 -> "门店管理员"
        3 -> "门店员工"
        else -> "管理员"
    }
    val isSuperAdmin = user?.optInt("role", -1) == 0
    var editVisible by remember { mutableStateOf(false) }
    var nickname by remember(user?.toString()) { mutableStateOf(user?.displayField("nickname", "").orEmpty()) }
    var username by remember(user?.toString()) { mutableStateOf(user?.displayField("username", "").orEmpty()) }
    var phone by remember(user?.toString()) { mutableStateOf(user?.displayField("phone", "").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // User Profile Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, CardBorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = PrimarySoft,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.take(1),
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(text = role)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Account Details Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, CardBorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ProfileDetailRow(
                    icon = Icons.Default.Person,
                    label = "用户名",
                    value = user?.displayField("username") ?: "-",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileDetailRow(
                    icon = Icons.Default.Phone,
                    label = "手机号",
                    value = maskPhone(user?.displayField("phone", "")),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (isSuperAdmin) {
                    ProfileDetailRow(
                        icon = Icons.Default.Business,
                        label = "所属门店",
                        value = user?.optJSONObject("store")?.displayField("name", "")?.ifBlank { "全部门店（全局权限）" }
                            ?: "全部门店（全局权限）",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ProfileDetailRow(
                    icon = Icons.Default.Shield,
                    label = "权限角色",
                    value = role,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = {
                nickname = user?.displayField("nickname", "").orEmpty()
                username = user?.displayField("username", "").orEmpty()
                phone = user?.displayField("phone", "").orEmpty()
                password = ""
                saveError = null
                editVisible = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("编辑资料", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (editVisible) {
        AlertDialog(
            onDismissRequest = { if (!saving) editVisible = false },
            title = { Text("编辑资料", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text("姓名或昵称") }, singleLine = true, shape = FieldShape)
                    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名") }, singleLine = true, shape = FieldShape)
                    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true, shape = FieldShape)
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("新密码（不修改请留空）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = FieldShape)
                    saveError?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && (username.isNotBlank() || phone.isNotBlank()),
                    onClick = {
                        saving = true
                        saveError = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApiClient.updateMe(JSONObject()
                                        .put("nickname", nickname.trim())
                                        .put("username", username.trim())
                                        .put("phone", phone.trim())
                                        .also { if (password.isNotBlank()) it.put("password", password) })
                                }
                            }.onSuccess {
                                onSessionUpdated(it)
                                editVisible = false
                            }.onFailure {
                                saveError = it.message ?: "资料保存失败"
                            }
                            saving = false
                        }
                    },
                ) { Text(if (saving) "保存中" else "保存") }
            },
            dismissButton = { TextButton(onClick = { editVisible = false }, enabled = !saving) { Text("取消") } },
        )
    }
}

@Composable
private fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = RegularText,
            fontSize = 14.sp,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
