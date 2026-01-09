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
        Log.i(TAG, "┌────────────────────────────────────────────────────┐")
        Log.i(TAG, "│ 📝 OCR TEXT PROCESSING")
        Log.i(TAG, "│ Text: $text")
        Log.i(TAG, "│ Bounds: $bounds")
        Log.i(TAG, "└────────────────────────────────────────────────────┘")
        
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
                    Log.i(TAG, "⚠️ No active dictionary, using API translation only")
                    translateWithApi(cleanText, "auto", "auto", bounds, null) // No dictionary context
                    return@launch
                }
                
                val sourceLang = activeDictionary.sourceLanguage
                val targetLang = activeDictionary.targetLanguage
                Log.i(TAG, "🌍 Active dictionary: $sourceLang → $targetLang")
                
                // Check if text matches source language
                val matchesLang = LanguageDetector.matchesLanguage(cleanText, sourceLang)
                Log.i(TAG, "🔍 Language match check: $matchesLang (text vs $sourceLang)")
                
                if (!matchesLang) {
                    Log.i(TAG, "➡️ Text doesn't match source language, using API")
                    translateWithApi(cleanText, "auto", targetLang, bounds, activeDictionary)
                    return@launch
                }
                
                // Determine if single word or phrase
                val isSingleWord = isSingleWord(cleanText)
                Log.i(TAG, "📖 Is single word: $isSingleWord")
                
                if (isSingleWord) {
                    // Try dictionary first, fallback to API
                    Log.i(TAG, "🔎 Processing as single word (dictionary + API fallback)")
                    processWord(cleanText, sourceLang, targetLang, bounds, activeDictionary)
                } else {
                    // Phrase - use API translation
                    Log.i(TAG, "💬 Processing as phrase (API only)")
                    translateWithApi(cleanText, sourceLang, targetLang, bounds, activeDictionary)
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
    private suspend fun processWord(
        word: String,
        sourceLang: String,
        targetLang: String,
        bounds: Rect,
        dictionary: com.godtap.dictionary.database.DictionaryMetadata?
    ) {
        try {
            // Search in dictionary
            val dictionaryLookup = DictionaryLookup(dictionaryRepository, sourceLang)
            val result = withContext(Dispatchers.IO) {
                dictionaryLookup.lookup(word)
            }
            
            if (result != null) {
                // Found in dictionary
                Log.i(TAG, "✅ Found dictionary result for '$word'")
                val entry = result.entry
                val translation = formatDictionaryResult(entry)
                val languageLabel = formatLanguageLabel(sourceLang, targetLang)
                
                withContext(Dispatchers.Main) {
                    overlayManager.showPopup(
                        word = word,
                        translation = "$languageLabel\n\n$translation",
                        lookupCount = entry.lookupCount,
                        x = bounds.centerX(),
                        y = bounds.bottom,
                        sourceLanguage = sourceLang
                    )
                }
            } else {
                // Not found in dictionary - use API
                Log.i(TAG, "⚠️ Word not found in dictionary, using API fallback")
                translateWithApi(word, sourceLang, targetLang, bounds, dictionary)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing word", e)
            // Fallback to API on error
            translateWithApi(word, sourceLang, targetLang, bounds, dictionary)
        }
    }
    
    /**
     * Translate text using API
     */
    private suspend fun translateWithApi(
        text: String,
        sourceLang: String,
        targetLang: String,
        bounds: Rect,
        dictionary: com.godtap.dictionary.database.DictionaryMetadata?
    ) {
        try {
            Log.i(TAG, "🌐 Starting API translation...")
            Log.i(TAG, "   Text: $text")
            Log.i(TAG, "   $sourceLang → $targetLang")
            
            val translation = withContext(Dispatchers.IO) {
                translationService.translate(text, sourceLang, targetLang)
            }
            
            if (translation != null) {
                Log.i(TAG, "✅ API translation SUCCESS: $translation")
                val languageLabel = formatLanguageLabel(sourceLang, targetLang)
                
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "🎯 Showing popup at (${bounds.centerX()}, ${bounds.bottom})")
                    overlayManager.showPopup(
                        word = text,
                        translation = "$languageLabel\n\n$translation",
                        lookupCount = 0,
                        x = bounds.centerX(),
                        y = bounds.bottom,
                        sourceLanguage = sourceLang
                    )
                    Log.i(TAG, "✔️ Popup show() called")
                }
            } else {
                Log.e(TAG, "❌ API translation returned NULL")
                showError("Translation failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error translating with API: ${e.message}", e)
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
     * Format language pair label for display
     */
    private fun formatLanguageLabel(sourceLang: String, targetLang: String): String {
        val sourceLabel = when (sourceLang.lowercase()) {
            "ja" -> "Japanese"
            "en" -> "English"
            "es" -> "Spanish"
            "ko" -> "Korean"
            "auto" -> "Auto"
            else -> sourceLang.uppercase()
        }
        val targetLabel = when (targetLang.lowercase()) {
            "ja" -> "Japanese"
            "en" -> "English"
            "es" -> "Spanish"
            "ko" -> "Korean"
            "auto" -> "Auto"
            else -> targetLang.uppercase()
        }
        return "🌐 $sourceLabel → $targetLabel"
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
