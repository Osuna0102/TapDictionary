package com.godtap.dictionary.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_entries")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val kanji: String?,           // e.g., "食べる"
    val reading: String,          // e.g., "たべる"
    val glosses: String,          // JSON array or comma-separated: "to eat, to consume"
    val partOfSpeech: String?,    // e.g., "verb", "noun"
    val frequency: Int = 0,       // Higher = more common
    val jlptLevel: Int? = null    // 5 = N5, 1 = N1
)
