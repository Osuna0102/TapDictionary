package com.godtap.dictionary.deinflection

/**
 * Japanese verb/adjective deinflector
 * Based on Yomitan's deinflection system (japanese-transforms.js)
 * 
 * Converts inflected forms to dictionary forms:
 * - 食べました → 食べる (past → dictionary form)
 * - 食べている → 食べる (progressive → dictionary form)
 * - 大きかった → 大きい (adjective past → dictionary form)
 */
class JapaneseDeinflector {
    
    /**
     * Deinflect a word and return all possible dictionary forms
     * Returns list in order of likelihood (most likely first)
     */
    fun deinflect(word: String): List<DeinflectionResult> {
        val results = mutableListOf<DeinflectionResult>()
        
        // Always include original form first
        results.add(DeinflectionResult(word, emptyList()))
        
        // Apply all deinflection rules
        for (rule in deinflectionRules) {
            if (word.endsWith(rule.inflectedSuffix)) {
                val stem = word.substring(0, word.length - rule.inflectedSuffix.length)
                val deinflected = stem + rule.dictionarySuffix
                
                if (deinflected.isNotEmpty()) {
                    results.add(DeinflectionResult(
                        term = deinflected,
                        rules = listOf(rule.name)
                    ))
                }
            }
        }
        
        return results.distinctBy { it.term }
    }
    
