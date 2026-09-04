package com.tcm.admin.util

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Pure Kotlin/Java implementation of BSDIFF40 patch algorithm.
 * Compatible with standard bsdiff output without requiring Android NDK or CMake toolchain.
 */
object BsPatch {

    private const val HEADER_MAGIC = "BSDIFF40"
    private const val HEADER_SIZE = 32

    /**
     * Apply binary patch to [oldFile] and output to [newFile].
     *
     * @param oldFile The currently installed APK or base file
     * @param newFile The destination file for the synthesized APK
     * @param patchFile The bsdiff .patch file
     * @param onProgress Optional callback reporting progress 0..100
     */
    @Throws(IOException::class)
    fun applyPatch(
        oldFile: File,
        newFile: File,
        patchFile: File,
        onProgress: ((Int) -> Unit)? = null
    ) {
        if (!oldFile.exists() || !oldFile.canRead()) {
            throw IOException("Old file does not exist or cannot be read: ${oldFile.absolutePath}")
        }
        if (!patchFile.exists() || !patchFile.canRead()) {
            throw IOException("Patch file does not exist or cannot be read: ${patchFile.absolutePath}")
        }

        FileInputStream(patchFile).use { patchIn ->
            val header = ByteArray(HEADER_SIZE)
            readFully(patchIn, header, HEADER_SIZE)

            val magic = String(header, 0, 8, Charsets.US_ASCII)
            if (magic != HEADER_MAGIC) {
                throw IOException("Corrupt patch: invalid magic '$magic' (expected $HEADER_MAGIC)")
            }

            val bzCtrlLen = readOffT(header, 8)
            val bzDataLen = readOffT(header, 16)
            val newSize = readOffT(header, 24)

            if (bzCtrlLen < 0 || bzDataLen < 0 || newSize < 0) {
                throw IOException("Corrupt patch: negative lengths in header")
            }

            val ctrlBytes = ByteArray(bzCtrlLen.toInt())
            readFully(patchIn, ctrlBytes, ctrlBytes.size)

            val diffBytes = ByteArray(bzDataLen.toInt())
            readFully(patchIn, diffBytes, diffBytes.size)

            val extraBytes = patchIn.readBytes()

            RandomAccessFile(oldFile, "r").use { oldRaf ->
                BufferedOutputStream(FileOutputStream(newFile)).use { newOut ->
                    BZip2CompressorInputStream(ByteArrayInputStream(ctrlBytes)).use { ctrlStream ->
                        BZip2CompressorInputStream(ByteArrayInputStream(diffBytes)).use { diffStream ->
                            BZip2CompressorInputStream(ByteArrayInputStream(extraBytes)).use { extraStream ->
                                var newPos = 0L
                                val oldBuf = ByteArray(8192)
                                val diffBuf = ByteArray(8192)
                                val extraBuf = ByteArray(8192)

                                while (newPos < newSize) {
                                    val diffLen = readOffT(ctrlStream)
                                    val extraLen = readOffT(ctrlStream)
                                    val seekOld = readOffT(ctrlStream)

                                    if (diffLen < 0 || extraLen < 0) {
                                        throw IOException("Corrupt patch: negative control lengths")
                                    }
                                    if (newPos + diffLen + extraLen > newSize) {
                                        throw IOException("Corrupt patch: size exceeds expected newSize")
                                    }

                                    // 1. Add diff block to old file bytes
                                    var diffRemaining = diffLen
                                    while (diffRemaining > 0) {
                                        val toRead = minOf(diffRemaining, oldBuf.size.toLong()).toInt()
                                        val oldRead = oldRaf.read(oldBuf, 0, toRead)
                                        if (oldRead < toRead) {
                                            throw EOFException("Unexpected end of old APK while reading diff block")
                                        }

                                        readFully(diffStream, diffBuf, toRead)
                                        for (i in 0 until toRead) {
                                            diffBuf[i] = (oldBuf[i] + diffBuf[i]).toByte()
                                        }
                                        newOut.write(diffBuf, 0, toRead)
                                        diffRemaining -= toRead
                                    }
                                    newPos += diffLen

                                    // 2. Copy extra block
                                    var extraRemaining = extraLen
                                    while (extraRemaining > 0) {
                                        val toRead = minOf(extraRemaining, extraBuf.size.toLong()).toInt()
                                        readFully(extraStream, extraBuf, toRead)
                                        newOut.write(extraBuf, 0, toRead)
                                        extraRemaining -= toRead
                                    }
                                    newPos += extraLen

                                    // 3. Seek old file
                                    val currentOldPos = oldRaf.filePointer
                                    oldRaf.seek(currentOldPos + seekOld)

                                    if (newSize > 0) {
                                        val progress = ((newPos * 100) / newSize).toInt().coerceIn(0, 100)
                                        onProgress?.invoke(progress)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes the SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val count = stream.read(buffer, offset, length - offset)
            if (count < 0) throw EOFException("Unexpected end of stream (wanted $length, got $offset)")
            offset += count
        }
    }

    private fun readOffT(buffer: ByteArray, offset: Int): Long {
        var y = (buffer[offset + 7].toLong() and 0x7FL)
        y = (y shl 8) or (buffer[offset + 6].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 5].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 4].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 3].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 2].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 1].toLong() and 0xFFL)
        y = (y shl 8) or (buffer[offset + 0].toLong() and 0xFFL)
        return if ((buffer[offset + 7].toInt() and 0x80) != 0) -y else y
    }

    private fun readOffT(stream: InputStream): Long {
        val buf = ByteArray(8)
        readFully(stream, buf, 8)
        return readOffT(buf, 0)
    }
}
