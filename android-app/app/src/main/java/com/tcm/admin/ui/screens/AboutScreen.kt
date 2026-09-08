package com.tcm.admin

import android.app.DownloadManager
import com.tcm.admin.util.DeviceUtils
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.tcm.admin.util.CacheManager
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
    var downloadFileName by remember { mutableStateOf("") }

    // Incremental update state
    var isPatchDownloading by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var synthesizeProgress by remember { mutableStateOf(0) }

    suspend fun fetchLatest(): JSONObject? {
        checking = true
        error = null
        val version = runCatching {
            withContext(Dispatchers.IO) {
                val deviceId = DeviceUtils.getDeviceId(context)
                ApiClient.androidAppVersion(
                    versionCode = BuildConfig.VERSION_CODE,
                    deviceId = deviceId,
                    context = context,
                )
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
            version.optString("fallbackUrl").ifBlank {
                version.displayField("apkUrl", "").ifBlank {
                    version.optString("downloadUrl")
                }
            }
        }.trim()
        if (rawUrl.isBlank()) {
            downloadError = "暂未配置下载地址"
            return
        }
        val updateBase = BuildConfig.UPDATE_BASE_URL.trimEnd('/').ifBlank { BuildConfig.API_BASE_URL.trimEnd('/') }
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            updateBase + "/" + rawUrl.trimStart('/')
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
            val fileName = "update_pending.apk"
            downloadFileName = fileName
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (destDir != null) {
                destDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".apk", ignoreCase = true) || file.name.endsWith(".tmp", ignoreCase = true)) {
                        file.delete()
                    }
                }
            }
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("药房助手更新 v$versionName")
                .setDescription("药房助手 v$versionName 下载完成")
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            downloadedUri = null
            downloadError = null
            downloadVersionName = versionName
            downloadProgress = 0
            downloadedBytes = 0L
            downloadTotalBytes = version.optLong("fallbackApkSize", 0L).takeIf { it > 0 }
                ?: version.optLong("fallbackSize", 0L).takeIf { it > 0 }
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
        val updateBase = BuildConfig.UPDATE_BASE_URL.trimEnd('/').ifBlank { BuildConfig.API_BASE_URL.trimEnd('/') }
        val patchUrl = if (rawPatchUrl.startsWith("http://") || rawPatchUrl.startsWith("https://")) {
            rawPatchUrl
        } else {
            updateBase + "/" + rawPatchUrl.trimStart('/')
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
                val patchFile = File(context.cacheDir, "update_patch.tmp")
                val synthesizedApk = File(context.cacheDir, "update_pending.apk")
                runCatching {
                    if (patchFile.exists()) patchFile.delete()
                    if (synthesizedApk.exists()) synthesizedApk.delete()
                }

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
                runCatching {
                    File(context.cacheDir, "update_patch.tmp").delete()
                    File(context.cacheDir, "update_pending.apk").delete()
                }
                // Fallback to full download
                isPatchDownloading = false
                isSynthesizing = false
                downloadError = "增量更新未成功（${e.message}），正在自动为您转为全量更新..."
                startFullDownload(version)
            }
        }
    }

    fun startUpdate(version: JSONObject) {
        CacheManager.cleanObsoleteApksAndPatches(context)
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
                    val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = if (destDir != null && downloadFileName.isNotBlank()) File(destDir, downloadFileName) else null
                    downloadedUri = if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                        runCatching {
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
                        }.getOrDefault(state.uri)
                    } else {
                        state.uri
                    }
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
    val fullApkSize = latest?.optLong("fallbackApkSize", 0L).takeIf { (it ?: 0L) > 0L }
        ?: latest?.optLong("fallbackSize", 0L).takeIf { (it ?: 0L) > 0L }
        ?: latest?.optLong("size", 0L) ?: 0L

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                        } else if (fullApkSize > 0) {
                            Text("安装包大小：${formatDownloadSize(fullApkSize)}", color = Muted, fontSize = 12.sp)
                        }
                        latest!!.opt("publishedAt")?.let { publishedAt ->
                            serverDateTime(publishedAt, "").takeIf { it.isNotBlank() }?.let { Text("发布时间：$it", color = Muted, fontSize = 12.sp) }
                        }
                        if (forceUpdate) Text("此版本为必需更新", color = Danger, fontSize = 12.sp)
                    }
                    latest != null -> Text("已是最新版本", color = Success, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))

                // Progress handling: Synthesizing -> Patch Downloading -> Full Downloading
                if (isSynthesizing) {
                    LinearProgressIndicator(
                        progress = { (synthesizeProgress / 100f).coerceIn(0f, 1f) },
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { fetchLatest() } },
                            enabled = !checking,
                            modifier = Modifier.fillMaxWidth(),
                            shape = FieldShape,
                        ) {
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
                                modifier = Modifier.fillMaxWidth(),
                                shape = FieldShape,
                            ) {
                                val btnText = when {
                                    forceUpdate && isIncremental -> "立即增量更新"
                                    forceUpdate -> "立即更新"
                                    isIncremental -> "增量更新 (${formatDownloadSize(patchSize)})"
                                    fullApkSize > 0L -> "下载更新 (${formatDownloadSize(fullApkSize)})"
                                    else -> "下载更新"
                                }
                                Text(btnText, maxLines = 1)
                            }
                        }
                    }
                }
                downloadError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 12.sp) }

                // 更新说明：放置在更新按钮下方
                val notes = latest?.optJSONArray("releaseNotes")
                val notesList = remember(notes) {
                    val list = mutableListOf<String>()
                    if (notes != null) {
                        for (i in 0 until notes.length()) {
                            val item = displayText(notes.opt(i), "").trim()
                            if (item.isNotBlank()) list.add(item)
                        }
                    }
                    list
                }
                val historyNotes = latest?.optJSONArray("historyReleaseNotes")
                val showNotesSection = latest != null && (hasUpdate || notesList.isNotEmpty() || (historyNotes != null && historyNotes.length() > 0))

                if (showNotesSection) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.8.dp,
                    )
                    Spacer(Modifier.height(14.dp))

                    ReleaseNotesSection(
                        latestJson = latest,
                        notesList = notesList,
                        targetVersion = latest?.optString("versionName", "") ?: "",
                        hasUpdate = hasUpdate,
                    )
                }
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

