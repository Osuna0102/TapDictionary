package com.godtap.dictionary.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for installed dictionaries
 * Supports multiple dictionaries with different language pairs
 */
@Entity(
    tableName = "dictionary_metadata",
    indices = [
        Index(value = ["sourceLanguage", "targetLanguage"]),
        Index(value = ["enabled"])
    ]
)
data class DictionaryMetadata(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Dictionary identification
    val name: String,                    // e.g., "JMdict (English)", "Spanish-Korean"
    val dictionaryId: String,            // Unique identifier
    val version: String,                 // Version string
    val format: DictionaryFormat,        // Yomichan, Migaku, DSL, etc.
    
    // Language pair
    val sourceLanguage: String,          // ISO 639-1: "ja", "es", "ko"
    val targetLanguage: String,          // ISO 639-1: "en", "es", "ko"
    
    // Download information
    val downloadUrl: String?,            // URL to download from
    val fileSize: Long = 0,              // Size in bytes
    
    // Status
    val enabled: Boolean = true,         // Whether to use in lookups
    val installed: Boolean = false,      // Whether downloaded and imported
    val installDate: Long? = null,       // Timestamp when installed
    val isActive: Boolean = false,       // Only one dictionary can be active at a time
    
    // Statistics
    val entryCount: Long = 0,            // Number of entries
    val lastUsed: Long? = null,          // Last lookup timestamp
    
    // Additional metadata
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
    val website: String? = null,
    val tags: String? = null             // Comma-separated: "frequency", "examples", etc.
)

/**
 * Dictionary format types
 */
enum class DictionaryFormat {
    YOMICHAN,       // Yomichan/Yomitan JSON format
    MIGAKU,         // Migaku dictionary format
    DSL,            // ABBYY Lingvo DSL format
    STARDICT,       // StarDict format
    EPWING,         // EPWING format (Japanese)
    CUSTOM          // Custom JSON format
}

/**
 * Language pair entity for filtering available dictionaries
 */
@Entity(tableName = "language_pairs")
data class LanguagePair(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceLanguage: String,         // ISO 639-1 code
    val targetLanguage: String,
    val languagePairName: String        // e.g., "Japanese → English"
)

/**
 * Updated DictionaryEntry to support multiple dictionaries
 */
data class MultiDictionaryEntry(
    val entry: DictionaryEntry,
    val dictionaryName: String,
    val dictionaryId: String,
    val confidence: Float = 1.0f        // Ranking score for multi-dict results
)
