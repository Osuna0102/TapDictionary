package com.godtap.dictionary.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Free translation API service for fallback when no dictionary entry is found
 * Uses MyMemory Translation API (completely free, no key required, 10,000 words/day)
 * API Documentation: https://mymemory.translated.net/doc/spec.php
 */
class TranslationService {
    
    companion object {
        private const val TAG = "TranslationService"
        private const val MYMEMORY_URL = "https://api.mymemory.translated.net/get"
        private const val TIMEOUT_SECONDS = 10L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    /**
     * Translate text using MyMemory free API
     * 
     * @param text Text to translate
     * @param sourceLang Source language code (ja, es, ko, etc.)
     * @param targetLang Target language code (en, es, ja, etc.)
     * @return Translated text or null on error
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String = "en"
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) return@withContext null
            
            // Map our language codes to MyMemory codes
            val sourceCode = mapLanguageCode(sourceLang)
            val targetCode = mapLanguageCode(targetLang)
            
            Log.d(TAG, "Translating: '$text' from $sourceCode to $targetCode")
            
            // Build URL with query parameters (MyMemory uses GET request)
            val langPair = "$sourceCode|$targetCode"
            val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
            val url = "$MYMEMORY_URL?q=$encodedText&langpair=$langPair"
            
            Log.d(TAG, "Request URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            // Execute request
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "Translation API error: ${response.code} - $errorBody")
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return@withContext null
            }
            
            Log.d(TAG, "Response: $responseBody")
            
            // Parse response - MyMemory returns: {"responseData":{"translatedText":"..."}}
            val translationResponse = json.decodeFromString<MyMemoryResponse>(responseBody)
            val translatedText = translationResponse.responseData.translatedText
            
            Log.d(TAG, "Translation successful: '$translatedText'")
            
            return@withContext translatedText
            
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            return@withContext null
        }
    }
    
    /**
     * Map our internal language codes to MyMemory codes
     * MyMemory uses ISO 639-1 codes
     */
    private fun mapLanguageCode(code: String): String {
        return when (code.lowercase()) {
            "ja" -> "ja-JP"  // Japanese
            "es" -> "es-ES"  // Spanish
            "ko" -> "ko-KR"  // Korean
            "zh" -> "zh-CN"  // Chinese (Simplified)
            "en" -> "en-US"  // English
            "fr" -> "fr-FR"  // French
            "de" -> "de-DE"  // German
            "pt" -> "pt-PT"  // Portuguese
            "ru" -> "ru-RU"  // Russian
            "ar" -> "ar-SA"  // Arabic
            "hi" -> "hi-IN"  // Hindi
            else -> "en-US"  // Default to English
        }
    }
    
    /**
     * Get target language based on source language
     * Typically translate to English, but can be configured
     */
    fun getTargetLanguage(sourceLang: String): String {
        return when (sourceLang.lowercase()) {
            "en" -> "es"  // English -> Spanish
            else -> "en"  // Everything else -> English
        }
    }
}

/**
 * MyMemory API response structure
 */
@Serializable
private data class MyMemoryResponse(
    val responseData: ResponseData
) {
    @Serializable
    data class ResponseData(
        val translatedText: String
    )
}