    companion object {
        /**
         * Deinflection rules based on Yomitan's japanese-transforms.js
         * Format: inflected suffix → dictionary suffix
         */
        private val deinflectionRules = listOf(
            // Verb past tense (た form)
            DeinflectionRule("た", "る", "past", "v1"),
            DeinflectionRule("いた", "く", "past", "v5"),
            DeinflectionRule("いだ", "ぐ", "past", "v5"),
            DeinflectionRule("した", "す", "past", "v5"),
            DeinflectionRule("った", "う", "past", "v5"),
            DeinflectionRule("った", "つ", "past", "v5"),
            DeinflectionRule("った", "る", "past", "v5"),
            DeinflectionRule("んだ", "ぬ", "past", "v5"),
            DeinflectionRule("んだ", "ぶ", "past", "v5"),
            DeinflectionRule("んだ", "む", "past", "v5"),
            
            // Polite form (ます)
            DeinflectionRule("ます", "る", "polite", "v1"),
            DeinflectionRule("います", "う", "polite", "v5"),
            DeinflectionRule("きます", "く", "polite", "v5"),
            DeinflectionRule("ぎます", "ぐ", "polite", "v5"),
            DeinflectionRule("します", "す", "polite", "v5"),
            DeinflectionRule("ちます", "つ", "polite", "v5"),
            DeinflectionRule("にます", "ぬ", "polite", "v5"),
            DeinflectionRule("びます", "ぶ", "polite", "v5"),
            DeinflectionRule("みます", "む", "polite", "v5"),
            DeinflectionRule("ります", "る", "polite", "v5"),
            
            // Polite past (ました)
            DeinflectionRule("ました", "る", "polite-past", "v1"),
            DeinflectionRule("いました", "う", "polite-past", "v5"),
            DeinflectionRule("きました", "く", "polite-past", "v5"),
            DeinflectionRule("ぎました", "ぐ", "polite-past", "v5"),
            DeinflectionRule("しました", "す", "polite-past", "v5"),
            DeinflectionRule("ちました", "つ", "polite-past", "v5"),
            DeinflectionRule("にました", "ぬ", "polite-past", "v5"),
            DeinflectionRule("びました", "ぶ", "polite-past", "v5"),
            DeinflectionRule("みました", "む", "polite-past", "v5"),
            DeinflectionRule("りました", "る", "polite-past", "v5"),
            
            // Te-form (ている progressive)
            DeinflectionRule("て", "る", "te-form", "v1"),
            DeinflectionRule("いて", "く", "te-form", "v5"),
            DeinflectionRule("いで", "ぐ", "te-form", "v5"),
            DeinflectionRule("して", "す", "te-form", "v5"),
            DeinflectionRule("って", "う", "te-form", "v5"),
            DeinflectionRule("って", "つ", "te-form", "v5"),
            DeinflectionRule("って", "る", "te-form", "v5"),
            DeinflectionRule("んで", "ぬ", "te-form", "v5"),
            DeinflectionRule("んで", "ぶ", "te-form", "v5"),
            DeinflectionRule("んで", "む", "te-form", "v5"),
            
            // Progressive (ている)
            DeinflectionRule("ている", "る", "progressive", "v1"),
            DeinflectionRule("いている", "く", "progressive", "v5"),
            DeinflectionRule("いでいる", "ぐ", "progressive", "v5"),
            DeinflectionRule("している", "す", "progressive", "v5"),
            DeinflectionRule("っている", "う", "progressive", "v5"),
            DeinflectionRule("っている", "つ", "progressive", "v5"),
            DeinflectionRule("っている", "る", "progressive", "v5"),
            DeinflectionRule("んでいる", "ぬ", "progressive", "v5"),
            DeinflectionRule("んでいる", "ぶ", "progressive", "v5"),
            DeinflectionRule("んでいる", "む", "progressive", "v5"),
            
            // Negative (ない)
            DeinflectionRule("ない", "る", "negative", "v1"),
            DeinflectionRule("わない", "う", "negative", "v5"),
            DeinflectionRule("かない", "く", "negative", "v5"),
            DeinflectionRule("がない", "ぐ", "negative", "v5"),
            DeinflectionRule("さない", "す", "negative", "v5"),
            DeinflectionRule("たない", "つ", "negative", "v5"),
            DeinflectionRule("なない", "ぬ", "negative", "v5"),
            DeinflectionRule("ばない", "ぶ", "negative", "v5"),
            DeinflectionRule("まない", "む", "negative", "v5"),
            DeinflectionRule("らない", "る", "negative", "v5"),
            
            // Potential (られる / できる)
            DeinflectionRule("られる", "る", "potential", "v1"),
            DeinflectionRule("える", "う", "potential", "v5"),
            DeinflectionRule("ける", "く", "potential", "v5"),
            DeinflectionRule("げる", "ぐ", "potential", "v5"),
            DeinflectionRule("せる", "す", "potential", "v5"),
            DeinflectionRule("てる", "つ", "potential", "v5"),
            DeinflectionRule("ねる", "ぬ", "potential", "v5"),
            DeinflectionRule("べる", "ぶ", "potential", "v5"),
            DeinflectionRule("める", "む", "potential", "v5"),
            DeinflectionRule("れる", "る", "potential", "v5"),
            
            // Causative (させる)
            DeinflectionRule("させる", "る", "causative", "v1"),
            DeinflectionRule("わせる", "う", "causative", "v5"),
            DeinflectionRule("かせる", "く", "causative", "v5"),
            DeinflectionRule("がせる", "ぐ", "causative", "v5"),
            DeinflectionRule("させる", "す", "causative", "v5"),
            DeinflectionRule("たせる", "つ", "causative", "v5"),
            DeinflectionRule("なせる", "ぬ", "causative", "v5"),
            DeinflectionRule("ばせる", "ぶ", "causative", "v5"),
            DeinflectionRule("ませる", "む", "causative", "v5"),
            DeinflectionRule("らせる", "る", "causative", "v5"),
            
            // Passive (られる)
            DeinflectionRule("られる", "る", "passive", "v1"),
            DeinflectionRule("われる", "う", "passive", "v5"),
            DeinflectionRule("かれる", "く", "passive", "v5"),
            DeinflectionRule("がれる", "ぐ", "passive", "v5"),
            DeinflectionRule("される", "す", "passive", "v5"),
            DeinflectionRule("たれる", "つ", "passive", "v5"),
            DeinflectionRule("なれる", "ぬ", "passive", "v5"),
            DeinflectionRule("ばれる", "ぶ", "passive", "v5"),
            DeinflectionRule("まれる", "む", "passive", "v5"),
            DeinflectionRule("られる", "る", "passive", "v5"),
            
            // Imperative
            DeinflectionRule("ろ", "る", "imperative", "v1"),
            DeinflectionRule("え", "う", "imperative", "v5"),
            DeinflectionRule("け", "く", "imperative", "v5"),
            DeinflectionRule("げ", "ぐ", "imperative", "v5"),
            DeinflectionRule("せ", "す", "imperative", "v5"),
            DeinflectionRule("て", "つ", "imperative", "v5"),
            DeinflectionRule("ね", "ぬ", "imperative", "v5"),
            DeinflectionRule("べ", "ぶ", "imperative", "v5"),
            DeinflectionRule("め", "む", "imperative", "v5"),
            DeinflectionRule("れ", "る", "imperative", "v5"),
            
            // I-adjectives
            DeinflectionRule("かった", "い", "adj-past", "adj-i"),
            DeinflectionRule("くない", "い", "adj-negative", "adj-i"),
            DeinflectionRule("くて", "い", "adj-te", "adj-i"),
            DeinflectionRule("ければ", "い", "adj-conditional", "adj-i"),
            DeinflectionRule("かったら", "い", "adj-conditional-past", "adj-i"),
            
            // Stem forms (連用形)
            DeinflectionRule("", "る", "stem", "v1"),  // Already stem for ichidan
            DeinflectionRule("い", "う", "stem", "v5"),
            DeinflectionRule("き", "く", "stem", "v5"),
            DeinflectionRule("ぎ", "ぐ", "stem", "v5"),
            DeinflectionRule("し", "す", "stem", "v5"),
            DeinflectionRule("ち", "つ", "stem", "v5"),
            DeinflectionRule("に", "ぬ", "stem", "v5"),
            DeinflectionRule("び", "ぶ", "stem", "v5"),
            DeinflectionRule("み", "む", "stem", "v5"),
            DeinflectionRule("り", "る", "stem", "v5"),
            
            // Special verbs
            DeinflectionRule("きます", "くる", "polite", "vk"),
            DeinflectionRule("きました", "くる", "polite-past", "vk"),
            DeinflectionRule("きて", "くる", "te-form", "vk"),
            DeinflectionRule("こない", "くる", "negative", "vk"),
            DeinflectionRule("します", "する", "polite", "vs"),
            DeinflectionRule("しました", "する", "polite-past", "vs"),
            DeinflectionRule("して", "する", "te-form", "vs"),
            DeinflectionRule("しない", "する", "negative", "vs")
        )
    }
}

/**
 * Result of deinflection with the transformation rules applied
 */
data class DeinflectionResult(
    val term: String,           // Deinflected dictionary form
    val rules: List<String>     // Rules applied (e.g., ["polite-past", "v5"])
)

/**
 * Single deinflection transformation rule
 */
private data class DeinflectionRule(
    val inflectedSuffix: String,    // e.g., "ました"
    val dictionarySuffix: String,   // e.g., "る"
    val name: String,                // e.g., "polite-past"
    val type: String                 // e.g., "v5" (godan verb)
)
