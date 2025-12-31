# Major Feature Roadmap: Real Dictionary Integration

## Current Limitations
- ❌ Hard-coded dictionary entries (only test data)
- ❌ No real dictionary database
- ❌ Poor tokenization (opens/closes nearby words)
- ❌ Limited vocabulary

## Goals
1. ✅ Integrate JMdict (comprehensive Japanese-English dictionary)
2. ✅ Import third-party dictionary files (like Yomichan/10ten use)
3. ✅ Improve tokenization and word detection
4. ✅ Better word boundary detection for tap events

## Research: How Yomichan & 10ten Handle Dictionaries

### Yomichan Architecture
- Uses **JMdict** (free Japanese-English dictionary)
- Dictionary format: **JSON** or **SQLite**
- Supports multiple dictionary sources
- Pre-processes dictionaries into optimized format
- Uses IndexedDB for browser storage

### 10ten Japanese Reader
- Also uses JMdict
- Includes JMnedict (names dictionary)
- Uses compressed dictionary format
- Client-side dictionary processing

### JMdict Overview
- **Source:** EDRDG (Electronic Dictionary Research and Development Group)
- **Format:** XML originally, but converted to JSON/SQLite for apps
- **Size:** ~175,000+ entries
- **License:** Creative Commons Attribution-ShareAlike 3.0
- **URL:** https://www.edrdg.org/jmdict/j_jmdict.html

## Implementation Plan

### Phase 1: Dictionary File Integration (Priority 1)
**Tasks:**
1. Download JMdict dictionary file
2. Choose storage format (SQLite vs JSON)
3. Create parser for JMdict format
4. Bundle dictionary with app or download on first launch
5. Update database schema for real dictionary entries

**Files to create/modify:**
- `app/src/main/assets/jmdict.db` (or download URL)
- `app/src/main/java/com/godtap/dictionary/parser/JMdictParser.kt`
- `app/src/main/java/com/godtap/dictionary/database/DictionaryEntry.kt` (expand)
- Update `build.gradle.kts` for asset compression

### Phase 2: Improved Tokenization (Priority 2)
**Current Issue:** Word boundaries not detected accurately, causing nearby words to trigger

**Solutions:**
1. Use proper Japanese tokenizer (Kuromoji or similar)
2. Implement longest-match-first algorithm
3. Better text extraction from accessibility events
4. Debounce tap events to prevent multiple triggers

**Libraries to evaluate:**
- Kuromoji (Japanese morphological analyzer)
- TinySegmenter (lightweight JavaScript port, could adapt)
- Sudachi (modern Japanese tokenizer)

### Phase 3: Better Word Detection (Priority 3)
**Improvements:**
1. Exact word boundary detection from tap coordinates
2. Match tapped word against tokenized results
3. Fallback to substring matching
4. Handle conjugated forms (e.g., 食べる → 食べた)

## Technical Decisions

### Dictionary Format: SQLite vs JSON
**Recommendation: SQLite**

**Pros:**
- ✅ Fast indexed lookups
- ✅ Low memory footprint
- ✅ Can query directly without loading everything
- ✅ Android native support

**Cons:**
- ❌ Larger initial file size
- ❌ More complex to update

**JSON Alternative:**
- Simpler to parse
- Easier to update
- But slower lookups for 175k+ entries

### Tokenizer Choice: Kuromoji
**Recommendation: Kuromoji**

```gradle
implementation 'com.atilika.kuromoji:kuromoji-ipadic:0.9.0'
```

**Why:**
- ✅ Pure Java (works on Android)
- ✅ No native dependencies
- ✅ Actively maintained
- ✅ Good accuracy
- ✅ Handles conjugations

### Dictionary Source Options

1. **JMdict_e** (English)
   - Most comprehensive
   - ~175,000 entries
   - Multiple meanings per entry
   
2. **Pre-built Yomichan dictionaries**
   - Already processed
   - JSON format
   - Can be converted to SQLite
   - Available at: https://foosoft.net/projects/yomichan/

3. **JMdict SQLite (third-party)**
   - Some developers maintain pre-converted versions
   - Example: https://github.com/scriptin/jmdict-simplified

## File Size Considerations

**JMdict compressed:** ~20-30 MB
**JMdict uncompressed:** ~100+ MB
**Yomichan format:** ~15-25 MB per dictionary

**Strategy:**
- Option A: Bundle dictionary in APK (increases APK size)
- Option B: Download on first launch (requires internet)
- **Recommended:** Hybrid - include basic dictionary, download full on demand

## Next Steps - Implementation Order

### Immediate (This Week)
1. ✅ Add Kuromoji dependency
2. ✅ Create JMdict downloader/importer
3. ✅ Update database schema
4. ✅ Implement JMdict parser

### Short-term (Next Week)
5. ✅ Replace hard-coded entries with database lookups
6. ✅ Improve tokenization with Kuromoji
7. ✅ Fix word boundary detection
8. ✅ Add conjugation handling

### Future Enhancements
9. ⏳ Support multiple dictionaries (JMnedict for names)
10. ⏳ User-imported custom dictionaries
11. ⏳ Offline first-launch dictionary
12. ⏳ Dictionary updates without app update

## Resources

### JMdict Downloads
- Official: http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz
- Simplified JSON: https://github.com/scriptin/jmdict-simplified
- Yomichan format: https://foosoft.net/projects/yomichan/#dictionaries

### Reference Projects
- Yomichan: https://github.com/FooSoft/yomichan
- 10ten: https://github.com/birchill/10ten-ja-reader
- Akebi (Android): https://github.com/blabs-dev/akebi-mobile

### Tokenization
- Kuromoji docs: https://github.com/atilika/kuromoji
- Japanese NLP overview: https://www.dampfkraft.com/nlp/japanese-tokenization.html

## Estimated Effort
- Dictionary integration: 2-3 days
- Tokenization improvements: 1-2 days
- Testing and refinement: 1-2 days
- **Total:** 4-7 days

---

**Status:** Planning phase
**Next Action:** Start Phase 1 - Dictionary Integration
