package com.tcm.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcm.admin.util.BsPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

    // Incremental update state
    var isPatchDownloading by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var synthesizeProgress by remember { mutableStateOf(0) }

    suspend fun fetchLatest(): JSONObject? {
        checking = true
        error = null
        val version = runCatching {
            withContext(Dispatchers.IO) {
                ApiClient.androidAppVersion(BuildConfig.VERSION_CODE)
            }
        }.onSuccess { result ->
            latest = result
            onUpdateAvailabilityChanged(result.optInt("versionCode", 0) > BuildConfig.VERSION_CODE)
            updatePrefs.edit()
                .putLong(LAST_UPDATE_CHECK_AT, System.currentTimeMillis())
                .putString(CACHED_UPDATE, result.toString())
                .apply()
        }.onFailure {
            error = it.message ?: "检查更新失败"
        }.getOrNull()
        checking = false
        return version
    }

    LaunchedEffect(Unit) {
        val lastCheckedAt = updatePrefs.getLong(LAST_UPDATE_CHECK_AT, 0L)
        val shouldCheck = lastCheckedAt <= 0L ||
            System.currentTimeMillis() - lastCheckedAt >= UPDATE_CHECK_INTERVAL_MS
        if (shouldCheck) fetchLatest()
    }

    fun startFullDownload(version: JSONObject) {
        val rawUrl = version.optString("fallbackApkUrl").ifBlank {
            version.displayField("apkUrl", "")
        }.trim()
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
            downloadTotalBytes = version.optLong("fallbackApkSize", 0L).takeIf { it > 0 }
                ?: version.optLong("size", 0L).coerceAtLeast(0L)
            downloadId = downloadManager.enqueue(request)
        }.onFailure { downloadError = it.message ?: "无法开始下载" }
    }

    fun startIncrementalUpdate(version: JSONObject) {
        val rawPatchUrl = version.displayField("patchUrl", "").trim()
        if (rawPatchUrl.isBlank()) {
            startFullDownload(version)
            return
        }
        val patchUrl = if (rawPatchUrl.startsWith("http://") || rawPatchUrl.startsWith("https://")) {
            rawPatchUrl
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + "/" + rawPatchUrl.trimStart('/')
        }
        val patchSha256 = version.displayField("patchSha256", "").lowercase()
        val targetApkSha256 = version.displayField("targetApkSha256", "").lowercase()
        val patchSize = version.optLong("patchSize", 0L).coerceAtLeast(0L)
        val versionName = version.optString("versionName", "latest")
        val versionCode = version.optInt("versionCode", 0)

        isPatchDownloading = true
        isSynthesizing = false
        synthesizeProgress = 0
        downloadError = null
        downloadedUri = null
        downloadVersionName = versionName
        downloadProgress = 0
        downloadedBytes = 0L
        downloadTotalBytes = patchSize

        scope.launch {
            try {
                val patchFile = File(context.cacheDir, "patch_v${BuildConfig.VERSION_CODE}_to_v${versionCode}.tmp")
                val synthesizedApk = File(context.cacheDir, "synthesized_v${versionCode}_${versionName}.apk")

                // 1. Download patch
                withContext(Dispatchers.IO) {
                    val conn = (URL(patchUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        requestMethod = "GET"
                        connect()
                    }
                    if (conn.responseCode !in 200..299) {
                        throw IOException("下载增量补丁失败，HTTP ${conn.responseCode}")
                    }
                    val totalLen = conn.contentLengthLong.takeIf { it > 0 } ?: patchSize
                    if (totalLen > 0) downloadTotalBytes = totalLen

                    conn.inputStream.use { input ->
                        FileOutputStream(patchFile).use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            var count = 0L
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                count += read
                                downloadedBytes = count
                                if (totalLen > 0) {
                                    downloadProgress = ((count * 100L) / totalLen).toInt().coerceIn(0, 100)
                                }
                            }
                        }
                    }
                }

                // 2. Synthesize
                isPatchDownloading = false
                isSynthesizing = true
                synthesizeProgress = 0

                withContext(Dispatchers.IO) {
                    if (patchSha256.isNotBlank()) {
                        val actualPatchSha256 = BsPatch.computeSha256(patchFile).lowercase()
                        if (actualPatchSha256 != patchSha256) {
                            throw IOException("增量补丁校验不通过 (SHA256 不匹配)")
                        }
                    }

                    val oldApk = File(context.applicationInfo.sourceDir)
                    if (!oldApk.exists()) {
                        throw IOException("无法访问当前应用源文件")
                    }

                    BsPatch.applyPatch(oldApk, synthesizedApk, patchFile) { prog ->
                        synthesizeProgress = prog
                    }

                    if (targetApkSha256.isNotBlank()) {
                        val actualNewSha256 = BsPatch.computeSha256(synthesizedApk).lowercase()
                        if (actualNewSha256 != targetApkSha256) {
                            throw IOException("合成新版本 APK 校验不通过 (SHA256 不匹配)")
                        }
                    }

                    patchFile.delete()
                }

                // 3. Success
                isSynthesizing = false
                downloadProgress = 100
                downloadedUri = Uri.fromFile(synthesizedApk)

            } catch (e: Exception) {
                // Fallback to full download
                isPatchDownloading = false
                isSynthesizing = false
                downloadError = "增量更新未成功（${e.message}），正在自动为您转为全量更新..."
                startFullDownload(version)
            }
        }
    }

    fun startUpdate(version: JSONObject) {
        val updateType = version.optString("updateType", "full")
        if (updateType == "incremental") {
            startIncrementalUpdate(version)
        } else {
            startFullDownload(version)
        }
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
    val isIncremental = latest?.optString("updateType") == "incremental"
    val patchSize = latest?.optLong("patchSize", 0L) ?: 0L

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("发现新版本 ${latest!!.displayField("versionName")}", color = Primary, fontWeight = FontWeight.SemiBold)
                            if (isIncremental) {
                                Spacer(Modifier.size(8.dp))
                                Surface(color = SuccessSoft, shape = RoundedCornerShape(4.dp)) {
                                    Text("增量更新（省流量）", color = Success, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        if (isIncremental && patchSize > 0) {
                            Text("补丁大小：${formatDownloadSize(patchSize)}（无需下载完整安装包）", color = Muted, fontSize = 12.sp)
                        }
                        latest!!.opt("publishedAt")?.let { publishedAt ->
                            serverDateTime(publishedAt, "").takeIf { it.isNotBlank() }?.let { Text("发布时间：$it", color = Muted, fontSize = 12.sp) }
                        }
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

                // Progress handling: Synthesizing -> Patch Downloading -> Full Downloading
                if (isSynthesizing) {
                    LinearProgressIndicator(
                        progress = { synthesizeProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("正在合成新版本安装包... $synthesizeProgress%", color = Muted, fontSize = 12.sp)
                } else if (isPatchDownloading) {
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
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "正在下载增量补丁 $downloadProgress%（${formatDownloadSize(downloadedBytes)} / ${formatDownloadSize(downloadTotalBytes)}）",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                } else if (downloadId != null) {
                    if (downloadTotalBytes > 0L) {
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
                            "正在下载完整安装包 $downloadProgress%（${formatDownloadSize(downloadedBytes)} / ${formatDownloadSize(downloadTotalBytes)}）"
                        } else {
                            "正在下载 ${formatDownloadSize(downloadedBytes)}"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                } else if (downloadedUri != null) {
                    Button(onClick = { installDownloaded(context, downloadedUri!!) }, modifier = Modifier.fillMaxWidth(), shape = FieldShape) {
                        Text("安装版本 ${downloadVersionName.ifBlank { "更新" }}")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scope.launch { fetchLatest() } }, enabled = !checking, modifier = Modifier.weight(1f), shape = FieldShape) {
                            Text("检查更新")
                        }
                        if (hasUpdate) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val version = fetchLatest() ?: return@launch
                                        if (version.optInt("versionCode", BuildConfig.VERSION_CODE) > BuildConfig.VERSION_CODE) {
                                            startUpdate(version)
                                        }
                                    }
                                },
                                enabled = !checking,
                                modifier = Modifier.weight(1f),
                                shape = FieldShape,
                            ) {
                                val btnText = when {
                                    forceUpdate && isIncremental -> "立即增量更新"
                                    forceUpdate -> "立即更新"
                                    isIncremental -> "增量更新 (${formatDownloadSize(patchSize)})"
                                    else -> "下载更新"
                                }
                                Text(btnText)
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
    val installUri = runCatching {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else uri
    }.getOrDefault(uri)

    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(installUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
