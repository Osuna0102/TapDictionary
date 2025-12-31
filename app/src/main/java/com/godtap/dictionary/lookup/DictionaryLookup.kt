package com.godtap.dictionary.lookup

import android.util.Log
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.deinflection.JapaneseDeinflector
import com.godtap.dictionary.repository.DictionaryRepository

/**
 * Dictionary lookup using Yomitan's exact algorithm:
 * 
 * 1. Progressive substring matching (longest first)
 * 2. Deinflection for each substring
 * 3. Fast indexed database lookups
 * 4. Returns first match found
 * 
 * This matches Yomitan's Translator._findTermsInternal() logic
 */
class DictionaryLookup(private val repository: DictionaryRepository) {
    
    companion object {
        private const val TAG = "DictionaryLookup"
        private const val MAX_LOOKUP_LENGTH = 25 // Yomitan uses similar limits
    }
    
    private val deinflector = JapaneseDeinflector()
    
    /**
     * Main lookup function - follows Yomitan's progressive substring approach
     * 
     * Algorithm (from Yomitan's translator.js):
     * 1. Scan ONLY from the start of the selected text (position 0)
     * 2. For each substring from longest to shortest:
     *    a. Generate deinflected forms
     *    b. Search database for each form
     *    c. Return first match
     * 
     * This matches how Yomitan works: it scans from the cursor position forward,
     * not searching for random words elsewhere in the text.
     */
    suspend fun lookup(text: String): LookupResult? {
        if (text.isBlank()) return null
        
        val searchText = text.take(MAX_LOOKUP_LENGTH)
        
        // Always scan from position 0 (start of selection/tap)
        // This matches Yomitan's behavior: find the word starting at the cursor position
        return lookupFromPosition(searchText, 0)
    }
    
    /**
     * Standard progressive substring matching from start of text
     * This is Yomitan's core algorithm: scan forward from cursor position
     */
    private suspend fun lookupFromPosition(text: String, offset: Int = 0, maxLength: Int = MAX_LOOKUP_LENGTH): LookupResult? {
        val searchText = text.take(maxLength)
        
        // Progressive substring matching (like Yomitan's _getAlgorithmDeinflections)
        // Try longest substrings first, then progressively shorter
        for (length in searchText.length downTo 1) {
            val substring = searchText.substring(0, length)
            
            Log.d(TAG, "Trying substring (len=$length): '$substring'")
            
            // Deinflect the substring (generates all possible dictionary forms)
            val deinflections = deinflector.deinflect(substring)
            
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
