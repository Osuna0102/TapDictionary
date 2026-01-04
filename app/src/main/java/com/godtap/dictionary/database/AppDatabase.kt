package com.godtap.dictionary.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DictionaryEntry::class,
        DictionaryMetadata::class,
        LanguagePair::class
    ], 
    version = 5, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun dictionaryMetadataDao(): DictionaryMetadataDao
    
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
                    .addMigrations(MIGRATION_4_5)
                    .build()
                
                // Note: Dictionary will be imported on first launch via DictionaryDownloader
                // No sample data - user must download JMdict
                
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add lookupCount column to dictionary_entries table
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN lookupCount INTEGER NOT NULL DEFAULT 0")
    }
}