private enum class ReleaseTagType {
    NEW, OPTIMIZE, FIX, GENERAL
}

private data class ReleaseEntry(
    val tag: String?,
    val tagType: ReleaseTagType,
    val content: String,
)

private data class VersionReleaseSection(
    val version: String,
    val isLatest: Boolean,
    val entries: List<ReleaseEntry>,
)

private fun parseReleaseNotesToSections(latestJson: JSONObject?, rawList: List<String>, targetVersion: String): List<VersionReleaseSection> {
    val historyNotes = latestJson?.optJSONArray("historyReleaseNotes")
    if (historyNotes != null && historyNotes.length() > 0) {
        val sections = mutableListOf<VersionReleaseSection>()
        for (i in 0 until historyNotes.length()) {
            val obj = historyNotes.optJSONObject(i) ?: continue
            val vName = obj.optString("versionName", "").ifBlank { obj.optInt("versionCode").toString() }
            val rawNotes = obj.optJSONArray("releaseNotes")
            val entries = mutableListOf<ReleaseEntry>()
            if (rawNotes != null) {
                for (j in 0 until rawNotes.length()) {
                    val line = rawNotes.optString(j, "").trim()
                    if (line.isNotBlank()) {
                        val cleaned = line.replace(Regex("""^[·•\-\*●◆▪▫\s\d\.]+"""), "").trim()
                        val (tag, tagType, content) = parseEntryTag(cleaned)
                        entries.add(ReleaseEntry(tag, tagType, content))
                    }
                }
            }
            if (entries.isNotEmpty()) {
                val versionName = if (vName.startsWith("v", ignoreCase = true)) vName else "v$vName"
                sections.add(
                    VersionReleaseSection(
                        version = versionName,
                        isLatest = sections.isEmpty(),
                        entries = entries,
                    )
                )
            }
        }
        if (sections.isNotEmpty()) return sections
    }

    val sections = mutableListOf<VersionReleaseSection>()
    var currentVersion = ""
    var currentEntries = mutableListOf<ReleaseEntry>()

    val allLines = rawList.flatMap { item ->
        item.lines().map { it.trim() }.filter { it.isNotBlank() }
    }

    val versionHeaderRegex = Regex("""^[【\[#\s]*(?:v|V|版本)?\s*(\d+\.\d+(?:\.\d+)?(?:-[A-Za-z0-9.]+)?)[】\]:\s]*$""")

    for (line in allLines) {
        val versionMatch = versionHeaderRegex.find(line)
        val isHeader = versionMatch != null ||
                (line.startsWith("【") && (line.contains("v", ignoreCase = true) || line.contains("."))) ||
                (line.startsWith("[") && (line.contains("v", ignoreCase = true) || line.contains(".")))

        if (isHeader) {
            if (currentVersion.isNotBlank() || currentEntries.isNotEmpty()) {
                val vName = currentVersion.ifBlank { "v${targetVersion.ifBlank { "最新" }}" }
                sections.add(
                    VersionReleaseSection(
                        version = vName,
                        isLatest = sections.isEmpty(),
                        entries = currentEntries.toList(),
                    )
                )
                currentEntries = mutableListOf()
            }
            currentVersion = if (versionMatch != null) {
                "v" + versionMatch.groupValues[1].removePrefix("v").removePrefix("V")
            } else {
                val clean = line.replace(Regex("""[【】\[\]#]"""), "").trim()
                if (!clean.startsWith("v", ignoreCase = true) && clean.firstOrNull()?.isDigit() == true) {
                    "v$clean"
                } else {
                    clean
                }
            }
        } else {
            val cleaned = line.replace(Regex("""^[·•\-\*●◆▪▫\s\d\.]+"""), "").trim()
            if (cleaned.isNotBlank()) {
                val (tag, tagType, content) = parseEntryTag(cleaned)
                currentEntries.add(ReleaseEntry(tag, tagType, content))
            }
        }
    }

    if (currentVersion.isNotBlank() || currentEntries.isNotEmpty()) {
        val vName = currentVersion.ifBlank { "v${targetVersion.ifBlank { "最新" }}" }
        sections.add(
            VersionReleaseSection(
                version = vName,
                isLatest = sections.isEmpty(),
                entries = currentEntries.toList(),
            )
        )
    }

    if (sections.isEmpty() && allLines.isNotEmpty()) {
        val entries = allLines.map { line ->
            val cleaned = line.replace(Regex("""^[·•\-\*●◆▪▫\s\d\.]+"""), "").trim()
            val (tag, tagType, content) = parseEntryTag(cleaned)
            ReleaseEntry(tag, tagType, content)
        }
        sections.add(
            VersionReleaseSection(
                version = if (targetVersion.isNotBlank()) "v$targetVersion" else "v${BuildConfig.VERSION_NAME}",
                isLatest = true,
                entries = entries,
            )
        )
    }

    return sections
}

