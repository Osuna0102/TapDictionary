package com.godtap.dictionary.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {
    
    /**
     * Search for entry by exact kanji match
     */
    @Query("SELECT * FROM dictionary_entries WHERE kanjiElements LIKE '%\"kanji\":\"' || :term || '\"%' LIMIT 10")
    suspend fun searchByKanji(term: String): List<DictionaryEntry>
    
    /**
     * Search for entry by exact reading match
     */
    @Query("SELECT * FROM dictionary_entries WHERE readingElements LIKE '%\"reading\":\"' || :term || '\"%' LIMIT 10")
    suspend fun searchByReading(term: String): List<DictionaryEntry>
    
    /**
     * Search by kanji or reading prefix
     */
    @Query("SELECT * FROM dictionary_entries WHERE kanjiElements LIKE '%\"kanji\":\"' || :term || '%' OR readingElements LIKE '%\"reading\":\"' || :term || '%' ORDER BY frequency DESC LIMIT 20")
    suspend fun searchByPrefix(term: String): List<DictionaryEntry>
    
    /**
     * Search by any substring in kanji or reading
     */
    @Query("SELECT * FROM dictionary_entries WHERE kanjiElements LIKE '%' || :term || '%' OR readingElements LIKE '%' || :term || '%' ORDER BY frequency DESC LIMIT 30")
    suspend fun searchBySubstring(term: String): List<DictionaryEntry>
    
    /**
     * Get most common words (for default display)
     */
    @Query("SELECT * FROM dictionary_entries WHERE isCommon = 1 ORDER BY frequency DESC LIMIT :limit")
    suspend fun getCommonWords(limit: Int = 100): List<DictionaryEntry>
    
    /**
     * Search by entry ID
     */
    @Query("SELECT * FROM dictionary_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getById(entryId: Long): DictionaryEntry?
    
    /**
     * Insert multiple entries (batch import)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)
    
    /**
     * Insert single entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryEntry)
    
    /**
     * Get total entry count
     */
    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getCount(): Int
    
    /**
     * Delete all entries (for re-import)
     */
    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()
    
    /**
     * Get entries by JLPT level
     */
    @Query("SELECT * FROM dictionary_entries WHERE jlptLevel = :level ORDER BY frequency DESC LIMIT :limit")
    suspend fun getByJlptLevel(level: Int, limit: Int = 100): List<DictionaryEntry>
}
