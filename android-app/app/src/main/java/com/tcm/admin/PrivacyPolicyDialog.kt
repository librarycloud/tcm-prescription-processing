package com.tcm.admin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
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

import com.tcm.admin.ApiClient

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(onAgree: () -> Unit, onDisagree: (() -> Unit)? = null) {
    val context = LocalContext.current
    var webUrlToShow by remember { mutableStateOf<String?>(null) }
    var showSecondaryConfirm by remember { mutableStateOf(false) }
    var isStandbyMode by remember { mutableStateOf(false) }

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
                                val baseUrl = ApiClient.currentBaseUrl.trimEnd('/')
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
                                            li { margin: 4px 0; }
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

    if (isStandbyMode) {
        PrivacyStandbyScreen(
            onReRead = { isStandbyMode = false },
            onShowPolicy = { webUrlToShow = "privacy_policy" },
            onShowAgreement = { webUrlToShow = "user_agreement" }
        )
    } else {
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
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "privacy_policy",
                                styles = TextLinkStyles(style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold))
                            ) {
                                webUrlToShow = "privacy_policy"
                            }
                        ) {
                            append("《隐私政策》")
                        }
                        append("和")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "user_agreement",
                                styles = TextLinkStyles(style = SpanStyle(color = Color(0xFF007AFF), fontWeight = FontWeight.Bold))
                            ) {
                                webUrlToShow = "user_agreement"
                            }
                        ) {
                            append("《用户协议》")
                        }
                        append("。\n\n我们将在获得您的明确同意后，收集必要的设备信息、网络信息等，并初始化相关第三方 SDK 以提供服务。")
                    }

                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { showSecondaryConfirm = true }) {
                            Text("暂不同意", color = Color.Gray)
                        }
                        Button(onClick = onAgree) {
                            Text("同 意")
                        }
                    }
                }
            }
        }
    }

    if (showSecondaryConfirm) {
        AlertDialog(
            onDismissRequest = { showSecondaryConfirm = false },
            title = { Text("温馨提示", fontWeight = FontWeight.Bold) },
            text = {
                Text("您需要同意《隐私政策》与《用户协议》才能使用本应用提供的中药房管理与处方加工等各项功能。\n\n若您选择暂不同意，应用将进入静置保护状态，暂不开启服务。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecondaryConfirm = false
                        isStandbyMode = true
                        onDisagree?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认暂不使用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecondaryConfirm = false }) {
                    Text("再想想")
                }
            }
        )
    }
}

@Composable
private fun PrivacyStandbyScreen(
    onReRead: () -> Unit,
    onShowPolicy: () -> Unit,
    onShowAgreement: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "保护",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "服务暂未开启",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "您尚未同意《隐私政策》与《用户协议》，相关中药房服务暂未激活。\n\n如需使用各项管理功能，请点击下方按钮重新阅读并同意协议；您也可以直接按 Home 键离开应用。",
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onReRead,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("重新阅读协议并同意", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onShowPolicy) {
                    Text("《隐私政策》", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text("·", color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = onShowAgreement) {
                    Text("《用户协议》", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
