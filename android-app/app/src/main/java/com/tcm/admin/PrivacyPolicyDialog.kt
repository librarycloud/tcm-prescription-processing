package com.tcm.admin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(onAgree: () -> Unit, onDisagree: () -> Unit) {
    val context = LocalContext.current
    var webUrlToShow by remember { mutableStateOf<String?>(null) }

    if (webUrlToShow != null) {
        Dialog(
            onDismissRequest = { webUrlToShow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column {
                    TopAppBar(
                        title = { Text(if (webUrlToShow?.contains("privacy") == true) "隐私政策" else "用户协议") },
                        navigationIcon = {
                            IconButton(onClick = { webUrlToShow = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                loadUrl(webUrlToShow!!)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "隐私政策与用户协议",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                val annotatedString = buildAnnotatedString {
                    append("感谢您使用本应用！我们非常重视您的个人信息和隐私保护。在您使用本应用前，请仔细阅读")
                    pushStringAnnotation(tag = "PRIVACY", annotation = "https://yourdomain.com/privacy.html")
                    withStyle(style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)) {
                        append("《隐私政策》")
                    }
                    pop()
                    append("和")
                    pushStringAnnotation(tag = "AGREEMENT", annotation = "https://yourdomain.com/agreement.html")
                    withStyle(style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)) {
                        append("《用户协议》")
                    }
                    pop()
                    append("。\n\n我们将在获得您的明确同意后，收集必要的设备信息、网络信息等，并初始化相关第三方 SDK 以提供服务。")
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                            .firstOrNull()?.let {
                                webUrlToShow = it.item
                            }
                        annotatedString.getStringAnnotations(tag = "AGREEMENT", start = offset, end = offset)
                            .firstOrNull()?.let {
                                webUrlToShow = it.item
                            }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDisagree) {
                        Text("暂不同意/退出", color = Color.Gray)
                    }
                    Button(onClick = onAgree) {
                        Text("同 意")
                    }
                }
            }
        }
    }
}
