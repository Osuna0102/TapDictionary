package com.godtap.dictionary.deinflection

/**
 * Spanish language transformer
 * 
 * Unlike Japanese, Spanish uses:
 * - Space-separated words
 * - Simple inflections (no complex deinflection needed for basic lookup)
 * - Accent marks that should be preserved
 * 
 * For initial implementation, we return the word as-is with minimal transformations
 */
class SpanishLanguageTransformer {
    
    /**
     * Transform Spanish word/phrase for dictionary lookup
     * Returns variations to try:
     * 1. Original form
     * 2. Lowercase (for case-insensitive matching)
     * 3. Without accents (optional fallback)
     */
    fun transform(word: String): List<DeinflectionResult> {
        val results = mutableListOf<DeinflectionResult>()
        
        // 1. Original form (most important)
        results.add(DeinflectionResult(
            term = word,
            rules = emptyList()
        ))
        
        // 2. Lowercase variant (if different)
        val lowercase = word.lowercase()
        if (lowercase != word) {
            results.add(DeinflectionResult(
                term = lowercase,
                rules = emptyList()
            ))
        }
        
        // 3. Without leading/trailing spaces
        val trimmed = word.trim()
        if (trimmed != word && trimmed.isNotEmpty()) {
            results.add(DeinflectionResult(
                term = trimmed,
                rules = emptyList()
            ))
        }
        
        return results
    }
    
    /**
     * Extract word boundaries for Spanish text
     * Spanish words are separated by spaces and punctuation
     * 
     * @param text Full text
     * @param position Click/tap position
     * @return Pair of (word start index, word end index)
     */
    fun findWordBoundaries(text: String, position: Int): Pair<Int, Int> {
        if (text.isEmpty() || position < 0 || position >= text.length) {
            return Pair(0, 0)
        }
        
        // Word boundaries in Spanish: spaces, punctuation, start/end of text
        val wordSeparators = setOf(' ', '\n', '\t', '.', ',', ';', ':', '!', '¡', '?', '¿', 
                                     '(', ')', '[', ']', '{', '}', '"', '\'', '-', '—', '/')
        
        // Find start of word (scan backward)
        var start = position
        while (start > 0 && text[start - 1] !in wordSeparators) {
            start--
        }
        
        // Find end of word (scan forward)
        var end = position
        while (end < text.length && text[end] !in wordSeparators) {
            end++
        }
        
        return Pair(start, end)
    }
    
    /**
     * Extract the word at the given position
     * 
     * @param text Full text
     * @param position Click/tap position
     * @return The word at that position, or null if not on a word
     */
    fun extractWordAtPosition(text: String, position: Int): String? {
        val (start, end) = findWordBoundaries(text, position)
        
        if (start >= end) return null
        
        val word = text.substring(start, end).trim()
        return if (word.isEmpty()) null else word
    }
}
