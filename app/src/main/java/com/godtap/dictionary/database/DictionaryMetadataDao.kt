package com.godtap.dictionary.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing dictionary metadata
 */
@Dao
interface DictionaryMetadataDao {
    
    // Insert & Update
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: DictionaryMetadata): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<DictionaryMetadata>)
    
    @Update
    suspend fun update(metadata: DictionaryMetadata)
    
    @Delete
    suspend fun delete(metadata: DictionaryMetadata)
    
    // Queries
    @Query("SELECT * FROM dictionary_metadata WHERE dictionaryId = :dictionaryId")
    suspend fun getByDictionaryId(dictionaryId: String): DictionaryMetadata?
    
    @Query("SELECT * FROM dictionary_metadata WHERE id = :id")
    suspend fun getById(id: Long): DictionaryMetadata?
    
    @Query("SELECT * FROM dictionary_metadata ORDER BY name ASC")
    suspend fun getAll(): List<DictionaryMetadata>
    
    @Query("SELECT * FROM dictionary_metadata ORDER BY name ASC")
    fun getAllFlow(): Flow<List<DictionaryMetadata>>
    
    @Query("SELECT * FROM dictionary_metadata WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<DictionaryMetadata>
    
    @Query("SELECT * FROM dictionary_metadata WHERE installed = 1 ORDER BY name ASC")
    suspend fun getInstalled(): List<DictionaryMetadata>
    
    @Query("""
        SELECT * FROM dictionary_metadata 
        WHERE sourceLanguage = :sourceLang AND targetLanguage = :targetLang
        ORDER BY name ASC
    """)
    suspend fun getByLanguagePair(sourceLang: String, targetLang: String): List<DictionaryMetadata>
    
    @Query("""
        SELECT * FROM dictionary_metadata 
        WHERE enabled = 1 
        AND sourceLanguage = :sourceLang 
        AND targetLanguage = :targetLang
        ORDER BY name ASC
    """)
    suspend fun getEnabledByLanguagePair(sourceLang: String, targetLang: String): List<DictionaryMetadata>
    
    // Status updates
    @Query("UPDATE dictionary_metadata SET enabled = :enabled WHERE dictionaryId = :dictionaryId")
    suspend fun setEnabled(dictionaryId: String, enabled: Boolean)
    
    @Query("""
        UPDATE dictionary_metadata 
        SET installed = :installed, installDate = :installDate, entryCount = :entryCount 
        WHERE dictionaryId = :dictionaryId
    """)
    suspend fun setInstalled(dictionaryId: String, installed: Boolean, installDate: Long?, entryCount: Long)
    
    @Query("UPDATE dictionary_metadata SET lastUsed = :timestamp WHERE dictionaryId = :dictionaryId")
    suspend fun updateLastUsed(dictionaryId: String, timestamp: Long)
    
    @Query("DELETE FROM dictionary_metadata WHERE dictionaryId = :dictionaryId")
    suspend fun deleteByDictionaryId(dictionaryId: String)
    
    @Query("DELETE FROM dictionary_metadata")
    suspend fun deleteAll()
    
    // Statistics
    @Query("SELECT COUNT(*) FROM dictionary_metadata")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM dictionary_metadata WHERE installed = 1")
    suspend fun getInstalledCount(): Int
    
    @Query("SELECT COUNT(*) FROM dictionary_metadata WHERE enabled = 1")
    suspend fun getEnabledCount(): Int
    
    @Query("SELECT SUM(entryCount) FROM dictionary_metadata WHERE enabled = 1")
    suspend fun getTotalEnabledEntries(): Long?
    
    // Get distinct language pairs
    @Query("""
        SELECT DISTINCT sourceLanguage || '-' || targetLanguage as languagePair
        FROM dictionary_metadata
        WHERE installed = 1
        ORDER BY languagePair
    """)
    suspend fun getAvailableLanguagePairs(): List<String>
    
    // Active dictionary management
    @Query("SELECT * FROM dictionary_metadata WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveDictionary(): DictionaryMetadata?
    
    @Query("UPDATE dictionary_metadata SET isActive = 0")
    suspend fun clearAllActive()
    
    @Query("UPDATE dictionary_metadata SET isActive = :isActive WHERE dictionaryId = :dictionaryId")
    suspend fun setActive(dictionaryId: String, isActive: Boolean)
}
