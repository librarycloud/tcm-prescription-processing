package com.tcm.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember(context) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    var latest by remember { mutableStateOf<JSONObject?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedUri by remember { mutableStateOf<Uri?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    suspend fun fetchLatest() {
        checking = true
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.androidAppVersion() } }
            .onSuccess { latest = it }
            .onFailure { error = it.message ?: "检查更新失败" }
        checking = false
    }

    LaunchedEffect(Unit) { fetchLatest() }

    fun startDownload(version: JSONObject) {
        val rawUrl = version.optString("apkUrl").trim()
        if (rawUrl.isBlank()) {
            downloadError = "暂未配置下载地址"
            return
        }
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + "/" + rawUrl.trimStart('/')
        }
        runCatching {
            val fileName = url.substringBefore('?').substringAfterLast('/').ifBlank {
                "app-release-${version.optString("versionName", "latest")}.apk"
            }
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("药房助手 ${version.optString("versionName")}")
                .setDescription("正在下载应用更新")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            downloadedUri = null
            downloadError = null
            downloadProgress = 0
            downloadId = downloadManager.enqueue(request)
        }.onFailure { downloadError = it.message ?: "无法开始下载" }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val state = withContext(Dispatchers.IO) {
                downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext Triple(-1, 0, null)
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val complete = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val progress = if (total > 0) ((complete * 100L) / total).toInt().coerceIn(0, 100) else 0
                    Triple(status, progress, downloadManager.getUriForDownloadedFile(id))
                }
            }
            downloadProgress = state.second
            when (state.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadedUri = state.third
                    downloadId = null
                    break
                }
                DownloadManager.STATUS_FAILED, -1 -> {
                    downloadError = "APK下载失败，请检查网络或下载地址"
                    downloadId = null
                    break
                }
            }
            delay(500)
        }
    }

    val currentCode = BuildConfig.VERSION_CODE
    val currentName = BuildConfig.VERSION_NAME
    val latestCode = latest?.optInt("versionCode", currentCode) ?: currentCode
    val hasUpdate = latestCode > currentCode
    val forceUpdate = latest?.optBoolean("forceUpdate", false) == true

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = PrimarySoft, shape = CardShape, modifier = Modifier.size(68.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Primary, modifier = Modifier.padding(17.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("药房助手", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("中药房管理平台", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("当前版本 $currentName ($currentCode)", color = Muted, fontSize = 13.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Primary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.size(7.dp))
                    Text("版本更新", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                when {
                    checking -> Text("正在检查最新版本...", color = Muted, fontSize = 13.sp)
                    error != null -> Text(error!!, color = Danger, fontSize = 13.sp)
                    latest != null && hasUpdate -> {
                        Text("发现新版本 ${latest!!.optString("versionName")}", color = Primary, fontWeight = FontWeight.SemiBold)
                        latest!!.optString("publishedAt").takeIf { it.isNotBlank() }?.let { Text("发布时间：$it", color = Muted, fontSize = 12.sp) }
                        val notes = latest!!.optJSONArray("releaseNotes")
                        if (notes != null && notes.length() > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("更新内容", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            (0 until notes.length()).forEach { Text("• ${notes.optString(it)}", color = Muted, fontSize = 12.sp) }
                        }
                        if (forceUpdate) Text("此版本为必需更新", color = Danger, fontSize = 12.sp)
                    }
                    latest != null -> Text("已是最新版本", color = Success, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (downloadId != null) {
                    LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = Primary)
                    Spacer(Modifier.height(5.dp))
                    Text("正在下载 $downloadProgress%", color = Muted, fontSize = 12.sp)
                } else if (downloadedUri != null) {
                    Button(onClick = { installDownloaded(context, downloadedUri!!) }, modifier = Modifier.fillMaxWidth(), shape = FieldShape) { Text("安装更新") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scope.launch { fetchLatest() } }, enabled = !checking, modifier = Modifier.weight(1f), shape = FieldShape) { Text("检查更新") }
                        if (hasUpdate) Button(onClick = { startDownload(latest!!) }, modifier = Modifier.weight(1f), shape = FieldShape) { Text(if (forceUpdate) "立即更新" else "下载更新") }
                    }
                }
                downloadError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 12.sp) }
            }
        }
    }
}

private fun installDownloaded(context: Context, uri: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        return
    }
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
