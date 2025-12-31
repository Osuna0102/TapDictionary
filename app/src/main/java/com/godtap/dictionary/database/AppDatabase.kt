package com.godtap.dictionary.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [DictionaryEntry::class], version = 1, exportSchema = false)
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
                
                // Populate with sample data if empty
                CoroutineScope(Dispatchers.IO).launch {
                    val count = instance.dictionaryDao().getCount()
                    if (count == 0) {
                        populateSampleData(instance)
                    }
                }
                
                INSTANCE = instance
                instance
            }
        }
        
        private suspend fun populateSampleData(database: AppDatabase) {
            val dao = database.dictionaryDao()
            
            val sampleEntries = listOf(
                // Common verbs
                DictionaryEntry(kanji = "食べる", reading = "たべる", glosses = "to eat, to consume", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "飲む", reading = "のむ", glosses = "to drink", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "見る", reading = "みる", glosses = "to see, to look, to watch", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "聞く", reading = "きく", glosses = "to hear, to listen, to ask", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "読む", reading = "よむ", glosses = "to read", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "書く", reading = "かく", glosses = "to write", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "話す", reading = "はなす", glosses = "to speak, to talk", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "行く", reading = "いく", glosses = "to go", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "来る", reading = "くる", glosses = "to come", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "する", reading = "する", glosses = "to do", partOfSpeech = "verb", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "勉強する", reading = "べんきょうする", glosses = "to study", partOfSpeech = "verb", frequency = 90, jlptLevel = 5),
                
                // Common nouns
                DictionaryEntry(kanji = "本", reading = "ほん", glosses = "book", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "学校", reading = "がっこう", glosses = "school", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "先生", reading = "せんせい", glosses = "teacher", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "学生", reading = "がくせい", glosses = "student", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "日本", reading = "にほん", glosses = "Japan", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "日本語", reading = "にほんご", glosses = "Japanese language", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "英語", reading = "えいご", glosses = "English language", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "時間", reading = "じかん", glosses = "time, hour", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "友達", reading = "ともだち", glosses = "friend", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "家", reading = "いえ", glosses = "house, home", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "水", reading = "みず", glosses = "water", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "寿司", reading = "すし", glosses = "sushi", partOfSpeech = "noun", frequency = 95, jlptLevel = 5),
                
                // Common adjectives
                DictionaryEntry(kanji = "美味しい", reading = "おいしい", glosses = "delicious, tasty", partOfSpeech = "adjective", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "大きい", reading = "おおきい", glosses = "big, large", partOfSpeech = "adjective", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "小さい", reading = "ちいさい", glosses = "small, little", partOfSpeech = "adjective", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "新しい", reading = "あたらしい", glosses = "new", partOfSpeech = "adjective", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "古い", reading = "ふるい", glosses = "old", partOfSpeech = "adjective", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "良い", reading = "よい", glosses = "good", partOfSpeech = "adjective", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "悪い", reading = "わるい", glosses = "bad", partOfSpeech = "adjective", frequency = 95, jlptLevel = 5),
                
                // Particles and common words
                DictionaryEntry(kanji = "私", reading = "わたし", glosses = "I, me", partOfSpeech = "pronoun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "今", reading = "いま", glosses = "now", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "毎日", reading = "まいにち", glosses = "every day", partOfSpeech = "adverb", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "今日", reading = "きょう", glosses = "today", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "明日", reading = "あした", glosses = "tomorrow", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                DictionaryEntry(kanji = "昨日", reading = "きのう", glosses = "yesterday", partOfSpeech = "noun", frequency = 100, jlptLevel = 5),
                
                // Conjugated forms (simplified for MVP)
                DictionaryEntry(kanji = "食べます", reading = "たべます", glosses = "eat (polite)", partOfSpeech = "verb", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "食べました", reading = "たべました", glosses = "ate (polite past)", partOfSpeech = "verb", frequency = 90, jlptLevel = 5),
                DictionaryEntry(kanji = "行きます", reading = "いきます", glosses = "go (polite)", partOfSpeech = "verb", frequency = 95, jlptLevel = 5),
                DictionaryEntry(kanji = "行きました", reading = "いきました", glosses = "went (polite past)", partOfSpeech = "verb", frequency = 90, jlptLevel = 5)
            )
            
            dao.insertAll(sampleEntries)
        }
    }
}
