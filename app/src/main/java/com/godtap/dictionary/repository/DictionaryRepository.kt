package com.godtap.dictionary.repository

import android.util.LruCache
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.database.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryRepository(private val database: AppDatabase) {
    
    private val cache = LruCache<String, DictionaryEntry>(100)
    
    suspend fun search(term: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        // Check cache first
        cache.get(term)?.let { return@withContext it }
        
        // Search by kanji
        var entry = database.dictionaryDao().searchByKanji(term).firstOrNull()
        
        // If not found, search by reading
        if (entry == null) {
            entry = database.dictionaryDao().searchByReading(term).firstOrNull()
        }
        
        // If not found, try prefix search
        if (entry == null) {
            val results = database.dictionaryDao().searchByPrefix(term)
            entry = results.firstOrNull()
        }
        
        // Cache result if found
        entry?.let { cache.put(term, it) }
        
        return@withContext entry
    }
    
    suspend fun searchMultiple(terms: List<String>): DictionaryEntry? = withContext(Dispatchers.IO) {
        // Search for longest term first (most specific)
        for (term in terms) {
            search(term)?.let { return@withContext it }
        }
        return@withContext null
    }
    
    /**
     * Search and return multiple results (for lookup UI)
     */
    suspend fun searchAll(term: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DictionaryEntry>()
        
        // Try exact matches first
        results.addAll(database.dictionaryDao().searchByKanji(term))
        results.addAll(database.dictionaryDao().searchByReading(term))
        
        // If no exact matches, try prefix/substring
        if (results.isEmpty()) {
            results.addAll(database.dictionaryDao().searchByPrefix(term))
        }
        
        // Remove duplicates by entryId
        return@withContext results.distinctBy { it.entryId }
    }
}
