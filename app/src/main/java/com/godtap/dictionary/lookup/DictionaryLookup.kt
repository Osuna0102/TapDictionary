package com.godtap.dictionary.lookup

import android.util.Log
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.deinflection.DeinflectionResult
import com.godtap.dictionary.deinflection.JapaneseDeinflector
import com.godtap.dictionary.deinflection.SpanishLanguageTransformer
import com.godtap.dictionary.repository.DictionaryRepository

/**
 * Multi-language dictionary lookup
 * 
 * Supports different lookup strategies per language:
 * - Japanese: Progressive substring with deinflection
 * - Spanish: Word boundary extraction + simple transformations
 * - Korean: Similar to Spanish (space-separated)
 * 
 * This matches Yomitan's Translator._findTermsInternal() logic for Japanese
 */
class DictionaryLookup(
    private val repository: DictionaryRepository,
    private val sourceLanguage: String = "ja" // Default to Japanese for backward compatibility
) {
    
    companion object {
        private const val TAG = "DictionaryLookup"
        private const val MAX_LOOKUP_LENGTH = 25 // Yomitan uses similar limits
    }
    
    private val japaneseDeinflector = JapaneseDeinflector()
    private val spanishTransformer = SpanishLanguageTransformer()
    
    /**
     * Main lookup function - language-aware
     * 
     * For Japanese: Progressive substring matching with deinflection
     * For Spanish/Korean: Extract word at position, then look up
     */
    suspend fun lookup(text: String): LookupResult? {
        if (text.isBlank()) return null
        
        return when (sourceLanguage) {
            "ja" -> lookupJapanese(text)
            "es", "ko", "zh" -> lookupSpaceDelimited(text)
            else -> lookupSpaceDelimited(text) // Default to space-delimited
        }
    }
    
    /**
     * Japanese lookup: Progressive substring matching (Yomitan algorithm)
     */
    private suspend fun lookupJapanese(text: String): LookupResult? {
        val searchText = text.take(MAX_LOOKUP_LENGTH)
        return lookupFromPositionJapanese(searchText, 0)
    }
    
    /**
     * Spanish/Korean lookup: Extract word at start position
     * These languages use spaces to separate words, so we extract the first word
     */
    private suspend fun lookupSpaceDelimited(text: String): LookupResult? {
        // For space-delimited languages, extract the first word
        val firstWord = extractFirstWord(text)
        if (firstWord.isEmpty()) return null
        
        Log.d(TAG, "Extracted word: '$firstWord'")
        
        // Get language-appropriate transformations
        val transformations = getTransformations(firstWord)
        
        Log.d(TAG, "  Generated ${transformations.size} forms: ${transformations.take(3).map { it.term }}...")
        
        // Try each transformation
        for (transformation in transformations) {
            val entry = repository.search(transformation.term)
            
            if (entry != null) {
                Log.d(TAG, "✓ Match found: '$firstWord' → '${transformation.term}'")
                
                return LookupResult(
                    entry = entry,
                    matchedText = firstWord,
                    matchLength = firstWord.length,
                    deinflectedForm = transformation.term,
                    inflectionRules = transformation.rules,
                    sourceOffset = 0
                )
            }
        }
        
        Log.d(TAG, "No match found for: '$firstWord'")
        return null
    }
    
    /**
     * Extract first complete word from text (space-delimited languages)
     */
    private fun extractFirstWord(text: String): String {
        val wordSeparators = setOf(' ', '\n', '\t', '.', ',', ';', ':', '!', '¡', '?', '¿',
                                     '(', ')', '[', ']', '{', '}', '"', '\'', '—', '/')
        
        // Skip leading separators
        var start = 0
        while (start < text.length && text[start] in wordSeparators) {
            start++
        }
        
        if (start >= text.length) return ""
        
        // Find end of word
        var end = start
        while (end < text.length && text[end] !in wordSeparators) {
            end++
        }
        
        return text.substring(start, end)
    }
    
    /**
     * Get appropriate transformations for the source language
     */
    private fun getTransformations(word: String): List<DeinflectionResult> {
        return when (sourceLanguage) {
            "es" -> spanishTransformer.transform(word)
            "ko", "zh" -> {
                // For Korean/Chinese, just try original and lowercase
                listOf(
                    DeinflectionResult(word, emptyList()),
                    DeinflectionResult(word.lowercase(), emptyList())
                ).distinctBy { it.term }
            }
            else -> listOf(DeinflectionResult(word, emptyList()))
        }
    }
    
    /**
     * Standard progressive substring matching from start of text (Japanese only)
     * This is Yomitan's core algorithm: scan forward from cursor position
     */
    private suspend fun lookupFromPositionJapanese(text: String, offset: Int = 0, maxLength: Int = MAX_LOOKUP_LENGTH): LookupResult? {
        val searchText = text.take(maxLength)
        
        // Progressive substring matching (like Yomitan's _getAlgorithmDeinflections)
        // Try longest substrings first, then progressively shorter
        for (length in searchText.length downTo 1) {
            val substring = searchText.substring(0, length)
            
            Log.d(TAG, "Trying substring (len=$length): '$substring'")
            
            // Deinflect the substring (generates all possible dictionary forms)
            val deinflections = japaneseDeinflector.deinflect(substring)
            
            Log.d(TAG, "  Generated ${deinflections.size} forms: ${deinflections.take(3).map { it.term }}...")
            
            // Search for each deinflected form
            for (deinflection in deinflections) {
                val entry = repository.search(deinflection.term)
                
                if (entry != null) {
                    Log.d(TAG, "✓ Match found: '$substring' → '${deinflection.term}' " +
                            "(rules: ${deinflection.rules.joinToString()})")
                    
                    return LookupResult(
                        entry = entry,
                        matchedText = substring,
                        matchLength = length,
                        deinflectedForm = deinflection.term,
                        inflectionRules = deinflection.rules,
                        sourceOffset = offset
                    )
                }
            }
        }
        
        Log.d(TAG, "No match found for: '$text'")
        return null
    }
    
    /**
     * Batch lookup with deinflection support
     * Searches multiple terms and their deinflected forms
     */
    suspend fun lookupBulk(terms: List<String>): List<LookupResult> {
        val results = mutableListOf<LookupResult>()
        
        for (term in terms) {
            lookup(term)?.let { results.add(it) }
        }
        
        return results
    }
    
    /**
     * Sentence translation mode - finds all sequential matches
     * Like Yomitan's continuous scanning mode
     */
    suspend fun translateSentence(text: String, maxResults: Int = 20): List<LookupResult> {
        val results = mutableListOf<LookupResult>()
        var offset = 0
        
        while (offset < text.length && results.size < maxResults) {
            // Skip non-Japanese characters
            val japaneseStart = findNextJapaneseChar(text, offset)
            if (japaneseStart == -1) break
            
            offset = japaneseStart
            
            // Try to find a match starting from this position
            val remainingText = text.substring(offset)
            val result = lookup(remainingText)
            
            if (result != null) {
                results.add(result.copy(sourceOffset = offset))
                offset += result.matchLength
            } else {
                // No match, skip this character
                offset++
            }
        }
        
        return results
    }
    
    /**
     * Find next Japanese character in text
     */
    private fun findNextJapaneseChar(text: String, startIndex: Int): Int {
        for (i in startIndex until text.length) {
            if (isJapanese(text[i])) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Check if character is Japanese
     */
    private fun isJapanese(char: Char): Boolean {
        val code = char.code
        return code in 0x3040..0x309F || // Hiragana
               code in 0x30A0..0x30FF || // Katakana  
               code in 0x4E00..0x9FAF || // CJK Unified Ideographs (Kanji)
               code in 0x3400..0x4DBF || // CJK Extension A
               code in 0xFF66..0xFF9F    // Halfwidth Katakana
    }
}

/**
 * Result of a dictionary lookup with deinflection info
 * Similar to Yomitan's TermDictionaryEntry structure
 */
data class LookupResult(
    val entry: DictionaryEntry,           // The dictionary entry found
    val matchedText: String,              // Original text that matched (e.g., "食べました")
    val matchLength: Int,                 // Length of matched text
    val deinflectedForm: String,          // Dictionary form (e.g., "食べる")
    val inflectionRules: List<String>,    // Rules applied (e.g., ["polite-past"])
    val sourceOffset: Int = 0             // Position in source text
) {
    /**
     * Check if deinflection was applied
     */
    val wasDeinflected: Boolean
        get() = inflectionRules.isNotEmpty()
}
