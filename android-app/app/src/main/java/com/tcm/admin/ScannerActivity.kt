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
import android.util.TypedValue
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
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
            textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
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
                if (mediaImage == null || delivered.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                val isOcrActive = ocrEnabled && !delivered.get()
                var pendingTasks = if (isOcrActive) 2 else 1
                val taskFinished = {
                    if (--pendingTasks <= 0) {
                        proxy.close()
                    }
                }

                val rot = proxy.imageInfo.rotationDegrees
                val imgWidth = if (rot == 90 || rot == 270) image.height.toFloat() else image.width.toFloat()
                val imgHeight = if (rot == 90 || rot == 270) image.width.toFloat() else image.height.toFloat()
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

                // 1. Barcode scanner
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val validBarcodes = barcodes.filter { !it.rawValue.isNullOrBlank() }
                        if (validBarcodes.isEmpty()) return@addOnSuccessListener

                        val targetBarcode = if (ocrEnabled) {
                            // In Inventory & TopBar SKU scan mode: strictly restrict to scanning frame
                            val inBox = validBarcodes.filter { b ->
                                val box = b.boundingBox
                                box != null && isInsideScanBox(RectF(box), imgScanBox)
                            }
                            if (inBox.isEmpty()) return@addOnSuccessListener
                            inBox.minByOrNull { b ->
                                val box = b.boundingBox ?: return@minByOrNull Float.MAX_VALUE
                                val cy = box.centerY().toFloat()
                                val cx = box.centerX().toFloat()
                                val dy = cy - imgScanBox.centerY()
                                val dx = cx - imgScanBox.centerX()
                                dx * dx + dy * dy
                            } ?: inBox.firstOrNull()
                        } else {
                            // General scan mode (packages, processing, stocktaking, etc.): original full-screen fast detection
                            validBarcodes.firstOrNull()
                        }

                        targetBarcode?.let { barcode ->
                            val value = barcode.rawValue
                            if (!value.isNullOrBlank()) {
                                handleBarcodeDetected(value, barcode.format)
                            }
                        }
                    }
                    .addOnCompleteListener { taskFinished() }

                // 2. Offline Chinese + Latin/Digit OCR text recognition restricted to scanning frame
                if (isOcrActive) {
                    val recognizer = textRecognizer
                    if (recognizer != null && !delivered.get() && ocrInFlight.compareAndSet(false, true)) {
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val candidate = extractSkuFromVisionText(visionText, imgScanBox)
                                if (BuildConfig.DEBUG) {
                                    val compactText = visionText.text.replace(Regex("\\s+"), " ").trim()
                                    if (compactText.isNotBlank() && compactText != lastLoggedOcrText) {
                                        val res = candidate ?: "none"
                                        Log.d("ScannerOCR", "raw=$compactText; candidate=$res")
                                        lastLoggedOcrText = compactText
                                    }
                                }
                                if (candidate != null) {
                                    handleCandidateDetected(candidate)
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
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun deliverResult(resultText: String) {
        if (!delivered.compareAndSet(false, true)) return
        triggerVibration()
        val data = Intent().putExtra(SCAN_RESULT, resultText)
        setResult(RESULT_OK, data)
        finish()
    }

    private class ScannerOverlayView(context: Context, private val ocrEnabled: Boolean) : View(context) {
        private val maskPaint = Paint().apply {
            color = 0x88000000.toInt()
            style = Paint.Style.FILL
        }
        private val transparentPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val framePaint = Paint().apply {
            color = 0xFF4CAF50.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f * context.resources.displayMetrics.density
            isAntiAlias = true
        }
        private val cornerPaint = Paint().apply {
            color = 0xFF00E676.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f * context.resources.displayMetrics.density
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, 0xAA000000.toInt())
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val box = scannerBoxRect(width.toFloat(), height.toFloat())
            val left = box.left
            val top = box.top
            val right = box.right
            val bottom = box.bottom
            val cornerLength = 24f * resources.displayMetrics.density

            // Dark semi-transparent background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

            // Transparent hole inside scanning box
            canvas.drawRect(left, top, right, bottom, transparentPaint)

            // Green frame border
            canvas.drawRect(left, top, right, bottom, framePaint)

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

        // SKU Patterns
        private val skuLabelRegex = Regex(
            """(?i)(?:^|[^a-zA-Z0-9])(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0)(?:[^a-zA-Z0-9]|$)"""
        )
        private val multilineSkuRegex = Regex(
            """(?i)(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0)[\s:：#\-_/|]*([0-9OolILsSbB|]{9})(?!\d)"""
        )
        private val candidate9Pattern = Regex("""(?<!\d)[0-9OolILsSbB|]{9}(?!\d)""")
        private val standalone9Pattern = Regex("""(?<!\d)[0-9]{9}(?!\d)""")

        // UPC / Barcode Patterns (12~13 digits)
        private val upcLabelRegex = Regex(
            """(?i)(?:UPC|条码|条形码|EAN)[\s:：#\-_/|]*([0-9OolILsSbB|]{12,13})(?!\d)"""
        )
        private val ean13Pattern = Regex("""(?<!\d)(69[0-9]{11})(?!\d)""")
        private val standalone12Or13Pattern = Regex("""(?<!\d)[0-9]{12,13}(?!\d)""")

        // Exclusion pattern for lines containing irrelevant numbers (phones, orders, dates, amounts, etc.)
        private val excludeLinePattern = Regex(
            """(?i)(?:手机|电话|虚拟号|备用|订单|时间|日期|运单号|单号|快递|金额|合计|应收|实收|找零|流水)"""
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
                val code: String,
                val distanceToCenter: Float
            )
            val skuCandidates = mutableListOf<CandidateResult>()
            val upcCandidates = mutableListOf<CandidateResult>()

            // --- 1. SKU Extraction (Highest Priority) ---
            // Strategy 1.1: Direct label on same line or next 1-2 lines
            for (i in lines.indices) {
                val line = lines[i]
                if (skuLabelRegex.containsMatchIn(line.text)) {
                    for (match in candidate9Pattern.findAll(line.text)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) {
                            skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(line.centerY - boxCenterY)))
                        }
                    }
                    for (offset in 1..2) {
                        val nextLine = lines.getOrNull(i + offset) ?: break
                        if (nextLine.isExcluded) continue
                        for (match in candidate9Pattern.findAll(nextLine.text)) {
                            val cleaned = cleanDigits(match.value)
                            if (cleaned.length == 9) {
                                skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(nextLine.centerY - boxCenterY)))
                            }
                        }
                    }
                }
            }

            // Strategy 1.2: Multiline regex with SKU label
            val combinedText = lines.joinToString("\n") { it.text }
            if (skuCandidates.isEmpty()) {
                multilineSkuRegex.find(combinedText)?.let { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, 0f))
                    }
                }
            }

            // Strategy 1.3: SKU label somewhere in box + candidate 9 on non-excluded lines
            if (skuCandidates.isEmpty() && skuLabelRegex.containsMatchIn(combinedText)) {
                for (line in lines) {
                    if (line.isExcluded) continue
                    if (upcLabelRegex.containsMatchIn(line.text)) continue
                    for (match in candidate9Pattern.findAll(line.text)) {
                        val cleaned = cleanDigits(match.value)
                        if (cleaned.length == 9) {
                            skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(line.centerY - boxCenterY)))
                        }
                    }
                }
            }

            // Strategy 1.4: Standalone 9 pure digits on non-excluded lines without SKU label
            if (skuCandidates.isEmpty()) {
                for (line in lines) {
                    if (line.isExcluded) continue
                    if (upcLabelRegex.containsMatchIn(line.text)) continue
                    if (Regex("""\d{10,}""").containsMatchIn(line.text)) continue
                    for (match in standalone9Pattern.findAll(line.text)) {
                        skuCandidates.add(CandidateResult(match.value, kotlin.math.abs(line.centerY - boxCenterY)))
                    }
                }
            }

            // If we found any valid SKU 9-digit candidates, return the one closest to center
            if (skuCandidates.isNotEmpty()) {
                skuCandidates.sortBy { it.distanceToCenter }
                return skuCandidates.first().code
            }

            // --- 2. UPC / Barcode Extraction (12~13 Digits, Fallback after SKU) ---
            // Strategy 2.1: Line with UPC / 条码 label
            for (line in lines) {
                if (line.isExcluded) continue
                upcLabelRegex.find(line.text)?.let { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length in 12..13) {
                        upcCandidates.add(CandidateResult(cleaned, kotlin.math.abs(line.centerY - boxCenterY)))
                    }
                }
            }

            // Strategy 2.2: China 69-prefix 13-digit EAN code
            for (line in lines) {
                if (line.isExcluded) continue
                for (match in ean13Pattern.findAll(line.text)) {
                    upcCandidates.add(CandidateResult(match.value, kotlin.math.abs(line.centerY - boxCenterY)))
                }
            }

            // Strategy 2.3: Standalone 12-13 digits on non-excluded lines
            for (line in lines) {
                if (line.isExcluded) continue
                for (match in standalone12Or13Pattern.findAll(line.text)) {
                    upcCandidates.add(CandidateResult(match.value, kotlin.math.abs(line.centerY - boxCenterY)))
                }
            }

            if (upcCandidates.isNotEmpty()) {
                upcCandidates.sortBy { it.distanceToCenter }
                return upcCandidates.first().code
            }

            return null
        }

        fun extractSku(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val normalized = normalizeOcrText(rawText)
            val lines = normalized.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

            // 1. SKU with label
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

            // 2. Multiline SKU regex
            multilineSkuRegex.find(normalized)?.let { match ->
                val cleaned = cleanDigits(match.groupValues[1])
                if (cleaned.length == 9) return cleaned
            }

            // 3. Standalone 9 digits
            for (line in lines) {
                if (excludeLinePattern.containsMatchIn(line)) continue
                if (upcLabelRegex.containsMatchIn(line)) continue
                if (Regex("""\d{10,}""").containsMatchIn(line)) continue
                for (match in standalone9Pattern.findAll(line)) {
                    return match.value
                }
            }

            // 4. Fallback UPC 12-13 digits
            for (line in lines) {
                if (excludeLinePattern.containsMatchIn(line)) continue
                upcLabelRegex.find(line)?.let { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length in 12..13) return cleaned
                }
                for (match in ean13Pattern.findAll(line)) {
                    return match.value
                }
                for (match in standalone12Or13Pattern.findAll(line)) {
                    return match.value
                }
            }

            return null
        }
    }
}
