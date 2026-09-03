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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private var ocrEnabled = false
    private var textRecognizer: TextRecognizer? = null
    private val ocrInFlight = AtomicBoolean(false)
    private lateinit var previewView: PreviewView

    @Volatile
    private var lastCandidate: String? = null
    @Volatile
    private var candidateFirstSeenTime: Long = 0L
    @Volatile
    private var candidateHitCount: Int = 0
    private val STABLE_HOLD_MS = 150L

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
        previewView = PreviewView(this)
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
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                val tasksRemaining = AtomicInteger(if (ocrEnabled) 2 else 1)
                fun taskFinished() {
                    if (tasksRemaining.decrementAndGet() == 0) closeFrame()
                }
                val barcodeTask = scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull()?.let { barcode ->
                            val value = barcode.rawValue
                            if (!value.isNullOrBlank()) {
                                handleBarcodeDetected(value, barcode.format)
                            }
                        }
                    }
                    .addOnCompleteListener { taskFinished() }

                // Run OCR independently of barcode scanning.
                if (ocrEnabled) {
                    val recognizer = textRecognizer
                    if (recognizer != null && ocrInFlight.compareAndSet(false, true)) {
                        recognizer.process(image)
                            .addOnSuccessListener { text ->
                                val sku = extractSku(text.text)
                                if (sku != null) {
                                    handleCandidateDetected(sku)
                                }
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
        // Require two identical reads before returning OCR or one-dimensional barcode values.
        if (candidate == lastCandidate) {
            candidateHitCount++
            if (candidateHitCount >= 2) {
                deliverResult(candidate)
            }
        } else {
            lastCandidate = candidate
            candidateHitCount = 1
            candidateFirstSeenTime = SystemClock.elapsedRealtime()
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
        scanner.close()
        textRecognizer?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun deliverResult(value: String) {
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
            val boxSize = (width * 0.72f).coerceAtMost(height * 0.5f)
            val left = (width - boxSize) / 2f
            val top = (height - boxSize) / 2f - (height * 0.05f)
            val right = left + boxSize
            val bottom = top + boxSize
            val cornerLength = 40f

            // Draw full semi-transparent mask
            canvas.drawRect(0f, 0f, width, height, maskPaint)

            // Clear center framing box with rounded corners
            val boxRect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(boxRect, 16f, 16f, transparentPaint)

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
                "将条形码、二维码、SKU 或商品名称对准框内"
            } else {
                "将条形码或二维码放入框内即可自动扫描"
            }
            canvas.drawText(hint, width / 2f, bottom + 70f, textPaint)
        }
    }

    companion object {
        const val SCAN_RESULT = "scan_result"
        const val EXTRA_ENABLE_SKU_OCR = "enable_sku_ocr"

        // Also recognize '5KU' (when 'S' is OCR-misrecognized as '5'), 'SK', '货号', '编码'
        private val skuLabelPattern = Regex("""(?i)(?:\bSKU\b|5KU|\bSK\b|货号|编码)""")
        private val excludeLinePattern = Regex("(?:电话|手机|虚拟|备用|订单|下单|时间|配送|原价|付款|编号|口袋|总数|减配送)")

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

        // Product names are extracted from a Chinese run; OCR may drop the item prefix or brackets.
        private val pocketPattern = Regex("""(?:[-—_~\s]*[0-9一二三四五六七八九十]*\s*号?\s*口?\s*袋[-—_~\s]*)""")
        private val itemPrefixPattern = Regex("""^[\s/_\-—~]*(?:[0-9一二三四五六七八九十]+\s*[.、:：/]|[lLiI]\s*[.、:]|[0-9一二三四五六七八九十]+\s+)[\s/_\-—~]*""")
        private val chineseRunPattern = Regex("""[\u3400-\u9fff]{2,30}""")
        private val productNoisePattern = Regex(
            """(?:^[/\\(（]|^\s*(?:规格|数量|单价|金额|合计|应付|找零|收款|条码|商品编码)(?:\b|$)|^\s*\d+(?:\.\d+)?\s*(?:ml|kg|g|mg|mm|cm|pcs|upc)\b)""",
            RegexOption.IGNORE_CASE,
        )

        private fun extractChineseProduct(line: String, allowUnmarked: Boolean = false): String? {
            if (line.isBlank() || excludeLinePattern.containsMatchIn(line)) return null
            if (productNoisePattern.containsMatchIn(line)) return null

            var candidate = line.trim()
            val hadItemPrefix = itemPrefixPattern.containsMatchIn(candidate)
            candidate = candidate.replace(itemPrefixPattern, "")
            val brandResult = stripBrandWrapper(candidate)
            val hadBrandWrapper = brandResult.first
            candidate = brandResult.second.trimStart()

            // Without a prefix, only accept a line that is known to be under a pocket marker.
            if (!hadItemPrefix && !hadBrandWrapper && !allowUnmarked) return null
            val match = chineseRunPattern.find(candidate) ?: return null
            val name = match.value.trim()
            return name.takeIf { it.length >= 2 }
        }

        private fun stripBrandWrapper(value: String): Pair<Boolean, String> {
            if (value.isEmpty()) return false to value
            val close = when (value.first()) {
                '[', '\u3010', '\uFF3B' -> setOf(']', '\u3011', '\uFF3D')
                '|', '\uFF5C' -> setOf('|', '\uFF5C')
                else -> return false to value
            }
            val closeIndex = value.indexOfFirst { it in close }
            return if (closeIndex > 0) {
                true to value.substring(closeIndex + 1)
            } else {
                false to value
            }
        }

        fun extractSku(text: String): String? {
            if (text.isBlank()) return null
            val normalized = text
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')

            val lines = normalized.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

            // Priority 1: Check lines with SKU / 5KU / 货号 / 编码 labels (e.g. SKU 303827503)
            lines.forEachIndexed { index, line ->
                val match = skuLabelPattern.find(line)
                if (match != null) {
                    val afterLabel = line.substring(match.range.last + 1)
                    val tokens = afterLabel.split(Regex("[\\s:：,，;；|/]+")).filter { it.isNotBlank() }
                    for (token in tokens) {
                        if (token.contains("UPC", ignoreCase = true)) break
                        val d = cleanDigits(token)
                        if (d.length == 9) return d
                    }

                    // Multi-column or wrapped block OCR reading (up to 3 subsequent lines)
                    for (offset in 1..3) {
                        val nextLine = lines.getOrNull(index + offset) ?: break
                        if (excludeLinePattern.containsMatchIn(nextLine) && !skuLabelPattern.containsMatchIn(nextLine)) {
                            break
                        }
                        if (nextLine.matches(Regex("(?i)^\\s*UPC\\s*$")) || nextLine.contains("UPC", ignoreCase = true)) {
                            continue
                        }
                        val nextTokens = nextLine.split(Regex("[\\s:：,，;；|/]+")).filter { it.isNotBlank() }
                        for (token in nextTokens) {
                            if (token.contains("UPC", ignoreCase = true)) break
                            val d = cleanDigits(token)
                            if (d.length == 9) return d
                        }
                    }
                }
            }

            // Priority 2: Extract Chinese product name following a non-round brand wrapper.
            for (line in lines) {
                if (!line.contains(Regex("""[\[\u3010\uFF3B|｜]"""))) continue
                extractChineseProduct(line)?.let { return it }
            }

            // Priority 3: Extract Chinese product name under "口袋" section if present
            val pocketIndex = lines.indexOfFirst { pocketPattern.containsMatchIn(it) }
            if (pocketIndex != -1) {
                for (offset in 1..5) {
                    val nextLine = lines.getOrNull(pocketIndex + offset) ?: break
                    if (excludeLinePattern.containsMatchIn(nextLine)) break
                    if (nextLine.contains("sku", ignoreCase = true) || nextLine.contains("upc", ignoreCase = true)) continue
                    if (nextLine.startsWith("/") || nextLine.startsWith("(") || nextLine.startsWith("（")) continue

                    extractChineseProduct(nextLine, allowUnmarked = true)?.let { return it }
                }
            }

            // Priority 4: Numbered product item line, even if OCR omitted a brand wrapper.
            for (line in lines) {
                extractChineseProduct(line)?.let { return it }
            }

            // Priority 5: Safe fallback across non-excluded lines for standalone 9 digits
            for (line in lines) {
                if (excludeLinePattern.containsMatchIn(line)) continue
                val tokens = line.split(Regex("[\\s:：,，;；|/]+")).filter { it.isNotBlank() }
                for (token in tokens) {
                    if (token.contains("UPC", ignoreCase = true)) continue
                    val d = cleanDigits(token)
                    if (d.length == 9) return d
                }
            }

            return null
        }
    }
}
