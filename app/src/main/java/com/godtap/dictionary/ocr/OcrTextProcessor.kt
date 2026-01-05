package com.godtap.dictionary.ocr

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.godtap.dictionary.api.TranslationService
import com.godtap.dictionary.lookup.DictionaryLookup
import com.godtap.dictionary.manager.DictionaryManager
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.repository.DictionaryRepository
import com.godtap.dictionary.util.LanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Processes OCR recognized text.
 * - If text is a single word: Search dictionary, fallback to API translation
 * - If text is a phrase: Use API translation
 */
class OcrTextProcessor(
    private val context: Context,
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryManager: DictionaryManager,
    private val translationService: TranslationService,
    private val overlayManager: OverlayManager
) {
    
    companion object {
        private const val TAG = "OcrTextProcessor"
        private const val WORD_MAX_LENGTH = 50 // Max length to consider as single word
    }
    
    /**
     * Process recognized text from OCR
     */
    fun processText(text: String, bounds: Rect, scope: CoroutineScope) {
        Log.d(TAG, "Processing OCR text: $text")
        
        scope.launch {
            try {
                // Clean up text
                val cleanText = text.trim()
                
                if (cleanText.isBlank()) {
                    Log.d(TAG, "Empty text, ignoring")
                    return@launch
                }
                
                // Detect language
                val activeDictionary = dictionaryManager.getActiveDictionary()
                if (activeDictionary == null) {
                    Log.d(TAG, "No active dictionary, using API translation only")
                    translateWithApi(cleanText, "auto", bounds) // Use auto-detection
                    return@launch
                }
                
                val sourceLang = activeDictionary.sourceLanguage
                Log.d(TAG, "Active dictionary source: $sourceLang")
                
                // Check if text matches source language
                if (!LanguageDetector.matchesLanguage(cleanText, sourceLang)) {
                    Log.d(TAG, "Text doesn't match source language, using API")
                    translateWithApi(cleanText, sourceLang, bounds)
                    return@launch
                }
                
                // Determine if single word or phrase
                val isSingleWord = isSingleWord(cleanText)
                
                if (isSingleWord) {
                    // Try dictionary first, fallback to API
                    Log.d(TAG, "Processing as single word")
                    processWord(cleanText, sourceLang, bounds)
                } else {
                    // Phrase - use API translation
                    Log.d(TAG, "Processing as phrase")
                    translateWithApi(cleanText, sourceLang, bounds)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing OCR text", e)
                showError("Error processing text: ${e.message}")
            }
        }
    }
    
    /**
     * Process single word - try dictionary first, fallback to API
     */
    private suspend fun processWord(word: String, sourceLang: String, bounds: Rect) {
        try {
            // Search in dictionary
            val dictionaryLookup = DictionaryLookup(dictionaryRepository, sourceLang)
            val result = withContext(Dispatchers.IO) {
                dictionaryLookup.lookup(word)
            }
            
            if (result != null) {
                // Found in dictionary
                Log.d(TAG, "Found dictionary result for '$word'")
                val entry = result.entry
                val translation = formatDictionaryResult(entry)
                
                withContext(Dispatchers.Main) {
                    overlayManager.showPopup(
                        word = word,
                        translation = translation,
                        lookupCount = entry.lookupCount,
                        x = bounds.centerX(),
                        y = bounds.bottom
                    )
                }
            } else {
                // Not found in dictionary - use API
                Log.d(TAG, "Word not found in dictionary, using API")
                translateWithApi(word, sourceLang, bounds)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing word", e)
            // Fallback to API on error
            translateWithApi(word, sourceLang, bounds)
        }
    }
    
    /**
     * Translate text using API
     */
    private suspend fun translateWithApi(text: String, sourceLang: String, bounds: Rect) {
        try {
            Log.d(TAG, "Translating with API: $text")
            
            val translation = withContext(Dispatchers.IO) {
                translationService.translate(text, sourceLang)
            }
            
            if (translation != null) {
                Log.d(TAG, "API translation: $translation")
                withContext(Dispatchers.Main) {
                    overlayManager.showPopup(
                        word = text,
                        translation = translation,
                        lookupCount = 0,
                        x = bounds.centerX(),
                        y = bounds.bottom
                    )
                }
            } else {
                Log.e(TAG, "API translation failed")
                showError("Translation failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error translating with API", e)
            showError("Translation error: ${e.message}")
        }
    }
    
    /**
     * Format dictionary result for display
     */
    private fun formatDictionaryResult(entry: com.godtap.dictionary.database.DictionaryEntry): String {
        val meanings = entry.senses
            .take(3) // Show top 3 meanings
            .mapIndexed { index, sense ->
                val glosses = sense.glosses.take(2).joinToString("; ")
                "${index + 1}. $glosses"
            }
            .joinToString("\n")
        
        return meanings.ifBlank { "No translation available" }
    }
    
    /**
     * Check if text is a single word
     */
    private fun isSingleWord(text: String): Boolean {
        // Remove punctuation and check
        val cleaned = text.replace(Regex("[^\\p{L}\\p{N}]"), "")
        
        // Single word if:
        // - No spaces
        // - Not too long
        // - Contains only letters/numbers
        return !text.contains(" ") &&
                text.length <= WORD_MAX_LENGTH &&
                cleaned == text.replace(Regex("[\\p{Punct}]"), "")
    }
    
    /**
     * Show error message
     */
    private suspend fun showError(message: String) {
        withContext(Dispatchers.Main) {
            overlayManager.showPopup(
                word = "Error",
                translation = message,
                lookupCount = 0
            )
        }
    }
}
