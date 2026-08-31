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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private lateinit var previewView: PreviewView

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
        val root = FrameLayout(this)
        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val overlay = ScannerOverlayView(this)
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
                scanner.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstOrNull()?.rawValue
                        if (!value.isNullOrBlank() && delivered.compareAndSet(false, true)) {
                            triggerVibration()
                            setResult(RESULT_OK, Intent().putExtra(SCAN_RESULT, value))
                            finish()
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        scanner.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private class ScannerOverlayView(context: Context) : View(context) {
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
            // Top-Left
            canvas.drawLine(left, top + cornerLength, left, top, cornerPaint)
            canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)
            // Top-Right
            canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)
            // Bottom-Left
            canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)
            // Bottom-Right
            canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)

            // Text hint below frame
            canvas.drawText("将条形码或二维码放入框内即可自动扫描", width / 2f, bottom + 70f, textPaint)
        }
    }

    companion object {
        const val SCAN_RESULT = "scan_result"
    }
}
