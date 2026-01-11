# GodTap Dictionary - AI Agent Instructions

## Project Overview

GodTap Dictionary is an Android accessibility service that provides instant translation popups for selected text across any app. It's a multi-language dictionary app inspired by Yomitan, supporting Japanese, Spanish, Korean, and potentially other languages with pluggable dictionary backends.

**Core Technologies:** Kotlin, Jetpack Compose, Room, Android AccessibilityService, ML Kit (OCR), Google Play Billing, AdMob

**Status:** Production-ready freemium app with gesture controls, OCR, and monetization features.

## Architecture

### Multi-Modal Translation Pipeline

The app supports three translation modes:

1. **Text Selection Mode** (Primary):
   - `TextSelectionAccessibilityService` monitors `TYPE_VIEW_TEXT_SELECTION_CHANGED` events
   - `LanguageDetector` identifies source language from selected text
   - Language-specific lookup strategies:
     - **Japanese**: Progressive substring matching with deinflection (mirrors Yomitan's `_findTermsInternal()`)
     - **Spanish/Korean**: Word boundary extraction with morphological transformations
   - Fallback to `TranslationService` (MyMemory API) when dictionary has no match
   - `OverlayManager` shows floating popup with optional AdMob banner (free tier only)

2. **OCR Mode** (New - Jan 2026):
   - User draws selection rectangle via `OcrSelectionOverlay`
   - ML Kit Text Recognition processes captured screen area
   - Recognized text fed into normal lookup pipeline
   - Supports text in images, screenshots, PDFs

3. **Gesture Controls** (New - Jan 2026):
   - `GestureOverlay` intercepts multi-touch events (Android 11+ compatibility)
   - **2-finger swipe right** → Activate OCR mode
   - **2-finger swipe left** → Toggle text underlining
   - **2-finger swipe up** → Toggle service on/off

### Key Components

- **`TextSelectionAccessibilityService`** (1699 lines): Core service with foreground notification, gesture detection, OCR integration, app filtering. Lives in `service/`
- **`DictionaryLookup`**: Language-aware lookup orchestrator. Japanese uses progressive substrings; Spanish/Korean extract word boundaries. Lives in `lookup/`
- **`JapaneseDeinflector`**: Transforms conjugated forms → dictionary forms (食べました → 食べる). Lives in `deinflection/`
- **`DictionaryManager`**: Manages multi-dictionary lifecycle (download, enable/disable, metadata). Lives in `manager/`
- **`TranslationService`**: MyMemory API fallback for words not in local dictionaries. Lives in `api/`
- **`OcrSelectionOverlay`** + **`OcrTextProcessor`**: ML Kit-powered screen text recognition. Lives in `ocr/`
- **`GestureOverlay`**: Invisible full-screen overlay for multi-touch gesture detection. Lives in `gesture/`
- **`OverlayManager`**: Manages floating popup window with 30s auto-dismiss, AdMob integration. Lives in `overlay/`
- **`BillingManager`**: Google Play Billing v6 integration, Pro version purchase ($4.99 one-time). Lives in `billing/`
- **`UsageLimitManager`**: Enforces 20 lookups/day, 10 TTS/day limits for free tier. Lives in `usage/`
- **`AdManager`**: AdMob banner ads (320x50) displayed in popup for free users. Lives in `ads/`

### Database Schema (Room v5)

- **`dictionary_entries`**: Main entries with `dictionaryId`, indexed on `primaryExpression`, `primaryReading`, `frequency`, includes `lookupCount` for usage tracking
- **`dictionary_metadata`**: Tracks available dictionaries, install status, enabled state
- **`language_pairs`**: Supported language pair configurations

Data models mirror JMdict structure: `KanjiElement[]`, `ReadingElement[]`, `Sense[]` with full metadata preservation.

## Development Workflows

### Build & Deploy

Use VS Code tasks (`.vscode/tasks.json`):

```bash
# Quick build + install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Full deployment with logcat (RECOMMENDED)
# VS Code Task: "Build, Install and Launch with Logcat"
# Sequence: Build → Stop → Install → Clear Logcat → Launch → Logcat Filter
```

**Hot-reload:** `./dev.ps1` (PowerShell) or `./dev.sh` (Bash) for auto-rebuild on file changes

### Debugging

**Critical logs to monitor:**
```bash
# Accessibility service + overlay + lookups (MOST USEFUL)
adb logcat TextSelectionService:D OverlayManager:D DictionaryLookup:D '*:S'

# OCR debugging
adb logcat OcrSelectionOverlay:D OcrTextProcessor:D '*:S'

# Gesture detection
adb logcat GestureOverlay:D '*:S'

# Billing/monetization
adb logcat BillingManager:D UsageLimitManager:D AdManager:D '*:S'
```

**Permission verification:**
```bash
# Check accessibility service status
adb shell settings get secure enabled_accessibility_services | grep godtap

# Check overlay permission
adb shell appops get com.godtap.dictionary SYSTEM_ALERT_WINDOW

# Check installed packages (for app filtering)
adb shell dumpsys package com.godtap.dictionary | grep permission
```

**Common issues:**
- AccessibilityService not detecting selection → Verify service enabled in Settings > Accessibility > GodTap Dictionary
- Popup not showing → Check `SYSTEM_ALERT_WINDOW` permission granted in Settings > Apps > Special access
- No dictionary results → Ensure at least one dictionary is downloaded and enabled via `DictionaryManager.initializeAvailableDictionaries()`
- OCR not working → Verify ML Kit dependencies downloaded (automatic on first use, requires internet)
- Gestures not detected → Check `GestureOverlay` is active (shown in notification with "Gestures Active" text)
- Ads not showing → Verify AdMob test mode enabled (`AdManager.USE_TEST_ADS = true`) and Google Play Services installed

### Testing

**Built-in test activities:**
- `TestActivity`: Provides Japanese/Spanish/Korean test text for selection testing
- `GestureTestActivity`: Visual feedback for multi-touch gesture detection
- `LocalDictionaryTestActivity`: Manual dictionary lookup testing without accessibility service
- `DictionaryDebugActivity`: Legacy manual lookup UI with language selection

**Key test scenarios:**
1. **Text Selection**: Select text in Chrome/Kindle → Verify popup appears with correct translation
2. **OCR**: 2-finger swipe right → Draw rectangle over text → Verify recognition + translation
3. **Gestures**: Test all gesture combinations in `GestureTestActivity`
4. **Monetization**: Test free tier limits (20 lookups/day) → Verify upgrade prompt → Test purchase flow (use test card)
5. **Multi-language**: Test Japanese, Spanish, Korean text with appropriate dictionaries enabled

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

### Monetization Integration

**Free Tier:**
- 20 dictionary lookups/day (enforced by `UsageLimitManager.checkLookupLimit()`)
- 10 TTS plays/day (enforced by `UsageLimitManager.checkTtsLimit()`)
- AdMob banner ads shown in popup (320x50px, bottom of overlay)
- Upgrade prompts after 5 lookups and at limit

**Pro Version ($4.99 one-time):**
- Unlimited lookups and TTS
- No ads
- Purchase via `BillingManager.launchPurchaseFlow(activity)`
- Status cached: `BillingManager.isProUser()` is synchronous

**Critical:** Always check `billingManager.isProUser()` before showing ads or enforcing limits.

## Integration Points

### AccessibilityService Configuration

Declared in `AndroidManifest.xml` with `accessibility_service_config.xml`:
- Monitors `TYPE_VIEW_TEXT_SELECTION_CHANGED`, `TYPE_VIEW_CLICKED`, `TYPE_VIEW_LONG_CLICKED`
- Filters: Excludes `android.inputmethodservice.SoftInputWindow`, `com.android.systemui`
- Requires `BIND_ACCESSIBILITY_SERVICE` permission

Service runs as **foreground service** with persistent notification (channel: `dictionary_service_channel`).

### External Dependencies

- **ML Kit Text Recognition (16.0.0)**: OCR with automatic model downloads on first use
- **OkHttp (4.12.0)**: Dictionary downloads with progress tracking
- **Kotlinx Serialization**: JSON parsing for Yomitan dictionary format
- **Google Play Billing (6.1.0)**: In-app purchases for Pro version
- **Google AdMob (22.6.0)**: Banner ads for free tier

### Cross-Component Communication

- `DictionaryApp.instance`: Global application context singleton
- Services → Repository: Direct instantiation via `AppDatabase.getDatabase(context)`
- UI → Manager: Lifecycle-scoped coroutines with `lifecycleScope.launch`
- Overlay → Service: Created in service context, uses `WindowManager.addView()`
- Billing → Usage: `BillingManager.getInstance()` singleton checked by all limit-aware components

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

**Add new gesture:**
1. Update `GestureOverlay.GestureListener` interface with new callback
2. Implement detection logic in `GestureOverlay.handleTouchEvent()`
3. Wire callback in `TextSelectionAccessibilityService`
4. Test in `GestureTestActivity` with visual feedback

**Modify monetization limits:**
1. Update constants in `UsageLimitManager` (e.g., `DAILY_LOOKUP_LIMIT`)
2. Ensure all limit checks call `billingManager.isProUser()` first
3. Update upgrade prompt messages in `UpgradePromptManager`
4. Test with test billing credentials (see `MONETIZATION_IMPLEMENTATION.md`)

## Testing Resources

- **TestActivity**: Provides test text in Japanese, Spanish, Korean for selection testing without external apps
- **GestureTestActivity**: Visual feedback for multi-touch gesture detection with on-screen labels
- **LocalDictionaryTestActivity**: Manual lookup testing UI with language selection
- **DictionaryDebugActivity**: Legacy manual lookup interface
- **Sample dictionaries**: `kty-es-ko/` folder contains small Korean-Spanish test dictionary for import verification

## Key Files Reference

- [`TextSelectionAccessibilityService.kt`](app/src/main/java/com/godtap/dictionary/service/TextSelectionAccessibilityService.kt): 1699 lines - Core accessibility service with gesture/OCR integration
- [`DictionaryLookup.kt`](app/src/main/java/com/godtap/dictionary/lookup/DictionaryLookup.kt): 267 lines - Language-aware lookup orchestration
- [`DictionaryManager.kt`](app/src/main/java/com/godtap/dictionary/manager/DictionaryManager.kt): 208 lines - Multi-dictionary lifecycle
- [`BillingManager.kt`](app/src/main/java/com/godtap/dictionary/billing/BillingManager.kt): Google Play Billing v6 integration
- [`AdManager.kt`](app/src/main/java/com/godtap/dictionary/ads/AdManager.kt): AdMob banner ad integration
- [`UsageLimitManager.kt`](app/src/main/java/com/godtap/dictionary/usage/UsageLimitManager.kt): Free tier limit enforcement
- [`README.md`](README.md): Comprehensive Spanish-language documentation with architecture diagrams
- [`MONETIZATION_IMPLEMENTATION.md`](MONETIZATION_IMPLEMENTATION.md): Complete monetization guide with test instructions

---

**Last Updated:** 2026-01-10 | **Database Version:** 5 | **Min SDK:** 30 (Android 11) | **Target SDK:** 34
