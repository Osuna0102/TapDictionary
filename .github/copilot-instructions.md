# GodTap Dictionary - AI Agent Instructions

## Project Overview

GodTap Dictionary is an Android accessibility service that provides instant translation popups for selected text across any app. It's a multi-language dictionary app inspired by Yomitan, supporting Japanese, Spanish, Korean, and potentially other languages with pluggable dictionary backends.

**Core Technologies:** Kotlin, Jetpack Compose, Room, Android AccessibilityService, Kuromoji (Japanese tokenization)

## Architecture

### Three-Phase Lookup Pipeline

The app uses a sophisticated multi-stage lookup process:

1. **Text Selection** → `TextSelectionAccessibilityService` monitors `TYPE_VIEW_TEXT_SELECTION_CHANGED` events
2. **Language Detection** → `LanguageDetector` identifies source language from selected text
3. **Lookup Strategy** → Different algorithms per language:
   - **Japanese**: Progressive substring matching with deinflection (mirrors Yomitan's `_findTermsInternal()`)
   - **Spanish/Korean**: Word boundary extraction with morphological transformations
4. **Display** → `OverlayManager` shows floating popup with `TYPE_APPLICATION_OVERLAY`

### Key Components

- **`TextSelectionAccessibilityService`**: Core service running as foreground service with notification. Filters sensitive contexts (passwords, keyboards). Lives in `service/`
- **`DictionaryLookup`**: Language-aware lookup orchestrator. Japanese uses progressive substrings; Spanish/Korean extract word boundaries. Lives in `lookup/`
- **`JapaneseDeinflector`**: Transforms conjugated forms → dictionary forms (食べました → 食べる). Lives in `deinflection/`
- **`DictionaryManager`**: Manages multi-dictionary lifecycle (download, enable/disable, metadata). Lives in `manager/`
- **`MultiDictionaryDownloader`**: Downloads and imports Yomitan-format dictionaries. Lives in `manager/`
- **`OverlayManager`**: Manages floating popup window with 30s auto-dismiss. Lives in `overlay/`
- **`DictionaryRepository`**: Room-backed search with indexed queries. Lives in `repository/`

### Database Schema (Room v4)

- **`dictionary_entries`**: Main entries with `dictionaryId`, indexed on `primaryExpression`, `primaryReading`, `frequency`
- **`dictionary_metadata`**: Tracks available dictionaries, install status, enabled state
- **`language_pairs`**: Supported language pair configurations

Data models mirror JMdict structure: `KanjiElement[]`, `ReadingElement[]`, `Sense[]` with full metadata preservation.

## Development Workflows

### Build & Deploy

Use predefined VS Code tasks (see `.vscode/tasks.json` patterns):

```bash
# Quick build + install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Full deployment with logcat
# Task: "Build, Install and Launch with Logcat"
# Sequence: Build → Stop → Install → Clear Logcat → Launch → Logcat Filter
```

**Hot-reload available:** `./dev.sh` uses fswatch for auto-rebuild on file changes (requires fswatch installation)

### Debugging

**Critical logs to monitor:**
```bash
# Accessibility service events
adb logcat TextSelectionService:D '*:S'

# Overlay popup lifecycle
adb logcat OverlayManager:D '*:S'

# Dictionary lookups
adb logcat DictionaryLookup:D '*:S'
```

**Permission verification:**
```bash
# Check accessibility service status
adb shell settings get secure enabled_accessibility_services | grep godtap

# Check overlay permission
adb shell appops get com.godtap.dictionary SYSTEM_ALERT_WINDOW
```

**Common issues:**
- AccessibilityService not detecting selection → Verify service enabled in Settings > Accessibility
- Popup not showing → Check `SYSTEM_ALERT_WINDOW` permission granted
- No dictionary results → Ensure at least one dictionary is downloaded and enabled via `DictionaryManager.initializeAvailableDictionaries()`

## Project-Specific Conventions

### Language-Specific Lookup Strategies

**Japanese (ja)**: Progressive substring matching from position 0, trying longest matches first (up to 25 chars). Uses `JapaneseDeinflector` to generate verb/adjective forms. Search order: exact kanji → reading → prefix match.

**Spanish/Korean (es, ko)**: Extract first complete word at selection boundaries (space/punctuation-delimited). Apply language-specific transformations (`SpanishLanguageTransformer` for conjugations/plurals). Single-pass lookup.

Always instantiate `DictionaryLookup` with correct `sourceLanguage` parameter based on detected language.

### Dictionary Import Format

Supports **Yomitan/Yomichan format** (`.zip` with `index.json`, `term_bank_*.json`). Parser lives in `parser/JMdictParser.kt`.

Default dictionary: JMdict English from `https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english_with_examples.zip`

Import process: Download → Extract JSON → Parse → Batch insert with `dictionaryId` tracking.

### Performance Patterns

- **LRU Cache**: Repository uses 500-entry cache for recent lookups
- **Indexed Columns**: All searches use Room indices on `primaryExpression`, `primaryReading`, `frequency`
- **Batch Operations**: Import uses chunked inserts (500 entries/batch) to avoid transaction limits
- **Background Work**: All DB operations use `Dispatchers.IO`, UI on `Dispatchers.Main`

### File Naming

- Activities: `*Activity.kt` (e.g., `DictionaryManagementActivity.kt`)
- Services: `*Service.kt` in `service/` package
- Compose screens: `*Screen.kt` or `*ScreenNew.kt` (new Material 3 variants) in `ui/`
- Utilities: Singleton `object`s in `util/` (e.g., `PermissionHelper`, `LanguageDetector`)

## Integration Points

### AccessibilityService Configuration

Declared in `AndroidManifest.xml` with `accessibility_service_config.xml`:
- Monitors `TYPE_VIEW_TEXT_SELECTION_CHANGED`, `TYPE_VIEW_CLICKED`, `TYPE_VIEW_LONG_CLICKED`
- Filters: Excludes `android.inputmethodservice.SoftInputWindow`, `com.android.systemui`
- Requires `BIND_ACCESSIBILITY_SERVICE` permission

Service runs as **foreground service** with persistent notification (channel: `dictionary_service_channel`).

### External Dependencies

- **Kuromoji (0.9.0)**: Japanese morphological analysis for advanced tokenization (not actively used in current MVP, placeholder for future)
- **OkHttp (4.12.0)**: Dictionary downloads with progress tracking
- **Kotlinx Serialization**: JSON parsing for Yomitan dictionary format

### Cross-Component Communication

- `DictionaryApp.instance`: Global application context singleton
- Services → Repository: Direct instantiation via `AppDatabase.getDatabase(context)`
- UI → Manager: Lifecycle-scoped coroutines with `lifecycleScope.launch`
- Overlay → Service: Created in service context, uses `WindowManager.addView()`

## Common Tasks

**Add new dictionary format:**
1. Create parser in `parser/` implementing term extraction
2. Add format enum to `DictionaryFormat` in `database/DictionaryMetadata.kt`
3. Update `MultiDictionaryDownloader.importDictionary()` with new parser case
4. Add format-specific metadata to `DictionaryManager.AVAILABLE_DICTIONARIES`

**Add new language:**
1. Add language detection logic to `LanguageDetector.detectLanguage()`
2. Create transformer in `deinflection/` (e.g., `KoreanTransformer.kt`)
3. Add lookup strategy case to `DictionaryLookup.lookup()` method
4. Update `LanguagePair` entities with new language codes

**Debug text selection issues:**
1. Check `TextSelectionAccessibilityService.handleTextSelectionChanged()` logs
2. Verify `rootInActiveWindow` is not null (some apps block accessibility)
3. Add app-specific exclusions to `shouldIgnorePackage()` if needed
4. Test with `TestActivity` built-in test screen with Japanese/Spanish text

## Testing Resources

- **TestActivity**: Provides test text in Japanese, Spanish, Korean for selection testing without external apps
- **DictionaryDebugActivity**: Manual lookup testing UI with language selection
- **Sample dictionaries**: `kty-es-ko/` folder contains small Korean-Spanish test dictionary for import verification

## Key Files Reference

- [`TextSelectionAccessibilityService.kt`](app/src/main/java/com/godtap/dictionary/service/TextSelectionAccessibilityService.kt): 538 lines - Core text monitoring logic
- [`DictionaryLookup.kt`](app/src/main/java/com/godtap/dictionary/lookup/DictionaryLookup.kt): 267 lines - Language-aware lookup orchestration
- [`DictionaryManager.kt`](app/src/main/java/com/godtap/dictionary/manager/DictionaryManager.kt): 208 lines - Multi-dictionary lifecycle
- [`README.md`](README.md): Comprehensive Spanish-language documentation with architecture diagrams
- [`README_MULTI_DICT.md`](README_MULTI_DICT.md): Multi-language dictionary system implementation guide

---

**Last Updated:** 2026-01-02 | **Database Version:** 4 | **Min SDK:** 24 (Android 7.0)
