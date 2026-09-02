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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    private val STABLE_HOLD_MS = 500L

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
                        val value = barcodes.firstOrNull()?.rawValue
                        if (!value.isNullOrBlank()) deliverResult(value)
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
        val now = SystemClock.elapsedRealtime()
        if (candidate == lastCandidate) {
            // Require candidate to remain steady in the camera view for at least 0.5s (500ms)
            if (now - candidateFirstSeenTime >= STABLE_HOLD_MS) {
                deliverResult(candidate)
            }
        } else {
            lastCandidate = candidate
            candidateFirstSeenTime = now
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
                "将条形码、二维码或 SKU 区域对准框内并保持稳定"
            } else {
                "将条形码或二维码放入框内即可自动扫描"
            }
            canvas.drawText(hint, width / 2f, bottom + 70f, textPaint)
        }
    }

    companion object {
        const val SCAN_RESULT = "scan_result"
        const val EXTRA_ENABLE_SKU_OCR = "enable_sku_ocr"

        private val skuLabelPattern = Regex("(?i)\\bSKU\\b")
        private val nineDigitPattern = Regex("(?<![0-9])([0-9]{9})(?![0-9])")
        private val excludeLinePattern = Regex("(?:电话|手机|虚拟|备用|订单|下单|时间|配送|原价|付款|编号|口袋|总数|减配送)")

        fun extractSku(text: String): String? {
            val normalized = text
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')

            fun normalizeDigits(value: String): String = value
                .map { when (it) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; 'S', 's' -> '5'; 'B', 'b' -> '8'; else -> it } }
                .filter(Char::isDigit)
                .joinToString("")

            val lines = normalized.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

            // Strictly locate the SKU label and search within its row or immediately succeeding rows
            lines.forEachIndexed { index, line ->
                if (skuLabelPattern.containsMatchIn(line)) {
                    // Check remainder of the SKU line
                    val afterLabel = line.substringAfter(skuLabelPattern.find(line)?.value ?: "", "")
                    val lineDigits = normalizeDigits(afterLabel)
                    val direct9 = nineDigitPattern.find(lineDigits)?.groupValues?.getOrNull(1)
                    if (direct9 != null) return direct9
                    if (lineDigits.length == 9) return lineDigits

                    // Check up to 3 subsequent lines (to handle multi-column block OCR reading)
                    var accumulated = lineDigits
                    for (offset in 1..3) {
                        val nextLine = lines.getOrNull(index + offset) ?: break
                        // Stop if hitting unrelated phone/order/price sections
                        if (excludeLinePattern.containsMatchIn(nextLine) && !skuLabelPattern.containsMatchIn(nextLine)) {
                            break
                        }
                        // Skip standalone "UPC" label
                        if (nextLine.matches(Regex("(?i)^\\s*UPC\\s*$"))) {
                            continue
                        }
                        // Ignore lines that are explicitly UPC numbers
                        if (nextLine.contains("UPC", ignoreCase = true)) {
                            continue
                        }

                        val nextDigits = normalizeDigits(nextLine)
                        val next9 = nineDigitPattern.find(nextDigits)?.groupValues?.getOrNull(1)
                        if (next9 != null) return next9
                        if (nextDigits.length == 9) return nextDigits

                        accumulated += nextDigits
                        val acc9 = nineDigitPattern.find(accumulated)?.groupValues?.getOrNull(1)
                        if (acc9 != null) return acc9
                        if (accumulated.length == 9) return accumulated
                    }
                }
            }

            return null
        }
    }
}
