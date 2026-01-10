package com.godtap.dictionary.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.manager.DictionaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository following Yomitan's fast lookup approach
 * - Uses indexed queries (no LIKE operations)
 * - Caches results for speed
 * - Supports bulk lookups
 * - Supports multiple dictionaries
 */
class DictionaryRepository(private val database: AppDatabase) {
    
    private val cache = LruCache<String, DictionaryEntry>(200)
    
    /**
     * Fast indexed search - exact match only
     * Hits primaryExpression or primaryReading indexes
     * Searches all dictionaries (backwards compatible)
     */
    suspend fun search(term: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        // Check cache first
        cache.get(term)?.let {
            Log.d("DictionaryRepo", "✓ Cache hit for: '$term'")
            return@withContext it
        }
        
        // Fast indexed lookup (all dictionaries)
        val entry = database.dictionaryDao().findExact(term, null)
        
        if (entry != null) {
            Log.d("DictionaryRepo", "✓ DB found '$term' → ${entry.primaryExpression ?: entry.primaryReading} (${entry.dictionaryId})")
            Log.d("DictionaryRepo", "  BEFORE increment: lookupCount = ${entry.lookupCount}")
            // Increment lookup count
            database.dictionaryDao().incrementLookupCount(entry.id)
            Log.d("DictionaryRepo", "  AFTER increment: lookupCount incremented in DB for entryId=${entry.id}")
            // Invalidate cache so next lookup gets updated count
            cache.remove(term)
            entry.primaryExpression?.let { cache.remove(it) }
            Log.d("DictionaryRepo", "  Cache invalidated for '$term'")
        } else {
            Log.d("DictionaryRepo", "⊘ DB miss for: '$term'")
        }
        
        return@withContext entry
    }
    
    /**
     * Bulk search - for deinflected forms
     * Searches multiple terms at once (like Yomitan's findTermsBulk)
     * Searches all dictionaries
     */
    suspend fun searchBulk(terms: List<String>): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        if (terms.isEmpty()) return@withContext emptyList()
        
        val uncachedTerms = terms.filter { cache.get(it) == null }
        
        if (uncachedTerms.isEmpty()) {
            return@withContext terms.mapNotNull { cache.get(it) }
        }
        
        val entries = database.dictionaryDao().findBulk(uncachedTerms, null)
        
        // Cache all results
        entries.forEach { entry ->
            entry.primaryExpression?.let { cache.put(it, entry) }
            cache.put(entry.primaryReading, entry)
        }
        
        return@withContext entries
    }
    
    /**
     * Search by expression (kanji) only
     */
    suspend fun searchByExpression(expression: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        database.dictionaryDao().searchByExpression(expression).firstOrNull()
    }
    
    /**
     * Search by reading (kana) only
     */
    suspend fun searchByReading(reading: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        database.dictionaryDao().searchByReading(reading).firstOrNull()
    }
    
    /**
     * Fuzzy search - finds entries containing the query string
     * Useful for debugging and finding similar entries
     */
    suspend fun searchFuzzy(query: String, limit: Int = 20): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        database.dictionaryDao().searchFuzzy(query, limit)
    }
    
    /**
     * Get common words for UI
     */
    suspend fun getCommonWords(limit: Int = 100): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        database.dictionaryDao().getCommonWords(limit)
    }
    
    /**
     * Get entry for lookup purposes (underlining) without incrementing count
     */
    suspend fun getEntryForLookup(term: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        // Check cache first
        cache.get(term)?.let {
            return@withContext it
        }
        
        // Fast indexed lookup (all dictionaries)
        val entry = database.dictionaryDao().findExact(term, null)
        
        if (entry != null) {
            // Do not increment lookup count for underlining
            // Cache it
            entry.primaryExpression?.let { cache.put(it, entry) }
            cache.put(entry.primaryReading, entry)
        }
        
        return@withContext entry
    }
    
    /**
     * Get recent lookups (entries with lookup count > 0)
     */
    suspend fun getRecentLookups(limit: Int = 10): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        database.dictionaryDao().getRecentLookups(limit)
    }
    
    /**
     * Get most looked up word
     */
    suspend fun getMostLookedUp(): DictionaryEntry? = withContext(Dispatchers.IO) {
        database.dictionaryDao().getMostLookedUp()
    }
    
    /**
     * Get total lookup count
     */
    suspend fun getTotalLookupCount(): Int = withContext(Dispatchers.IO) {
        database.dictionaryDao().getTotalLookupCount() ?: 0
    }
}
