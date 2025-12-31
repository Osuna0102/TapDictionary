package com.godtap.dictionary.lookup

import android.util.Log
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.repository.DictionaryRepository

/**
 * Fast dictionary lookup using the same approach as Yomitan/10ten:
 * 1. Try progressively shorter substrings (longest match first)
 * 2. Use direct database index lookups (no tokenization)
 * 3. Return first match found
 */
class DictionaryLookup(private val repository: DictionaryRepository) {
    
    companion object {
        private const val TAG = "DictionaryLookup"
        private const val MAX_LOOKUP_LENGTH = 20 // Maximum characters to try
    }
    
    /**
     * Fast lookup - tries substrings from longest to shortest
     * This is how Yomitan works: no tokenization, just progressive substring matching
     */
    suspend fun lookup(text: String): LookupResult? {
        if (text.isBlank()) return null
        
        // Limit input length for performance
        val searchText = text.take(MAX_LOOKUP_LENGTH)
        
        // Try progressively shorter substrings, starting with the full text
        for (length in searchText.length downTo 1) {
            val substring = searchText.substring(0, length)
            
            // Direct database lookup (fast indexed search)
            val entry = repository.search(substring)
            
            if (entry != null) {
                Log.d(TAG, "Found match: '$substring' (length: $length) from input: '$text'")
                return LookupResult(
                    entry = entry,
                    matchedText = substring,
                    matchLength = length
                )
            }
        }
        
        Log.d(TAG, "No match found for: '$text'")
        return null
    }
    
    /**
     * Batch lookup for translation mode - finds all sequential matches
     * Like Yomitan's "translate" function for full sentences
     */
    suspend fun translateSentence(text: String, maxResults: Int = 10): List<LookupResult> {
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
                // No match, skip this character and continue
                offset++
            }
        }
        
        return results
    }
    
    private fun findNextJapaneseChar(text: String, startIndex: Int): Int {
        for (i in startIndex until text.length) {
            val char = text[i]
            if (isJapanese(char)) {
                return i
            }
        }
        return -1
    }
    
    private fun isJapanese(char: Char): Boolean {
        val code = char.code
        return code in 0x3040..0x309F || // Hiragana
               code in 0x30A0..0x30FF || // Katakana  
               code in 0x4E00..0x9FAF || // CJK Unified Ideographs
               code in 0x3400..0x4DBF    // CJK Extension A
    }
}

/**
 * Result of a dictionary lookup
 */
data class LookupResult(
    val entry: DictionaryEntry,
    val matchedText: String,
    val matchLength: Int,
    val sourceOffset: Int = 0
)
