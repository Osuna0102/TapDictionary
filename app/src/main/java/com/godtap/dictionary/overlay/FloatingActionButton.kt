package com.godtap.dictionary.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.godtap.dictionary.R

/**
 * Floating action button overlay for quick actions
 * Works reliably on all devices including MIUI
 */
class FloatingActionButton(private val context: Context) {
    private val TAG = "FloatingActionButton"
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false
    
    // Callbacks for button actions
    var onOcrClick: (() -> Unit)? = null
    var onUnderlineToggle: (() -> Unit)? = null
    var onServiceToggle: (() -> Unit)? = null
    
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Create floating button view
            overlayView = object : FrameLayout(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                private var expanded = false
                private var initialX = 0f
                private var initialY = 0f
                private var touchX = 0f
                private var touchY = 0f
                
                init {
                    paint.color = Color.parseColor("#2196F3")
                    paint.style = Paint.Style.FILL
                    setWillNotDraw(false)
                }
                
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    
                    if (expanded) {
                        // Draw expanded menu with 3 buttons
                        val buttonRadius = 50f
                        val spacing = 150f
                        
                        // Main button
                        paint.color = Color.parseColor("#2196F3")
                        canvas.drawCircle(width / 2f, height / 2f, buttonRadius, paint)
                        
                        // OCR button (top)
                        paint.color = Color.parseColor("#4CAF50")
                        canvas.drawCircle(width / 2f, height / 2f - spacing, buttonRadius * 0.8f, paint)
                        
                        // Underline button (left)
                        paint.color = Color.parseColor("#FF9800")
                        canvas.drawCircle(width / 2f - spacing, height / 2f, buttonRadius * 0.8f, paint)
                        
                        // Service toggle button (right)
                        paint.color = Color.parseColor("#F44336")
                        canvas.drawCircle(width / 2f + spacing, height / 2f, buttonRadius * 0.8f, paint)
                    } else {
                        // Draw collapsed button
                        paint.color = Color.parseColor("#2196F3")
                        canvas.drawCircle(width / 2f, height / 2f, 50f, paint)
                        
                        // Draw icon (3 horizontal lines)
                        paint.color = Color.WHITE
                        paint.strokeWidth = 6f
                        canvas.drawLine(width / 2f - 20, height / 2f - 15, width / 2f + 20, height / 2f - 15, paint)
                        canvas.drawLine(width / 2f - 20, height / 2f, width / 2f + 20, height / 2f, paint)
                        canvas.drawLine(width / 2f - 20, height / 2f + 15, width / 2f + 20, height / 2f + 15, paint)
                    }
                }
                
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = (layoutParams as WindowManager.LayoutParams).x.toFloat()
                            initialY = (layoutParams as WindowManager.LayoutParams).y.toFloat()
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        
                        MotionEvent.ACTION_MOVE -> {
                            val params = layoutParams as WindowManager.LayoutParams
                            params.x = (initialX + (event.rawX - touchX)).toInt()
                            params.y = (initialY + (event.rawY - touchY)).toInt()
                            windowManager?.updateViewLayout(this, params)
                            return true
                        }
                        
                        MotionEvent.ACTION_UP -> {
                            val deltaX = Math.abs(event.rawX - touchX)
                            val deltaY = Math.abs(event.rawY - touchY)
                            
                            if (deltaX < 20 && deltaY < 20) {
                                // It's a click, not a drag
                                if (expanded) {
                                    // Check which button was clicked
                                    val buttonRadius = 50f
                                    val spacing = 150f
                                    val centerX = width / 2f
                                    val centerY = height / 2f
                                    
                                    val clickX = event.x
                                    val clickY = event.y
                                    
                                    // OCR button (top)
                                    if (Math.hypot((clickX - centerX).toDouble(), (clickY - (centerY - spacing)).toDouble()) < buttonRadius) {
                                        Log.i(TAG, "OCR button clicked")
                                        onOcrClick?.invoke()
                                    }
                                    // Underline button (left)
                                    else if (Math.hypot((clickX - (centerX - spacing)).toDouble(), (clickY - centerY).toDouble()) < buttonRadius) {
                                        Log.i(TAG, "Underline toggle clicked")
                                        onUnderlineToggle?.invoke()
                                    }
                                    // Service toggle button (right)
                                    else if (Math.hypot((clickX - (centerX + spacing)).toDouble(), (clickY - centerY).toDouble()) < buttonRadius) {
                                        Log.i(TAG, "Service toggle clicked")
                                        onServiceToggle?.invoke()
                                    }
                                    
                                    expanded = false
                                } else {
                                    expanded = true
                                }
                                invalidate()
                            }
                            return true
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(300, 300)
            }
            
            // Window layout parameters
            val params = WindowManager.LayoutParams(
                300,
                300,
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
            params.x = 50
            params.y = 500
            
            windowManager?.addView(overlayView, params)
            isShowing = true
            
            Log.i(TAG, "Floating action button shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
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
            Log.i(TAG, "Floating action button hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding floating button", e)
        }
    }
    
    fun isShowing() = isShowing
}
