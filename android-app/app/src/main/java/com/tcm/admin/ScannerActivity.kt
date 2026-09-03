package com.tcm.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
    @Volatile
    private var currentCamera: Camera? = null
    private var isTorchOn = false
    private var isDebugLogOpen = false
    @Volatile
    private var latestOcrDebugLog: String? = null
    private var debugLogTextView: TextView? = null
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

    @SuppressLint("ClickableViewAccessibility")
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
        previewView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val cam = currentCamera
                if (cam != null) {
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
                v.performClick()
            }
            true
        }
        root.addView(previewView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val overlay = ScannerOverlayView(this, ocrEnabled)
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Top control bar (Back Button + Flashlight / Torch toggle + Debug Log toggle)
        val density = resources.displayMetrics.density
        val topBar = FrameLayout(this).apply {
            setPadding((16 * density).toInt(), (40 * density).toInt(), (16 * density).toInt(), 0)
        }

        // Back Button
        val backBtn = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
            }
            setOnClickListener { finish() }
        }
        val backParams = FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
            gravity = Gravity.START or Gravity.TOP
        }
        topBar.addView(backBtn, backParams)

        // Right side buttons container (Debug Log + Torch)
        val rightButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Debug Log toggle button
        val debugBtn = TextView(this).apply {
            text = "🐞"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
            }
        }
        val debugBtnParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
            marginEnd = (12 * density).toInt()
        }
        rightButtons.addView(debugBtn, debugBtnParams)

        // Torch toggle button
        val torchBtn = TextView(this).apply {
            text = "🔦"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
            }
            setOnClickListener {
                val cam = currentCamera ?: return@setOnClickListener
                if (cam.cameraInfo.hasFlashUnit()) {
                    isTorchOn = !isTorchOn
                    cam.cameraControl.enableTorch(isTorchOn)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isTorchOn) 0xCCFFD600.toInt() else 0x66000000)
                    }
                }
            }
        }
        rightButtons.addView(torchBtn, LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()))

        val rightParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.TOP
        }
        topBar.addView(rightButtons, rightParams)

        root.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // Floating Debug Log Panel (at the bottom)
        val debugLogPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(0xEE1E1E1E.toInt())
                setStroke((1 * density).toInt(), 0x44FFFFFF)
            }
            visibility = View.GONE
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(this).apply {
            text = "🐞 实时识别日志 (OCR Debug)"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val copyBtn = TextView(this).apply {
            text = "复制日志"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(0x33FFFFFF)
            }
            setOnClickListener {
                val textToCopy = latestOcrDebugLog
                if (!textToCopy.isNullOrBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Log", textToCopy))
                    Toast.makeText(this@ScannerActivity, "识别日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ScannerActivity, "暂无识别日志", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                isDebugLogOpen = false
                debugLogPanel.visibility = View.GONE
                debugBtn.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x66000000)
                }
            }
        }
        headerLayout.addView(headerTitle)
        headerLayout.addView(copyBtn)
        headerLayout.addView(closeBtn)
        debugLogPanel.addView(headerLayout)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }
        val tv = TextView(this).apply {
            text = "等待相机识别...\n将包含 9 位数字的 SKU 对准绿色方框"
            setTextColor(0xFF00E676.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        debugLogTextView = tv
        scrollView.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        debugLogPanel.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (240 * density).toInt()
        ).apply {
            gravity = Gravity.BOTTOM
            val m = (12 * density).toInt()
            setMargins(m, 0, m, (16 * density).toInt())
        }
        root.addView(debugLogPanel, panelParams)

        debugBtn.setOnClickListener {
            isDebugLogOpen = !isDebugLogOpen
            debugLogPanel.visibility = if (isDebugLogOpen) View.VISIBLE else View.GONE
            debugBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDebugLogOpen) 0xCC00C853.toInt() else 0x66000000)
            }
            if (isDebugLogOpen) {
                val current = latestOcrDebugLog
                if (!current.isNullOrBlank()) {
                    tv.text = current
                }
            }
        }

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
                val pendingTasks = AtomicInteger(if (isOcrActive) 2 else 1)
                val taskFinished = {
                    if (pendingTasks.decrementAndGet() <= 0) {
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
                                val (candidate, debugLog) = extractSkuWithDebug(visionText, imgScanBox)
                                latestOcrDebugLog = debugLog
                                if (isDebugLogOpen) {
                                    val logTv = debugLogTextView
                                    logTv?.post { logTv.text = debugLog }
                                }
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
            currentCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
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
        debugLogTextView = null
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
                "将条形码、二维码或 SKU 数字对准框内\n(轻触屏幕可对焦)"
            } else {
                "将条形码或二维码放入框内即可自动扫描"
            }
            if (hint.contains("\n")) {
                val lines = hint.split("\n")
                canvas.drawText(lines[0], width / 2f, bottom + 55f * resources.displayMetrics.density / 2.75f, textPaint)
                canvas.drawText(lines[1], width / 2f, bottom + 95f * resources.displayMetrics.density / 2.75f, textPaint)
            } else {
                canvas.drawText(hint, width / 2f, bottom + 70f, textPaint)
            }
        }
    }

    companion object {
        private const val TAG = "ScannerActivity"
        const val SCAN_RESULT = "scan_result"
        const val EXTRA_ENABLE_SKU_OCR = "enable_sku_ocr"

        // SKU Patterns
        private val skuLabelRegex = Regex(
            """(?i)(?:^|[^a-zA-Z0-9])(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0|S\s*K|K\s*U)(?:[^a-zA-Z0-9]|$)"""
        )
        private val multilineSkuRegex = Regex(
            """(?i)(?:S\s*K\s*U|5\s*K\s*U|S\s*K\s*0|S\s*K|K\s*U)[\s:：#\-_/|]*([0-9OolILsSbB|\s\-_]{9,20})"""
        )
        private val candidate9Pattern = Regex("""(?<!\d)[0-9OolILsSbB|]{9}(?!\d)""")
        private val standalone9Pattern = Regex("""(?<!\d)[0-9]{9}(?!\d)""")

        // Exclusion pattern for lines containing irrelevant text/numbers (UPC, barcodes, phones, orders, dates, amounts, etc.)
        private val excludeLinePattern = Regex(
            """(?i)(?:UPC|条码|条形码|EAN|手机|电话|虚拟号|备用|订单|时间|日期|运单号|单号|快递|金额|合计|应收|实收|找零|流水|原价|已付款)"""
        )

        private fun isInsideScanBox(itemRect: RectF, scanBox: RectF): Boolean {
            val cx = itemRect.centerX()
            val cy = itemRect.centerY()
            return cx >= scanBox.left && cx <= scanBox.right &&
                   cy >= scanBox.top && cy <= scanBox.bottom
        }

        internal fun cleanDigits(token: String): String {
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

        internal fun normalizeOcrText(text: String): String {
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

        private data class RawElement(
            val text: String,
            val rect: RectF
        )

        private data class LogicalRow(
            val text: String,
            val top: Float,
            val centerY: Float,
            val isExcluded: Boolean
        )

        private data class CandidateResult(
            val code: String,
            val distanceToCenter: Float
        )

        private fun buildLogicalRows(elements: List<RawElement>): List<LogicalRow> {
            if (elements.isEmpty()) return emptyList()
            val sorted = elements.sortedBy { it.rect.centerY() }
            val clusters = mutableListOf<MutableList<RawElement>>()

            for (elem in sorted) {
                var matched = false
                for (cluster in clusters) {
                    val avgCenterY = cluster.map { it.rect.centerY() }.average().toFloat()
                    val avgHeight = cluster.map { it.rect.height() }.average().toFloat()
                    val threshold = maxOf(avgHeight, elem.rect.height()) * 0.75f
                    if (kotlin.math.abs(elem.rect.centerY() - avgCenterY) <= threshold) {
                        cluster.add(elem)
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    clusters.add(mutableListOf(elem))
                }
            }

            return clusters.map { cluster ->
                cluster.sortBy { it.rect.left }
                val joinedText = cluster.joinToString(" ") { it.text }
                val avgTop = cluster.map { it.rect.top }.minOrNull() ?: 0f
                val avgCenterY = cluster.map { it.rect.centerY() }.average().toFloat()
                LogicalRow(
                    text = joinedText,
                    top = avgTop,
                    centerY = avgCenterY,
                    isExcluded = excludeLinePattern.containsMatchIn(joinedText)
                )
            }.sortedBy { it.top }
        }

        data class OcrExtractionResult(
            val sku: String?,
            val debugText: String
        )

        fun extractSkuFromVisionText(visionText: Text, scanBox: RectF?): String? {
            return extractSkuWithDebug(visionText, scanBox).sku
        }

        fun extractSkuWithDebug(visionText: Text, scanBox: RectF?): OcrExtractionResult {
            if (visionText.textBlocks.isEmpty()) {
                return OcrExtractionResult(null, "【框内文本】: 暂未检测到文字")
            }

            val elements = mutableListOf<RawElement>()
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
                    elements.add(RawElement(normalized, rect ?: RectF()))
                }
            }

            if (elements.isEmpty()) {
                return OcrExtractionResult(null, "【框内文本】: 绿框内未包含任何文字\n(请将 SKU 移入绿框)")
            }

            val logicalRows = buildLogicalRows(elements)
            val boxCenterY = scanBox?.centerY() ?: (logicalRows.map { it.centerY }.average().toFloat())

            val skuCandidates = mutableListOf<CandidateResult>()

            // --- 1. SKU Extraction (Strict 9 digits only) ---
            for (i in logicalRows.indices) {
                val row = logicalRows[i]
                if (row.isExcluded) continue

                // Normalize spaces between digits on the row (e.g. "303 827 503" -> "303827503")
                val collapsed = row.text.replace(Regex("""(?<=\d)\s+(?=\d)"""), "")

                // 1.1 Regex match on row text
                multilineSkuRegex.findAll(collapsed).forEach { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(row.centerY - boxCenterY)))
                    }
                }

                // 1.2 If row contains SKU label, check remaining text on the same row or subsequent rows
                if (skuLabelRegex.containsMatchIn(collapsed)) {
                    val afterLabel = skuLabelRegex.replace(collapsed, " ")
                    val cleaned = cleanDigits(afterLabel)
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(row.centerY - boxCenterY)))
                    }
                    for (match in candidate9Pattern.findAll(afterLabel)) {
                        val c = cleanDigits(match.value)
                        if (c.length == 9) {
                            skuCandidates.add(CandidateResult(c, kotlin.math.abs(row.centerY - boxCenterY)))
                        }
                    }

                    // Also check next 1-2 rows
                    for (offset in 1..2) {
                        val nextRow = logicalRows.getOrNull(i + offset) ?: break
                        if (nextRow.isExcluded) continue
                        val nextCollapsed = nextRow.text.replace(Regex("""(?<=\d)\s+(?=\d)"""), "")
                        val nextCleaned = cleanDigits(nextCollapsed)
                        if (nextCleaned.length == 9) {
                            skuCandidates.add(CandidateResult(nextCleaned, kotlin.math.abs(nextRow.centerY - boxCenterY)))
                        } else {
                            for (match in candidate9Pattern.findAll(nextCollapsed)) {
                                val c = cleanDigits(match.value)
                                if (c.length == 9) {
                                    skuCandidates.add(CandidateResult(c, kotlin.math.abs(nextRow.centerY - boxCenterY)))
                                }
                            }
                        }
                    }
                }
            }

            // 1.3 Combined text regex
            if (skuCandidates.isEmpty()) {
                val combinedText = logicalRows.filter { !it.isExcluded }.joinToString("\n") {
                    it.text.replace(Regex("""(?<=\d)\s+(?=\d)"""), "")
                }
                multilineSkuRegex.findAll(combinedText).forEach { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, 0f))
                    }
                }
            }

            // 1.4 Standalone 9 pure digits on non-excluded rows (without long digit interference)
            if (skuCandidates.isEmpty()) {
                for (row in logicalRows) {
                    if (row.isExcluded) continue
                    val collapsed = row.text.replace(Regex("""(?<=\d)\s+(?=\d)"""), "")
                    if (Regex("""\d{10,}""").containsMatchIn(collapsed)) continue
                    for (match in standalone9Pattern.findAll(collapsed)) {
                        skuCandidates.add(CandidateResult(match.value, kotlin.math.abs(row.centerY - boxCenterY)))
                    }
                }
            }

            val finalSku = if (skuCandidates.isNotEmpty()) {
                skuCandidates.sortBy { it.distanceToCenter }
                skuCandidates.first().code
            } else {
                null
            }

            val sb = java.lang.StringBuilder()
            sb.append("【框内文本】(${elements.size} 项):\n")
            for (elem in elements) {
                sb.append("• ").append(elem.text).append("\n")
            }
            sb.append("\n【重组逻辑行】(${logicalRows.size} 行):\n")
            for ((idx, row) in logicalRows.withIndex()) {
                val status = if (row.isExcluded) " [排除/条码/电话]" else ""
                sb.append("${idx + 1}: ${row.text}$status\n")
            }
            sb.append("\n【提取结果】: ")
            if (finalSku != null) {
                sb.append("✅ 命中 SKU: ").append(finalSku)
            } else {
                sb.append("❌ 未检测到 9 位 SKU")
            }

            return OcrExtractionResult(finalSku, sb.toString())
        }

        fun extractSku(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val normalized = normalizeOcrText(rawText)
            val lines = normalized.split(Regex("[\\r\\n]+"))
                .map { it.trim().replace(Regex("""(?<=\d)\s+(?=\d)"""), "") }
                .filter { it.isNotEmpty() }

            // 1. SKU with label
            for (i in lines.indices) {
                val line = lines[i]
                if (skuLabelRegex.containsMatchIn(line)) {
                    val afterLabel = skuLabelRegex.replace(line, " ")
                    val cleaned = cleanDigits(afterLabel)
                    if (cleaned.length == 9) return cleaned
                    for (match in candidate9Pattern.findAll(line)) {
                        val c = cleanDigits(match.value)
                        if (c.length == 9) return c
                    }
                    for (offset in 1..2) {
                        val nextLine = lines.getOrNull(i + offset) ?: break
                        if (excludeLinePattern.containsMatchIn(nextLine)) continue
                        val c = cleanDigits(nextLine)
                        if (c.length == 9) return c
                        for (match in candidate9Pattern.findAll(nextLine)) {
                            val mc = cleanDigits(match.value)
                            if (mc.length == 9) return mc
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
                if (Regex("""\d{10,}""").containsMatchIn(line)) continue
                for (match in standalone9Pattern.findAll(line)) {
                    return match.value
                }
            }

            return null
        }
    }
}
