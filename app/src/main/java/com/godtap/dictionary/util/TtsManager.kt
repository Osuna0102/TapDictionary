package com.godtap.dictionary.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

/**
 * Manager for Text-to-Speech functionality
 * Provides local pronunciation of words in different languages
 */
class TtsManager(context: Context) {
    
    companion object {
        private const val TAG = "TtsManager"
        
        // Language codes and their TTS locale mappings
        private val LANGUAGE_LOCALES = mapOf(
            "ja" to Locale.JAPANESE,
            "es" to Locale("es", "ES"),
            "ko" to Locale.KOREAN,
            "en" to Locale.US,
            "zh" to Locale.CHINESE,
            "fr" to Locale.FRENCH,
            "de" to Locale.GERMAN,
            "it" to Locale.ITALIAN,
            "pt" to Locale("pt", "BR"),
            "ru" to Locale("ru", "RU")
        )
    }
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: String? = null
    
    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
        
        // Set up utterance listener for debugging
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started for: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed for: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error for: $utteranceId")
            }
        })
    }
    
    /**
     * Speak the given text in the specified language
     * @param text The text to speak
     * @param languageCode ISO 639-1 language code (ja, es, ko, etc.)
     * @return true if speaking started successfully
     */
    fun speak(text: String, languageCode: String): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized yet")
            return false
        }
        
        val tts = textToSpeech ?: run {
            Log.e(TAG, "TTS instance is null")
            return false
        }
        
        // Set language if different from current
        if (currentLanguage != languageCode) {
            val locale = LANGUAGE_LOCALES[languageCode] ?: run {
                Log.w(TAG, "Unsupported language: $languageCode, using default")
                Locale.getDefault()
            }
            
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported: $languageCode")
                // Try to speak anyway with default language
            } else {
                currentLanguage = languageCode
                Log.d(TAG, "TTS language set to: $languageCode ($locale)")
            }
        }
        
        // Stop any ongoing speech
        tts.stop()
        
        // Speak the text
        val utteranceId = "tts_${System.currentTimeMillis()}"
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        
        if (result == TextToSpeech.SUCCESS) {
            Log.d(TAG, "Speaking: '$text' in $languageCode")
            return true
        } else {
            Log.e(TAG, "Failed to speak: $text")
            return false
        }
    }
    
    /**
     * Stop any ongoing speech
     */
    fun stop() {
        textToSpeech?.stop()
        Log.d(TAG, "TTS stopped")
    }
    
    /**
     * Check if a language is available for TTS
     * @return true if the language is supported
     */
    fun isLanguageAvailable(languageCode: String): Boolean {
        val tts = textToSpeech ?: return false
        val locale = LANGUAGE_LOCALES[languageCode] ?: return false
        
        val result = tts.isLanguageAvailable(locale)
        return result >= TextToSpeech.LANG_AVAILABLE
    }
    
    /**
     * Get a list of supported languages
     */
    fun getSupportedLanguages(): List<String> {
        val tts = textToSpeech ?: return emptyList()
        return LANGUAGE_LOCALES.keys.filter { languageCode ->
            val locale = LANGUAGE_LOCALES[languageCode]
            locale != null && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        }
    }
    
    /**
     * Shutdown TTS engine
     * Call this when the service is destroyed
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        Log.d(TAG, "TTS shutdown")
    }
    
    /**
     * Check if TTS is ready to use
     */
    fun isReady(): Boolean = isInitialized && textToSpeech != null
}
