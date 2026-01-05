package com.godtap.dictionary.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.*

/**
 * Invisible gesture detection zone
 * Detects multi-finger swipes without blocking touches
 */
class InvisibleGestureZone(private val context: Context) {
    private val TAG = "InvisibleGestureZone"
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false
    
    // Gesture tracking
    private var gestureStartTime = 0L
    private val gestureStartX = mutableMapOf<Int, Float>()
    private val gestureStartY = mutableMapOf<Int, Float>()
    private var maxFingerCount = 0
    
    // Callbacks
    var onTwoFingerSwipeRight: (() -> Unit)? = null
    var onTwoFingerSwipeLeft: (() -> Unit)? = null
    var onTwoFingerSwipeUp: (() -> Unit)? = null
    var onThreeFingerSwipeRight: (() -> Unit)? = null
    var onThreeFingerSwipeLeft: (() -> Unit)? = null
    var onThreeFingerSwipeUp: (() -> Unit)? = null
    
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Create invisible view that covers entire screen
            overlayView = object : View(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            // First finger down
                            gestureStartTime = System.currentTimeMillis()
                            gestureStartX.clear()
                            gestureStartY.clear()
                            maxFingerCount = 1
                            gestureStartX[event.getPointerId(0)] = event.rawX
                            gestureStartY[event.getPointerId(0)] = event.rawY
                            Log.d(TAG, "👆 First finger down at (${event.rawX}, ${event.rawY})")
                            return false // Don't consume - let touch pass through
                        }
                        
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            // Additional finger down
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            gestureStartX[pointerId] = event.getX(pointerIndex) + event.rawX - event.x
                            gestureStartY[pointerId] = event.getY(pointerIndex) + event.rawY - event.y
                            maxFingerCount = maxOf(maxFingerCount, event.pointerCount)
                            Log.i(TAG, "👆👆 Finger ${event.pointerCount} down (max: $maxFingerCount)")
                            return false // Don't consume
                        }
                        
                        MotionEvent.ACTION_UP -> {
                            // Last finger up - detect gesture
                            if (maxFingerCount >= 2) {
                                Log.i(TAG, "🎯 ALL FINGERS UP - Detecting $maxFingerCount-finger gesture")
                                detectGesture(event)
                            }
                            gestureStartX.clear()
                            gestureStartY.clear()
                            maxFingerCount = 0
                            return false // Don't consume
                        }
                        
                        MotionEvent.ACTION_POINTER_UP -> {
                            return false // Don't consume
                        }
                        
                        MotionEvent.ACTION_CANCEL -> {
                            Log.d(TAG, "❌ Gesture cancelled")
                            gestureStartX.clear()
                            gestureStartY.clear()
                            maxFingerCount = 0
                            return false // Don't consume
                        }
                    }
                    return false // CRITICAL: Always return false to pass touches through!
                }
                
                private fun detectGesture(event: MotionEvent) {
                    val duration = System.currentTimeMillis() - gestureStartTime
                    
                    // Calculate average movement
                    var totalDeltaX = 0f
                    var totalDeltaY = 0f
                    var count = 0
                    
                    for ((pointerId, startX) in gestureStartX) {
                        val startY = gestureStartY[pointerId] ?: continue
                        // Use last known position for this pointer
                        var endX = startX
                        var endY = startY
                        for (i in 0 until event.pointerCount) {
                            if (event.getPointerId(i) == pointerId) {
                                endX = event.getX(i) + event.rawX - event.x
                                endY = event.getY(i) + event.rawY - event.y
                                break
                            }
                        }
                        totalDeltaX += (endX - startX)
                        totalDeltaY += (endY - startY)
                        count++
                    }
                    
                    if (count == 0) {
                        Log.d(TAG, "⊘ No movement data")
                        return
                    }
                    
                    val avgDeltaX = totalDeltaX / count
                    val avgDeltaY = totalDeltaY / count
                    val minSwipeDistance = 200f // pixels
                    
                    Log.i(TAG, "📊 Gesture: fingers=$maxFingerCount, dx=$avgDeltaX, dy=$avgDeltaY, duration=${duration}ms")
                    
                    // Detect swipe direction
                    val absX = Math.abs(avgDeltaX)
                    val absY = Math.abs(avgDeltaY)
                    
                    if (absX < minSwipeDistance && absY < minSwipeDistance) {
                        Log.d(TAG, "⊘ Movement too small ($absX x $absY)")
                        return
                    }
                    
                    if (absX > absY) {
                        // Horizontal swipe
                        if (avgDeltaX > 0) {
                            handleSwipeRight(maxFingerCount)
                        } else {
                            handleSwipeLeft(maxFingerCount)
                        }
                    } else {
                        // Vertical swipe
                        if (avgDeltaY < 0) {
                            handleSwipeUp(maxFingerCount)
                        }
                    }
                }
                
                private fun handleSwipeRight(fingers: Int) {
                    Log.i(TAG, "╔═══════════════════════════════════════════╗")
                    Log.i(TAG, "║ ✅ $fingers-FINGER SWIPE RIGHT             ║")
                    Log.i(TAG, "╚═══════════════════════════════════════════╝")
                    when (fingers) {
                        2 -> onTwoFingerSwipeRight?.invoke()
                        3 -> onThreeFingerSwipeRight?.invoke()
                    }
                }
                
                private fun handleSwipeLeft(fingers: Int) {
                    Log.i(TAG, "╔═══════════════════════════════════════════╗")
                    Log.i(TAG, "║ ✅ $fingers-FINGER SWIPE LEFT              ║")
                    Log.i(TAG, "╚═══════════════════════════════════════════╝")
                    when (fingers) {
                        2 -> onTwoFingerSwipeLeft?.invoke()
                        3 -> onThreeFingerSwipeLeft?.invoke()
                    }
                }
                
                private fun handleSwipeUp(fingers: Int) {
                    Log.i(TAG, "╔═══════════════════════════════════════════╗")
                    Log.i(TAG, "║ ✅ $fingers-FINGER SWIPE UP                ║")
                    Log.i(TAG, "╚═══════════════════════════════════════════╝")
                    when (fingers) {
                        2 -> onTwoFingerSwipeUp?.invoke()
                        3 -> onThreeFingerSwipeUp?.invoke()
                    }
                }
            }
            
            // Window parameters - invisible, doesn't block touches
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            
            windowManager?.addView(overlayView, params)
            isShowing = true
            
            Log.i(TAG, "✅ Invisible gesture zone activated!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing gesture zone", e)
        }
    }
    
    fun hide() {
        if (!isShowing) return
        
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
            isShowing = false
            Log.i(TAG, "Gesture zone hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding gesture zone", e)
        }
    }
    
    fun isShowing() = isShowing
}
