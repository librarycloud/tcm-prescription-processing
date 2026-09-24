package com.tcm.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import android.text.format.DateUtils

@Composable
internal fun SecurityPrivacyScreen() {
    var sessions by remember { mutableStateOf<List<SessionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRevokingId by remember { mutableStateOf<String?>(null) }
    var showRevokeAllAlert by remember { mutableStateOf(false) }
    var revokeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchSessions() {
        scope.launch {
            isLoading = true
            runCatching {
                sessions = ApiClient.fetchSessions()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchSessions()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PageBackground),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "登录设备管理",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = com.tcm.admin.CardShape,
                border = BorderStroke(0.5.dp, com.tcm.admin.CardBorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column {
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    } else {
                        sessions.forEachIndexed { index, session ->
                            SessionRow(
                                session = session,
                                isRevoking = isRevokingId == session.jti,
                                onRevoke = {
                                    scope.launch {
                                        isRevokingId = session.jti
                                        val result = runCatching {
                                            ApiClient.revokeSession(session.jti)
                                        }
                                        if (result.isSuccess) {
                                            // 只有后端确认成功才从本地移除，避免幽灵记录
                                            sessions = sessions.filter { it.jti != session.jti }
                                        } else {
                                            revokeError = "退出失败，请检查网络后重试"
                                        }
                                        isRevokingId = null
                                    }
                                }
                            )
                            if (index < sessions.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = com.tcm.admin.CardBorderColor
                                )
                            }
                        }
                    }
                }
            }
            
            Text(
                text = "这些是当前登录了你账号的设备。如果有不认识的设备，或者已经不再使用的设备，请将其退出登录。",
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        if (!isLoading && sessions.any { !it.isCurrent }) {
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showRevokeAllAlert = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = com.tcm.admin.CardShape,
                    border = BorderStroke(0.5.dp, com.tcm.admin.CardBorderColor),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("退出所有其他设备", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }

    if (showRevokeAllAlert) {
        AlertDialog(
            onDismissRequest = { showRevokeAllAlert = false },
            title = { Text("退出所有其他设备", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确认将当前账号在所有其他设备上退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeAllAlert = false
                    scope.launch {
                        val others = sessions.filter { !it.isCurrent }
                        for (s in others) {
                            runCatching { ApiClient.revokeSession(s.jti) }
                        }
                        fetchSessions()
                    }
                }) {
                    Text("确认退出", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAllAlert = false }) {
                    Text("取消", color = Muted)
                }
            }
        )
    }

    if (revokeError != null) {
        AlertDialog(
            onDismissRequest = { revokeError = null },
            title = { Text("操作失败", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text(revokeError ?: "") },
            confirmButton = {
                TextButton(onClick = { revokeError = null }) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun SessionRow(session: SessionItem, isRevoking: Boolean, onRevoke: () -> Unit) {
    var showRevokeConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dname = session.deviceName.ifEmpty { "未知设备" }
                Text(
                    text = dname,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                if (session.isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "当前设备",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Success,
                        modifier = Modifier
                            .background(SuccessSoft, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${session.ip.ifEmpty { "未知 IP" }} (${session.location})", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val timeString = formatter.format(java.util.Date(session.lastActiveAt))
                Text("活跃于 $timeString", fontSize = 12.sp, color = Muted)
            }
        }

        if (!session.isCurrent) {
            if (isRevoking) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.error)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    onClick = { showRevokeConfirm = true }
                ) {
                    Text(
                        "退出",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showRevokeConfirm) {
        val dname = session.deviceName.ifEmpty { "该设备" }
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("退出登录", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确认将当前账号在 $dname 上退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeConfirm = false
                    onRevoke()
                }) {
                    Text("确认", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) {
                    Text("取消", color = Muted)
                }
            }
        )
    }
}
