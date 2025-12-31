package com.godtap.dictionary.tokenizer

import com.godtap.dictionary.util.JapaneseTextDetector

/**
 * Simple Japanese tokenizer for MVP
 * Generates all possible substrings, prioritizing longer ones
 */
class JapaneseTokenizer {
    
    fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        // Extract only Japanese characters
        val cleanText = text.filter { JapaneseTextDetector.isJapaneseChar(it) }
        if (cleanText.isEmpty()) return emptyList()
        
        val tokens = mutableListOf<String>()
        
        // Add the full text first (highest priority)
        tokens.add(cleanText)
        
        // Generate all substrings from longest to shortest
        for (length in cleanText.length - 1 downTo 1) {
            for (start in 0..cleanText.length - length) {
                val token = cleanText.substring(start, start + length)
                if (token.isNotEmpty() && !tokens.contains(token)) {
                    tokens.add(token)
                }
            }
        }
        
        return tokens
    }
    
    /**
     * Tokenize with focus on word-like segments
     * This tries to be smarter by breaking on character type boundaries
     */
    fun tokenizeSmarter(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        // Extract only Japanese characters
        val cleanText = text.filter { JapaneseTextDetector.isJapaneseChar(it) }
        if (cleanText.isEmpty()) return emptyList()
        
        val tokens = mutableListOf<String>()
        
        // Add full text
        tokens.add(cleanText)
        
        // Break text into segments by character type
        val segments = segmentByCharacterType(cleanText)
        
        // Add each segment
        segments.forEach { segment ->
            if (segment.isNotEmpty() && !tokens.contains(segment)) {
                tokens.add(segment)
            }
        }
        
        // Generate substrings from each segment and combinations
        for (segment in segments) {
            for (length in segment.length - 1 downTo 1) {
                for (start in 0..segment.length - length) {
                    val token = segment.substring(start, start + length)
                    if (token.isNotEmpty() && !tokens.contains(token)) {
                        tokens.add(token)
                    }
                }
            }
        }
        
        // Also try combinations of adjacent segments
        for (i in 0 until segments.size - 1) {
            val combined = segments[i] + segments[i + 1]
            if (!tokens.contains(combined)) {
                tokens.add(combined)
            }
        }
        
        return tokens
    }
    
    private fun segmentByCharacterType(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<String>()
        var currentSegment = StringBuilder()
        var currentType: CharType? = null
        
        for (char in text) {
            val charType = getCharType(char)
            
            if (currentType == null || currentType == charType) {
                currentSegment.append(char)
                currentType = charType
            } else {
                if (currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.toString())
                }
                currentSegment = StringBuilder()
                currentSegment.append(char)
                currentType = charType
            }
        }
        
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString())
        }
        
        return segments
    }
    
    private fun getCharType(char: Char): CharType {
        return when {
            char in '\u4E00'..'\u9FAF' -> CharType.KANJI
            char in '\u3040'..'\u309F' -> CharType.HIRAGANA
            char in '\u30A0'..'\u30FF' -> CharType.KATAKANA
            char in '\uFF65'..'\uFF9F' -> CharType.KATAKANA
            else -> CharType.OTHER
        }
    }
    
    private enum class CharType {
        KANJI, HIRAGANA, KATAKANA, OTHER
    }
}
