# Phase 1 Implementation: JMdict Integration

## Step-by-Step Implementation

### Step 1: Add Dependencies

Add to `app/build.gradle.kts`:
```kotlin
dependencies {
    // Kuromoji for Japanese tokenization
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    
    // JSON parsing (already might have)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### Step 2: Download JMdict Dictionary

We'll use Yomichan's pre-processed JMdict format:
- URL: `https://github.com/FooSoft/yomichan/raw/dictionaries/jmdict_english.zip`
- Format: JSON files in ZIP
- Size: ~15-25 MB

**Options:**
A) Bundle in app (increases APK size)
B) Download on first launch
C) Hybrid: Basic dictionary + download full

**Recommendation: Option B** - Download on first launch with progress indicator

### Step 3: Update Database Schema

Current schema is too simple. We need:

```kotlin
@Entity(tableName = "dictionary_entries")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Main fields
    val expression: String,          // Kanji form (e.g., "食べる")
    val reading: String,              // Kana reading (e.g., "たべる")
    val glossJson: String,            // JSON array of English meanings
    val partOfSpeech: String?,        // e.g., "v1,vt" 
    val tags: String?,                // Additional tags
    
    // Search optimization
    val expressionIndex: String,      // For indexing
    val readingIndex: String,         // For indexing
    
    // Additional info
    val sequence: Int,                // JMdict sequence number
    val priority: Int = 0,            // For ranking results
    val frequency: Int? = null        // Frequency data if available
)

// Indices for fast lookup
@Database(
    entities = [DictionaryEntry::class],
    version = 2,  // Increment version!
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
```

### Step 4: Create JMdict Parser

Yomichan dictionary format structure:
```json
{
  "title": "JMdict",
  "format": 3,
  "revision": "jmdict4",
  "sequenced": true
}
```

Term bank files (`term_bank_1.json`, `term_bank_2.json`, etc.):
```json
[
  ["食べる", "たべる", "", "vs-c", 1, ["to eat"], 1, ""],
  ["寿司", "すし", "", "n", 2, ["sushi", "Japanese rice dish"], 2, ""],
  ...
]
```

Format: `[expression, reading, definitionTags, rules, score, glossary, sequence, termTags]`

### Step 5: Implementation Files to Create

```
app/src/main/java/com/godtap/dictionary/
├── downloader/
│   ├── DictionaryDownloader.kt       # Downloads dictionary ZIP
│   └── DictionaryInstaller.kt        # Extracts and installs
├── parser/
│   ├── YomichanParser.kt             # Parses Yomichan format
│   └── JMdictImporter.kt             # Imports to Room database
├── database/
│   ├── DictionaryEntry.kt            # Updated entity
│   ├── DictionaryDao.kt              # Updated DAO with new queries
│   └── AppDatabase.kt                # Updated database
└── tokenizer/
    └── KuromojiTokenizer.kt          # Replace current tokenizer
```

### Step 6: Download & Install Flow

```kotlin
class DictionaryDownloader(private val context: Context) {
    
    suspend fun downloadDictionary(
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/FooSoft/yomichan/raw/dictionaries/jmdict_english.zip"
            val outputFile = File(context.cacheDir, "jmdict.zip")
            
            // Download with progress
            val connection = URL(url).openConnection() as HttpURLConnection
            val fileSize = connection.contentLength
            
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress((totalRead * 100 / fileSize).toInt())
                    }
                }
            }
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Step 7: Parse & Import

```kotlin
class YomichanParser {
    
    data class YomichanEntry(
        val expression: String,
        val reading: String,
        val definitionTags: String,
        val rules: String,
        val score: Int,
        val glossary: List<String>,
        val sequence: Int,
        val termTags: String
    )
    
    suspend fun parseAndImport(
        zipFile: File,
        database: AppDatabase,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry?
            var fileCount = 0
            
            // First pass: count files
            while (zis.nextEntry.also { entry = it } != null) {
                if (entry?.name?.startsWith("term_bank_") == true) {
                    fileCount++
                }
            }
            
            // Second pass: parse
            ZipInputStream(zipFile.inputStream()).use { zis2 ->
                var processed = 0
                while (zis2.nextEntry.also { entry = it } != null) {
                    if (entry?.name?.startsWith("term_bank_") == true) {
                        val json = zis2.bufferedReader().readText()
                        val entries = parseTermBank(json)
                        database.dictionaryDao().insertAll(entries)
                        processed++
                        onProgress(processed, fileCount)
                    }
                }
            }
        }
    }
    
    private fun parseTermBank(json: String): List<DictionaryEntry> {
        // Parse JSON array
        // Transform to DictionaryEntry objects
    }
}
```

### Step 8: Kuromoji Integration

```kotlin
class KuromojiTokenizer {
    private val tokenizer by lazy {
        Tokenizer.builder()
            .mode(Tokenizer.Mode.SEARCH)  // Better for dictionary lookup
            .build()
    }
    
    fun tokenize(text: String): List<Token> {
        return tokenizer.tokenize(text)
    }
    
    fun tokenizeSmarter(text: String): List<String> {
        val tokens = tokenize(text)
        
        // Generate all possible substrings for matching
        val candidates = mutableListOf<String>()
        
        // Start with longest matches
        for (i in tokens.indices) {
            for (j in (i + 1)..tokens.size) {
                val substring = tokens.subList(i, j)
                    .joinToString("") { it.surface }
                candidates.add(substring)
            }
        }
        
        // Sort by length (longest first)
        return candidates.sortedByDescending { it.length }
    }
}
```

### Step 9: Update Repository

```kotlin
class DictionaryRepository(private val dao: DictionaryDao) {
    
    suspend fun searchMultiple(tokens: List<String>): DictionaryEntry? {
        // Try each token, longest first
        for (token in tokens) {
            val result = dao.searchByExpression(token)
                ?: dao.searchByReading(token)
            
            if (result != null) {
                return result
            }
        }
        return null
    }
    
    suspend fun searchExact(text: String): DictionaryEntry? {
        return dao.searchByExpression(text)
            ?: dao.searchByReading(text)
    }
}
```

### Step 10: UI for Dictionary Download

Create `DictionarySetupActivity.kt`:
- Show on first launch
- Progress bar for download
- Progress bar for import
- "Skip for now" option
- "Retry" on error

### Testing Strategy

1. **Unit Tests:**
   - Test Yomichan parser with sample data
   - Test Kuromoji tokenization
   - Test database queries

2. **Integration Tests:**
   - Test full download → parse → import flow
   - Test dictionary lookup with real data

3. **Manual Tests:**
   - Test with various Japanese text
   - Test edge cases (katakana, kanji, mixed)
   - Performance testing with large dataset

## Timeline

- **Day 1:** Dependencies, database migration, Kuromoji integration
- **Day 2:** Download/install system, Yomichan parser
- **Day 3:** UI for setup, testing, bug fixes
- **Day 4:** Performance optimization, edge case handling

## File Sizes & Performance

- **Download:** ~20 MB (one-time)
- **Database:** ~150 MB (on device)
- **Lookup speed:** < 50ms per query
- **Memory:** ~20-30 MB additional

## Next Steps

Ready to start implementation?

1. ✅ Add dependencies
2. ✅ Update database schema  
3. ✅ Create downloader
4. ✅ Create parser
5. ✅ Integrate Kuromoji
6. ✅ Create setup UI
7. ✅ Testing

Shall we begin with Step 1?
