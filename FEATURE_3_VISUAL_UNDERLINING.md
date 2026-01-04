# Feature 3: Visual Word Underlining Implementation Guide

## Overview
This document outlines the implementation approach for adding visual word underlining to the GodTap Dictionary Android app. The feature would display colored underlines beneath words in any app based on their lookup frequency, providing visual learning feedback.

**Status:** Not implemented due to API limitations
**API Requirement:** Android 14+ (API 34)
**Complexity:** High
**Estimated Effort:** 2-3 weeks

## Core Requirements

### Visual Feedback System
- **New words** (lookup count = 1): Green underline
- **Familiar words** (lookup count < 3): Yellow underline  
- **Known words** (lookup count < 5): Orange underline
- **Mastered words** (lookup count < 10): Red underline
- **Expert words** (lookup count ≥ 10): No underline

### Technical Constraints
- **API Level:** Requires Android 14+ (API 34) for `SurfaceControl` and advanced overlay capabilities
- **Current App Target:** API 24+ (Android 7.0)
- **Compatibility:** Would exclude ~70% of current Android devices
- **Performance:** High CPU/GPU usage for real-time text analysis

## Implementation Architecture

### 1. SurfaceControl Integration
```kotlin
// Requires API 34+
val surfaceControl = SurfaceControl.Builder()
    .setName("GodTapUnderline")
    .setBufferSize(displayWidth, displayHeight)
    .setFormat(PixelFormat.RGBA_8888)
    .build()

val transaction = SurfaceControl.Transaction()
transaction.setLayer(surfaceControl, Integer.MAX_VALUE)
transaction.show(surfaceControl)
transaction.apply()
```

### 2. Text Detection Pipeline
```kotlin
class TextUnderlineRenderer(context: Context) {
    private val surfaceControl: SurfaceControl
    private val canvas = Canvas()
    
    fun renderUnderlines(textBounds: List<TextBound>, lookupCounts: Map<String, Int>) {
        // Clear previous frame
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        textBounds.forEach { bound ->
            val count = lookupCounts[bound.text] ?: 0
            val color = getUnderlineColor(count)
            drawUnderline(canvas, bound.rect, color)
        }
        
        // Update surface
        transaction.setBuffer(surfaceControl, hardwareBuffer)
        transaction.apply()
    }
}
```

### 3. Accessibility Service Extensions
```kotlin
class TextSelectionAccessibilityService : AccessibilityService() {
    private lateinit var underlineRenderer: TextUnderlineRenderer
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Build.VERSION.SDK_INT >= 34) {
            underlineRenderer = TextUnderlineRenderer(this)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            TYPE_VIEW_TEXT_CHANGED,
            TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                processTextForUnderlines(event)
            }
        }
    }
}
```

### 4. Database Integration
```kotlin
suspend fun getLookupCountsForVisibleText(visibleText: List<String>): Map<String, Int> {
    return visibleText.associateWith { term ->
        dictionaryRepository.search(term)?.lookupCount ?: 0
    }
}
```

## Implementation Steps

### Phase 1: API Level Upgrade
1. Update `build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           minSdk = 34  // Upgrade from 24
           targetSdk = 34
       }
   }
   ```
2. Update manifest permissions
3. Handle backward compatibility

### Phase 2: SurfaceControl Setup
1. Create `TextUnderlineRenderer` class
2. Initialize `SurfaceControl` in service
3. Set up hardware buffer for rendering
4. Implement frame clearing and updating

### Phase 3: Text Analysis
1. Extend accessibility event handling
2. Extract visible text from UI hierarchy
3. Identify word boundaries and positions
4. Query lookup counts for visible words

### Phase 4: Rendering Pipeline
1. Implement underline drawing logic
2. Map lookup counts to colors
3. Handle text reflow and scrolling
4. Optimize for performance (60fps target)

### Phase 5: User Controls
1. Add toggle in notification
2. Settings for underline colors
3. Performance mode options
4. Battery optimization handling

## Technical Challenges

### 1. Performance Optimization
- **Text Analysis:** Real-time OCR-like text extraction from app UIs
- **Rendering:** 60fps underline updates during scrolling
- **Memory:** Hardware buffer management for different screen sizes
- **Battery:** GPU-intensive rendering impact

### 2. Compatibility Issues
- **API Fragmentation:** Only works on Android 14+
- **Device Support:** Limited to flagship devices with good GPU
- **App Compatibility:** Different apps render text differently
- **Theme Support:** Dark mode and custom themes

### 3. Privacy Concerns
- **Text Access:** Reading all visible text across apps
- **Data Collection:** Tracking reading habits
- **Security:** Potential for text interception
- **User Consent:** Clear opt-in requirements

### 4. UI/UX Challenges
- **Visual Clutter:** Too many underlines reduce readability
- **Color Accessibility:** Color choices must work for color-blind users
- **False Positives:** Underlining non-dictionary words
- **Performance Lag:** Delayed rendering affects user experience

## Alternative Approaches

### 1. Overlay-Based Underlines (API 24+)
```kotlin
// Draw underlines using WindowManager overlays
// Less efficient but works on older devices
class OverlayUnderlineRenderer(context: Context) {
    fun drawUnderline(word: String, bounds: Rect, color: Int) {
        val view = View(context).apply {
            background = createUnderlineDrawable(color)
            layoutParams = WindowManager.LayoutParams(
                bounds.width(),
                2, // underline thickness
                bounds.left,
                bounds.bottom,
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
        windowManager.addView(view, layoutParams)
    }
}
```

### 2. Post-Processing Effects
- Use `Canvas.drawText()` with underline spans
- Apply effects after text rendering
- Less accurate positioning

### 3. Third-Party Libraries
- Explore OCR libraries for text detection
- Use ML Kit for text recognition
- Consider OpenCV for image processing

## Testing Strategy

### Unit Tests
- `TextUnderlineRendererTest`: Rendering logic
- `LookupCountMappingTest`: Color mapping
- `SurfaceControlTest`: Hardware buffer management

### Integration Tests
- Accessibility service event handling
- Database lookup count queries
- UI hierarchy text extraction

### Performance Tests
- Frame rate during scrolling
- Memory usage with many underlines
- Battery drain measurement

### Compatibility Tests
- Different Android versions
- Various device manufacturers
- Popular apps (Chrome, Twitter, etc.)

## Deployment Considerations

### Gradual Rollout
1. **Beta Release:** API 34+ devices only
2. **Feature Flag:** Server-side toggle
3. **User Opt-in:** Clear consent flow
4. **Performance Monitoring:** Crash and ANR tracking

### Fallback Strategy
- Graceful degradation on older APIs
- Alternative visual feedback methods
- Clear messaging about requirements

## Conclusion

While technically feasible, the visual word underlining feature faces significant challenges:

- **API Limitations:** Requires Android 14+, limiting user base
- **Performance Impact:** High CPU/GPU usage for real-time rendering
- **Complexity:** Advanced SurfaceControl integration
- **Privacy Concerns:** Extensive text access across apps

**Recommendation:** Defer implementation until Android adoption reaches critical mass (2026+) or explore alternative feedback mechanisms that work on current API levels.

## References
- [Android SurfaceControl Documentation](https://developer.android.com/reference/android/view/SurfaceControl)
- [AccessibilityService Guide](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Android Canvas Drawing](https://developer.android.com/reference/android/graphics/Canvas)