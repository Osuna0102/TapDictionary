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
        
        // ENHANCED: Parse structured content to extract examples, notes, references, etc.
        val structuredData = try {
            when {
                glossesJson == null -> StructuredContentData(glosses = glosses)
                else -> parseStructuredContentEnhanced(glossesJson)
            }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to extract enhanced data: ${e.message}")
            StructuredContentData(glosses = glosses)
        }
        
        // Use enhanced data if available, otherwise fall back to basic glosses
        val finalGlosses = structuredData.glosses.ifEmpty { glosses }
        
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
        
        // Create sense with ALL extracted data
        val senses = listOf(Sense(
            glosses = finalGlosses,
            partsOfSpeech = partsOfSpeech,
            misc = if (termTags.isNotEmpty()) listOf(termTags) else emptyList(),
            // NEW: Enhanced data fields
            examples = structuredData.examples.map { (source, translation) ->
                com.godtap.dictionary.database.ExampleSentence(source, translation)
            },
            notes = structuredData.notes,
            references = structuredData.references.map { (text, href) ->
                com.godtap.dictionary.database.WordReference(text, href)
            },
            antonyms = structuredData.antonyms.map { (text, href) ->
                com.godtap.dictionary.database.WordReference(text, href)
            },
            infoGlossary = structuredData.infoGlossary,
            sourceLanguages = structuredData.sourceLanguages
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
     * Comprehensive structured content parsing result
     */
    private data class StructuredContentData(
        val glosses: List<String> = emptyList(),
        val examples: List<Pair<String, String>> = emptyList(), // (source, translation)
        val notes: List<String> = emptyList(),
        val references: List<Pair<String, String>> = emptyList(), // (text, href)
        val antonyms: List<Pair<String, String>> = emptyList(),
        val infoGlossary: List<String> = emptyList(),
        val sourceLanguages: List<String> = emptyList()
    )

    /**
     * Parse structured content to extract ALL available data types
     * Now returns comprehensive dictionary data including examples, notes, references, etc.
     */
    private fun parseStructuredContent(glossesJson: kotlinx.serialization.json.JsonElement): List<String> {
        try {
            val jsonString = glossesJson.toString()
            
            // Check if it's structured content format
            if (!jsonString.contains("structured-content")) {
                // Regular glosses format (plain strings)
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
                
                // Recursively search for glossary/glosses lists in the nested structure
                extractGlossaryFromContent(content, definitions)
            }
            
            return definitions.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to parse structured content: ${e.message}")
            // Fallback: return as single string
            return listOf(glossesJson.toString().trim('"'))
        }
    }
    
    /**
     * ENHANCED: Parse structured content and extract ALL data types
     * This method extracts glosses, examples, notes, references, antonyms, etc.
     */
    private fun parseStructuredContentEnhanced(glossesJson: kotlinx.serialization.json.JsonElement): StructuredContentData {
        val result = StructuredContentData()
        
        try {
            val jsonString = glossesJson.toString()
            
            // Check if it's structured content format
            if (!jsonString.contains("structured-content")) {
                // Regular glosses format (plain strings)
                val glosses = when {
                    jsonString.startsWith("[") -> {
                        json.decodeFromString<List<String>>(jsonString)
                    }
                    else -> listOf(jsonString.trim('"'))
                }
                return result.copy(glosses = glosses)
            }
            
            // Parse as structured content
            val structuredArray = json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(jsonString)
            
            val glosses = mutableListOf<String>()
            val examples = mutableListOf<Pair<String, String>>()
            val notes = mutableListOf<String>()
            val references = mutableListOf<Pair<String, String>>()
            val antonyms = mutableListOf<Pair<String, String>>()
            val infoGlossary = mutableListOf<String>()
            val sourceLanguages = mutableListOf<String>()
            
            for (structuredItem in structuredArray) {
                val content = structuredItem["content"] as? kotlinx.serialization.json.JsonArray ?: continue
                
                // Extract all data types from the nested structure
                extractAllDataTypes(
                    content,
                    glosses,
                    examples,
                    notes,
                    references,
                    antonyms,
                    infoGlossary,
                    sourceLanguages
                )
            }
            
            return StructuredContentData(
                glosses = glosses.filter { it.isNotBlank() },
                examples = examples,
                notes = notes.filter { it.isNotBlank() },
                references = references,
                antonyms = antonyms,
                infoGlossary = infoGlossary.filter { it.isNotBlank() },
                sourceLanguages = sourceLanguages.filter { it.isNotBlank() }
            ).also {
                Log.d("JMdictParser", "✓ Parsed structured content: ${it.glosses.size} glosses, ${it.examples.size} examples, ${it.notes.size} notes, ${it.references.size} references, ${it.antonyms.size} antonyms")
            }
        } catch (e: Exception) {
            Log.w("JMdictParser", "Failed to parse structured content: ${e.message}")
            // Fallback: return as single string in glosses
            return result.copy(glosses = listOf(glossesJson.toString().trim('"')))
        }
    }
    
    /**
     * Recursively extract ALL data types from structured content
     * Handles nested content arrays and identifies data types by data.content attribute
     */
    private fun extractAllDataTypes(
        content: kotlinx.serialization.json.JsonArray,
        glosses: MutableList<String>,
        examples: MutableList<Pair<String, String>>,
        notes: MutableList<String>,
        references: MutableList<Pair<String, String>>,
        antonyms: MutableList<Pair<String, String>>,
        infoGlossary: MutableList<String>,
        sourceLanguages: MutableList<String>
    ) {
        for (contentItem in content) {
            val contentObj = contentItem as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (contentObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val data = contentObj["data"] as? kotlinx.serialization.json.JsonObject
            val dataContent = (data?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            // Check what type of list this is based on data.content attribute
            when (dataContent) {
                "glosses", "glossary" -> {
                    // Main definitions
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found glossary list")
                        val listContent = contentObj["content"]
                        when (listContent) {
                            is kotlinx.serialization.json.JsonArray -> {
                                extractDefinitionsFromList(listContent, glosses)
                            }
                            is kotlinx.serialization.json.JsonObject -> {
                                val singleItemArray = kotlinx.serialization.json.JsonArray(listOf(listContent))
                                extractDefinitionsFromList(singleItemArray, glosses)
                            }
                            else -> {
                                // JsonPrimitive or null - ignore
                            }
                        }
                    }
                }
                "examples" -> {
                    // Example sentences (source + translation)
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found examples list")
                        extractExamplesFromList(contentObj["content"] as? kotlinx.serialization.json.JsonArray, examples)
                    }
                }
                "notes" -> {
                    // Usage notes
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found notes list")
                        extractTextListFromContent(contentObj["content"] as? kotlinx.serialization.json.JsonArray, notes)
                    }
                }
                "references" -> {
                    // Related words with links
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found references list")
                        extractReferencesFromList(contentObj["content"] as? kotlinx.serialization.json.JsonArray, references)
                    }
                }
                "antonyms" -> {
                    // Antonyms with links
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found antonyms list")
                        extractReferencesFromList(contentObj["content"] as? kotlinx.serialization.json.JsonArray, antonyms)
                        Log.d("JMdictParser", "  Extracted ${antonyms.size} antonyms so far")
                    }
                }
                "infoGlossary" -> {
                    // Additional glossary info
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found infoGlossary list")
                        extractTextListFromContent(contentObj["content"] as? kotlinx.serialization.json.JsonArray, infoGlossary)
                    }
                }
                "sourceLanguages" -> {
                    // Etymology/source language info
                    if (tag == "ol" || tag == "ul") {
                        Log.d("JMdictParser", "✓ Found sourceLanguages list")
                        extractTextListFromContent(contentObj["content"] as? kotlinx.serialization.json.JsonArray, sourceLanguages)
                    }
                }
            }
            
            // Recurse into nested content arrays
            val nestedContent = contentObj["content"] as? kotlinx.serialization.json.JsonArray
            if (nestedContent != null && dataContent == null) {
                // Only recurse if we haven't already processed this list
                extractAllDataTypes(nestedContent, glosses, examples, notes, references, antonyms, infoGlossary, sourceLanguages)
            }
        }
    }
    
    /**
     * Extract example sentences from list (Japanese sentence + English translation)
     */
    private fun extractExamplesFromList(
        list: kotlinx.serialization.json.JsonArray?,
        examples: MutableList<Pair<String, String>>
    ) {
        if (list == null) return
        
        var sourceSentence = ""
        for (item in list) {
            val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            if (tag == "li") {
                val liContent = itemObj["content"]
                val lang = (itemObj["lang"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                
                when (liContent) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        val text = liContent.content.trim()
                        if (text.isNotBlank()) {
                            if (lang == "en") {
                                // This is the translation - pair with previous source
                                if (sourceSentence.isNotBlank()) {
                                    examples.add(Pair(sourceSentence, text))
                                    Log.d("JMdictParser", "  ✓ Extracted example: '$sourceSentence' → '$text'")
                                    sourceSentence = ""
                                }
                            } else {
                                // This is the source sentence (Japanese, etc.)
                                sourceSentence = text
                            }
                        }
                    }
                    else -> {
                        // JsonArray, JsonObject, or null - ignore
                    }
                }
            }
        }
    }
    
    /**
     * Extract text list (for notes, infoGlossary, sourceLanguages)
     */
    private fun extractTextListFromContent(
        list: kotlinx.serialization.json.JsonArray?,
        output: MutableList<String>
    ) {
        if (list == null) return
        
        for (item in list) {
            val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            if (tag == "li") {
                val liContent = itemObj["content"]
                
                when (liContent) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        val text = liContent.content.trim()
                        if (text.isNotBlank()) {
                            output.add(text)
                            Log.d("JMdictParser", "  ✓ Extracted text: '$text'")
                        }
                    }
                    is kotlinx.serialization.json.JsonArray -> {
                        // Sometimes content is wrapped in array
                        for (contentPart in liContent) {
                            if (contentPart is kotlinx.serialization.json.JsonPrimitive) {
                                val text = contentPart.content.trim()
                                if (text.isNotBlank()) {
                                    output.add(text)
                                }
                            }
                        }
                    }
                    else -> {
                        // JsonObject or null - ignore
                    }
                }
            }
        }
    }
    
    /**
     * Extract references/antonyms (words with optional links)
     */
    private fun extractReferencesFromList(
        list: kotlinx.serialization.json.JsonArray?,
        references: MutableList<Pair<String, String>>
    ) {
        if (list == null) return
        
        for (item in list) {
            val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            if (tag == "li") {
                val liContent = itemObj["content"]
                
                when (liContent) {
                    is kotlinx.serialization.json.JsonArray -> {
                        // Complex case: content is array of mixed types (strings + links + spans)
                        // Example: ["antonym: ", {"tag":"a","content":"word","href":"..."}, {...}]
                        for (contentPart in liContent) {
                            when (contentPart) {
                                is kotlinx.serialization.json.JsonObject -> {
                                    val partTag = (contentPart["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    if (partTag == "a") {
                                        // This is a link - extract text and href
                                        val text = (contentPart["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                                        val href = (contentPart["href"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                                        if (text.isNotBlank()) {
                                            references.add(Pair(text, href))
                                            Log.d("JMdictParser", "  ✓ Extracted reference/antonym: '$text' (href=$href)")
                                        }
                                    }
                                }
                                is kotlinx.serialization.json.JsonPrimitive -> {
                                    // Skip plain text prefixes like "antonym: " or "see also: "
                                    val text = contentPart.content.trim()
                                    if (text.isNotBlank() && !text.endsWith(":")) {
                                        // Only add if it's not a prefix
                                        references.add(Pair(text, ""))
                                    }
                                }
                                else -> {
                                    // JsonArray or other types - ignore
                                }
                            }
                        }
                    }
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        // Simple case: plain text reference
                        val text = liContent.content.trim()
                        if (text.isNotBlank()) {
                            references.add(Pair(text, ""))
                            Log.d("JMdictParser", "  ✓ Extracted reference: '$text'")
                        }
                    }
                    else -> {
                        // JsonObject or null - ignore
                    }
                }
            }
        }
    }

    
    /**
     * Recursively search for glossary/glosses lists in structured content
     * Handles nested content arrays (e.g., Japanese JMdict format)
     */
    private fun extractGlossaryFromContent(
        content: kotlinx.serialization.json.JsonArray,
        definitions: MutableList<String>
    ) {
        for (contentItem in content) {
            val contentObj = contentItem as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (contentObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            // Check if this is a glossary/glosses list
            if (tag == "ol" || tag == "ul") {
                val data = contentObj["data"] as? kotlinx.serialization.json.JsonObject
                val dataContent = (data?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                
                if (dataContent == "glosses" || dataContent == "glossary") {
                    // Found the glossary list! Extract definitions from list items
                    Log.d("JMdictParser", "✓ Found glossary/glosses list (tag=$tag, data=$dataContent)")
                    val listContent = contentObj["content"]
                    
                    when (listContent) {
                        is kotlinx.serialization.json.JsonArray -> {
                            // Content is an array of li items
                            extractDefinitionsFromList(listContent, definitions)
                        }
                        is kotlinx.serialization.json.JsonObject -> {
                            // Content is a single li item (wrap in array)
                            Log.d("JMdictParser", "  Note: Single object instead of array, wrapping")
                            val singleItemArray = kotlinx.serialization.json.JsonArray(listOf(listContent))
                            extractDefinitionsFromList(singleItemArray, definitions)
                        }
                        else -> {
                            Log.w("JMdictParser", "⊘ Glossary list has unexpected content type: ${listContent?.javaClass?.simpleName}")
                        }
                    }
                    continue // Don't recurse into glossary lists
                }
            }
            
            // Recurse into nested content arrays
            val nestedContent = contentObj["content"] as? kotlinx.serialization.json.JsonArray
            if (nestedContent != null) {
                extractGlossaryFromContent(nestedContent, definitions)
            }
        }
    }
    
    private fun extractDefinitionsFromList(
        list: kotlinx.serialization.json.JsonArray,
        definitions: MutableList<String>
    ) {
        for (item in list) {
            val itemObj = item as? kotlinx.serialization.json.JsonObject ?: continue
            val tag = (itemObj["tag"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            
            if (tag == "li") {
                val liContent = itemObj["content"]
                
                when (liContent) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        // Simple case: content is directly a string
                        val text = liContent.content
                        if (text.isNotBlank() && !isSourceLanguageExample(text)) {
                            Log.d("JMdictParser", "  ✓ Extracted gloss: '$text'")
                            definitions.add(text)
                        }
                    }
                    is kotlinx.serialization.json.JsonArray -> {
                        // Complex case: content is an array of objects (divs, etc.)
                        for (contentItem in liContent) {
                            when (contentItem) {
                                is kotlinx.serialization.json.JsonPrimitive -> {
                                    val text = contentItem.content
                                    if (text.isNotBlank() && !isSourceLanguageExample(text)) {
                                        Log.d("JMdictParser", "  ✓ Extracted gloss (from array): '$text'")
                                        definitions.add(text)
                                    }
                                }
                                else -> {
                                    Log.d("JMdictParser", "  ⊘ Skipping non-primitive in li array: ${contentItem.javaClass.simpleName}")
                                }
                            }
                        }
                    }
                    else -> {
                        Log.d("JMdictParser", "  ⊘ Unexpected li content type: ${liContent?.javaClass?.simpleName}")
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

