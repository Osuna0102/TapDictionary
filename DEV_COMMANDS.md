# VS Code Development Commands for AndroidGodTap

## Quick Commands (Press Cmd+Shift+P, then type "Tasks: Run Task")

### Build & Deploy
- **Build Debug APK** - Compiles the app
- **Install APK** - Installs to connected device
- **Build and Install** - Does both in sequence
- **Quick Deploy** - Build, install, and stop the running app

### App Control
- **Launch App** - Starts the app on device
- **Stop App** - Force stops the running app
- **Clear App Data** - Clears all app data

### Debugging & Logs
- **Logcat - TextSelectionService** - Shows only TextSelectionService and OverlayManager logs (RECOMMENDED)
- **Logcat - App Only** - Shows all logs from your app
- **Logcat - All** - Shows all device logs
- **Clear Logcat** - Clears the log buffer

### Full Workflow
- **Build, Install and Launch with Logcat** - Complete deployment with live logs

## Terminal Commands

All these are available directly in the terminal:

```bash
# Build
export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.godtap.dictionary/com.godtap.dictionary.MainActivity

# Stop
adb shell am force-stop com.godtap.dictionary

# Live filtered logs (TextSelectionService only)
adb logcat 'TextSelectionService:D' 'OverlayManager:D' '*:E'

# All app logs
adb logcat | grep 'com.godtap.dictionary'

# Clear logs
adb logcat -c
```

## Current Status

✅ **Fixed Issues:**
1. Removed problematic `CardView` that was causing resource ID errors
2. Replaced `ImageButton` with simple `TextView` for close button
3. Removed `FLAG_WATCH_OUTSIDE_TOUCH` that was auto-closing the popup
4. Simplified layout inflater (no themed context)

✅ **Development Setup:**
- ADB connected: Device U8Z5KBW8EAGULFHY
- Java 17 configured for builds
- VS Code tasks configured
- Logcat monitoring active

## Testing Instructions

### Test Popup Button
1. Open the GodTap Dictionary app on your device
2. Grant both permissions (Overlay + Accessibility) if not already done
3. You'll see two buttons: **"Test Popup"** and the original test button
4. Click **"Test Popup"** to display a sample Japanese word overlay
5. Watch the terminal/logcat for:
   - `OverlayManager: Popup shown`
   - The popup should stay visible for 30 seconds
   - Click the ✕ button to close it manually

### Test Text Selection
1. Select Japanese text in Chrome browser
2. Watch the logcat terminal for:
   - `TextSelectionService: Text selected`
   - `TextSelectionService: Japanese text detected`
   - `OverlayManager: Popup shown`
3. Popup should now stay visible for 30 seconds

## Files Modified

- `app/src/main/res/layout/overlay_dictionary_popup.xml` - Simplified layout
- `app/src/main/java/com/godtap/dictionary/overlay/OverlayManager.kt` - Removed FLAG_WATCH_OUTSIDE_TOUCH
- `.vscode/tasks.json` - Added build/deploy/debug tasks
