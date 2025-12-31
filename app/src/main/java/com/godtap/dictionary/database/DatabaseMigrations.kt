package com.godtap.dictionary.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 2 to 3
 * Adds indexed columns for Yomitan-style fast lookups
 */
object DatabaseMigrations {
    
    /**
     * Migration 2 → 3: Add primaryExpression and primaryReading columns with indexes
     * 
     * WARNING: This migration is SLOW if you have a large database.
     * It's faster to clear app data and reimport.
     * 
     * To use this migration, update AppDatabase.kt:
     * Room.databaseBuilder(...)
     *     .addMigrations(MIGRATION_2_3)
     *     .build()
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Step 1: Add new columns (nullable initially)
            database.execSQL("""
                ALTER TABLE dictionary_entries 
                ADD COLUMN primaryExpression TEXT
            """)
            
            database.execSQL("""
                ALTER TABLE dictionary_entries 
                ADD COLUMN primaryReading TEXT
            """)
            
            // Step 2: Populate from JSON (SLOW - extracts first element from JSON arrays)
            // This is a simplified version - may need adjustment based on your JSON format
            
            // For primaryReading (always present)
            database.execSQL("""
                UPDATE dictionary_entries
                SET primaryReading = TRIM(SUBSTR(
                    SUBSTR(readingElements, INSTR(readingElements, '"reading":"') + 11),
                    1,
                    INSTR(SUBSTR(readingElements, INSTR(readingElements, '"reading":"') + 11), '"') - 1
                ))
                WHERE primaryReading IS NULL
            """)
            
            // For primaryExpression (only if kanji exists)
            database.execSQL("""
                UPDATE dictionary_entries
                SET primaryExpression = TRIM(SUBSTR(
                    SUBSTR(kanjiElements, INSTR(kanjiElements, '"kanji":"') + 9),
                    1,
                    INSTR(SUBSTR(kanjiElements, INSTR(kanjiElements, '"kanji":"') + 9), '"') - 1
                ))
                WHERE kanjiElements LIKE '%"kanji":"%'
                AND primaryExpression IS NULL
            """)
            
            // Step 3: Create indexes (CRITICAL for performance)
            database.execSQL("""
                CREATE INDEX idx_primary_expression 
                ON dictionary_entries(primaryExpression)
            """)
            
            database.execSQL("""
                CREATE INDEX idx_primary_reading 
                ON dictionary_entries(primaryReading)
            """)
            
            database.execSQL("""
                CREATE INDEX idx_frequency 
                ON dictionary_entries(frequency)
            """)
        }
    }
    
    /**
     * RECOMMENDED: Clear and reimport instead of migration
     * 
     * 1. Clear app data:
     *    adb shell pm clear com.godtap.dictionary
     * 
     * 2. Rebuild and install:
     *    ./gradlew assembleDebug
     *    adb install -r app/build/outputs/apk/debug/app-debug.apk
     * 
     * 3. Reimport dictionary
     * 
     * This is much faster than running the migration on large databases.
     */
}
