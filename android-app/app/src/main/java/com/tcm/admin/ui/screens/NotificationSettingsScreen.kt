package com.tcm.admin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cn.jpush.android.api.JPushInterface
import com.tcm.admin.isAppDarkTheme

@Composable
internal fun NotificationSettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("TcmPrefs", Context.MODE_PRIVATE)

    var soundEffectsEnabled by remember { mutableStateOf(prefs.getBoolean("soundEffectsEnabled", true)) }
    var actionHapticEnabled by remember { mutableStateOf(prefs.getBoolean("actionHapticEnabled", true)) }
    
    var receiveNotifications by remember { mutableStateOf(prefs.getBoolean("receiveNotifications", true)) }
    var prescriptionNotify by remember { mutableStateOf(prefs.getBoolean("prescriptionNotify", true)) }
    var transferNotify by remember { mutableStateOf(prefs.getBoolean("transferNotify", true)) }

    var systemNotificationAuthorized by remember { mutableStateOf(checkSystemNotification(context)) }

    // Re-check when resuming
    LaunchedEffect(Unit) {
        systemNotificationAuthorized = checkSystemNotification(context)
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun syncNotificationPreferences() {
        if (receiveNotifications) TcmFcmService.registerCurrentToken(context)
    }

    // Toggle JPush Push Service based on receiveNotifications
    LaunchedEffect(receiveNotifications) {
        if (receiveNotifications) {
            TcmFcmService.registerCurrentToken(context)
        } else {
            JPushInterface.stopPush(context)
            TcmJPushReceiver.unregisterToken(context)
            TcmFcmService.unregisterToken(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // App Interaction Sounds & Haptics
        Text("应用内交互", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "交互提示音",
                    subtitle = "扫码、成功或错误时的提示音效",
                    isOn = soundEffectsEnabled,
                    onToggle = { 
                        soundEffectsEnabled = it
                        saveBoolean("soundEffectsEnabled", it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                ToggleRow(
                    title = "震动反馈",
                    subtitle = "重要操作成功或失败时的震动反馈",
                    isOn = actionHapticEnabled,
                    onToggle = { 
                        actionHapticEnabled = it
                        saveBoolean("actionHapticEnabled", it)
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        
        Text("消息与推送通知", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardShape,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "接收系统业务通知",
                    subtitle = "及时获取处方、加工与调拨状态变更",
                    isOn = receiveNotifications,
                    onToggle = { 
                        receiveNotifications = it
                        saveBoolean("receiveNotifications", it)
                    }
                )
                if (receiveNotifications) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                    ToggleRow(
                        title = "处方与导入提醒",
                        subtitle = "E6处方导入及新处方待审提醒",
                        isOn = prescriptionNotify,
                        onToggle = { 
                            prescriptionNotify = it
                            saveBoolean("prescriptionNotify", it)
                            syncNotificationPreferences()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                    ToggleRow(
                        title = "调拨与盘点待办提醒",
                        subtitle = "门店物资借调、归还及盘点任务提醒",
                        isOn = transferNotify,
                        onToggle = { 
                            transferNotify = it
                            saveBoolean("transferNotify", it)
                            syncNotificationPreferences()
                        }
                    )
                }
            }
        }
        
        if (!systemNotificationAuthorized) {
            Spacer(Modifier.height(12.dp))
            val isDark = isAppDarkTheme
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color(0xFF2E2405) else Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF785E00) else Color(0xFFFDE68A)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "系统通知权限已关闭，建议前往系统设置开启通知，以便及时收到业务消息。",
                        color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = { openAppSettings(context) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDark) Color(0xFF4D3800) else Color(0xFFFEF3C7),
                            contentColor = if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text("去开启", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val isDark = isAppDarkTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isOn) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (isDark) Color(0xFFD4D4D8) else Color.White,
                uncheckedTrackColor = if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                uncheckedBorderColor = if (isDark) Color(0xFF52525B) else Color.Transparent,
            )
        )
    }
}

private fun checkSystemNotification(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
    }
    context.startActivity(intent)
}
