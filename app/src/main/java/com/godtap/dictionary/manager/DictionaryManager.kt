package com.godtap.dictionary.manager

import android.content.Context
import android.util.Log
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.database.DictionaryFormat
import com.godtap.dictionary.database.DictionaryMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Manages multiple dictionaries with different language pairs
 * Handles enabling/disabling, downloading, and metadata management
 */
class DictionaryManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val metadataDao = database.dictionaryMetadataDao()
    private val dictionaryDao = database.dictionaryDao()
    
    companion object {
        private const val TAG = "DictionaryManager"
        
        // Predefined dictionaries - only Japanese->English by default
        val AVAILABLE_DICTIONARIES = listOf(
            DictionaryMetadata(
                name = "JMdict (English)",
                dictionaryId = "jmdict_en",
                version = "latest",
                format = DictionaryFormat.YOMICHAN,
                sourceLanguage = "ja",
                targetLanguage = "en",
                downloadUrl = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english_with_examples.zip",
                fileSize = 30_000_000,
                description = "Comprehensive Japanese-English dictionary with 175,000+ entries",
                author = "EDRDG / Yomidevs",
                license = "CC BY-SA 3.0",
                website = "https://www.edrdg.org/jmdict/j_jmdict.html",
                tags = "frequency,examples,common",
                isActive = true  // Default active dictionary
            )
        )
    }
    
    /**
     * Initialize dictionary metadata database with available dictionaries
     */
    suspend fun initializeAvailableDictionaries() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing available dictionaries")
        
        // Insert all available dictionaries (they won't overwrite existing ones)
        AVAILABLE_DICTIONARIES.forEach { dict ->
            val existing = metadataDao.getByDictionaryId(dict.dictionaryId)
            if (existing == null) {
                metadataDao.insert(dict.copy(enabled = false, installed = false))
                Log.d(TAG, "Added dictionary: ${dict.name}")
            }
        }
    }
    
    /**
     * Get all dictionaries
     */
    suspend fun getAllDictionaries(): List<DictionaryMetadata> = withContext(Dispatchers.IO) {
        metadataDao.getAll()
    }
    
    /**
     * Get all dictionaries as Flow for reactive UI
     */
    fun getAllDictionariesFlow(): Flow<List<DictionaryMetadata>> {
        return metadataDao.getAllFlow()
    }
    
    /**
     * Get enabled dictionaries
     */
    suspend fun getEnabledDictionaries(): List<DictionaryMetadata> = withContext(Dispatchers.IO) {
        metadataDao.getEnabled()
    }
    
    /**
     * Get installed dictionaries
     */
    suspend fun getInstalledDictionaries(): List<DictionaryMetadata> = withContext(Dispatchers.IO) {
        metadataDao.getInstalled()
    }
    
    /**
     * Get dictionaries by language pair
     */
    suspend fun getDictionariesByLanguagePair(
        sourceLang: String,
        targetLang: String
    ): List<DictionaryMetadata> = withContext(Dispatchers.IO) {
        metadataDao.getByLanguagePair(sourceLang, targetLang)
    }
    
    /**
     * Enable or disable a dictionary
     */
    suspend fun setDictionaryEnabled(dictionaryId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Setting dictionary $dictionaryId enabled=$enabled")
        metadataDao.setEnabled(dictionaryId, enabled)
    }
    
    /**
     * Mark dictionary as installed
     */
    suspend fun markDictionaryInstalled(
        dictionaryId: String,
        entryCount: Long
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Marking dictionary $dictionaryId as installed with $entryCount entries")
        metadataDao.setInstalled(
            dictionaryId = dictionaryId,
            installed = true,
            installDate = System.currentTimeMillis(),
            entryCount = entryCount
        )
    }
    
    /**
     * Update last used timestamp
     */
    suspend fun updateDictionaryLastUsed(dictionaryId: String) = withContext(Dispatchers.IO) {
        metadataDao.updateLastUsed(dictionaryId, System.currentTimeMillis())
    }
    
    /**
     * Delete a dictionary (metadata and all entries)
     */
    suspend fun deleteDictionary(dictionaryId: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Deleting dictionary: $dictionaryId")
        
        // Delete all entries
        dictionaryDao.deleteByDictionary(dictionaryId)
        
        // Update metadata to mark as not installed
        metadataDao.setInstalled(
            dictionaryId = dictionaryId,
            installed = false,
            installDate = null,
            entryCount = 0
        )
    }
    
    /**
     * Get statistics
     */
    suspend fun getStatistics(): DictionaryStats = withContext(Dispatchers.IO) {
        DictionaryStats(
            totalDictionaries = metadataDao.getCount(),
            installedDictionaries = metadataDao.getInstalledCount(),
            enabledDictionaries = metadataDao.getEnabledCount(),
            totalEntries = metadataDao.getTotalEnabledEntries() ?: 0,
            availableLanguagePairs = metadataDao.getAvailableLanguagePairs()
        )
    }
    
    /**
     * Get enabled dictionary IDs for lookup
     */
    suspend fun getEnabledDictionaryIds(): List<String> = withContext(Dispatchers.IO) {
        metadataDao.getEnabled().map { it.dictionaryId }
    }
    
    /**
     * Add a custom dictionary
     */
    suspend fun addCustomDictionary(metadata: DictionaryMetadata) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Adding custom dictionary: ${metadata.name}")
        metadataDao.insert(metadata)
    }
    
    /**
     * Get the currently active dictionary
     */
    suspend fun getActiveDictionary(): DictionaryMetadata? = withContext(Dispatchers.IO) {
        metadataDao.getActiveDictionary()
    }
    
    /**
     * Set a dictionary as active (only one can be active at a time)
     */
    suspend fun setActiveDictionary(dictionaryId: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Setting active dictionary: $dictionaryId")
        // Clear all active flags first
        metadataDao.clearAllActive()
        // Set the new active dictionary
        metadataDao.setActive(dictionaryId, true)
        // Also enable it
        metadataDao.setEnabled(dictionaryId, true)
    }
}

/**
 * Dictionary statistics data class
 */
data class DictionaryStats(
    val totalDictionaries: Int,
    val installedDictionaries: Int,
    val enabledDictionaries: Int,
    val totalEntries: Long,
    val availableLanguagePairs: List<String>
)
