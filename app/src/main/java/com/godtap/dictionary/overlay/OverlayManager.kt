package com.godtap.dictionary.overlay

import android.content.Context
import android.content.SharedPreferences
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
import com.godtap.dictionary.util.TtsManager

class OverlayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OverlayManager"
        private const val AUTO_DISMISS_DELAY = 30000L // 30 seconds for testing
        private const val TTS_PREFS_NAME = "tts_settings"
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    private val ttsManager = TtsManager(context)
    private val ttsPrefs: SharedPreferences = context.getSharedPreferences(TTS_PREFS_NAME, Context.MODE_PRIVATE)
    
    // Store current word and language for TTS
    private var currentWord: String = ""
    private var currentLanguage: String = "ja" // Default to Japanese
    
    /**
     * Show popup immediately with loading indicator
     * This will be updated with actual content when lookup completes
     */
    fun showLoadingPopup(word: String, x: Int = -1, y: Int = -1, sourceLanguage: String = "ja") {
        Log.d(TAG, "showLoadingPopup() called for: $word, language: $sourceLanguage")
        
        // Store word and language for TTS
        currentWord = word
        currentLanguage = sourceLanguage
        Log.d(TAG, "Stored currentLanguage as: $currentLanguage")
        
        handler.post {
            try {
                // Remove existing popup if any
                hidePopupInternal()
                
                // Inflate the popup view
                val themedContext = ContextThemeWrapper(context, R.style.Theme_GodTapDictionary)
                val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set loading content
                view.findViewById<TextView>(R.id.wordText).text = word
                view.findViewById<TextView>(R.id.translationText).text = "🔍 Searching..."
                view.findViewById<TextView>(R.id.lookupCountText).text = "..."
                
                // Disable speaker button while loading
                view.findViewById<View>(R.id.speakerButton)?.apply {
                    isEnabled = false
                    alpha = 0.5f
                }
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Dismiss on background click
                view.setOnClickListener {
                    hidePopup()
                }
                
                // Don't dismiss when clicking the card itself
                view.findViewById<View>(R.id.popupCard)?.setOnClickListener {
                    // Consume click - don't dismiss
                }
                
                // Create layout params
                val params = createLayoutParams(x, y)
                
                // Add view to window
                windowManager.addView(view, params)
                overlayView = view
                
                Log.d(TAG, "Loading popup shown for: $word")
                
                // Auto dismiss after delay (longer for loading)
                scheduleAutoDismiss()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing loading popup", e)
            }
        }
    }
    
    fun showPopup(word: String, translation: String, lookupCount: Int = 0, x: Int = -1, y: Int = -1, sourceLanguage: String = "ja") {
        Log.d(TAG, "showPopup() called for: $word (count: $lookupCount), language: $sourceLanguage")
        
        // Store word and language for TTS
        currentWord = word
        currentLanguage = sourceLanguage
        Log.d(TAG, "Stored currentLanguage as: $currentLanguage")
        
        handler.post {
            try {
                // Check if we have an existing view to update
                val existingView = overlayView
                
                if (existingView != null) {
                    // Update existing popup instead of creating a new one
                    Log.d(TAG, "Updating existing popup with translation")
                    existingView.findViewById<TextView>(R.id.wordText).text = word
                    existingView.findViewById<TextView>(R.id.translationText).text = translation
                    existingView.findViewById<TextView>(R.id.lookupCountText).text = lookupCount.toString()
                    
                    // Enable speaker button
                    existingView.findViewById<View>(R.id.speakerButton)?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        setOnClickListener {
                            Log.d(TAG, "TTS button clicked for: $currentWord in $currentLanguage")
                            
                            // Load saved voice preference for this language
                            val savedVoice = ttsPrefs.getString("voice_$currentLanguage", null)
                            if (savedVoice != null) {
                                ttsManager.setVoice(savedVoice)
                            }
                            
                            val success = ttsManager.speak(currentWord, currentLanguage)
                            if (!success) {
                                Log.w(TAG, "TTS failed or not available")
                            }
                        }
                    }
                    
                    // Reset auto-dismiss timer
                    scheduleAutoDismiss()
                    return@post
                }
                
                // No existing view - create new popup
                // Inflate the popup view with themed context to resolve theme attributes
                val themedContext = ContextThemeWrapper(context, R.style.Theme_GodTapDictionary)
                val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set content
                view.findViewById<TextView>(R.id.wordText).text = word
                view.findViewById<TextView>(R.id.translationText).text = translation
                view.findViewById<TextView>(R.id.lookupCountText).text = lookupCount.toString()
                
                // Speaker button for TTS
                view.findViewById<View>(R.id.speakerButton)?.apply {
                    isEnabled = true
                    alpha = 1.0f
                    setOnClickListener {
                        Log.d(TAG, "TTS button clicked for: $currentWord in $currentLanguage")
                        
                        // Load saved voice preference for this language
                        val savedVoice = ttsPrefs.getString("voice_$currentLanguage", null)
                        if (savedVoice != null) {
                            ttsManager.setVoice(savedVoice)
                        }
                        
                        val success = ttsManager.speak(currentWord, currentLanguage)
                        if (!success) {
                            Log.w(TAG, "TTS failed or not available")
                        }
                    }
                }
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Detect clicks on transparent background (outside the card)
                view.setOnClickListener {
                    // Click on transparent background - dismiss
                    hidePopup()
                }
                
                // Don't dismiss when clicking the card itself
                view.findViewById<View>(R.id.popupCard)?.setOnClickListener {
                    // Consume click - don't dismiss
                    // This prevents the click from bubbling to parent view
                }
                
                // Create layout params - position BELOW selected text or at specified coordinates
                val params = createLayoutParams(x, y)
                
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
    
    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (x >= 0 && y >= 0) {
                // Position at top-right of the tap/selection coordinates
                gravity = Gravity.TOP or Gravity.START
                // Convert dp to pixels for consistent positioning
                val offsetDp = 8 // 8dp offset from selection
                val scale = context.resources.displayMetrics.density
                val offsetPx = (offsetDp * scale + 0.5f).toInt()
                
                // Position at top-right with small offset
                this.x = x + offsetPx
                this.y = y - offsetPx // Slightly above to avoid covering selection
            } else {
                // Default centered position
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.x = 0
                this.y = 300
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
            // Stop TTS when popup is dismissed
            ttsManager.stop()
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
    
    /**
     * Cleanup resources
     * Call this when the service is destroyed
     */
    fun cleanup() {
        hidePopup()
        ttsManager.shutdown()
    }
}
