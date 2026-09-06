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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val options = listOf(
        "system" to "跟随系统",
        "light" to "亮色",
        "dark" to "暗色",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("外观", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                options.forEachIndexed { index, (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(value) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedTheme == value,
                            onClick = { onThemeSelected(value) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = Ink, fontSize = 15.sp)
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
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

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("预设风格", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
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
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
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
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("点选推荐色彩，或输入任意 16 进制颜色代码", fontSize = 12.sp, color = Muted)

                Spacer(Modifier.height(14.dp))
                Text("推荐色彩", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
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
