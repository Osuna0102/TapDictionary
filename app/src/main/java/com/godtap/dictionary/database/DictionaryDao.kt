package com.godtap.dictionary.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO following Yomitan's IndexedDB approach:
 * - Fast indexed lookups on primaryExpression and primaryReading
 * - No LIKE queries (they bypass indexes and cause full table scans)
 * - Exact match queries hit indexes directly
 */
@Dao
interface DictionaryDao {
    
    /**
     * Fast indexed exact match (like Yomitan's findTermsBulk with exact match)
     * Uses index on primaryExpression and primaryReading
     * Can filter by specific dictionaries
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE (primaryExpression = :term OR primaryReading = :term)
        AND (:dictionaryIds IS NULL OR dictionaryId IN (:dictionaryIds))
        ORDER BY frequency DESC
        LIMIT 1
    """)
    suspend fun findExact(term: String, dictionaryIds: List<String>? = null): DictionaryEntry?
    
    /**
     * Increment lookup count for a specific entry
     */
    @Query("UPDATE dictionary_entries SET lookupCount = lookupCount + 1 WHERE id = :entryId")
    suspend fun incrementLookupCount(entryId: Long)
    
    /**
     * Batch search - finds first match for each term in the list
     * This is similar to Yomitan's bulk search capability
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE (primaryExpression IN (:terms) OR primaryReading IN (:terms))
        AND (:dictionaryIds IS NULL OR dictionaryId IN (:dictionaryIds))
        ORDER BY frequency DESC
    """)
    suspend fun findBulk(terms: List<String>, dictionaryIds: List<String>? = null): List<DictionaryEntry>
    
    /**
     * Search by expression only (kanji form)
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE primaryExpression = :expression
        ORDER BY frequency DESC
        LIMIT 10
    """)
    suspend fun searchByExpression(expression: String): List<DictionaryEntry>
    
    /**
     * Search by reading only (hiragana/katakana)
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE primaryReading = :reading
        ORDER BY frequency DESC
        LIMIT 10
    """)
    suspend fun searchByReading(reading: String): List<DictionaryEntry>
    
    /**
     * Get most common words (for default display)
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE isCommon = 1 
        ORDER BY frequency DESC 
        LIMIT :limit
    """)
    suspend fun getCommonWords(limit: Int = 100): List<DictionaryEntry>
    
    /**
     * Fuzzy search - finds entries containing the query string
     * WARNING: Uses LIKE which is slow, only for debugging
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE primaryExpression LIKE '%' || :query || '%' 
           OR primaryReading LIKE '%' || :query || '%'
        ORDER BY frequency DESC 
        LIMIT :limit
    """)
    suspend fun searchFuzzy(query: String, limit: Int = 20): List<DictionaryEntry>
    
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
     * Delete entries for a specific dictionary
     */
    @Query("DELETE FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun deleteByDictionary(dictionaryId: String)
    
    /**
     * Get entry count for a specific dictionary
     */
    @Query("SELECT COUNT(*) FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun getCountByDictionary(dictionaryId: String): Int
    
    /**
     * Get entries by JLPT level
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE jlptLevel = :level 
        ORDER BY frequency DESC 
        LIMIT :limit
    """)
    suspend fun getByJlptLevel(level: Int, limit: Int = 100): List<DictionaryEntry>
    
    /**
     * Get recent lookups (entries with lookupCount > 0) ordered by most recent
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE lookupCount > 0 
        ORDER BY id DESC 
        LIMIT :limit
    """)
    suspend fun getRecentLookups(limit: Int = 10): List<DictionaryEntry>
    
    /**
     * Get most looked up word
     */
    @Query("""
        SELECT * FROM dictionary_entries 
        WHERE lookupCount > 0 
        ORDER BY lookupCount DESC 
        LIMIT 1
    """)
    suspend fun getMostLookedUp(): DictionaryEntry?
    
    /**
     * Get total lookup count across all entries
     */
    @Query("SELECT SUM(lookupCount) FROM dictionary_entries")
    suspend fun getTotalLookupCount(): Int
}
