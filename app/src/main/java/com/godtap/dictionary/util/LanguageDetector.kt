package com.godtap.dictionary.util

/**
 * Multi-language text detector
 * Detects various languages for dictionary lookup
 */
object LanguageDetector {
    
    // Japanese character ranges
    private val HIRAGANA_RANGE = '\u3040'..'\u309F'
    private val KATAKANA_RANGE = '\u30A0'..'\u30FF'
    private val KANJI_RANGE = '\u4E00'..'\u9FAF'
    private val KATAKANA_HALF_WIDTH_RANGE = '\uFF65'..'\uFF9F'
    
    // Korean character ranges
    private val HANGUL_SYLLABLES_RANGE = '\uAC00'..'\uD7AF'
    private val HANGUL_JAMO_RANGE = '\u1100'..'\u11FF'
    private val HANGUL_COMPATIBILITY_JAMO_RANGE = '\u3130'..'\u318F'
    
    // Spanish/Latin alphabet with diacritics
    private val SPANISH_LETTERS = setOf(
        'á', 'é', 'í', 'ó', 'ú', 'ü', 'ñ',
        'Á', 'É', 'Í', 'Ó', 'Ú', 'Ü', 'Ñ',
        '¿', '¡'
    )
    
    /**
     * Detect if text contains Japanese characters
     */
    fun containsJapanese(text: String): Boolean {
        return text.any { isJapaneseChar(it) }
    }
    
    /**
     * Detect if text contains Korean characters
     */
    fun containsKorean(text: String): Boolean {
        return text.any { isKoreanChar(it) }
    }
    
    /**
     * Detect if text contains Spanish/Latin text
     * This is tricky because Spanish uses Latin alphabet like English
     * We consider it Spanish if it has Spanish diacritics or is mostly Latin letters
     */
    fun containsSpanish(text: String): Boolean {
        // Check for Spanish-specific characters
        if (text.any { it in SPANISH_LETTERS }) {
            return true
        }
        
        // Check if text is mostly Latin letters (a-z, A-Z)
        val latinCount = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val totalChars = text.filter { !it.isWhitespace() }.length
        
        // If more than 50% Latin letters, consider it Spanish/Latin text
        return totalChars > 0 && (latinCount.toFloat() / totalChars) > 0.5f
    }
    
    /**
     * Detect if text contains Chinese characters
     */
    fun containsChinese(text: String): Boolean {
        // Chinese uses same Kanji range but typically no kana
        return text.any { it in KANJI_RANGE } && !text.any { it in HIRAGANA_RANGE || it in KATAKANA_RANGE }
    }
    
    /**
     * Detect the primary language in text
     * Returns ISO 639-1 language code or null if unknown
     */
    fun detectLanguage(text: String): String? {
        if (containsJapanese(text)) return "ja"
        if (containsKorean(text)) return "ko"
        if (containsChinese(text)) return "zh"
        if (containsSpanish(text)) return "es"
        return null
    }
    
    /**
     * Check if character is Japanese
     */
    fun isJapaneseChar(char: Char): Boolean {
        return char in HIRAGANA_RANGE ||
               char in KATAKANA_RANGE ||
               char in KANJI_RANGE ||
               char in KATAKANA_HALF_WIDTH_RANGE
    }
    
    /**
     * Check if character is Korean
     */
    fun isKoreanChar(char: Char): Boolean {
        return char in HANGUL_SYLLABLES_RANGE ||
               char in HANGUL_JAMO_RANGE ||
               char in HANGUL_COMPATIBILITY_JAMO_RANGE
    }
    
    /**
     * Check if character is Spanish/Latin
     */
    fun isSpanishChar(char: Char): Boolean {
        return char in 'a'..'z' ||
               char in 'A'..'Z' ||
               char in SPANISH_LETTERS
    }
    
    /**
     * Extract characters of a specific language from text
     */
    fun extractLanguageText(text: String, languageCode: String): String {
        return when (languageCode) {
            "ja" -> text.filter { isJapaneseChar(it) }
            "ko" -> text.filter { isKoreanChar(it) }
            "es" -> text.filter { isSpanishChar(it) || it.isWhitespace() }
            else -> text
        }
    }
    
    /**
     * Check if text matches the expected language
     */
    fun matchesLanguage(text: String, languageCode: String): Boolean {
        return when (languageCode) {
            "ja" -> containsJapanese(text)
            "ko" -> containsKorean(text)
            "es" -> containsSpanish(text)
            "zh" -> containsChinese(text)
            else -> false
        }
    }
}
