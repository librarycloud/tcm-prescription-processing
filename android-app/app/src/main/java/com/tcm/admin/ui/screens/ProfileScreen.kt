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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
internal fun ProfileScreen(user: JSONObject?, onLogout: () -> Unit) {
    val displayName = user?.optString("nickname").orEmpty().ifBlank {
        user?.optString("username").orEmpty().ifBlank { "管理员" }
    }
    val role = when (user?.optInt("role", 0)) {
        0 -> "全局管理员"
        2 -> "门店管理员"
        3 -> "门店员工"
        else -> "管理员"
    }

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
                    value = user?.optString("username").orEmpty().ifBlank { "-" },
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
                    value = user?.optJSONObject("store")?.optString("name").orEmpty().ifBlank { "全部门店（全局权限）" },
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
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        
        Spacer(Modifier.height(16.dp))
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
