package com.tcm.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
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

private const val UPDATE_PREFS = "android_update_check"
private const val LAST_UPDATE_CHECK_AT = "last_update_check_at"
private const val CACHED_UPDATE = "cached_update"
private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

@Composable
internal fun AboutScreen(
    onUpdateAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember(context) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    val updatePrefs = remember(context) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
    }
    var latest by remember(updatePrefs) {
        mutableStateOf(updatePrefs.getString(CACHED_UPDATE, null)?.let { value ->
            runCatching { JSONObject(value) }.getOrNull()
        })
    }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var downloadTotalBytes by remember { mutableStateOf(0L) }
    var downloadedUri by remember { mutableStateOf<Uri?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadVersionName by remember { mutableStateOf("") }

    suspend fun fetchLatest(): JSONObject? {
        checking = true
        error = null
        val version = runCatching { withContext(Dispatchers.IO) { ApiClient.androidAppVersion() } }
            .onSuccess { result ->
                latest = result
                onUpdateAvailabilityChanged(result.optInt("versionCode", 0) > BuildConfig.VERSION_CODE)
                updatePrefs.edit()
                    .putLong(LAST_UPDATE_CHECK_AT, System.currentTimeMillis())
                    .putString(CACHED_UPDATE, result.toString())
                    .apply()
            }
            .onFailure { error = it.message ?: "检查更新失败" }
            .getOrNull()
        checking = false
        return version
    }

    LaunchedEffect(Unit) {
        val lastCheckedAt = updatePrefs.getLong(LAST_UPDATE_CHECK_AT, 0L)
        val shouldCheck = lastCheckedAt <= 0L ||
            System.currentTimeMillis() - lastCheckedAt >= UPDATE_CHECK_INTERVAL_MS
        if (shouldCheck) fetchLatest()
    }

    fun startDownload(version: JSONObject) {
        val rawUrl = version.displayField("apkUrl", "").trim()
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
            val versionCode = version.optInt("versionCode", 0).coerceAtLeast(0)
            val versionName = version.opt("versionName")?.toString()?.trim()
                .orEmpty()
                .ifBlank { "latest" }
                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                .ifBlank { "latest" }
            val cacheKey = version.displayField("sha256", "")
                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                .ifBlank { versionCode.toString() }
                .take(16)
            val separator = if (url.contains('?')) "&" else "?"
            val downloadUrl = "$url${separator}versionCode=$versionCode&cacheKey=${java.net.URLEncoder.encode(cacheKey, "UTF-8")}"
            val fileName = "app-release-v${versionCode}-${versionName}-${cacheKey}.apk"
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("药房助手更新 v$versionName")
                .setDescription("药房助手 v$versionName 下载完成")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            downloadedUri = null
            downloadError = null
            downloadVersionName = versionName
            downloadProgress = 0
            downloadedBytes = 0L
            downloadTotalBytes = version.optLong("size", 0L).coerceAtLeast(0L)
            downloadId = downloadManager.enqueue(request)
        }.onFailure { downloadError = it.message ?: "无法开始下载" }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val state = withContext(Dispatchers.IO) {
                downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext DownloadState(-1, 0L, 0L, 0, null)
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val complete = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val knownTotal = if (total > 0) total else downloadTotalBytes
                    val progress = if (knownTotal > 0) ((complete * 100L) / knownTotal).toInt().coerceIn(0, 100) else 0
                    DownloadState(status, complete.coerceAtLeast(0L), knownTotal, progress, downloadManager.getUriForDownloadedFile(id))
                }
            }
            downloadedBytes = state.downloadedBytes
            if (state.totalBytes > 0) downloadTotalBytes = state.totalBytes
            downloadProgress = state.progress
            when (state.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadProgress = 100
                    downloadedUri = state.uri
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
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
                        Text("发现新版本 ${latest!!.displayField("versionName")}", color = Primary, fontWeight = FontWeight.SemiBold)
                        latest!!.displayField("publishedAt", "").takeIf { it.isNotBlank() }?.let { Text("发布时间：$it", color = Muted, fontSize = 12.sp) }
                        val notes = latest!!.optJSONArray("releaseNotes")
                        if (notes != null && notes.length() > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("更新内容", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            (0 until notes.length()).forEach { Text("• ${displayText(notes.opt(it), "")}", color = Muted, fontSize = 12.sp) }
                        }
                        if (forceUpdate) Text("此版本为必需更新", color = Danger, fontSize = 12.sp)
                    }
                    latest != null -> Text("已是最新版本", color = Success, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (downloadId != null) {
                    if (downloadTotalBytes > 0L) {
                        // Use two clipped rectangles instead of the Material rounded indicator.
                        // Rounded end caps can expose the white track at the join and at the tail.
                        val progressShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(progressShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((downloadProgress / 100f).coerceIn(0f, 1f))
                                    .background(Primary),
                            )
                        }
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (downloadTotalBytes > 0L) {
                            "正在下载 $downloadProgress%（${formatDownloadSize(downloadedBytes)} / ${formatDownloadSize(downloadTotalBytes)}）"
                        } else {
                            "正在下载 ${formatDownloadSize(downloadedBytes)}"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                } else if (downloadedUri != null) {
                    Button(onClick = { installDownloaded(context, downloadedUri!!) }, modifier = Modifier.fillMaxWidth(), shape = FieldShape) { Text("安装版本 ${downloadVersionName.ifBlank { "更新" }}") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scope.launch { fetchLatest() } }, enabled = !checking, modifier = Modifier.weight(1f), shape = FieldShape) { Text("检查更新") }
                        if (hasUpdate) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        // Always use the current release metadata for the download task and notification.
                                        val version = fetchLatest() ?: return@launch
                                        if (version.optInt("versionCode", BuildConfig.VERSION_CODE) > BuildConfig.VERSION_CODE) {
                                            startDownload(version)
                                        }
                                    }
                                },
                                enabled = !checking,
                                modifier = Modifier.weight(1f),
                                shape = FieldShape,
                            ) {
                                Text(if (forceUpdate) "立即更新" else "下载更新")
                            }
                        }
                    }
                }
                downloadError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 12.sp) }
            }
        }
    }
}

private data class DownloadState(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Int,
    val uri: Uri?,
)

private fun formatDownloadSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(java.util.Locale.US, bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(java.util.Locale.US, bytes / 1024f)
    else -> "$bytes B"
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
