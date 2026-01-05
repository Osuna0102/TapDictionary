package com.godtap.dictionary.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

/**
 * OCR area selection overlay.
 * Shows a full-screen overlay where user can tap-drag to select a rectangle area.
 * The selected area is then captured and processed with ML Kit Text Recognition.
 */
class OcrSelectionOverlay(
    private val context: Context,
    private val onTextRecognized: (String, Rect) -> Unit,
    private val takeScreenshotCallback: ((Rect, (Bitmap?) -> Unit) -> Unit)? = null
) {
    
    companion object {
        private const val TAG = "OcrSelectionOverlay"
        private const val SELECTION_TIMEOUT = 10000L // 10 seconds to make selection
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var overlayView: SelectionView? = null
    private var isShowing = false
    
    // ML Kit Text Recognizer
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Show the OCR area selection overlay
     */
    fun show() {
        if (isShowing) {
            Log.w(TAG, "Overlay already showing")
            return
        }
        
        Log.d(TAG, "Showing OCR selection overlay")
        
        handler.post {
            try {
                // Create selection view
                val view = SelectionView(context) { rect ->
                    // User completed selection
                    captureAndRecognizeText(rect)
                }
                
                // Create full-screen overlay params
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                
                windowManager.addView(view, params)
                overlayView = view
                isShowing = true
                
                // Auto-dismiss after timeout
                handler.postDelayed({
                    dismiss()
                }, SELECTION_TIMEOUT)
                
                Log.d(TAG, "OCR selection overlay shown")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing OCR overlay", e)
            }
        }
    }
    
    /**
     * Dismiss the overlay
     */
    fun dismiss() {
        if (!isShowing) return
        
        Log.d(TAG, "Dismissing OCR selection overlay")
        
        handler.post {
            try {
                overlayView?.let { view ->
                    windowManager.removeView(view)
                }
                overlayView = null
                isShowing = false
                
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing overlay", e)
            }
        }
    }
    
    /**
     * Capture screenshot of selected area and recognize text
     */
    private fun captureAndRecognizeText(rect: Rect) {
        Log.d(TAG, "Capturing area: $rect")
        
        ocrScope.launch {
            try {
                // Get screenshot of the selected area
                val bitmap = captureScreenArea(rect)
                
                if (bitmap != null) {
                    // Run ML Kit text recognition
                    val recognizedText = recognizeText(bitmap)
                    
                    if (recognizedText.isNotBlank()) {
                        Log.d(TAG, "Recognized text: $recognizedText")
                        withContext(Dispatchers.Main) {
                            onTextRecognized(recognizedText, rect)
                        }
                    } else {
                        Log.d(TAG, "No text recognized")
                    }
                    
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Failed to capture screenshot")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in text recognition", e)
            } finally {
                dismiss()
            }
        }
    }
    
    /**
     * Capture screenshot of specific screen area
     * Uses AccessibilityService's takeScreenshot() API (Android 11+)
     */
    private suspend fun captureScreenArea(rect: Rect): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (takeScreenshotCallback == null) {
                Log.e(TAG, "Screenshot callback not provided")
                return@withContext null
            }
            
            // Use continuation to await screenshot
            var screenshot: Bitmap? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            
            withContext(Dispatchers.Main) {
                takeScreenshotCallback.invoke(rect) { bitmap ->
                    screenshot = bitmap
                    latch.countDown()
                }
            }
            
            // Wait for screenshot (with timeout)
            val captured = latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            if (!captured) {
                Log.e(TAG, "Screenshot timeout")
                return@withContext null
            }
            
            screenshot
            
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            null
        }
    }
    
    /**
     * Recognize text from bitmap using ML Kit
     */
    private suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Use kotlinx-coroutines-play-services await() extension
            val visionText = textRecognizer.process(image).await()
            
            Log.d(TAG, "Text recognition successful: ${visionText.text}")
            visionText.text
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text", e)
            ""
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        dismiss()
        textRecognizer.close()
    }
    
    /**
     * Custom view for drawing selection rectangle
     */
    private class SelectionView(
        context: Context,
        private val onSelectionComplete: (Rect) -> Unit
    ) : View(context) {
        
        private val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.CYAN
        }
        
        private val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(50, 0, 255, 255) // Semi-transparent cyan
        }
        
        private val instructionPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var isSelecting = false
        
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isSelecting = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        endX = event.x
                        endY = event.y
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isSelecting) {
                        endX = event.x
                        endY = event.y
                        isSelecting = false
                        
                        // Create rectangle from selection
                        val rect = Rect(
                            min(startX, endX).toInt(),
                            min(startY, endY).toInt(),
                            max(startX, endX).toInt(),
                            max(startY, endY).toInt()
                        )
                        
                        // Only process if selection is significant
                        if (rect.width() > 50 && rect.height() > 50) {
                            onSelectionComplete(rect)
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            // Draw semi-transparent background
            canvas.drawColor(Color.argb(128, 0, 0, 0))
            
            // Draw instructions
            if (!isSelecting) {
                canvas.drawText(
                    "Tap and drag to select text area",
                    width / 2f,
                    height / 2f,
                    instructionPaint
                )
            }
            
            // Draw selection rectangle
            if (isSelecting || (startX != endX && startY != endY)) {
                val left = min(startX, endX)
                val top = min(startY, endY)
                val right = max(startX, endX)
                val bottom = max(startY, endY)
                
                // Fill
                canvas.drawRect(left, top, right, bottom, fillPaint)
                // Stroke
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }
}
