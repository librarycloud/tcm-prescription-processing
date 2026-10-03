package com.tcm.admin.ui.screens

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
import com.tcm.admin.ui.*
import cn.jpush.android.api.JPushInterface

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

    // Toggle JPush Push Service based on receiveNotifications
    LaunchedEffect(receiveNotifications) {
        if (receiveNotifications) {
            JPushInterface.resumePush(context)
        } else {
            JPushInterface.stopPush(context)
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
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
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
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                    ToggleRow(
                        title = "处方与导入提醒",
                        subtitle = "E6处方导入及新处方待审提醒",
                        isOn = prescriptionNotify,
                        onToggle = { 
                            prescriptionNotify = it
                            saveBoolean("prescriptionNotify", it)
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                    ToggleRow(
                        title = "调拨与盘点待办提醒",
                        subtitle = "门店物资借调、归还及盘点任务提醒",
                        isOn = transferNotify,
                        onToggle = { 
                            transferNotify = it
                            saveBoolean("transferNotify", it)
                        }
                    )
                }
            }
        }
        
        if (!systemNotificationAuthorized) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3CD))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "系统通知权限已关闭，建议前往系统设置开启通知，以便及时收到业务消息。",
                    color = Color(0xFF856404),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "去开启",
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        openAppSettings(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
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
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Primary
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
