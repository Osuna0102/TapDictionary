package com.godtap.dictionary.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {
    
    @Query("SELECT * FROM dictionary_entries WHERE kanji = :term LIMIT 1")
    suspend fun searchByKanji(term: String): DictionaryEntry?
    
    @Query("SELECT * FROM dictionary_entries WHERE reading = :term LIMIT 1")
    suspend fun searchByReading(term: String): DictionaryEntry?
    
    @Query("SELECT * FROM dictionary_entries WHERE kanji LIKE :term || '%' OR reading LIKE :term || '%' LIMIT 10")
    suspend fun searchByPrefix(term: String): List<DictionaryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)
    
    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getCount(): Int
}
