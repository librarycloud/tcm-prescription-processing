package com.tcm.admin

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    onSessionUpdated: (AdminSession) -> Unit,
    onLogout: () -> Unit,
) {
    val displayName = user?.optString("nickname").orEmpty().ifBlank {
        user?.optString("username").orEmpty().ifBlank { "管理员" }
    }
    val role = when (user?.optInt("role", 0)) {
        0 -> "全局管理员"
        2 -> "门店管理员"
        3 -> "门店员工"
        else -> "管理员"
    }
    var editVisible by remember { mutableStateOf(false) }
    var nickname by remember(user?.toString()) { mutableStateOf(user?.optString("nickname").orEmpty()) }
    var username by remember(user?.toString()) { mutableStateOf(user?.optString("username").orEmpty()) }
    var phone by remember(user?.toString()) { mutableStateOf(user?.optString("phone").orEmpty()) }
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = CardShape,
            border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = CardShape,
            border = BorderStroke(1.dp, Color(0xFFEBEEF5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ProfileDetailRow(
                    icon = Icons.Default.Person,
                    label = "用户名",
                    value = user?.displayField("username") ?: "-",
                )
                HorizontalDivider(color = Color(0xFFF2F3F5))
                ProfileDetailRow(
                    icon = Icons.Default.Phone,
                    label = "手机号",
                    value = maskPhone(user?.optString("phone")),
                )
                HorizontalDivider(color = Color(0xFFF2F3F5))
                ProfileDetailRow(
                    icon = Icons.Default.Business,
                    label = "所属门店",
                    value = user?.optJSONObject("store")?.displayField("name", "")?.ifBlank { "全部门店（全局权限）" }
                        ?: "全部门店（全局权限）",
                )
                HorizontalDivider(color = Color(0xFFF2F3F5))
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
                nickname = user?.optString("nickname").orEmpty()
                username = user?.optString("username").orEmpty()
                phone = user?.optString("phone").orEmpty()
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

        Spacer(Modifier.height(10.dp))

        // Logout Button
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = FieldShape,
            border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Danger,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        
        Spacer(Modifier.height(16.dp))
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
            .padding(vertical = 14.dp),
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
