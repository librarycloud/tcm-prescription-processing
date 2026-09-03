package com.tcm.admin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private fun scannerBoxRect(width: Float, height: Float): RectF {
    val boxSize = (width * 0.72f).coerceAtMost(height * 0.5f)
    val left = (width - boxSize) / 2f
    val top = (height - boxSize) / 2f - (height * 0.05f)
    return RectF(left, top, left + boxSize, top + boxSize)
}

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private var ocrEnabled = false
    @Volatile
    private var textRecognizer: TextRecognizer? = null
    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null
    private val ocrInFlight = AtomicBoolean(false)
    private lateinit var previewView: PreviewView

    @Volatile
    private var lastCandidate: String? = null
    @Volatile
    private var candidateHitCount: Int = 0
    private var lastLoggedOcrText: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocrEnabled = intent.getBooleanExtra(EXTRA_ENABLE_SKU_OCR, false)
        if (ocrEnabled) {
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val overlay = ScannerOverlayView(this, ocrEnabled)
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun triggerVibration() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val frameClosed = AtomicBoolean(false)
                fun closeFrame() {
                    if (frameClosed.compareAndSet(false, true)) proxy.close()
                }
                val rotation = proxy.imageInfo.rotationDegrees
                val image = InputImage.fromMediaImage(mediaImage, rotation)
                val isOcrActive = ocrEnabled && textRecognizer != null && !delivered.get()
                val tasksRemaining = AtomicInteger(if (isOcrActive) 2 else 1)
                fun taskFinished() {
                    if (tasksRemaining.decrementAndGet() == 0) closeFrame()
                }

                val isRotated = (rotation == 90 || rotation == 270)
                val imgWidth = if (isRotated) proxy.height.toFloat() else proxy.width.toFloat()
                val imgHeight = if (isRotated) proxy.width.toFloat() else proxy.height.toFloat()
                val viewWidth = previewView.width.toFloat()
                val viewHeight = previewView.height.toFloat()

                val imgScanBox = if (viewWidth > 0f && viewHeight > 0f) {
                    val scale = maxOf(viewWidth / imgWidth, viewHeight / imgHeight)
                    val scaledWidth = imgWidth * scale
                    val scaledHeight = imgHeight * scale
                    val dx = (scaledWidth - viewWidth) / 2f
                    val dy = (scaledHeight - viewHeight) / 2f
                    val viewScanBox = scannerBoxRect(viewWidth, viewHeight)
                    RectF(
                        (viewScanBox.left + dx) / scale,
                        (viewScanBox.top + dy) / scale,
                        (viewScanBox.right + dx) / scale,
                        (viewScanBox.bottom + dy) / scale
                    )
                } else {
                    scannerBoxRect(imgWidth, imgHeight)
                }

                // 1. Barcode scanner restricted to scanning frame
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val validBarcodes = barcodes.filter { !it.rawValue.isNullOrBlank() }
                        val inBox = validBarcodes.filter { b ->
                            val box = b.boundingBox
                            box != null && isInsideScanBox(RectF(box), imgScanBox)
                        }
                        if (inBox.isEmpty()) return@addOnSuccessListener
                        val targetBarcode = inBox.minByOrNull { b ->
                            val box = b.boundingBox ?: return@minByOrNull Float.MAX_VALUE
                            val cy = box.centerY().toFloat()
                            val cx = box.centerX().toFloat()
                            val dy = cy - imgScanBox.centerY()
                            val dx = cx - imgScanBox.centerX()
                            dx * dx + dy * dy
                        } ?: inBox.firstOrNull()

                        targetBarcode?.let { barcode ->
                            val value = barcode.rawValue
                            if (!value.isNullOrBlank()) {
                                handleBarcodeDetected(value, barcode.format)
                            }
                        }
                    }
                    .addOnCompleteListener { taskFinished() }

                // 2. Offline Latin/Digit OCR text recognition restricted to scanning frame
                if (isOcrActive) {
                    val recognizer = textRecognizer
                    if (recognizer != null && !delivered.get() && ocrInFlight.compareAndSet(false, true)) {
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val sku = extractSkuFromVisionText(visionText, imgScanBox)
                                if (BuildConfig.DEBUG) {
                                    val compactText = visionText.text.replace(Regex("\\s+"), " ").trim()
                                    if (compactText.isNotBlank() && compactText != lastLoggedOcrText) {
                                        val res = sku ?: "none"
                                        Log.d("ScannerOCR", "raw=$compactText; candidate=$res")
                                        lastLoggedOcrText = compactText
                                    }
                                }
                                if (sku != null) {
                                    handleCandidateDetected(sku)
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.w(TAG, "ML Kit recognition failed", error)
                            }
                            .addOnCompleteListener {
                                ocrInFlight.set(false)
                                taskFinished()
                            }
                    } else {
                        taskFinished()
                    }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleCandidateDetected(candidate: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { handleCandidateDetected(candidate) }
            return
        }
        if (delivered.get()) return
        // 2 consecutive matching frames confirmation to prevent transient OCR flicker
        val requiredHits = 2
        if (candidate == lastCandidate) {
            candidateHitCount++
            if (candidateHitCount >= requiredHits) {
                deliverResult(candidate)
            }
        } else {
            lastCandidate = candidate
            candidateHitCount = 1
        }
    }

    private fun handleBarcodeDetected(value: String, format: Int) {
        if (format == Barcode.FORMAT_QR_CODE) {
            deliverResult(value)
        } else {
            handleCandidateDetected(value)
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        scanner.close()
        textRecognizer?.close()
        textRecognizer = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun deliverResult(value: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { deliverResult(value) }
            return
        }
        if (delivered.compareAndSet(false, true)) {
            triggerVibration()
            setResult(RESULT_OK, Intent().putExtra(SCAN_RESULT, value))
            finish()
        }
    }

    private class ScannerOverlayView(context: Context, private val ocrEnabled: Boolean) : View(context) {
        private val maskPaint = Paint().apply {
            color = 0x88000000.toInt()
        }
        private val transparentPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val cornerPaint = Paint().apply {
            color = 0xFF10B981.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val aimPaint = Paint().apply {
            color = 0x4410B981.toInt()
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        private val textPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 38f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            val boxRect = scannerBoxRect(width, height)
            val left = boxRect.left
            val top = boxRect.top
            val right = boxRect.right
            val bottom = boxRect.bottom
            val cornerLength = 40f

            // Draw full semi-transparent mask
            canvas.drawRect(0f, 0f, width, height, maskPaint)

            // Clear center framing box with rounded corners
            canvas.drawRoundRect(boxRect, 16f, 16f, transparentPaint)

            // Draw center alignment guide
            val centerY = (top + bottom) / 2f
            canvas.drawLine(left + 24f, centerY, right - 24f, centerY, aimPaint)

            // Draw 4 corner highlights
            canvas.drawLine(left, top + cornerLength, left, top, cornerPaint)
            canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)
            canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)
            canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)
            canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)

            // Text hint below frame
            val hint = if (ocrEnabled) {
                "将条形码、二维码或 SKU 数字对准框内"
            } else {
                "将条形码或二维码放入框内即可自动扫描"
            }
            canvas.drawText(hint, width / 2f, bottom + 70f, textPaint)
        }
    }

    companion object {
        private const val TAG = "ScannerActivity"
        const val SCAN_RESULT = "scan_result"
        const val EXTRA_ENABLE_SKU_OCR = "enable_sku_ocr"

        private val skuLabelRegex = Regex(
            """(?i)(?:^|[^a-zA-Z0-9])(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0)(?:[^a-zA-Z0-9]|$)"""
        )
        private val candidate9Pattern = Regex("""(?<!\d)[0-9OolILsSbB|]{9}(?!\d)""")
        private val standalone9Pattern = Regex("""(?<!\d)[0-9]{9}(?!\d)""")
        private val excludeLinePattern = Regex(
            """(?i)(?:手机|电话|虚拟号|备用|订单|UPC|条码|时间|日期|运单号)"""
        )
        private val multilineSkuRegex = Regex(
            """(?i)(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0)[\s:：#\-_/|]*([0-9OolILsSbB|]{9})(?!\d)"""
        )

        private fun isInsideScanBox(itemRect: RectF, scanBox: RectF): Boolean {
            val cx = itemRect.centerX()
            val cy = itemRect.centerY()
            return cx >= scanBox.left && cx <= scanBox.right &&
                   cy >= scanBox.top && cy <= scanBox.bottom
        }

        private fun cleanDigits(token: String): String {
            return token.map {
                when (it) {
                    'O', 'o' -> '0'
                    'I', 'l', '|' -> '1'
                    'S', 's' -> '5'
                    'B', 'b' -> '8'
                    else -> it
                }
            }.filter(Char::isDigit).joinToString("")
        }

        private fun normalizeOcrText(text: String): String {
            return text.map { char ->
                when (char) {
                    '\u3000', '\u00A0' -> ' '
                    in '０'..'９' -> ('0'.code + (char.code - '０'.code)).toChar()
                    in 'Ａ'..'Ｚ' -> ('A'.code + (char.code - 'Ａ'.code)).toChar()
                    in 'ａ'..'ｚ' -> ('a'.code + (char.code - 'ａ'.code)).toChar()
                    else -> char
                }
            }.joinToString("")
        }

        fun extractSkuFromVisionText(visionText: Text, scanBox: RectF?): String? {
            if (visionText.textBlocks.isEmpty()) return null

            data class PositionedLine(
                val text: String,
                val top: Float,
                val centerY: Float,
                val isExcluded: Boolean
            )

            val lines = mutableListOf<PositionedLine>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isEmpty()) continue
                    val box = line.boundingBox
                    val rect = if (box != null) RectF(box) else null
                    if (scanBox != null) {
                        if (rect == null || !isInsideScanBox(rect, scanBox)) {
                            continue
                        }
                    }
                    val normalized = normalizeOcrText(lineText)
                    lines.add(
                        PositionedLine(
                            text = normalized,
                            top = rect?.top ?: 0f,
                            centerY = rect?.centerY() ?: 0f,
                            isExcluded = excludeLinePattern.containsMatchIn(normalized)
                        )
                    )
                }
            }

            if (lines.isEmpty()) {
                return null
            }

            lines.sortBy { it.top }
            val boxCenterY = scanBox?.centerY() ?: (lines.map { it.centerY }.average().toFloat())

            data class CandidateResult(
                val sku: String,
                val distanceToCenter: Float
            )
            val candidates = mutableListOf<CandidateResult>()

            // Strategy 1: Lines with SKU / 5KU label (same line or next 1-2 lines)
            for (i in lines.indices) {
                val line = lines[i]
                if (skuLabelRegex.containsMatchIn(line.text)) {
                    for (match in candidate9Pattern.findAll(line.text)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) {
                            candidates.add(CandidateResult(cleaned, kotlin.math.abs(line.centerY - boxCenterY)))
                        }
                    }
                    for (offset in 1..2) {
                        val nextLine = lines.getOrNull(i + offset) ?: break
                        if (nextLine.isExcluded) continue
                        for (match in candidate9Pattern.findAll(nextLine.text)) {
                            val cleaned = cleanDigits(match.value)
                            if (cleaned.length == 9) {
                                candidates.add(CandidateResult(cleaned, kotlin.math.abs(nextLine.centerY - boxCenterY)))
                            }
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                candidates.sortBy { it.distanceToCenter }
                return candidates.first().sku
            }

            // Strategy 2: Multiline regex
            val combinedText = lines.joinToString("\n") { it.text }
            multilineSkuRegex.find(combinedText)?.let { match ->
                val cleaned = cleanDigits(match.groupValues[1])
                if (cleaned.length == 9) return cleaned
            }

            // Strategy 3: SKU label somewhere in box + candidate 9 on non-excluded lines
            val hasSkuLabel = skuLabelRegex.containsMatchIn(combinedText)
            if (hasSkuLabel) {
                for (line in lines) {
                    if (line.isExcluded) continue
                    for (match in candidate9Pattern.findAll(line.text)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) {
                            candidates.add(CandidateResult(cleaned, kotlin.math.abs(line.centerY - boxCenterY)))
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    candidates.sortBy { it.distanceToCenter }
                    return candidates.first().sku
                }
            }

            // Strategy 4: Fallback for standalone 9 pure digits on non-excluded lines in box
            for (line in lines) {
                if (line.isExcluded) continue
                for (match in standalone9Pattern.findAll(line.text)) {
                    candidates.add(CandidateResult(match.value, kotlin.math.abs(line.centerY - boxCenterY)))
                }
            }
            if (candidates.isNotEmpty()) {
                candidates.sortBy { it.distanceToCenter }
                return candidates.first().sku
            }

            return null
        }

        fun extractSku(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val normalized = normalizeOcrText(rawText)
            val lines = normalized.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

            // Strategy 1: Check lines with SKU / 5KU labels (same line or next 1-2 lines)
            for (i in lines.indices) {
                val line = lines[i]
                if (skuLabelRegex.containsMatchIn(line)) {
                    for (match in candidate9Pattern.findAll(line)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) return cleaned
                    }
                    for (offset in 1..2) {
                        val nextLine = lines.getOrNull(i + offset) ?: break
                        if (excludeLinePattern.containsMatchIn(nextLine)) continue
                        for (match in candidate9Pattern.findAll(nextLine)) {
                            val cleaned = cleanDigits(match.value)
                            if (cleaned.length == 9) return cleaned
                        }
                    }
                }
            }

            // Strategy 2: Multiline regex with SKU label
            multilineSkuRegex.find(normalized)?.let { match ->
                val cleaned = cleanDigits(match.groupValues[1])
                if (cleaned.length == 9) return cleaned
            }

            // Strategy 3: Multi-column OCR - if SKU label exists anywhere in the text, match any 9-digit candidate on non-excluded lines
            val hasSkuLabel = skuLabelRegex.containsMatchIn(normalized)
            if (hasSkuLabel) {
                for (line in lines) {
                    if (excludeLinePattern.containsMatchIn(line)) continue
                    for (match in candidate9Pattern.findAll(line)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) return cleaned
                    }
                }
            }

            // Strategy 4: Fallback for standalone 9 pure digits (strictly digits without confusable substitutions) on non-excluded lines
            for (line in lines) {
                if (excludeLinePattern.containsMatchIn(line)) continue
                for (match in standalone9Pattern.findAll(line)) {
                    return match.value
                }
            }

            return null
        }
    }
}
