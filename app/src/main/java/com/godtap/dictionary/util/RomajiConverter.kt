package com.godtap.dictionary.util

/**
 * Simple Hiragana/Katakana to Romaji converter
 * For display purposes in the dictionary popup
 */
object RomajiConverter {
    
    private val hiraganaToRomaji = mapOf(
        // Basic hiragana
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "ゐ" to "wi", "ゑ" to "we", "を" to "wo", "ん" to "n",
        
        // Small kana
        "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o",
        "っ" to "", // small tsu (handled separately for doubling)
        
        // Combined sounds
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        
        // Katakana (common ones)
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "wo", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
        "ッ" to "" // small tsu
    )
    
    /**
     * Convert hiragana/katakana to romaji
     * Returns null if the string doesn't contain kana
     */
    fun toRomaji(kana: String?): String? {
        if (kana.isNullOrBlank()) return null
        
        val result = StringBuilder()
        var i = 0
        
        while (i < kana.length) {
            // Try 2-character combinations first (for きゃ, しゃ, etc.)
            if (i < kana.length - 1) {
                val twoChar = kana.substring(i, i + 2)
                if (hiraganaToRomaji.containsKey(twoChar)) {
                    result.append(hiraganaToRomaji[twoChar])
                    i += 2
                    continue
                }
            }
            
            // Try single character
            val char = kana[i].toString()
            when {
                char == "っ" || char == "ッ" -> {
                    // Small tsu: double the next consonant
                    if (i < kana.length - 1) {
                        val nextChar = kana[i + 1].toString()
                        val nextRomaji = hiraganaToRomaji[nextChar]
                        if (nextRomaji != null && nextRomaji.isNotEmpty()) {
                            result.append(nextRomaji[0]) // Double the first consonant
                        }
                    }
                }
                hiraganaToRomaji.containsKey(char) -> {
                    result.append(hiraganaToRomaji[char])
                }
                else -> {
                    // Not a kana character (kanji, punctuation, etc.)
                    result.append(char)
                }
            }
            i++
        }
        
        return if (result.toString() == kana) {
            // No conversion happened - not kana
            null
        } else {
            result.toString()
        }
    }
    
    /**
     * Extract hiragana from a string like "食べる (たべる)"
     * Returns null if not found
     */
    fun extractHiragana(text: String): String? {
        val match = Regex("\\(([ぁ-ん]+)\\)").find(text)
        return match?.groupValues?.get(1)
    }
}
