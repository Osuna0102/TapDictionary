# GodTap Dictionary - Development Guide

## 🚀 Quick Start

### Prerequisites
- Android device connected via USB (or emulator running)
- Java 17
- fswatch installed: `brew install fswatch`

### Start Development
```bash
./dev.sh
```

That's it! The script will:
1. Build the APK
2. Install on your device
3. Launch the app
4. Watch for file changes
5. Auto-rebuild and redeploy on save

### What Happens on First Launch
The app will automatically:
1. Download JMdict dictionary from Yomichan (~30MB)
2. Extract and import entries
3. Be ready to use

**No manual setup needed!**

## 📱 Using the App

1. **Grant Permissions** (shown in app):
   - Overlay permission (for popup)
   - Accessibility service (for text selection)

2. **Use it anywhere**:
   - Select Japanese text in any app
   - Popup shows automatically with translation

## 🔧 Development Workflow

### Hot-Reload Active
- Edit any `.kt`, `.xml`, or manifest file
- Save (Cmd+S)
- Watch terminal - it rebuilds and redeploys automatically
- App restarts with your changes (~15-20 seconds)

### View Logs
Open a second terminal:
```bash
adb logcat TextSelectionService:D OverlayManager:D '*:S'
```

Or use VS Code task: "Logcat - TextSelectionService"

### Manual Commands
```bash
# Build only
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.godtap.dictionary/.MainActivity

# Clear app data
adb shell pm clear com.godtap.dictionary
```

## 🗂️ Project Structure

```
app/src/main/java/com/godtap/dictionary/
├── MainActivity.kt              # Main UI & dictionary download
├── database/                    # Room database
│   ├── DictionaryEntry.kt      # JMdict data model
│   ├── DictionaryDao.kt        # Database queries
│   └── AppDatabase.kt          # Database setup
├── downloader/                  # Dictionary management
│   └── DictionaryDownloader.kt # Downloads & imports JMdict
├── parser/                      # Dictionary parsing
│   └── JMdictParser.kt         # Parses Yomichan format
├── service/                     # Core functionality
│   └── TextSelectionAccessibilityService.kt # Text selection handling
├── overlay/                     # UI overlay
│   └── OverlayManager.kt       # Popup management
└── repository/
    └── DictionaryRepository.kt # Dictionary lookups
```

## 📚 Dictionary

- **Source**: Yomichan JMdict (same as 10ten, Yomitan)
- **URL**: https://github.com/yomidevs/jmdict-yomitan/releases/latest
- **Format**: Term bank JSON files
- **Size**: ~30MB download, ~50MB extracted
- **Entries**: ~175,000+ Japanese words
- **Auto-downloads**: On first app launch

### Manually Trigger Re-Download
```kotlin
// In MainActivity or settings
val downloader = DictionaryDownloader(context)
scope.launch {
    downloader.deleteDictionary()  // Clear old
    downloader.downloadAndImport(listener) // Re-download
}
```

## 🐛 Debugging

### Check Dictionary Status
```kotlin
val status = downloader.getDictionaryStatus()
// status.isImported, status.entryCount, status.version
```

### View All Logs
```bash
adb logcat | grep 'com.godtap.dictionary'
```

### Clear Everything and Start Fresh
```bash
adb shell pm clear com.godtap.dictionary
./dev.sh
```

## ⚡ Performance

- **Build time**: ~10-15 seconds (incremental)
- **Install time**: ~3-5 seconds
- **Hot-reload cycle**: ~20 seconds total
- **Dictionary import**: ~30-60 seconds (first launch only)

## 🎯 Available Tasks (VS Code)

Press `Cmd+Shift+P` → "Tasks: Run Task":
- **Build Debug APK** - Just build
- **Install APK** - Just install
- **Launch App** - Just launch
- **Quick Deploy** - Build + Install + Stop

Or just use `./dev.sh` for everything!

## ✨ Tips

- **Faster builds**: Let Gradle daemon run (it does by default)
- **Better logs**: Use specific filters (TextSelectionService, etc.)
- **Test on real device**: Emulator is slower
- **Keep fswatch running**: It's much faster than polling

---

**That's it!** Run `./dev.sh` and start coding! 🎉
