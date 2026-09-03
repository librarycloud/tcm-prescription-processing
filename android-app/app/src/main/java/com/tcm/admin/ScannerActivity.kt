package com.tcm.admin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.ImageFormat
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
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
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private fun scannerBoxRect(width: Float, height: Float): RectF {
    val boxSize = (width * 0.72f).coerceAtMost(height * 0.5f)
    val left = (width - boxSize) / 2f
    val top = (height - boxSize) / 2f - (height * 0.05f)
    return RectF(left, top, left + boxSize, top + boxSize)
}

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private var ocrEnabled = false
    @Volatile
    private var paddleOcr: PaddleOCR? = null
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
            loadPaddleOcr()
        }
        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            // Keep the preview and OCR ROI on the same centered, filled coordinate system.
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

    private fun loadPaddleOcr() {
        ocrScope.launch {
            try {
                check(OpenCVUtils.init(this@ScannerActivity)) {
                    "Failed to initialize OpenCV native library"
                }
                val loaded = PaddleOCR.create(
                    context = this@ScannerActivity,
                    config = PaddleOCRConfig(
                        // Match the official PP-OCRv6 small model configuration.
                        detThresh = 0.2f,
                        detBoxThresh = 0.45f,
                        detUnclipRatio = 1.4f,
                        recScoreThresh = 0.0f,
                        recBatchSize = 1,
                    ),
                    engineConfig = EngineConfig(numThreads = 4),
                )
                if (isActive) {
                    paddleOcr = loaded
                    Log.i(TAG, "PP-OCRv6 small ready, coldLoad=${loaded.coldLoadTimeMs}ms")
                } else {
                    loaded.release()
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    Log.e(TAG, "Failed to load PP-OCRv6 small", t)
                }
            }
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
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                // Reserve one completion for barcode scanning and one for OCR whenever OCR
                // mode is enabled. This keeps the ImageProxy open until both consumers have
                // finished, including the model-loading/in-flight cases below.
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

                // OCR is intentionally restricted to the visible scanner box.
                if (ocrEnabled) {
                    val ocr = paddleOcr
                    if (ocr != null && !delivered.get() && ocrInFlight.compareAndSet(false, true)) {
                        val frameBitmap = runCatching { imageProxyToBitmap(proxy) }
                            .onFailure { Log.w(TAG, "Failed to convert camera frame for OCR", it) }
                            .getOrNull()
                        if (frameBitmap == null) {
                            ocrInFlight.set(false)
                            taskFinished()
                        } else {
                            ocrScope.launch {
                                var roiBitmap: Bitmap? = null
                                try {
                                    val currentRoi = cropToScannerBox(frameBitmap)
                                    roiBitmap = currentRoi
                                    val runResult = ocr.recognize(currentRoi)
                                    val resultLines = runResult.results.filter { it.text.isNotBlank() }
                                    val lineText = resultLines.joinToString("\n") { it.text }
                                    // A recognizer can split "SKU" into separate boxes. The joined
                                    // pass keeps that case recoverable without scanning outside the ROI.
                                    val sku = extractSku(lineText)
                                        ?: extractSku(resultLines.joinToString(" ") { it.text })
                                    if (BuildConfig.DEBUG) {
                                        val compactText = lineText.replace(Regex("\\s+"), " ").trim()
                                        val boxDetails = resultLines.joinToString(" | ") { result ->
                                            val minX = result.box.points.minOf { it.x }
                                            val minY = result.box.points.minOf { it.y }
                                            val maxX = result.box.points.maxOf { it.x }
                                            val maxY = result.box.points.maxOf { it.y }
                                            "${result.text}@(${minX.roundToInt()},${minY.roundToInt()},${maxX.roundToInt()},${maxY.roundToInt()})/${String.format(Locale.US, "%.2f", result.confidence)}"
                                        }
                                        val logKey = "$compactText|$boxDetails"
                                        if (logKey != lastLoggedOcrText) {
                                            Log.d(
                                                "ScannerOCR",
                                                "engine=paddle; roi=${currentRoi.width}x${currentRoi.height}; " +
                                                    "raw=$compactText; boxes=$boxDetails; candidate=${sku ?: "none"}",
                                            )
                                            lastLoggedOcrText = logKey
                                        }
                                    }
                                    if (sku != null) {
                                        handleCandidateDetected(sku)
                                    }
                                } catch (t: Throwable) {
                                    if (t !is CancellationException) {
                                        Log.w(TAG, "PP-OCRv6 frame recognition failed", t)
                                    }
                                } finally {
                                    if (roiBitmap != null && roiBitmap !== frameBitmap) {
                                        roiBitmap.recycle()
                                    }
                                    frameBitmap.recycle()
                                    ocrInFlight.set(false)
                                    taskFinished()
                                }
                            }
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
        // Keep two-frame confirmation to avoid writing a transient OCR error into the search
        // box. Chinese OCR is intentionally disabled in this scanner.
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
        val ocr = paddleOcr
        paddleOcr = null
        ocrScope.cancel()
        ocr?.release()
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

    private fun cropToScannerBox(bitmap: Bitmap): Bitmap {
        val viewWidth = previewView.width
        val viewHeight = previewView.height
        val coordinateWidth = if (viewWidth > 0) viewWidth.toFloat() else bitmap.width.toFloat()
        val coordinateHeight = if (viewHeight > 0) viewHeight.toFloat() else bitmap.height.toFloat()
        val frame = scannerBoxRect(coordinateWidth, coordinateHeight)
        // PreviewView.ScaleType.FILL_CENTER renders the upright image with a centered crop.
        val scale = max(
            coordinateWidth / bitmap.width.toFloat(),
            coordinateHeight / bitmap.height.toFloat(),
        )
        val renderedWidth = bitmap.width * scale
        val renderedHeight = bitmap.height * scale
        val offsetX = (coordinateWidth - renderedWidth) / 2f
        val offsetY = (coordinateHeight - renderedHeight) / 2f
        val left = ((frame.left - offsetX) / scale).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = ((frame.top - offsetY) / scale).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = ((frame.right - offsetX) / scale).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((frame.bottom - offsetY) / scale).roundToInt().coerceIn(top + 1, bitmap.height)
        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        val image = proxy.image ?: return null
        val nv21 = imageToNv21(image)
        val jpegStream = ByteArrayOutputStream()
        val compressed = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            .compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 90, jpegStream)
        if (!compressed) return null
        val decoded = BitmapFactory.decodeByteArray(jpegStream.toByteArray(), 0, jpegStream.size()) ?: return null
        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation == 0) return decoded

        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun imageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val output = ByteArray(width * height + 2 * chromaWidth * chromaHeight)
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer.duplicate().apply { rewind() }
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (column in 0 until width) {
                output[row * width + column] = safePlaneByte(
                    yBuffer,
                    rowStart + column * yPlane.pixelStride,
                )
            }
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer.duplicate().apply { rewind() }
        val vBuffer = vPlane.buffer.duplicate().apply { rewind() }
        var outputIndex = width * height
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uPlane.rowStride
            val vRowStart = row * vPlane.rowStride
            for (column in 0 until chromaWidth) {
                output[outputIndex++] = safePlaneByte(
                    vBuffer,
                    vRowStart + column * vPlane.pixelStride,
                )
                output[outputIndex++] = safePlaneByte(
                    uBuffer,
                    uRowStart + column * uPlane.pixelStride,
                )
            }
        }
        return output
    }

    private fun safePlaneByte(buffer: ByteBuffer, index: Int): Byte {
        return if (index in 0 until buffer.limit()) buffer.get(index) else 0
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

        // SKU-only mode: accept common OCR variants, but never use a generic number as a
        // scan result. The label is required immediately before a 9-digit OCR result.
        private val skuLabelPattern = Regex(
            """(?i)(?:\bS\s*K\s*U|\b5\s*K\s*U)(?=\s*[:：#]?\s*[0-9OolILsSbB|]|\b)""",
        )
        // Match the complete digit-like run, not just the first nine digits. This prevents an
        // 11-digit phone number or a longer order/UPC number from being truncated to 9 digits.
        private val skuDigitCandidatePattern = Regex(
            """^[\s:：#/,，;；|]*([0-9OolILsSbB|](?:\s*[0-9OolILsSbB|])*)(?![0-9A-Za-z])""",
        )

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
                    '\u3000' -> ' '
                    in '０'..'９' -> ('0'.code + (char.code - '０'.code)).toChar()
                    in 'Ａ'..'Ｚ' -> ('A'.code + (char.code - 'Ａ'.code)).toChar()
                    in 'ａ'..'ｚ' -> ('a'.code + (char.code - 'ａ'.code)).toChar()
                    else -> char
                }
            }.joinToString("")
        }

        private fun findNineDigitSku(text: String): String? {
            val normalized = normalizeOcrText(text)
            val match = skuDigitCandidatePattern.find(normalized) ?: return null
            return cleanDigits(match.groupValues[1]).takeIf { it.length == 9 }
        }

        fun extractSku(text: String): String? {
            if (text.isBlank()) return null
            val normalized = normalizeOcrText(text.replace('\u00A0', ' '))

            // Only inspect the text immediately after an SKU label (for example:
            // "SKU 303827503" or OCR boxes returned as "SKU\\n303827503").
            for (match in skuLabelPattern.findAll(normalized)) {
                val afterLabel = normalized.substring(match.range.last + 1)
                findNineDigitSku(afterLabel)?.let { return it }
            }

            return null
        }
    }
}
