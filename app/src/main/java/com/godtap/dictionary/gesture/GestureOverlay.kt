package com.godtap.dictionary.gesture

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Invisible overlay window that intercepts touch events for gesture detection.
 * Works on all Android versions (not just API 34+)
 * 
 * This is the recommended approach for gesture detection in accessibility services
 * on Android versions before API 34.
 */
class GestureOverlay(
    private val context: Context,
    private val listener: GestureListener
) {
    companion object {
        private const val TAG = "GestureOverlay"
        
        // Gesture thresholds
        private const val SWIPE_MIN_DISTANCE = 100f // Minimum pixels for swipe
        private const val SWIPE_MAX_OFF_PATH = 200f // Maximum perpendicular deviation
    }
    
    interface GestureListener {
        fun onTwoFingerSwipeRight()  // OCR mode
        fun onTwoFingerSwipeLeft()   // Toggle underlining
        fun onTwoFingerSwipeUp()     // Toggle service on/off
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isShowing = false
    
    // Gesture tracking
    private var gestureStartTime = 0L
    private val gestureStartX = mutableMapOf<Int, Float>()
    private val gestureStartY = mutableMapOf<Int, Float>()
    
    /**
     * Show the invisible overlay to start intercepting touch events
     */
    fun show() {
        if (isShowing) {
            Log.d(TAG, "Overlay already showing")
            return
        }
        
        try {
            // Create full-screen overlay for gesture detection
            // Only shown when user activates via notification (opt-in)
            overlayView = object : View(context) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    event?.let {
                        handleTouchEvent(it)
                    }
                    // Consume multi-touch events, pass through single-touch
                    return event?.pointerCount ?: 0 >= 2
                }
            }.apply {
                // Completely transparent
                alpha = 0f
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                // NOT_TOUCH_MODAL: Allows single-touch to pass through
                // We consume only when pointerCount >= 2
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            
            windowManager.addView(overlayView, params)
            isShowing = true
            Log.i(TAG, "✓ Gesture overlay active (10s window, optimized blocking)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show gesture overlay: ${e.message}", e)
        }
    }
    
    /**
     * Hide the overlay and stop intercepting touches
     */
    fun hide() {
        if (!isShowing) return
        
        try {
            overlayView?.let {
                windowManager.removeView(it)
            }
            overlayView = null
            isShowing = false
            Log.i(TAG, "Gesture overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide gesture overlay: ${e.message}", e)
        }
    }
    
    /**
     * Handle touch events from the overlay
     * Optimized: Single-touch events are passed through in onTouchEvent
     */
    private fun handleTouchEvent(event: MotionEvent) {
        // LOG ALL TOUCH EVENTS WITH FULL DETAILS
        val action = event.actionMasked
        val actionName = when (action) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_POINTER_DOWN -> "ACTION_POINTER_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "ACTION_POINTER_UP"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            else -> "UNKNOWN($action)"
        }
        
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "TOUCH EVENT: $actionName")
        Log.i(TAG, "  Pointer Count: ${event.pointerCount}")
        Log.i(TAG, "  Event Time: ${event.eventTime}")
        for (i in 0 until event.pointerCount) {
            Log.i(TAG, "  Finger $i: (${event.getX(i)}, ${event.getY(i)}) ID=${event.getPointerId(i)}")
        }
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartTime = System.currentTimeMillis()
                gestureStartX.clear()
                gestureStartY.clear()
                gestureStartX[event.getPointerId(0)] = event.x
                gestureStartY[event.getPointerId(0)] = event.y
                Log.d(TAG, "Gesture START: 1 finger at (${event.x}, ${event.y})")
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                gestureStartX[pointerId] = event.getX(pointerIndex)
                gestureStartY[pointerId] = event.getY(pointerIndex)
                Log.d(TAG, "Gesture: ${event.pointerCount} fingers detected")
            }
            
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Gesture END: Detecting with ${gestureStartX.size} fingers")
                detectGesture(event)
                gestureStartX.clear()
                gestureStartY.clear()
            }
            
            MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "Gesture CANCELED")
                gestureStartX.clear()
                gestureStartY.clear()
            }
        }
    }
    
    /**
     * Detect gesture type based on finger count and movement
     */
    private fun detectGesture(event: MotionEvent) {
        val fingerCount = gestureStartX.size
        val duration = System.currentTimeMillis() - gestureStartTime
        
        Log.d(TAG, "Detecting gesture: $fingerCount fingers, ${duration}ms")
        
        when (fingerCount) {
            2 -> detectTwoFingerGesture(event, duration)
            else -> Log.d(TAG, "Gesture ignored: Only 2-finger swipes supported (got $fingerCount fingers)")
        }
    }
    
    private fun detectTwoFingerGesture(event: MotionEvent, duration: Long) {
        val (avgDeltaX, avgDeltaY) = calculateAverageMovement(event)
        
        Log.d(TAG, "2-finger: ΔX=$avgDeltaX, ΔY=$avgDeltaY")
        
        // Determine primary direction and check if movement is significant
        val isHorizontal = abs(avgDeltaX) > abs(avgDeltaY)
        
        if (isHorizontal) {
            // Horizontal swipe: check left or right
            if (avgDeltaX > SWIPE_MIN_DISTANCE && abs(avgDeltaY) < SWIPE_MAX_OFF_PATH) {
                // Swipe RIGHT → OCR Mode
                Log.i(TAG, "╔══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 2-FINGER SWIPE RIGHT → OCR MODE      ║")
                Log.i(TAG, "╚══════════════════════════════════════════╝")
                listener.onTwoFingerSwipeRight()
            } else if (avgDeltaX < -SWIPE_MIN_DISTANCE && abs(avgDeltaY) < SWIPE_MAX_OFF_PATH) {
                // Swipe LEFT → Toggle Underlining
                Log.i(TAG, "╔══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 2-FINGER SWIPE LEFT → UNDERLINE      ║")
                Log.i(TAG, "╚══════════════════════════════════════════╝")
                listener.onTwoFingerSwipeLeft()
            }
        } else {
            // Vertical swipe: check up or down
            if (avgDeltaY < -SWIPE_MIN_DISTANCE && abs(avgDeltaX) < SWIPE_MAX_OFF_PATH) {
                // Swipe UP → Toggle Service On/Off
                Log.i(TAG, "╔══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 2-FINGER SWIPE UP → TOGGLE ON/OFF    ║")
                Log.i(TAG, "╚══════════════════════════════════════════╝")
                listener.onTwoFingerSwipeUp()
            }
            // Down swipe reserved for future use
        }
    }
    
    /**
     * Calculate average movement across all tracked fingers
     */
    private fun calculateAverageMovement(event: MotionEvent): Pair<Float, Float> {
        var totalDeltaX = 0f
        var totalDeltaY = 0f
        var count = 0
        
        for ((pointerId, startX) in gestureStartX) {
            val startY = gestureStartY[pointerId] ?: continue
            
            // Find this pointer in the current event
            for (i in 0 until event.pointerCount) {
                if (event.getPointerId(i) == pointerId) {
                    val deltaX = event.getX(i) - startX
                    val deltaY = event.getY(i) - startY
                    totalDeltaX += deltaX
                    totalDeltaY += deltaY
                    count++
                    break
                }
            }
        }
        
        return if (count > 0) {
            Pair(totalDeltaX / count, totalDeltaY / count)
        } else {
            Pair(0f, 0f)
        }
    }
}
