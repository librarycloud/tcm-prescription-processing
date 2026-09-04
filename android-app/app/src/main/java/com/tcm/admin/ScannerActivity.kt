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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private fun scannerBoxRect(width: Float, height: Float, ocrEnabled: Boolean = false): RectF {
    val boxWidth = width * 0.88f
    val boxHeight = if (ocrEnabled) {
        (boxWidth * 0.60f).coerceAtMost(height * 0.45f)
    } else {
        (boxWidth * 0.82f).coerceAtMost(height * 0.5f)
    }
    val left = (width - boxWidth) / 2f
    val top = (height - boxHeight) / 2f - (height * 0.05f)
    return RectF(left, top, left + boxWidth, top + boxHeight)
}

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private var ocrEnabled = false
    @Volatile
    private var paddleOcr: PaddleOCR? = null
    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile
    private var currentCamera: Camera? = null
    private var isTorchOn = false
    private var isDebugLogOpen = false
    @Volatile
    private var isRecognitionPaused = false
    @Volatile
    private var latestOcrDebugLog: String? = null
    private var debugLogTextView: TextView? = null
    private var debugHistoryTextView: TextView? = null
    private var debugPauseBtn: TextView? = null
    private val ocrLogHistory = java.util.Collections.synchronizedList(mutableListOf<OcrSnapshot>())
    private var viewingHistoryIndex = -1
    private var lastRecordedOcrTime = 0L

    private data class OcrSnapshot(
        val timestamp: String,
        val candidate: String?,
        val fullLog: String,
        val summary: String,
    )

    private val ocrInFlight = AtomicBoolean(false)
    private lateinit var previewView: PreviewView
    private var overlayView: ScannerOverlayView? = null

    private fun updateDebugLogUi() {
        val logTv = debugLogTextView ?: return
        val historyTv = debugHistoryTextView ?: return
        val pauseBtn = debugPauseBtn ?: return

        overlayView?.postInvalidate()

        if (isRecognitionPaused) {
            pauseBtn.text = "▶ 继续"
            pauseBtn.setTextColor(Color.WHITE)
            (pauseBtn.background as? GradientDrawable)?.setColor(0xCC00C853.toInt())
        } else {
            pauseBtn.text = "⏸ 暂停"
            pauseBtn.setTextColor(0xFFFFD54F.toInt())
            (pauseBtn.background as? GradientDrawable)?.setColor(0x55FFB300.toInt())
        }

        synchronized(ocrLogHistory) {
            val total = ocrLogHistory.size
            if (total == 0) {
                if (isRecognitionPaused) {
                    historyTv.text = "⏸ 识别已暂停 (暂无记录)"
                } else {
                    historyTv.text = "● 实时识别中 (等待中...)"
                }
                if (!latestOcrDebugLog.isNullOrBlank()) {
                    logTv.text = latestOcrDebugLog
                }
                return
            }

            if (viewingHistoryIndex in 0 until total) {
                val s = ocrLogHistory[viewingHistoryIndex]
                val cur = viewingHistoryIndex + 1
                historyTv.text = "[$cur/$total] ${s.timestamp} ${s.summary}"
                logTv.text = "【帧时间】: ${s.timestamp} (历史 $cur/$total)\n${s.fullLog}"
            } else {
                if (isRecognitionPaused) {
                    historyTv.text = "⏸ 已暂停 · 最新 (共 $total 帧)"
                } else {
                    historyTv.text = "● 实时 · 最新 (已录 $total 帧)"
                }
                val s = ocrLogHistory.lastOrNull()
                if (s != null) {
                    logTv.text = "【帧时间】: ${s.timestamp} (最新)\n${s.fullLog}"
                } else if (!latestOcrDebugLog.isNullOrBlank()) {
                    logTv.text = latestOcrDebugLog
                }
            }
        }
    }

    private val candidateHitWindow = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
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
            latestOcrDebugLog = "⏳ 正在初始化 PP-OCRv6 引擎..."
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val cvSuccess = OpenCVUtils.init(applicationContext)
                    if (!cvSuccess) {
                        val err = OpenCVUtils.lastError ?: "未知错误"
                        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
                        Log.e(TAG, "OpenCV native library failed to load: $err (Device ABIs: $abis)")
                        latestOcrDebugLog = "❌ OpenCV 加载失败: $err\n(设备 ABI: $abis)"
                        runOnUiThread { updateDebugLogUi() }
                        return@launch
                    }
                    val ocr = PaddleOCR.create(
                        applicationContext,
                        PaddleOCRConfig(
                            detThresh = 0.25f,
                            detBoxThresh = 0.45f,
                            recScoreThresh = 0.0f,
                            recBatchSize = 1,
                            minBoxAspectRatio = 1.25f, // 几何预过滤：排除单字方块中文（如“盒”、“片”、“OTC”等）与竖排文字
                            minBoxWidth = 32f, // 排除无法容纳 9 位数字的极短碎框
                            minBoxHeight = 8f, // 排除极小噪点文字
                            sortByCenterDistance = true, // 准心中心优先排序识别！
                            maxRecBoxes = 6, // 每帧上限识别 6 个中心候选框，彻底杜绝无目标空转卡顿
                        ),
                        EngineConfig(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                    )
                    paddleOcr = ocr
                    Log.d(TAG, "PP-OCRv6 engine initialized successfully in ${ocr.coldLoadTimeMs}ms")
                    latestOcrDebugLog = "✅ PP-OCRv6 引擎已就绪 (冷启动耗时: ${ocr.coldLoadTimeMs}ms)\n等待对焦标签..."
                    runOnUiThread { updateDebugLogUi() }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to initialize PP-OCRv6", t)
                    val errorDetail = "${t.javaClass.simpleName}: ${t.message ?: t.cause?.message ?: "未知错误"}"
                    latestOcrDebugLog = "❌ PP-OCRv6 初始化失败: $errorDetail\n请确认模型文件已正确放入 assets/models/"
                    runOnUiThread { updateDebugLogUi() }
                }
            }
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
        overlayView = overlay
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

        var debugBtn: TextView? = null
        if (BuildConfig.DEBUG) {
            // Debug Log toggle button
            debugBtn = TextView(this).apply {
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
        }

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

        if (BuildConfig.DEBUG && debugBtn != null) {
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

        fun createPillBtn(title: String, bgColor: Int, txtColor: Int, onClick: () -> Unit): TextView = TextView(this).apply {
            text = title
            setTextColor(txtColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(bgColor)
            }
            setOnClickListener { onClick() }
        }

        // Row 1: Header (Title + Pause/Resume + Copy + Close)
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(this).apply {
            text = "🐞 识别日志"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pauseBtn = createPillBtn("⏸ 暂停", 0x55FFB300.toInt(), 0xFFFFD54F.toInt()) {
            isRecognitionPaused = !isRecognitionPaused
            if (isRecognitionPaused) {
                viewingHistoryIndex = synchronized(ocrLogHistory) { ocrLogHistory.lastIndex }
                Toast.makeText(this@ScannerActivity, "识别已暂停，日志已冻结", Toast.LENGTH_SHORT).show()
            } else {
                viewingHistoryIndex = -1
                Toast.makeText(this@ScannerActivity, "已恢复实时识别", Toast.LENGTH_SHORT).show()
            }
            updateDebugLogUi()
        }
        debugPauseBtn = pauseBtn

        val copyBtn = createPillBtn("复制", 0x33FFFFFF, Color.WHITE) {
            val textToCopy = synchronized(ocrLogHistory) {
                if (viewingHistoryIndex in ocrLogHistory.indices) {
                    val s = ocrLogHistory[viewingHistoryIndex]
                    "[时间: ${s.timestamp}]\n${s.fullLog}"
                } else {
                    ocrLogHistory.lastOrNull()?.let { "[时间: ${it.timestamp}]\n${it.fullLog}" } ?: latestOcrDebugLog
                }
            }
            if (!textToCopy.isNullOrBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OCR Log", textToCopy))
                Toast.makeText(this@ScannerActivity, "已复制当前识别日志", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ScannerActivity, "暂无识别日志", Toast.LENGTH_SHORT).show()
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
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
        val pauseMargin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (6 * density).toInt()
        }
        headerLayout.addView(pauseBtn, pauseMargin)
        val copyMargin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (4 * density).toInt()
        }
        headerLayout.addView(copyBtn, copyMargin)
        headerLayout.addView(closeBtn)
        debugLogPanel.addView(headerLayout)

        // Row 2: History Navigation Toolbar
        val historyBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }

        fun createNavBtn(title: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = title
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding((7 * density).toInt(), (3 * density).toInt(), (7 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 6 * density
                setColor(0x22FFFFFF)
            }
            setOnClickListener { onClick() }
        }

        val prevBtn = createNavBtn("◀ 上帧") {
            isRecognitionPaused = true
            synchronized(ocrLogHistory) {
                if (ocrLogHistory.isNotEmpty()) {
                    if (viewingHistoryIndex == -1) {
                        viewingHistoryIndex = (ocrLogHistory.lastIndex - 1).coerceAtLeast(0)
                    } else {
                        viewingHistoryIndex = (viewingHistoryIndex - 1).coerceAtLeast(0)
                    }
                }
            }
            updateDebugLogUi()
        }
        val historyTv = TextView(this).apply {
            text = "● 实时识别中"
            setTextColor(0xFF81D4FA.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        debugHistoryTextView = historyTv

        val nextBtn = createNavBtn("下帧 ▶") {
            synchronized(ocrLogHistory) {
                if (ocrLogHistory.isNotEmpty() && viewingHistoryIndex != -1) {
                    if (viewingHistoryIndex < ocrLogHistory.lastIndex) {
                        viewingHistoryIndex++
                    } else {
                        viewingHistoryIndex = -1
                    }
                }
            }
            updateDebugLogUi()
        }
        val latestBtn = createNavBtn("最新 ⏭") {
            viewingHistoryIndex = -1
            updateDebugLogUi()
        }
        val clearBtn = createNavBtn("清空 🗑") {
            synchronized(ocrLogHistory) {
                ocrLogHistory.clear()
            }
            viewingHistoryIndex = -1
            updateDebugLogUi()
            Toast.makeText(this@ScannerActivity, "已清空记录", Toast.LENGTH_SHORT).show()
        }

        historyBarLayout.addView(prevBtn)
        val hNextMargin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (4 * density).toInt()
        }
        historyBarLayout.addView(historyTv)
        historyBarLayout.addView(nextBtn, hNextMargin)
        val hLatestMargin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (4 * density).toInt()
        }
        historyBarLayout.addView(latestBtn, hLatestMargin)
        val hClearMargin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = (4 * density).toInt()
        }
        historyBarLayout.addView(clearBtn, hClearMargin)
        debugLogPanel.addView(historyBarLayout)

        // Row 3: Log content view
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }
        val tv = TextView(this).apply {
            text = "等待相机识别...\n将包含 9 位数字的 SKU 对准绿色方框"
            setTextColor(0xFF00E676.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        debugLogTextView = tv
        scrollView.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        debugLogPanel.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (320 * density).toInt()
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
                    updateDebugLogUi()
                } else {
                    // Automatically unpause and clear candidate buffer when closing debug panel
                    isRecognitionPaused = false
                    candidateHitWindow.clear()
                    overlayView?.postInvalidate()
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
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null || delivered.get() || isRecognitionPaused) {
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
                    val viewScanBox = scannerBoxRect(viewWidth, viewHeight, ocrEnabled)
                    RectF(
                        (viewScanBox.left + dx) / scale,
                        (viewScanBox.top + dy) / scale,
                        (viewScanBox.right + dx) / scale,
                        (viewScanBox.bottom + dy) / scale
                    )
                } else {
                    scannerBoxRect(imgWidth, imgHeight, ocrEnabled)
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

                        if (!isRecognitionPaused) {
                            targetBarcode?.let { barcode ->
                                val value = barcode.rawValue
                                if (!value.isNullOrBlank()) {
                                    if (BuildConfig.DEBUG && isDebugLogOpen) {
                                        isRecognitionPaused = true
                                        viewingHistoryIndex = -1
                                        triggerVibration()
                                        val now = System.currentTimeMillis()
                                        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
                                        val barLog = "【条码识别成功】\n格式: ${barcode.format}\n内容: $value\n(当前为调试模式，已自动暂停，未填入搜索框)"
                                        latestOcrDebugLog = barLog
                                        synchronized(ocrLogHistory) {
                                            if (ocrLogHistory.size >= 25) ocrLogHistory.removeAt(0)
                                            ocrLogHistory.add(OcrSnapshot(timeStr, value, barLog, "✅ 条码: $value"))
                                        }
                                        runOnUiThread { updateDebugLogUi() }
                                    } else {
                                        handleBarcodeDetected(value, barcode.format)
                                    }
                                }
                            }
                        }
                    }
                    .addOnCompleteListener { taskFinished() }

                // 2. Offline PP-OCRv6 text recognition restricted to scanning frame (ROI cropped)
                if (isOcrActive) {
                    val ocr = paddleOcr
                    if (ocr == null) {
                        taskFinished()
                    } else if (!delivered.get() && ocrInFlight.compareAndSet(false, true)) {
                        val roiBitmap = try {
                            val rawBitmap = proxy.toBitmap()
                            val rotation = proxy.imageInfo.rotationDegrees
                            val rotatedBitmap = if (rotation != 0) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                val r = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                rawBitmap.recycle()
                                r
                            } else {
                                rawBitmap
                            }

                            // 严格限制在取景框内（无外扩边距，彻底排除药盒外部日期、批号等无关文本干扰，大幅提升单帧处理速度）
                            val cropLeft = imgScanBox.left.toInt().coerceIn(0, rotatedBitmap.width - 1)
                            val cropTop = imgScanBox.top.toInt().coerceIn(0, rotatedBitmap.height - 1)
                            val cropRight = imgScanBox.right.toInt().coerceIn(cropLeft + 1, rotatedBitmap.width)
                            val cropBottom = imgScanBox.bottom.toInt().coerceIn(cropTop + 1, rotatedBitmap.height)
                            val cropWidth = cropRight - cropLeft
                            val cropHeight = cropBottom - cropTop

                            val roi = Bitmap.createBitmap(rotatedBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                            if (roi != rotatedBitmap) {
                                rotatedBitmap.recycle()
                            }
                            roi
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to extract ROI bitmap", t)
                            latestOcrDebugLog = "❌ 提取取景框图像失败: ${t.message}"
                            null
                        }

                        taskFinished()

                        if (roiBitmap != null) {
                            lifecycleScope.launch(Dispatchers.Default) {
                                try {
                                    val sharpness = com.paddle.ocr.util.BitmapUtils.calculateSharpness(roiBitmap)
                                    if (sharpness < 6.0) {
                                        // 仅拦截剧烈大甩动导致的极度模糊帧，大幅放宽对轻微晃动/手持微抖的限制
                                        val blurMsg = "【画面状态】: ⚠️ 正在快速移动 (清晰度: ${sharpness.toInt()})，等待对准..."
                                        latestOcrDebugLog = blurMsg
                                        if (BuildConfig.DEBUG && isDebugLogOpen) {
                                            runOnUiThread { updateDebugLogUi() }
                                        }
                                        return@launch
                                    }

                                    val ocrRunResult = ocr.recognize(roiBitmap) { currentResults ->
                                        extractSkuFromPaddleOcr(currentResults, roiBitmap.height.toFloat()).sku != null
                                    }
                                    val (candidate, isExplicit, debugLog) = extractSkuFromPaddleOcr(ocrRunResult, roiBitmap.height.toFloat())

                                    val formattedLog = if (candidate != null && isDebugLogOpen) {
                                        debugLog.replace("✅ 命中 SKU: $candidate", "✅ 命中 SKU: $candidate (调试模式：已自动停止刷新，未填入搜索框)")
                                    } else {
                                        debugLog
                                    }
                                    latestOcrDebugLog = formattedLog

                                    val now = System.currentTimeMillis()
                                    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
                                    val summary = if (candidate != null) "✅ SKU: $candidate" else "未匹配 (${ocrRunResult.results.size}段文字)"
                                    val rawText = ocrRunResult.results.joinToString(" ") { it.text }.trim()
                                    if (candidate != null || now - lastRecordedOcrTime > 400L || (rawText.isNotBlank() && rawText != lastLoggedOcrText)) {
                                        lastRecordedOcrTime = now
                                        synchronized(ocrLogHistory) {
                                            if (ocrLogHistory.size >= 25) {
                                                ocrLogHistory.removeAt(0)
                                            }
                                            ocrLogHistory.add(OcrSnapshot(timeStr, candidate, formattedLog, summary))
                                        }
                                        if (rawText.isNotBlank()) {
                                            lastLoggedOcrText = rawText
                                        }
                                    }

                                    if (BuildConfig.DEBUG && isDebugLogOpen) {
                                        if (candidate != null && !isRecognitionPaused) {
                                            isRecognitionPaused = true
                                            viewingHistoryIndex = -1
                                            triggerVibration()
                                        }
                                        runOnUiThread {
                                            updateDebugLogUi()
                                        }
                                    } else {
                                        if (rawText.isNotBlank() && rawText != lastLoggedOcrText) {
                                            val res = candidate ?: "none"
                                            Log.d("ScannerOCR", "raw=$rawText; candidate=$res")
                                        }
                                        if (candidate != null && !isRecognitionPaused) {
                                            handleCandidateDetected(candidate, isExplicit = isExplicit)
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "PP-OCRv6 recognition failed", t)
                                    latestOcrDebugLog = "❌ PP-OCRv6 推理异常: ${t.message}"
                                    runOnUiThread { updateDebugLogUi() }
                                } finally {
                                    roiBitmap.recycle()
                                    ocrInFlight.set(false)
                                }
                            }
                        } else {
                            ocrInFlight.set(false)
                        }
                    } else {
                        taskFinished()
                    }
                }
            }
            provider.unbindAll()
            currentCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            currentCamera?.let { cam ->
                previewView.post {
                    val factory = previewView.meteringPointFactory
                    val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                    val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleCandidateDetected(candidate: String, isExplicit: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { handleCandidateDetected(candidate, isExplicit) }
            return
        }
        if (delivered.get()) return

        // 无论是显式带标签（SKU:/编号:/货号:）还是框内纯 9 位数字，
        // 经过严格的 9 位校验、排除规则与加宽取景框范围限定，均支持单帧立即出结果，
        // 彻底解决手持轻微晃动无法凑齐多帧导致识别率不高的问题（实现 ~100ms 级极速秒出）
        deliverResult(candidate)
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
        lifecycleScope.launch(Dispatchers.IO) {
            ocr?.release()
        }
        cameraExecutor.shutdown()
        debugLogTextView = null
        debugHistoryTextView = null
        debugPauseBtn = null
        overlayView = null
        ocrLogHistory.clear()
        candidateHitWindow.clear()
        super.onDestroy()
    }

    private fun deliverResult(resultText: String) {
        if (!delivered.compareAndSet(false, true)) return
        triggerVibration()
        val data = Intent().putExtra(SCAN_RESULT, resultText)
        setResult(RESULT_OK, data)
        finish()
    }

    private inner class ScannerOverlayView(context: Context, private val ocrEnabled: Boolean) : View(context) {
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
            val box = scannerBoxRect(width.toFloat(), height.toFloat(), ocrEnabled)
            val left = box.left
            val top = box.top
            val right = box.right
            val bottom = box.bottom
            val cornerLength = 24f * resources.displayMetrics.density

            // Dark semi-transparent background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

            // Transparent hole inside scanning box
            canvas.drawRect(left, top, right, bottom, transparentPaint)

            // Frame border (Amber when paused, Green when scanning)
            framePaint.color = if (isRecognitionPaused) 0xFFFFB300.toInt() else 0xFF4CAF50.toInt()
            canvas.drawRect(left, top, right, bottom, framePaint)

            // Draw 4 corner highlights
            cornerPaint.color = if (isRecognitionPaused) 0xFFFFD54F.toInt() else 0xFF00E676.toInt()
            canvas.drawLine(left, top + cornerLength, left, top, cornerPaint)
            canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)
            canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)
            canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)
            canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)

            // Text hint below frame
            val hint = if (isRecognitionPaused) {
                "⏸ 识别已暂停 (正在分析日志，点击继续恢复)"
            } else if (ocrEnabled) {
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
        // SKU prefix pattern supporting only SKU and its common OCR confusion variants:
        // SKU, SHU, SU, 5KU, 5HU, 5U, S0, SK0, SH0, SK, SH, KU, HU,
        // with dots/dashes/spaces e.g. S.K.U, S-K-U, S/K/U, S H U, S U
        private const val SKU_PREFIX_RAW =
            """(?:[S5$][\s.\-_/]*[KHXkhx]?[\s.\-_/]*[U0OVuv]?|[KHXkhx][\s.\-_/]*[U0OVuv])"""

        private val skuLabelRegex = Regex(
            """(?i)(?:^|[^a-zA-Z0-9\u4e00-\u9fa5])$SKU_PREFIX_RAW(?::|：|#|\s|$)"""
        )
        private val multilineSkuRegex = Regex(
            """(?i)$SKU_PREFIX_RAW[\s:：#\-_/|]*([0-9A-Za-z|!〇\s.\-_]{8,24})"""
        )
        private val candidate9Pattern = Regex(
            """(?<![a-zA-Z0-9])[0-9OolILsSbBrRcCzZgGqQtTDd|!〇]{9}(?![a-zA-Z0-9])"""
        )
        private val standalone9Pattern = Regex("""(?<!\d)[0-9]{9}(?!\d)""")

        // Exclusion pattern for lines containing irrelevant text/numbers (UPC, barcodes, phones, orders, dates, amounts, 货号, 编码, etc.)
        private val excludeLinePattern = Regex(
            """(?i)(?:UPC|条码|条形码|EAN|手机|电话|虚拟号|备用|订单|时间|日期|运单号|单号|快递|金额|合计|应收|实收|找零|流水|原价|已付款|货号|物料|(?:商品)?编码)"""
        )

        private val tokenCharPattern = Regex("""(?<=[0-9A-Za-z|!〇])\s+(?=[0-9A-Za-z|!〇])""")

        private fun isInsideScanBox(itemRect: RectF, scanBox: RectF): Boolean {
            val cx = itemRect.centerX()
            val cy = itemRect.centerY()
            return cx >= scanBox.left && cx <= scanBox.right &&
                   cy >= scanBox.top && cy <= scanBox.bottom
        }

        internal fun cleanDigits(token: String): String {
            return token.map { c ->
                when (c) {
                    'O', 'o', 'C', 'c', 'D', 'd', 'Q', 'q', '〇' -> '0'
                    'I', 'l', '|', 'i', '!', 'J', 'j' -> '1'
                    'Z', 'z' -> '2'
                    'E' -> '3'
                    'S', 's', '$' -> '5'
                    'b', 'G' -> '6'
                    'T', 't' -> '7'
                    'B', 'R', 'r' -> '8'
                    'g', 'q' -> '9'
                    else -> c
                }
            }.filter(Char::isDigit).joinToString("")
        }

        internal fun normalizeOcrText(text: String): String {
            return text.map { char ->
                when (char) {
                    '\u3000', '\u00A0' -> ' '
                    '〇' -> '0'
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
            val distanceToCenter: Float,
            val isExplicit: Boolean = false
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
                    isExcluded = excludeLinePattern.containsMatchIn(joinedText) && !skuLabelRegex.containsMatchIn(joinedText)
                )
            }.sortedBy { it.top }
        }

        data class OcrExtractionResult(
            val sku: String?,
            val isExplicitLabel: Boolean = false,
            val debugText: String
        )

        fun extractSkuFromPaddleOcr(ocrRunResult: OCRRunResult, boxHeight: Float): OcrExtractionResult {
            return extractSkuFromPaddleOcr(
                results = ocrRunResult.results,
                boxHeight = boxHeight,
                detectionTimeMs = ocrRunResult.detectionTimeMs,
                recognitionTimeMs = ocrRunResult.recognitionTimeMs,
                totalTimeMs = ocrRunResult.totalTimeMs,
                totalDetectedBoxes = ocrRunResult.totalDetectedBoxes,
                recognizedBoxCount = ocrRunResult.recognizedBoxCount,
                earlyStopped = ocrRunResult.earlyStopped,
            )
        }

        fun extractSkuFromPaddleOcr(
            results: List<com.paddle.ocr.model.OCRResult>,
            boxHeight: Float,
            detectionTimeMs: Long = 0L,
            recognitionTimeMs: Long = 0L,
            totalTimeMs: Long = 0L,
            totalDetectedBoxes: Int = 0,
            recognizedBoxCount: Int = 0,
            earlyStopped: Boolean = false,
        ): OcrExtractionResult {
            if (results.isEmpty()) {
                return OcrExtractionResult(null, false, "【框内文本】: 暂未检测到文字")
            }

            val elements = mutableListOf<RawElement>()
            for (item in results) {
                val lineText = item.text.trim()
                if (lineText.isEmpty()) continue
                val pts = item.box.points
                val left = pts.minOf { it.x }
                val top = pts.minOf { it.y }
                val right = pts.maxOf { it.x }
                val bottom = pts.maxOf { it.y }
                val rect = RectF(left, top, right, bottom)
                val normalized = normalizeOcrText(lineText)
                elements.add(RawElement(normalized, rect))
            }

            if (elements.isEmpty()) {
                return OcrExtractionResult(null, false, "【框内文本】: 绿框内未包含任何文字\n(请将 SKU 移入绿框)")
            }

            val logicalRows = buildLogicalRows(elements)
            val boxCenterY = boxHeight / 2f

            val skuCandidates = mutableListOf<CandidateResult>()

            // --- 1. SKU Extraction (Strict 9 digits only) ---
            for (i in logicalRows.indices) {
                val row = logicalRows[i]
                if (row.isExcluded) continue

                // Normalize spaces between alphanumerics/digits on the row (e.g. "303 A27 503" -> "303A27503")
                val collapsed = row.text.replace(tokenCharPattern, "")

                // 1.1 Regex match on row text
                multilineSkuRegex.findAll(collapsed).forEach { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(row.centerY - boxCenterY), isExplicit = true))
                    }
                }

                // 1.2 If row contains SKU label, check remaining text on the same row or subsequent rows
                if (skuLabelRegex.containsMatchIn(collapsed)) {
                    val afterLabel = skuLabelRegex.replace(collapsed, " ")
                    val cleaned = cleanDigits(afterLabel)
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, kotlin.math.abs(row.centerY - boxCenterY), isExplicit = true))
                    }
                    for (match in candidate9Pattern.findAll(afterLabel)) {
                        val c = cleanDigits(match.value)
                        if (c.length == 9) {
                            skuCandidates.add(CandidateResult(c, kotlin.math.abs(row.centerY - boxCenterY), isExplicit = true))
                        }
                    }

                    // Also check next 1-2 rows
                    for (offset in 1..2) {
                        val nextRow = logicalRows.getOrNull(i + offset) ?: break
                        if (nextRow.isExcluded) continue
                        val nextCollapsed = nextRow.text.replace(tokenCharPattern, "")
                        val nextCleaned = cleanDigits(nextCollapsed)
                        if (nextCleaned.length == 9) {
                            skuCandidates.add(CandidateResult(nextCleaned, kotlin.math.abs(nextRow.centerY - boxCenterY), isExplicit = true))
                        } else {
                            for (match in candidate9Pattern.findAll(nextCollapsed)) {
                                val c = cleanDigits(match.value)
                                if (c.length == 9) {
                                    skuCandidates.add(CandidateResult(c, kotlin.math.abs(nextRow.centerY - boxCenterY), isExplicit = true))
                                }
                            }
                        }
                    }
                }
            }

            // 1.3 Combined text regex
            if (skuCandidates.isEmpty()) {
                val combinedText = logicalRows.filter { !it.isExcluded }.joinToString("\n") {
                    it.text.replace(tokenCharPattern, "")
                }
                multilineSkuRegex.findAll(combinedText).forEach { match ->
                    val cleaned = cleanDigits(match.groupValues[1])
                    if (cleaned.length == 9) {
                        skuCandidates.add(CandidateResult(cleaned, 0f, isExplicit = true))
                    }
                }
            }

            // 1.4 Standalone 9 pure digits on non-excluded rows (without long digit interference)
            if (skuCandidates.isEmpty()) {
                for (row in logicalRows) {
                    if (row.isExcluded) continue
                    val collapsed = row.text.replace(tokenCharPattern, "")
                    if (Regex("""\d{10,}""").containsMatchIn(collapsed)) continue

                    // 1.4.1 Direct standalone 9 pure digits
                    for (match in standalone9Pattern.findAll(collapsed)) {
                        skuCandidates.add(CandidateResult(match.value, kotlin.math.abs(row.centerY - boxCenterY), isExplicit = false))
                    }

                    // 1.4.2 Standalone 9 digits with confusable letters (e.g. 3048285O3, 30482850B)
                    for (match in candidate9Pattern.findAll(collapsed)) {
                        val c = cleanDigits(match.value)
                        if (c.length == 9) {
                            skuCandidates.add(CandidateResult(c, kotlin.math.abs(row.centerY - boxCenterY), isExplicit = false))
                        }
                    }
                }
            }

            val bestCandidate = if (skuCandidates.isNotEmpty()) {
                skuCandidates.sortedWith(
                    compareByDescending<CandidateResult> { it.isExplicit }
                        .thenBy { it.distanceToCenter }
                ).first()
            } else {
                null
            }
            val finalSku = bestCandidate?.code
            val isExplicit = bestCandidate?.isExplicit ?: false

            val sb = java.lang.StringBuilder()
            sb.append("【PP-OCRv6 耗时】: 检测 ${detectionTimeMs}ms, 识别 ${recognitionTimeMs}ms (总计 ${totalTimeMs}ms)\n")
            if (totalDetectedBoxes > 0) {
                val skipped = (totalDetectedBoxes - recognizedBoxCount).coerceAtLeast(0)
                val earlyStopTag = if (earlyStopped) "，命中中心目标立即熔断" else ""
                sb.append("【识别加速】: 检测 ${totalDetectedBoxes} 框，过滤/跳过 ${skipped} 个无关框${earlyStopTag} (实际仅识别 ${recognizedBoxCount} 框)\n")
            }
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
                val tag = if (isExplicit) " [有前缀: 1帧即出]" else " [纯数字: 1帧即出]"
                sb.append("✅ 命中 SKU: ").append(finalSku).append(tag)
            } else {
                sb.append("❌ 未检测到 9 位 SKU")
            }

            return OcrExtractionResult(finalSku, isExplicit, sb.toString())
        }

        fun extractSku(rawText: String?): String? {
            if (rawText.isNullOrBlank()) return null
            val normalized = normalizeOcrText(rawText)
            val lines = normalized.split(Regex("[\\r\\n]+"))
                .map { it.trim().replace(tokenCharPattern, "") }
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
                for (match in candidate9Pattern.findAll(line)) {
                    val c = cleanDigits(match.value)
                    if (c.length == 9) return c
                }
            }

            return null
        }
    }
}
