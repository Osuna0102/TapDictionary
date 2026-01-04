package com.godtap.dictionary.util

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.godtap.dictionary.overlay.TextUnderlineRenderer

/**
 * Extracts visible text and word boundaries from AccessibilityNodeInfo tree.
 * Used for visual underlining feature.
 */
object TextExtractor {
    
    private const val TAG = "TextExtractor"
    private const val MAX_TEXT_LENGTH = 5000 // Limit text extraction to prevent performance issues
    
    /**
     * Extract all visible words with their screen bounds from accessibility node tree
     */
    fun extractVisibleWords(rootNode: AccessibilityNodeInfo?): List<TextUnderlineRenderer.WordBounds> {
        if (rootNode == null) return emptyList()
        
        val wordBounds = mutableListOf<TextUnderlineRenderer.WordBounds>()
        
        try {
            extractWordsRecursive(rootNode, wordBounds)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting visible words", e)
        }
        
        return wordBounds
    }
    
    /**
     * Recursively traverse node tree and extract words with bounds
     */
    private fun extractWordsRecursive(
        node: AccessibilityNodeInfo,
        wordBounds: MutableList<TextUnderlineRenderer.WordBounds>
    ) {
        try {
            // Skip invisible nodes
            if (!node.isVisibleToUser) return
            
            // Extract text from this node
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && text.length < MAX_TEXT_LENGTH) {
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)
                
                // Extract words from text
                val words = extractWords(text, nodeBounds)
                wordBounds.addAll(words)
            }
            
            // Recursively process children
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    extractWordsRecursive(child, wordBounds)
                    child.recycle()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing node", e)
        }
    }
    
    /**
     * Extract individual words from text and estimate their bounds.
     * This is a simplified implementation - actual bounds depend on font metrics.
     */
    private fun extractWords(text: String, containerBounds: Rect): List<TextUnderlineRenderer.WordBounds> {
        val words = mutableListOf<TextUnderlineRenderer.WordBounds>()
        
        // Split text into words (handles multiple languages)
        val tokens = tokenizeText(text)
        
        // Estimate bounds for each word
        // This is approximate - actual rendering would require font metrics
        val totalLength = text.length
        val containerWidth = containerBounds.width()
        val containerHeight = containerBounds.height()
        
        var currentX = containerBounds.left
        val avgCharWidth = if (totalLength > 0) containerWidth / totalLength else 0
        
        tokens.forEach { token ->
            if (token.text.isNotBlank() && token.text.length > 1) {
                // Estimate word bounds
                val wordWidth = token.text.length * avgCharWidth
                val wordBounds = Rect(
                    currentX,
                    containerBounds.top,
                    (currentX + wordWidth).coerceAtMost(containerBounds.right),
                    containerBounds.bottom
                )
                
                words.add(TextUnderlineRenderer.WordBounds(token.text, wordBounds))
                currentX += wordWidth
            }
        }
        
        return words
    }
    
    /**
     * Tokenize text into words (handles Japanese, Korean, Spanish, etc.)
     */
    private fun tokenizeText(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        
        // Detect if text contains CJK characters (Japanese, Korean, Chinese)
        val hasCJK = text.any { char ->
            char.code in 0x3040..0x309F || // Hiragana
            char.code in 0x30A0..0x30FF || // Katakana
            char.code in 0x4E00..0x9FFF || // CJK Unified Ideographs
            char.code in 0xAC00..0xD7AF    // Hangul
        }
        
        if (hasCJK) {
            // For CJK text, treat each character or small group as a token
            tokenizeCJK(text, tokens)
        } else {
            // For alphabetic languages, split by spaces and punctuation
            tokenizeAlphabetic(text, tokens)
        }
        
        return tokens
    }
    
    /**
     * Tokenize CJK text (each character or word can be a lookup target)
     */
    private fun tokenizeCJK(text: String, tokens: MutableList<Token>) {
        var i = 0
        val sb = StringBuilder()
        
        while (i < text.length) {
            val char = text[i]
            
            // Check if CJK character
            val isCJK = char.code in 0x3040..0x309F || // Hiragana
                       char.code in 0x30A0..0x30FF || // Katakana
                       char.code in 0x4E00..0x9FFF || // CJK Unified Ideographs
                       char.code in 0xAC00..0xD7AF    // Hangul
            
            if (isCJK) {
                // Add accumulated non-CJK text
                if (sb.isNotEmpty()) {
                    tokens.add(Token(sb.toString(), i - sb.length))
                    sb.clear()
                }
                
                // Extract compound word (up to 5 characters for Japanese)
                val start = i
                val end = (i + 5).coerceAtMost(text.length)
                val compound = text.substring(start, end)
                
                // Add progressively smaller substrings (like Yomitan lookup)
                for (len in compound.length downTo 1) {
                    val word = compound.substring(0, len)
                    tokens.add(Token(word, start))
                }
                
                i++
            } else {
                sb.append(char)
                i++
            }
        }
        
        // Add remaining text
        if (sb.isNotEmpty()) {
            tokens.add(Token(sb.toString(), text.length - sb.length))
        }
    }
    
    /**
     * Tokenize alphabetic text (Spanish, English, etc.)
     */
    private fun tokenizeAlphabetic(text: String, tokens: MutableList<Token>) {
        val wordRegex = "\\p{L}+".toRegex() // Match word characters
        
        wordRegex.findAll(text).forEach { match ->
            tokens.add(Token(match.value, match.range.first))
        }
    }
    
    /**
     * Token data class
     */
    data class Token(
        val text: String,
        val startIndex: Int
    )
}
