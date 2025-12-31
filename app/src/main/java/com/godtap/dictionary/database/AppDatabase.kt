package com.godtap.dictionary.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [DictionaryEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun dictionaryDao(): DictionaryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dictionary_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                
                // Note: Dictionary will be imported on first launch via DictionaryDownloader
                // No sample data - user must download JMdict
                
                INSTANCE = instance
                instance
            }
        }
    }
}
