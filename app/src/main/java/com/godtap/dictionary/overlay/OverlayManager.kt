package com.godtap.dictionary.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.godtap.dictionary.R

class OverlayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OverlayManager"
        private const val AUTO_DISMISS_DELAY = 30000L // 30 seconds for testing
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    
    fun showPopup(word: String, translation: String) {
        Log.d(TAG, "showPopup() called for: $word")
        handler.post {
            try {
                // Remove existing popup if any (synchronously to avoid race condition)
                hidePopupInternal()
                
                // Inflate the popup view directly without themed context
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set content
                view.findViewById<TextView>(R.id.wordText).text = word
                view.findViewById<TextView>(R.id.translationText).text = translation
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Click outside to close
                view.setOnClickListener {
                    // Don't close on click inside
                }
                
                // Create layout params
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    x = 0
                    y = -200 // Slightly above center
                }
                
                // Add view to window
                windowManager.addView(view, params)
                overlayView = view
                
                Log.d(TAG, "Popup shown: $word -> $translation")
                
                // Auto dismiss after delay
                scheduleAutoDismiss()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing popup", e)
            }
        }
    }
    
    fun hidePopup() {
        Log.d(TAG, "hidePopup() called")
        handler.post {
            hidePopupInternal()
        }
    }
    
    private fun hidePopupInternal() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                Log.d(TAG, "Popup hidden")
            }
            cancelAutoDismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding popup", e)
        }
    }
    
    private fun scheduleAutoDismiss() {
        cancelAutoDismiss()
        autoDismissRunnable = Runnable {
            hidePopup()
        }.also {
            handler.postDelayed(it, AUTO_DISMISS_DELAY)
        }
    }
    
    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let {
            handler.removeCallbacks(it)
            autoDismissRunnable = null
        }
    }
    
    fun isShowing(): Boolean = overlayView != null
}
