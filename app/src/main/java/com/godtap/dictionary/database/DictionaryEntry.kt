package com.godtap.dictionary.database

import androidx.room.Entity
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
 */
@Entity(tableName = "dictionary_entries")
@TypeConverters(Converters::class)
data class DictionaryEntry(
    @PrimaryKey
    val entryId: Long,            // JMdict sequence number
    
    // Kanji elements (multiple forms possible)
    val kanjiElements: List<KanjiElement>,
    
    // Reading elements (hiragana/katakana pronunciations)
    val readingElements: List<ReadingElement>,
    
    // Sense entries (meanings with context)
    val senses: List<Sense>,
    
    // Metadata
    val frequency: Int = 0,       // Frequency score (higher = more common)
    val jlptLevel: Int? = null,   // JLPT level (5 = N5, 1 = N1)
    val isCommon: Boolean = false // Common word flag
) {
    /**
     * Get primary kanji form (first kanji element or null)
     */
    fun getPrimaryKanji(): String? = kanjiElements.firstOrNull()?.kanji
    
    /**
     * Get primary reading (first reading element)
     */
    fun getPrimaryReading(): String = readingElements.first().reading
    
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
    val restrictions: List<String> = emptyList() // Restricted to specific readings
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
