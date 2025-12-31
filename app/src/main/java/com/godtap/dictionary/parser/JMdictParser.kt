package com.godtap.dictionary.parser

import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.database.KanjiElement
import com.godtap.dictionary.database.ReadingElement
import com.godtap.dictionary.database.Sense
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Parser for JMdict in Yomichan/Yomitan format
 * Compatible with the format used by Yomichan, 10ten, and Yomitan
 * Format: term_bank_*.json files containing arrays of term entries
 */
class JMdictParser {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Parse Yomichan term bank file
     * Format: Array of [expression, reading, tags, rules, score, glosses, sequence, term_tags]
     * @param file The term_bank_*.json file
     * @return List of parsed dictionary entries
     */
    fun parseYomichanTermBank(file: File): List<DictionaryEntry> {
        val jsonString = file.readText()
        val rawEntries: List<List<kotlinx.serialization.json.JsonElement>> = json.decodeFromString(jsonString)
        
        return rawEntries.mapIndexedNotNull { index, raw ->
            try {
                convertYomichanEntry(raw, index.toLong())
            } catch (e: Exception) {
                Log.w("JMdictParser", "Failed to parse entry: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Convert Yomichan array format to our DictionaryEntry
     * Format: [expression, reading, tags, rules, score, glosses, sequence, term_tags]
     * Example: ["食べる", "たべる", "P", "v1", 0, ["to eat", "to consume"], 1358280, ""]
     */
    private fun convertYomichanEntry(raw: List<kotlinx.serialization.json.JsonElement>, fallbackId: Long): DictionaryEntry {
        // Extract fields from array
        val expression = raw.getOrNull(0)?.toString()?.trim('"') ?: ""
        val reading = raw.getOrNull(1)?.toString()?.trim('"') ?: expression
        val tags = raw.getOrNull(2)?.toString()?.trim('"') ?: ""
        val rules = raw.getOrNull(3)?.toString()?.trim('"') ?: ""
        val score = raw.getOrNull(4)?.toString()?.toIntOrNull() ?: 0
        val glossesJson = raw.getOrNull(5)
        val sequence = raw.getOrNull(6)?.toString()?.toLongOrNull() ?: fallbackId
        val termTags = raw.getOrNull(7)?.toString()?.trim('"') ?: ""
        
        // Parse glosses (can be array or string)
        val glosses = try {
            when {
                glossesJson == null -> emptyList()
                glossesJson.toString().startsWith("[") -> {
                    json.decodeFromString<List<String>>(glossesJson.toString())
                }
                else -> listOf(glossesJson.toString().trim('"'))
            }
        } catch (e: Exception) {
            listOf(glossesJson.toString().trim('"'))
        }
        
        // Parse kanji elements
        val kanjiElements = if (expression.isNotEmpty() && expression != reading) {
            listOf(KanjiElement(
                kanji = expression,
                priority = if (tags.contains("P")) listOf("common") else emptyList()
            ))
        } else {
            emptyList()
        }
        
        // Parse reading elements
        val readingElements = listOf(ReadingElement(
            reading = reading,
            noKanji = kanjiElements.isEmpty(),
            priority = if (tags.contains("P")) listOf("common") else emptyList()
        ))
        
        // Parse parts of speech from rules
        val partsOfSpeech = parseRulesToPOS(rules)
        
        // Create sense
        val senses = listOf(Sense(
            glosses = glosses,
            partsOfSpeech = partsOfSpeech,
            misc = if (termTags.isNotEmpty()) listOf(termTags) else emptyList()
        ))
        
        // Calculate frequency
        val frequency = calculateYomichanFrequency(tags, score)
        
        // Get primary expression and reading for indexed lookups (CRITICAL for speed)
        val primaryExpression = kanjiElements.firstOrNull()?.kanji
        val primaryReading = readingElements.first().reading
        
        return DictionaryEntry(
            entryId = sequence,
            primaryExpression = primaryExpression,  // Indexed column
            primaryReading = primaryReading,        // Indexed column
            kanjiElements = kanjiElements,
            readingElements = readingElements,
            senses = senses,
            frequency = frequency,
            jlptLevel = extractJlptFromTags(tags),
            isCommon = tags.contains("P")
        )
    }
    
    /**
     * Parse Yomichan rules to parts of speech
     */
    private fun parseRulesToPOS(rules: String): List<String> {
        if (rules.isEmpty()) return emptyList()
        
        val posMap = mapOf(
            "v1" to "ichidan verb",
            "v5" to "godan verb",
            "vs" to "suru verb",
            "vk" to "kuru verb",
            "adj-i" to "i-adjective",
            "adj-na" to "na-adjective",
            "n" to "noun",
            "pn" to "pronoun",
            "adv" to "adverb"
        )
        
        return rules.split(" ").mapNotNull { posMap[it] }
    }
    
    /**
     * Calculate frequency from Yomichan tags and score
     */
    private fun calculateYomichanFrequency(tags: String, score: Int): Int {
        var freq = 50 // Base frequency
        
        if (tags.contains("P")) freq += 40  // Popular
        if (tags.contains("news")) freq += 30
        if (tags.contains("ichi")) freq += 25
        if (tags.contains("spec")) freq += 20
        if (tags.contains("gai")) freq += 15
        
        // Adjust by score (usually negative, so we invert)
        freq += (-score).coerceIn(0, 10)
        
        return freq.coerceIn(0, 100)
    }
    
    /**
     * Extract JLPT level from tags
     */
    private fun extractJlptFromTags(tags: String): Int? {
        return when {
            tags.contains("jlpt-n5") || tags.contains("jlpt5") -> 5
            tags.contains("jlpt-n4") || tags.contains("jlpt4") -> 4
            tags.contains("jlpt-n3") || tags.contains("jlpt3") -> 3
            tags.contains("jlpt-n2") || tags.contains("jlpt2") -> 2
            tags.contains("jlpt-n1") || tags.contains("jlpt1") -> 1
            else -> null
        }
    }
    
    // Keep old methods for backward compatibility but mark as deprecated
    @Deprecated("Use parseYomichanTermBank instead")
    fun parse(inputStream: InputStream): List<DictionaryEntry> {
        return emptyList()
    }
}