private fun parseEntryTag(text: String): Triple<String?, ReleaseTagType, String> {
    val trimmed = text.trim()
    val knownTags = listOf(
        Pair("优化", ReleaseTagType.OPTIMIZE),
        Pair("新增", ReleaseTagType.NEW),
        Pair("修复", ReleaseTagType.FIX),
        Pair("改进", ReleaseTagType.OPTIMIZE),
        Pair("重构", ReleaseTagType.OPTIMIZE),
        Pair("补充", ReleaseTagType.GENERAL),
        Pair("支持", ReleaseTagType.NEW),
        Pair("升级", ReleaseTagType.NEW),
        Pair("提升", ReleaseTagType.OPTIMIZE),
    )

    for ((tagName, tagType) in knownTags) {
        if (trimmed.startsWith("【$tagName】") || trimmed.startsWith("[$tagName]")) {
            val content = trimmed.substringAfter("】").substringAfter("]").trimStart('：', ':', ' ')
            return Triple(tagName, tagType, content.ifBlank { trimmed })
        }
        if (trimmed.startsWith(tagName)) {
            val after = trimmed.removePrefix(tagName).trimStart('：', ':', ' ')
            if (after.isNotBlank()) {
                return Triple(tagName, tagType, after)
            }
        }
    }
    return Triple(null, ReleaseTagType.GENERAL, trimmed)
}

@Composable
private fun ReleaseNotesSection(
    latestJson: JSONObject?,
    notesList: List<String>,
    targetVersion: String,
    hasUpdate: Boolean,
) {
    val sections = remember(latestJson, notesList, targetVersion) {
        parseReleaseNotesToSections(latestJson, notesList, targetVersion)
    }
    var showHistory by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (hasUpdate) "更新内容" else "版本动态",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Ink,
            )
            Spacer(Modifier.weight(1f))
            if (sections.size > 1) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = "共 ${sections.size} 个版本",
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (sections.isNotEmpty()) {
            val latestSection = sections.first()
            val historySections = sections.drop(1)

            // 1. Latest Version Featured Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.28f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimarySoft,
                        ) {
                            Text(
                                text = latestSection.version,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasUpdate) "本次更新" else "当前最新",
                            color = Success,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        latestSection.entries.forEach { entry ->
                            ReleaseEntryRow(entry)
                        }
                    }
                }
            }

            // 2. Historical Versions Expandable Timeline
            if (historySections.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showHistory = !showHistory },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (showHistory) "收起历史更新日志" else "查看历史更新日志（${historySections.size} 个版本）",
                            fontSize = 12.5.sp,
                            color = Muted,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (showHistory) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        historySections.forEachIndexed { index, histSection ->
                            Column {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = histSection.version,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Ink,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Column(
                                    modifier = Modifier.padding(start = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    histSection.entries.forEach { entry ->
                                        ReleaseEntryRow(entry)
                                    }
                                }
                            }
                            if (index < historySections.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else if (hasUpdate) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Text(
                    text = "本次更新包含功能优化与常规稳定性提升。",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleaseEntryRow(entry: ReleaseEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        if (entry.tag != null) {
            val (tagBg, tagText) = when (entry.tagType) {
                ReleaseTagType.NEW -> Pair(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF059669))
                ReleaseTagType.OPTIMIZE -> Pair(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706))
                ReleaseTagType.FIX -> Pair(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626))
                ReleaseTagType.GENERAL -> Pair(PrimarySoft, Primary)
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = tagBg,
                modifier = Modifier.padding(top = 1.5.dp),
            ) {
                Text(
                    text = entry.tag,
                    color = tagText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp, end = 8.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Primary),
            )
        }
        Text(
            text = entry.content,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
