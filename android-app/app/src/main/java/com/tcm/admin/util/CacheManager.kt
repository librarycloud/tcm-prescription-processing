package com.tcm.admin.util

import android.content.Context
import android.os.Environment
import com.tcm.admin.ApiClient
import java.io.File
import java.util.Locale

object CacheManager {

    /**
     * Calculates the total size of all app caches in bytes.
     * Includes:
     * - context.cacheDir (internal cache, response cache, synthesized APKs, patches)
     * - context.externalCacheDir (external cache)
     * - Update APKs/tmp files in external downloads directory
     * - Photo caches in cacheDir and legacy filesDir/processing-photos
     */
    fun calculateCacheSizeBytes(context: Context): Long {
        var total = 0L
        runCatching {
            total += getDirectorySize(context.cacheDir)
            context.externalCacheDir?.let { total += getDirectorySize(it) }
            context.codeCacheDir?.let { total += getDirectorySize(it) }

            // Downloaded update APKs / patches in external downloads
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    val name = file.name.lowercase(Locale.US)
                    if (name.endsWith(".apk") || name.endsWith(".tmp") || name.endsWith(".patch")) {
                        total += file.length()
                    }
                }
            }

            // Legacy processing photos in filesDir
            val legacyPhotos = File(context.filesDir, "processing-photos")
            if (legacyPhotos.exists()) {
                total += getDirectorySize(legacyPhotos)
            }
        }
        return total
    }

    /**
     * Formats bytes to human-readable text (e.g. "312.4 MB", "1.2 MB", "0 B").
     */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(Locale.US, bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.0f KB".format(Locale.US, bytes / 1024f)
        else -> "$bytes B"
    }

    /**
     * Automatically cleans up obsolete installation packages (.apk) and temporary patch files (.tmp)
     * from previous updates. Safe to call on app startup and before starting updates.
     */
    fun cleanObsoleteApksAndPatches(context: Context): Long {
        var freedBytes = 0L
        runCatching {
            // 1. Clean cacheDir for APKs, patches, and temp files
            context.cacheDir.listFiles()?.forEach { file ->
                val name = file.name.lowercase(Locale.US)
                if (name.endsWith(".apk") || name.endsWith(".tmp") || name.endsWith(".patch") ||
                    name.startsWith("patch_") || name.startsWith("synthesized_")) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }

            // 2. Clean externalCacheDir for APKs and temp files
            context.externalCacheDir?.listFiles()?.forEach { file ->
                val name = file.name.lowercase(Locale.US)
                if (name.endsWith(".apk") || name.endsWith(".tmp") || name.endsWith(".patch")) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }

            // 3. Clean external downloads directory for update APKs from past versions
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { file ->
                val name = file.name.lowercase(Locale.US)
                if (name.endsWith(".apk") || name.endsWith(".tmp") || name.endsWith(".patch")) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }

            // 4. Clean legacy processing-photos directory in filesDir if any
            val legacyPhotos = File(context.filesDir, "processing-photos")
            if (legacyPhotos.exists()) {
                val len = getDirectorySize(legacyPhotos)
                if (deleteRecursively(legacyPhotos)) freedBytes += len
            }
        }
        return freedBytes
    }

    /**
     * Clears all app caches completely without affecting user login status or saved preferences.
     * Returns the number of freed bytes.
     */
    fun clearAllCache(context: Context): Long {
        val before = calculateCacheSizeBytes(context)
        runCatching {
            // 1. Clear API response cache
            ApiClient.clearResponseCache(context)

            // 2. Clear photo cache (both in cacheDir and legacy filesDir)
            ApiClient.clearProcessingPhotoCache(context)
            val legacyPhotos = File(context.filesDir, "processing-photos")
            if (legacyPhotos.exists()) {
                deleteRecursively(legacyPhotos)
            }

            // 3. Clear all files inside cacheDir
            context.cacheDir.listFiles()?.forEach { file ->
                deleteRecursively(file)
            }

            // 4. Clear all files inside externalCacheDir
            context.externalCacheDir?.listFiles()?.forEach { file ->
                deleteRecursively(file)
            }

            // 5. Clear update APKs in external files downloads dir
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { file ->
                val name = file.name.lowercase(Locale.US)
                if (name.endsWith(".apk") || name.endsWith(".tmp") || name.endsWith(".patch")) {
                    file.delete()
                }
            }
        }
        val after = calculateCacheSizeBytes(context)
        return (before - after).coerceAtLeast(0L)
    }

    private fun getDirectorySize(fileOrDir: File?): Long {
        if (fileOrDir == null || !fileOrDir.exists()) return 0L
        if (fileOrDir.isFile) return fileOrDir.length()
        var size = 0L
        fileOrDir.listFiles()?.forEach { child ->
            size += if (child.isDirectory) getDirectorySize(child) else child.length()
        }
        return size
    }

    private fun deleteRecursively(fileOrDir: File?): Boolean {
        if (fileOrDir == null || !fileOrDir.exists()) return true
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return fileOrDir.delete()
    }
}
