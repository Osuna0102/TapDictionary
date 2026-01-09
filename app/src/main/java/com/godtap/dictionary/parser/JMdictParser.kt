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
        Log.d("JMdictParser", "Parsing term bank: ${file.name}, size: ${file.length()} bytes")
        
        // For small files (< 10MB), use the fast method
        if (file.length() < 10 * 1024 * 1024) {
            return parseYomichanTermBankFast(file)
        }
        
        // For large files, use streaming to avoid OOM
        Log.i("JMdictParser", "Large file detected (${file.length() / (1024*1024)}MB), using streaming parser")
        return parseYomichanTermBankStreaming(file)
    }
    
    /**
     * Fast parsing for small files - loads entire JSON into memory
     */
    private fun parseYomichanTermBankFast(file: File): List<DictionaryEntry> {
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
     * Streaming parser for large files - processes JSON array entry by entry
     * to avoid loading the entire file into memory
     */
    private fun parseYomichanTermBankStreaming(file: File): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        var entryIndex = 0L
        val chunkSize = 1000 // Process 1000 entries at a time
        
        file.inputStream().bufferedReader().use { reader ->
            // Skip opening bracket
            var char = reader.read()
            while (char != -1 && char.toChar() != '[') {
                char = reader.read()
            }
            
            if (char == -1) {
                throw IllegalStateException("Invalid JSON: Missing opening bracket")
            }
            
            var buffer = StringBuilder()
            var depth = 0
            var inString = false
            var escapeNext = false
            
            char = reader.read()
            while (char != -1) {
                val c = char.toChar()
                
                when {
                    escapeNext -> {
                        buffer.append(c)
                        escapeNext = false
                    }
                    c == '\\' && inString -> {
                        buffer.append(c)
                        escapeNext = true
                    }
                    c == '"' -> {
                        buffer.append(c)
                        inString = !inString
                    }
                    inString -> {
                        buffer.append(c)
                    }
                    c == '[' -> {
                        buffer.append(c)
                        depth++
                    }
                    c == ']' -> {
                        if (depth == 0) {
                            // End of top-level array
                            break
                        }
                        buffer.append(c)
                        depth--
                        
                        if (depth == 0) {
                            // End of entry
                            try {
                                val entryJson = buffer.toString().trim()
                                if (entryJson.isNotEmpty()) {
                                    val rawEntry: List<kotlinx.serialization.json.JsonElement> = 
                                        json.decodeFromString(entryJson)
                                    convertYomichanEntry(rawEntry, entryIndex)?.let {
                                        entries.add(it)
                                    }
                                    entryIndex++
                                    
                                    // Log progress every chunk
                                    if (entryIndex % chunkSize == 0L) {
                                        Log.d("JMdictParser", "Parsed $entryIndex entries...")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("JMdictParser", "Failed to parse entry $entryIndex: ${e.message}")
                            }
                            buffer.clear()
                        }
                    }
                    c == ',' && depth == 0 -> {
                        // Skip commas between entries
                    }
                    !c.isWhitespace() || depth > 0 -> {
                        buffer.append(c)
                    }
                }
                
                char = reader.read()
            }
        }
        
        Log.i("JMdictParser", "Streaming parse complete: $entryIndex entries")
        return entries
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
        
        // Parse glosses (can be array, string, or structured content)
        val glosses = try {
            when {
                glossesJson == null -> emptyList()
                else -> parseStructuredContent(glossesJson)
            }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to parse glosses: ${e.message}")
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
     * Parse structured content to extract only target language definitions
     * Filters out example sentences and source language text
     */
    private fun parseStructuredContent(glossesJson: kotlinx.serialization.json.JsonElement): List<String> {
        try {
            val jsonString = glossesJson.toString()
            
            // Check if it's structured content format
            if (!jsonString.contains("structured-content")) {
                // Regular glosses format
                return when {
                    jsonString.startsWith("[") -> {
                        json.decodeFromString<List<String>>(jsonString)
                    }
                    else -> listOf(jsonString.trim('"'))
                }
            }
            
            // Parse as structured content
            val definitions = mutableListOf<String>()
            
            // Extract content from the JSON structure
            // Structured content has format: [{"type": "structured-content", "content": [...]}]
            val structuredArray = json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(jsonString)
            
            for (structuredItem in structuredArray) {
                val content = structuredItem["content"] as? kotlinx.serialization.json.JsonArray ?: continue
                
                // Navigate through the nested structure to find glosses
                for (contentItem in content) {
                    val contentObj = contentItem as? kotlinx.serialization.json.JsonObject ?: continue
                    val tag = (contentObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    
                    // Look for "ol" (ordered list) with "glosses" data
                    if (tag == "ol") {
                        val data = contentObj["data"] as? kotlinx.serialization.json.JsonObject
                        val dataContent = (data?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        
                        if (dataContent == "glosses") {
                            // Extract the actual definitions from list items
                            val listContent = contentObj["content"] as? kotlinx.serialization.json.JsonArray
                            listContent?.let { list ->
                                extractDefinitionsFromList(list, definitions)
                            }
                        }
                    }
                }
            }
            
            return definitions.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to parse structured content: ${e.message}")
            // Fallback: return as single string
            return listOf(glossesJson.toString().trim('"'))
        }
    }
    
    /**
     * Extract definitions from structured content list
     * Now includes example sentences formatted nicely
     */
    private fun extractDefinitionsFromList(
        list: kotlinx.serialization.json.JsonArray,
        definitions: MutableList<String>
    ) {
        for (item in list) {
            val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            if (tag == "li") {
                val content = itemObj["content"] as? kotlinx.serialization.json.JsonArray ?: continue
                
                for (contentItem in content) {
                    val divObj = contentItem as? kotlinx.serialization.json.JsonObject ?: continue
                    val divContent = divObj["content"] as? kotlinx.serialization.json.JsonArray ?: continue
                    
                    val definitionBuilder = StringBuilder()
                    
                    // Extract first text element (the actual definition)
                    for (element in divContent) {
                        when (element) {
                            is kotlinx.serialization.json.JsonPrimitive -> {
                                val text = element.content
                                // Add the main definition
                                if (text.isNotBlank() && !isSourceLanguageExample(text)) {
                                    definitionBuilder.append(text)
                                }
                            }
                            is kotlinx.serialization.json.JsonObject -> {
                                // Check if this is a details element with examples
                                val detailsTag = (element["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                if (detailsTag == "details") {
                                    val examples = extractExamples(element)
                                    if (examples.isNotEmpty()) {
                                        definitionBuilder.append("\n")
                                        examples.forEachIndexed { index, example ->
                                            definitionBuilder.append("\n  • $example")
                                        }
                                    }
                                }
                            }
                            else -> continue
                        }
                    }
                    
                    val fullDefinition = definitionBuilder.toString().trim()
                    if (fullDefinition.isNotBlank()) {
                        definitions.add(fullDefinition)
                    }
                }
            }
        }
    }
    
    /**
     * Extract example sentences from details element
     * Returns list of formatted examples
     */
    private fun extractExamples(detailsElement: kotlinx.serialization.json.JsonObject): List<String> {
        val examples = mutableListOf<String>()
        
        try {
            val detailsContent = detailsElement["content"] as? kotlinx.serialization.json.JsonArray ?: return examples
            
            for (item in detailsContent) {
                val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
                val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                
                // Skip the summary element, look for example divs
                if (tag == "div") {
                    val data = itemObj["data"] as? kotlinx.serialization.json.JsonObject
                    val dataContent = (data?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    
                    if (dataContent == "extra-info") {
                        // Navigate to example-sentence
                        val content = itemObj["content"] as? kotlinx.serialization.json.JsonObject ?: continue
                        val exampleDiv = content["content"] as? kotlinx.serialization.json.JsonArray ?: continue
                        
                        for (exampleItem in exampleDiv) {
                            val exampleObj = exampleItem as? kotlinx.serialization.json.JsonObject ?: continue
                            val exampleData = exampleObj["data"] as? kotlinx.serialization.json.JsonObject
                            val exampleContent = (exampleData?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            
                            if (exampleContent == "example-sentence-a") {
                                val exampleText = (exampleObj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                if (!exampleText.isNullOrBlank()) {
                                    examples.add(exampleText)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to extract examples: ${e.message}")
        }
        
        return examples
    }
    
    /**
     * Check if text appears to be a source language example rather than a target language definition
     * For Spanish-Korean: filters out standalone Spanish text without Korean
     * But allows mixed examples like "respirar con dificultad 어렵게 숨쉬다"
     */
    private fun isSourceLanguageExample(text: String): Boolean {
        // Too long (likely a full example sentence)
        if (text.length > 100) return true
        
        return false
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

