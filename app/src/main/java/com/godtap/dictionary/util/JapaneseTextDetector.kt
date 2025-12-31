package com.godtap.dictionary.util

object JapaneseTextDetector {
    
    private val HIRAGANA_RANGE = '\u3040'..'\u309F'
    private val KATAKANA_RANGE = '\u30A0'..'\u30FF'
    private val KANJI_RANGE = '\u4E00'..'\u9FAF'
    private val KATAKANA_HALF_WIDTH_RANGE = '\uFF65'..'\uFF9F'
    
    fun containsJapanese(text: String): Boolean {
        return text.any { char ->
            char in HIRAGANA_RANGE ||
            char in KATAKANA_RANGE ||
            char in KANJI_RANGE ||
            char in KATAKANA_HALF_WIDTH_RANGE
        }
    }
    
    fun isJapaneseChar(char: Char): Boolean {
        return char in HIRAGANA_RANGE ||
               char in KATAKANA_RANGE ||
               char in KANJI_RANGE ||
               char in KATAKANA_HALF_WIDTH_RANGE
    }
    
    fun extractJapaneseText(text: String): String {
        return text.filter { isJapaneseChar(it) }
    }
}
