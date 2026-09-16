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

import com.tcm.admin.api.ApiClient

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
                        title = { Text(if (webUrlToShow == "privacy_policy") "隐私政策" else "用户协议") },
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
                                val htmlData = """
                                    <!DOCTYPE html>
                                    <html lang="zh-CN">
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                        <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                                        <style>
                                            body { font-family: -apple-system, sans-serif; line-height: 1.6; padding: 16px; color: #333; }
                                            .loading { text-align: center; color: #666; margin-top: 50px; }
                                            img { max-width: 100%; height: auto; }
                                            pre { background: #f6f8fa; padding: 16px; overflow: auto; border-radius: 6px; }
                                            blockquote { border-left: 4px solid #dfe2e5; padding: 0 15px; color: #6a737d; margin: 0 0 16px 0; }
                                        </style>
                                    </head>
                                    <body>
                                        <div id="content"><div class="loading">加载中...</div></div>
                                        <script>
                                            fetch('${ApiClient.currentBaseUrl.trimEnd('/')}/app/legal-docs')
                                                .then(res => res.json())
                                                .then(json => {
                                                    const data = json.code === 0 ? json.data : (json || {});
                                                    const md = data.${if(webUrlToShow == "privacy_policy") "privacy_policy" else "user_agreement"} || '暂无内容';
                                                    document.getElementById('content').innerHTML = marked.parse(md);
                                                })
                                                .catch(e => {
                                                    document.getElementById('content').innerHTML = '<div class="loading">加载失败，请检查网络并重试</div>';
                                                });
                                        </script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL(null, htmlData, "text/html", "utf-8", null)
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
                    pushStringAnnotation(tag = "PRIVACY", annotation = "privacy_policy")
                    withStyle(style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)) {
                        append("《隐私政策》")
                    }
                    pop()
                    append("和")
                    pushStringAnnotation(tag = "AGREEMENT", annotation = "user_agreement")
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
