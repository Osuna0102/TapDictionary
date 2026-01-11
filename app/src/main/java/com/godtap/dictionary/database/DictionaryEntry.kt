package com.godtap.dictionary.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * JMdict-compatible dictionary entry entity
 * Supports full JMdict data structure with multiple readings, meanings, and metadata
 * 
 * PERFORMANCE: Indexed columns (primaryExpression, primaryReading) enable fast lookups
 * following Yomitan's approach
 */
@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(value = ["primaryExpression"]),
        Index(value = ["primaryReading"]),
        Index(value = ["frequency"]),
        Index(value = ["dictionaryId"])
    ]
)
@TypeConverters(Converters::class)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,              // Auto-generated ID
    
    val entryId: Long,             // JMdict sequence number (or unique per dictionary)
    val dictionaryId: String = "jmdict_en", // Dictionary this entry belongs to
    
    // INDEXED COLUMNS FOR FAST LOOKUP (like Yomitan's IndexedDB indices)
    val primaryExpression: String?,  // First kanji form (null if kana-only word)
    val primaryReading: String,      // First reading (always present)
    
    // Full data structures
    val kanjiElements: List<KanjiElement>,
    val readingElements: List<ReadingElement>,
    val senses: List<Sense>,
    
    // Metadata
    val frequency: Int = 0,       // Frequency score (higher = more common)
    val jlptLevel: Int? = null,   // JLPT level (5 = N5, 1 = N1)
    val isCommon: Boolean = false, // Common word flag
    val lookupCount: Int = 0     // Number of times this word was looked up
) {
    /**
     * Get primary kanji form (first kanji element or null)
     */
    fun getPrimaryKanji(): String? = kanjiElements.firstOrNull()?.kanji
    
    /**
     * Get first reading element (detailed reading object)
     */
    fun getFirstReadingElement(): ReadingElement = readingElements.first()
    
    /**
     * Get all glosses (English meanings) from all senses
     */
    fun getAllGlosses(): List<String> = senses.flatMap { it.glosses }
    
    /**
     * Get primary gloss (first meaning)
     */
    fun getPrimaryGloss(): String = senses.firstOrNull()?.glosses?.firstOrNull() ?: ""
    
    /**
     * Get all parts of speech
     */
    fun getPartsOfSpeech(): List<String> = senses.flatMap { it.partsOfSpeech }
}

/**
 * Kanji element (written form)
 */
@Serializable
data class KanjiElement(
    val kanji: String,                    // e.g., "食べる"
    val priority: List<String> = emptyList(), // Priority tags (news1, ichi1, etc.)
    val info: List<String> = emptyList()  // Additional information
)

/**
 * Reading element (pronunciation)
 */
@Serializable
data class ReadingElement(
    val reading: String,                  // e.g., "たべる"
    val noKanji: Boolean = false,        // True if this entry has no kanji
    val restrictedTo: List<String> = emptyList(), // Restricted to specific kanji forms
    val priority: List<String> = emptyList(), // Priority tags
    val info: List<String> = emptyList() // Additional information
)

/**
 * Example sentence with source and translation
 */
@Serializable
data class ExampleSentence(
    val source: String,      // Original sentence (e.g., Japanese)
    val translation: String  // Translated sentence (e.g., English)
)

/**
 * Reference to a related word
 */
@Serializable
data class WordReference(
    val text: String,        // Display text
    val href: String = ""    // Link reference (if available)
)

/**
 * Sense (meaning with context)
 */
@Serializable
data class Sense(
    val glosses: List<String>,           // English meanings
    val partsOfSpeech: List<String>,     // e.g., ["verb", "ichidan"]
    val fields: List<String> = emptyList(), // Usage fields (comp, ling, etc.)
    val misc: List<String> = emptyList(),   // Miscellaneous info
    val dialects: List<String> = emptyList(), // Dialect info
    val info: List<String> = emptyList(),    // Additional info
    val languageSource: List<String> = emptyList(), // Loanword source
    val restrictions: List<String> = emptyList(), // Restricted to specific readings
    // NEW: Enhanced data fields
    val examples: List<ExampleSentence> = emptyList(), // Example sentences
    val notes: List<String> = emptyList(),             // Usage notes
    val references: List<WordReference> = emptyList(), // Related words
    val antonyms: List<WordReference> = emptyList(),   // Antonyms
    val infoGlossary: List<String> = emptyList(),      // Additional glossary info
    val sourceLanguages: List<String> = emptyList()    // Etymology info
)

/**
 * Type converters for Room database
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    
    @TypeConverter
    fun fromKanjiElementList(value: List<KanjiElement>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toKanjiElementList(value: String): List<KanjiElement> {
        return json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromReadingElementList(value: List<ReadingElement>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toReadingElementList(value: String): List<ReadingElement> {
        return json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromSenseList(value: List<Sense>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toSenseList(value: String): List<Sense> {
        return json.decodeFromString(value)
    }
}
