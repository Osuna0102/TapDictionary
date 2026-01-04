package com.godtap.dictionary.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.repository.DictionaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders colored underlines beneath words based on their lookup frequency.
 * 
 * Color scheme (based on lookup count):
 * - New words (count == 1): Green
 * - Familiar (count < 3): Yellow
 * - Known (count < 5): Orange
 * - Mastered (count < 10): Red
 * - Expert (count >= 10): No underline
 * 
 * Performance optimizations:
 * - Debounces rendering for 2 seconds during scrolling
 * - Batch processes visible text analysis
 * - Reuses overlay views to minimize allocations
 */
class TextUnderlineRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "TextUnderlineRenderer"
        private const val DEBOUNCE_DELAY_MS = 500L // Reduced to 0.5 seconds for better responsiveness
        private const val MAX_UNDERLINES = 50 // Limit number of underlines to prevent performance issues
        private const val UNDERLINE_HEIGHT = 4 // Increased thickness for better visibility
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val renderScope = CoroutineScope(Dispatchers.Main)
    
    // Track currently displayed underline views
    private val activeUnderlineViews = mutableListOf<View>()
    
    // Debouncing job for rendering
    private var renderJob: Job? = null
    private var lastRenderTime = 0L
    
    // Flag to enable/disable rendering
    var isEnabled = false
        set(value) {
            if (!value && field) {
                // Disabled - clear all underlines
                clearAllUnderlines()
            }
            field = value
        }
    
    /**
     * Request to render underlines for visible text with debouncing.
     * Automatically debounces rapid calls (e.g., during scrolling).
     */
    fun requestRender(visibleWords: List<WordBounds>, repository: DictionaryRepository) {
        if (!isEnabled) return
        
        // Cancel previous pending render
        renderJob?.cancel()
        
        // Schedule new render with delay
        renderJob = renderScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            
            // Check if enough time has passed since last render
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRenderTime < DEBOUNCE_DELAY_MS) {
                Log.d(TAG, "Skipping render - too soon since last render")
                return@launch
            }
            
            Log.d(TAG, "Rendering underlines for ${visibleWords.size} words")
            lastRenderTime = currentTime
            
            renderUnderlines(visibleWords, repository)
        }
    }
    
    /**
     * Force immediate render without debouncing (for screen changes)
     */
    fun forceRender(visibleWords: List<WordBounds>, repository: DictionaryRepository) {
        if (!isEnabled) return
        
        renderJob?.cancel()
        renderJob = renderScope.launch {
            Log.d(TAG, "Force rendering underlines for ${visibleWords.size} words")
            lastRenderTime = System.currentTimeMillis()
            renderUnderlines(visibleWords, repository)
        }
    }
    
    /**
     * Actual rendering logic - queries database and draws underlines
     */
    private suspend fun renderUnderlines(visibleWords: List<WordBounds>, repository: DictionaryRepository) {
        try {
            // Clear existing underlines
            withContext(Dispatchers.Main) {
                clearAllUnderlines()
            }
            
            // Limit number of words to process
            val wordsToProcess = visibleWords.take(MAX_UNDERLINES)
            
            // Batch query lookup counts for all visible words
            val lookupCounts = withContext(Dispatchers.IO) {
                getLookupCounts(wordsToProcess.map { it.text }, repository)
            }
            
            // Draw underlines for words that need them
            withContext(Dispatchers.Main) {
                var underlinesDrawn = 0
                wordsToProcess.forEach { wordBound ->
                    val count = lookupCounts[wordBound.text] ?: 0
                    val color = getUnderlineColor(count)
                    
                    // Only draw if word should have underline (count < 10)
                    if (color != null) {
                        drawUnderline(wordBound.bounds, color)
                        underlinesDrawn++
                    }
                }
                Log.d(TAG, "Drew $underlinesDrawn underlines out of ${wordsToProcess.size} words")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering underlines", e)
        }
    }
    
    /**
     * Query database for lookup counts of multiple words
     */
    private suspend fun getLookupCounts(words: List<String>, repository: DictionaryRepository): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        
        try {
            // Batch query to reduce DB calls
            words.forEach { word ->
                val entry = repository.getEntryForLookup(word)
                if (entry != null) {
                    counts[word] = entry.lookupCount
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying lookup counts", e)
        }
        
        return counts
    }
    
    /**
     * Determine underline color based on lookup count
     * Returns null if no underline should be shown
     */
    private fun getUnderlineColor(lookupCount: Int): Int? {
        return when {
            lookupCount == 0 -> null // Not looked up yet - no underline
            lookupCount == 1 -> Color.GREEN // New word
            lookupCount < 3 -> Color.YELLOW // Familiar
            lookupCount < 5 -> Color.rgb(255, 165, 0) // Orange
            lookupCount < 10 -> Color.RED // Mastered
            else -> Color.BLACK // Expert - no underline
        }
    }
    
    /**
     * Draw a single underline at specified bounds
     */
    private fun drawUnderline(bounds: Rect, color: Int) {
        try {
            // Create underline view
            val underlineView = View(context).apply {
                setBackgroundColor(color)
            }
            
            // Position underline below the word, at the baseline
            val underlineY = bounds.bottom - UNDERLINE_HEIGHT
            
            val params = WindowManager.LayoutParams(
                bounds.width(),
                UNDERLINE_HEIGHT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bounds.left
                y = underlineY
            }
            
            // Add to window
            windowManager.addView(underlineView, params)
            activeUnderlineViews.add(underlineView)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing underline", e)
        }
    }
    
    /**
     * Clear all currently displayed underlines
     */
    fun clearAllUnderlines() {
        handler.post {
            activeUnderlineViews.forEach { view ->
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // View may already be removed
                }
            }
            activeUnderlineViews.clear()
            Log.d(TAG, "Cleared all underlines")
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        clearAllUnderlines()
        renderJob?.cancel()
        renderScope.cancel()
    }
    
    /**
     * Data class for word bounds
     */
    data class WordBounds(
        val text: String,
        val bounds: Rect
    )
}
