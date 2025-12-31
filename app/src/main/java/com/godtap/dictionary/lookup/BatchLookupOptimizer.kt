package com.godtap.dictionary.lookup

import android.util.Log
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.deinflection.JapaneseDeinflector
import com.godtap.dictionary.repository.DictionaryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Batch lookup optimizer
 * Groups deinflected forms and does bulk database queries (like Yomitan's findTermsBulk)
 * 
 * This provides additional performance for sentence translation mode
 */
class BatchLookupOptimizer(private val repository: DictionaryRepository) {
    
    companion object {
        private const val TAG = "BatchLookup"
    }
    
    private val deinflector = JapaneseDeinflector()
    
    /**
     * Optimized batch lookup using bulk queries
     * Processes multiple terms and searches all their deinflections at once
     */
    suspend fun lookupBatch(terms: List<String>): Map<String, LookupResult> = coroutineScope {
        if (terms.isEmpty()) return@coroutineScope emptyMap()
        
        // Generate all deinflections for all terms
        val allDeinflections = mutableMapOf<String, List<String>>()
        
        for (term in terms) {
            val deinflected = deinflector.deinflect(term).map { it.term }
            allDeinflections[term] = deinflected
        }
        
        // Collect all unique forms to search
        val allForms = allDeinflections.values.flatten().distinct()
        
        // BULK QUERY - search all forms at once (like Yomitan)
        val entries = repository.searchBulk(allForms)
        val entryMap = mutableMapOf<String, DictionaryEntry>()
        
        // Index by both expression and reading for fast lookup
        for (entry in entries) {
            entry.primaryExpression?.let { entryMap[it] = entry }
            entryMap[entry.primaryReading] = entry
        }
        
        // Match results back to original terms
        val results = mutableMapOf<String, LookupResult>()
        
        for ((originalTerm, deinflectedForms) in allDeinflections) {
            for (form in deinflectedForms) {
                val entry = entryMap[form]
                if (entry != null) {
                    results[originalTerm] = LookupResult(
                        entry = entry,
                        matchedText = originalTerm,
                        matchLength = originalTerm.length,
                        deinflectedForm = form,
                        inflectionRules = emptyList(),  // Could track this if needed
                        sourceOffset = 0
                    )
                    break  // Use first match
                }
            }
        }
        
        Log.d(TAG, "Batch lookup: ${terms.size} terms, ${allForms.size} forms, ${results.size} matches")
        return@coroutineScope results
    }
    
    /**
     * Parallel sentence scanning with batch optimization
     * Splits sentence into windows, processes all windows in parallel
     */
    suspend fun scanSentenceOptimized(text: String, windowSize: Int = 15): List<LookupResult> = coroutineScope {
        val results = mutableListOf<LookupResult>()
        var offset = 0
        
        // Process in chunks for better batching
        val chunkSize = 5
        
        while (offset < text.length) {
            val japaneseStart = findNextJapaneseChar(text, offset)
            if (japaneseStart == -1) break
            
            // Extract multiple windows at once for batch processing
            val windows = mutableListOf<Pair<Int, String>>()
            
            for (i in 0 until chunkSize) {
                val pos = japaneseStart + i
                if (pos >= text.length) break
                
                val window = text.substring(pos, minOf(pos + windowSize, text.length))
                if (window.any { isJapanese(it) }) {
                    windows.add(Pair(pos, window))
                }
            }
            
            // Process all windows in parallel with batch lookup
            val windowResults = windows.map { (pos, window) ->
                async {
                    // Try substrings from longest to shortest
                    for (length in window.length downTo 1) {
                        val substring = window.substring(0, length)
                        if (!substring.any { isJapanese(it) }) continue
                        
                        val deinflections = deinflector.deinflect(substring)
                        val forms = deinflections.map { it.term }
                        
                        // Bulk search
                        val found = repository.searchBulk(forms).firstOrNull()
                        if (found != null) {
                            return@async Triple(pos, substring, found)
                        }
                    }
                    null
                }
            }.awaitAll().filterNotNull()
            
            // Add results
            for ((pos, matched, entry) in windowResults) {
                results.add(LookupResult(
                    entry = entry,
                    matchedText = matched,
                    matchLength = matched.length,
                    deinflectedForm = entry.primaryExpression ?: entry.primaryReading,
                    inflectionRules = emptyList(),
                    sourceOffset = pos
                ))
            }
            
            // Move offset forward
            offset = if (windowResults.isNotEmpty()) {
                val lastResult = windowResults.last()
                lastResult.first + lastResult.second.length
            } else {
                japaneseStart + 1
            }
        }
        
        return@coroutineScope results
    }
    
    private fun findNextJapaneseChar(text: String, startIndex: Int): Int {
        for (i in startIndex until text.length) {
            if (isJapanese(text[i])) return i
        }
        return -1
    }
    
    private fun isJapanese(char: Char): Boolean {
        val code = char.code
        return code in 0x3040..0x309F || // Hiragana
               code in 0x30A0..0x30FF || // Katakana  
               code in 0x4E00..0x9FAF || // CJK Unified Ideographs
               code in 0x3400..0x4DBF || // CJK Extension A
               code in 0xFF66..0xFF9F    // Halfwidth Katakana
    }
}
