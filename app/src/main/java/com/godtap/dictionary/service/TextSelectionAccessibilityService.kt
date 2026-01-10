package com.godtap.dictionary.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityButtonController
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.core.app.NotificationCompat
import com.godtap.dictionary.MainActivity
import com.godtap.dictionary.DictionaryApp
import com.godtap.dictionary.R
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.database.DictionaryEntry
import com.godtap.dictionary.database.KanjiElement
import com.godtap.dictionary.database.ReadingElement
import com.godtap.dictionary.database.Sense
import com.godtap.dictionary.lookup.DictionaryLookup
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.repository.DictionaryRepository
import com.godtap.dictionary.util.JapaneseTextDetector
import com.godtap.dictionary.util.LanguageDetector
import com.godtap.dictionary.manager.DictionaryManager
import com.godtap.dictionary.api.TranslationService
import com.godtap.dictionary.receiver.NotificationActionReceiver
import com.godtap.dictionary.overlay.TextUnderlineRenderer
import com.godtap.dictionary.util.TextExtractor

import com.godtap.dictionary.ocr.OcrSelectionOverlay
import com.godtap.dictionary.ocr.OcrTextProcessor
import com.godtap.dictionary.gesture.GestureOverlay
import com.godtap.dictionary.overlay.FloatingActionButton
import com.godtap.dictionary.util.AppFilterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class TextSelectionAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TextSelectionService"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_UNDERLINE_ENABLED = "underline_enabled"
        
        var isRunning = false
            private set
        
        private var serviceInstance: TextSelectionAccessibilityService? = null
        
        /**
         * Update notification from external components (e.g., BroadcastReceiver)
         */
        fun updateNotification(context: Context, enabled: Boolean) {
            serviceInstance?.updateNotificationState(enabled)
        }
        
        /**
         * Update underline notification from external components
         */
        fun updateUnderlineNotification(context: Context, enabled: Boolean) {
            serviceInstance?.updateUnderlineState(enabled)
        }
        // Native gestures are always enabled (onGesture callback) - no start/stop needed
    }
    
    private lateinit var overlayManager: OverlayManager
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var translationService: TranslationService
    private lateinit var underlineRenderer: TextUnderlineRenderer
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appFilterManager: AppFilterManager

    private lateinit var ocrSelectionOverlay: OcrSelectionOverlay
    private lateinit var ocrTextProcessor: OcrTextProcessor
    
    // Floating button (hidden/minimized) - backup control method
    private lateinit var floatingButton: FloatingActionButton
    
    // Native gesture detection via onGesture callback (requires touch exploration)
    // Using 3-finger gestures to avoid conflicts with system 2-finger gestures
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastProcessedText: String? = null
    private var lastProcessedTime: Long = 0
    private var isServiceEnabled: Boolean = true
    private var isUnderlineEnabled: Boolean = false
    
    // SharedPreferences listener for real-time updates
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_UNDERLINE_ENABLED -> {
                val newValue = sharedPreferences.getBoolean(KEY_UNDERLINE_ENABLED, false)
                if (newValue != isUnderlineEnabled) {
                    Log.d(TAG, "Underline preference changed externally: $isUnderlineEnabled -> $newValue")
                    isUnderlineEnabled = newValue
                    underlineRenderer.isEnabled = newValue
                    if (!newValue) {
                        underlineRenderer.clearAllUnderlines()
                    }
                    showNotification()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "══════════════════════════════════════════════════════")
        Log.i(TAG, "SERVICE CREATED")
        Log.i(TAG, "══════════════════════════════════════════════════════")
        serviceInstance = this
        overlayManager = OverlayManager(this)
        
        // Set up callback for word clicks from clickable sentence popup
        overlayManager.onWordClickCallback = { word, language, depth ->
            Log.d(TAG, "Word clicked from popup: $word, language: $language, depth: $depth")
            serviceScope.launch {
                // Look up the clicked word
                lookupWord(word, -1, -1, language, depth)
            }
        }
        
        // Native gesture detection will be enabled in onServiceConnected()
        Log.i(TAG, "Service created - gesture detection will use native Android API")

        
        // Initialize database and repository
        val database = AppDatabase.getDatabase(this)
        dictionaryRepository = DictionaryRepository(database)
        // DictionaryLookup will be created per-lookup with the appropriate language
        dictionaryManager = DictionaryManager(this)
        translationService = TranslationService()
        
        // Initialize underline renderer
        underlineRenderer = TextUnderlineRenderer(this)
        
        // Initialize OCR components
        ocrTextProcessor = OcrTextProcessor(
            context = this,
            dictionaryRepository = dictionaryRepository,
            dictionaryManager = dictionaryManager,
            translationService = translationService,
            overlayManager = overlayManager
        )
        
        ocrSelectionOverlay = OcrSelectionOverlay(
            context = this,
            onTextRecognized = { text, bounds ->
                Log.d(TAG, "OCR recognized text: $text at $bounds")
                ocrTextProcessor.processText(text, bounds, serviceScope)
            },
            takeScreenshotCallback = { rect, callback ->
                takeScreenshotForOcr(rect, callback)
            }
        )
        
        // Initialize floating button (will be hidden by default)
        floatingButton = FloatingActionButton(this).apply {
            onOcrClick = { activateOcrMode() }
            onUnderlineToggle = { toggleUnderlineState() }
            onServiceToggle = { toggleServiceState() }
        }
        
        // Load preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isServiceEnabled = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, true)
        // Underlining is DISABLED by default - user must enable it manually
        isUnderlineEnabled = sharedPreferences.getBoolean(KEY_UNDERLINE_ENABLED, false)
        
        // Initialize app filter manager
        appFilterManager = AppFilterManager(this)
        underlineRenderer.isEnabled = isUnderlineEnabled
        
        // Register preference change listener for real-time updates
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d(TAG, "SharedPreferences listener registered")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "SERVICE CONNECTED - Full Diagnostics")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        showNotification()

        // Log detailed service configuration
        serviceInfo?.let { info ->
            Log.i(TAG, "Service Info Details:")
            Log.i(TAG, "  - Flags: ${info.flags} (0x${info.flags.toString(16)})")
            Log.i(TAG, "  - Event Types: ${info.eventTypes}")
            Log.i(TAG, "  - Feedback Type: ${info.feedbackType}")
            Log.i(TAG, "  - Can Retrieve Window Content: ${info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0}")
            Log.i(TAG, "  - Touch Exploration: ${info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0}")
            Log.i(TAG, "  - Can Take Screenshot: ${android.os.Build.VERSION.SDK_INT >= 30}")
            
            // CRITICAL: Check if touch exploration is actually ENABLED by the system
            val isTouchExplorationEnabled = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE != 0
            if (isTouchExplorationEnabled) {
                Log.w(TAG, "⚠️ Touch exploration REQUESTED but system may not have enabled it!")
                Log.w(TAG, "⚠️ On MIUI/Xiaomi devices, multi-finger gestures may not work!")
                Log.w(TAG, "⚠️ This is a known limitation of MIUI's accessibility implementation.")
            }
            
            // List all event types being monitored
            val eventTypeNames = mutableListOf<String>()
            if (info.eventTypes and AccessibilityEvent.TYPE_VIEW_CLICKED != 0) eventTypeNames.add("TYPE_VIEW_CLICKED")
            if (info.eventTypes and AccessibilityEvent.TYPE_VIEW_LONG_CLICKED != 0) eventTypeNames.add("TYPE_VIEW_LONG_CLICKED")
            if (info.eventTypes and AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED != 0) eventTypeNames.add("TYPE_VIEW_TEXT_SELECTION_CHANGED")
            if (info.eventTypes and AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED != 0) eventTypeNames.add("TYPE_WINDOW_STATE_CHANGED")
            if (info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0) eventTypeNames.add("TYPE_WINDOW_CONTENT_CHANGED")
            if (info.eventTypes and AccessibilityEvent.TYPE_VIEW_SCROLLED != 0) eventTypeNames.add("TYPE_VIEW_SCROLLED")
            if (info.eventTypes and AccessibilityEvent.TYPE_GESTURE_DETECTION_START != 0) eventTypeNames.add("TYPE_GESTURE_DETECTION_START")
            if (info.eventTypes and AccessibilityEvent.TYPE_GESTURE_DETECTION_END != 0) eventTypeNames.add("TYPE_GESTURE_DETECTION_END")
            if (info.eventTypes and AccessibilityEvent.TYPE_TOUCH_INTERACTION_START != 0) eventTypeNames.add("TYPE_TOUCH_INTERACTION_START")
            if (info.eventTypes and AccessibilityEvent.TYPE_TOUCH_INTERACTION_END != 0) eventTypeNames.add("TYPE_TOUCH_INTERACTION_END")
            Log.i(TAG, "  - Monitored Events: ${eventTypeNames.joinToString(", ")}")
        }

        // Register accessibility button callback (for navigation bar button + volume button shortcut)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val buttonController = accessibilityButtonController
                val accessibilityButtonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController?) {
                        onAccessibilityButtonClicked()
                    }

                    override fun onAvailabilityChanged(controller: AccessibilityButtonController?, available: Boolean) {
                        onAccessibilityButtonAvailabilityChanged(available)
                    }
                }
                buttonController?.registerAccessibilityButtonCallback(accessibilityButtonCallback)
                Log.i(TAG, "✅ Accessibility button callback registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register accessibility button callback", e)
            }
        }

        // Accessibility shortcuts configured (volume buttons + accessibility button)
        Log.i(TAG, "╔════════════════════════════════════════════════════════╗")
        Log.i(TAG, "║ ✅ ACCESSIBILITY SHORTCUTS READY!                      ║")
        Log.i(TAG, "║                                                        ║")
        Log.i(TAG, "║   Use these shortcuts to activate OCR:                ║")
        Log.i(TAG, "║   📱 Accessibility Button (nav bar)                    ║")
        Log.i(TAG, "║   🔊 Hold BOTH Volume Buttons (3 seconds)             ║")
        Log.i(TAG, "║                                                        ║")
        Log.i(TAG, "║   Or use notification buttons for other actions       ║")
        Log.i(TAG, "╚════════════════════════════════════════════════════════╝")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        // Don't show floating button by default (kept for fallback)
        // Uncomment to show: floatingButton.show()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // Check if service is enabled
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled, ignoring event")
            return
        }
        
        // Ignore events from our own app to reduce log spam
        if (event.packageName == packageName) {
            return
        }
        
        // Log all events for debugging with human-readable names
        val eventTypeName = when(event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> "GESTURE_START"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> "GESTURE_END"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TOUCH_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TOUCH_END"
            else -> "UNKNOWN(${event.eventType})"
        }
        Log.d(TAG, "Event: $eventTypeName (${event.eventType}), Package: ${event.packageName}, Class: ${event.className}")
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelectionChanged(event)
                // Trigger underline rendering on text selection (debounced)
                if (isUnderlineEnabled) {
                    renderUnderlines(event)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleViewLongClicked(event)
            }
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                // Handle gesture events (API 30+/Android 11+)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    handleGestureEvent(event)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Clear underlines immediately on scroll/content change, then debounce render
                if (isUnderlineEnabled) {
                    underlineRenderer.clearAllUnderlines()
                    renderUnderlines(event)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Force render on screen change
                if (isUnderlineEnabled) {
                    forceRenderUnderlines(event)
                }
            }
        }
    }
    
    /**
     * Handle accessibility gesture events (API 30+/Android 11+)
     * These gestures are detected by the system and don't block touches
     */
    @androidx.annotation.RequiresApi(30)
    private fun handleGestureEvent(event: AccessibilityEvent) {
        // Note: In API 30-33, we need to check parcelable data
        // The gesture ID may be in event.parcelableData or other properties
        
        // For now, log all gesture events to see what data is available
        Log.i(TAG, "═══════════════════════════════════════════════════")
        Log.i(TAG, "GESTURE EVENT DETECTED!")
        Log.i(TAG, "  Event type: ${event.eventType}")
        Log.i(TAG, "  Package: ${event.packageName}")
        Log.i(TAG, "  Text: ${event.text}")
        Log.i(TAG, "  ContentDescription: ${event.contentDescription}")
        Log.i(TAG, "═══════════════════════════════════════════════════")
        
        // Try to extract gesture information from the event
        // The exact method varies by Android version
        try {
            // Some devices may have gesture info in different places
            val parcelable = event.parcelableData
            Log.d(TAG, "Parcelable data: $parcelable")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading gesture data: ${e.message}")
        }
        
        // TODO: Map specific gestures once we identify how to extract them
        // For now, trigger OCR mode on any gesture as a test
        Log.i(TAG, "Triggering OCR mode (test)")
        activateOcrMode()
    }
    
    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        // Don't process sensitive contexts
        if (!shouldProcessText(event)) return
        
        // Get the FULL text from the node (not just selected portion)
        // This is critical because fromIndex/toIndex refer to positions in the full text
        val fullText = extractFullText(event)
        
        if (fullText != null && fullText.isNotBlank()) {
            // Check active dictionary to determine which language to detect
            serviceScope.launch {
                val activeDictionary = dictionaryManager.getActiveDictionary()
                if (activeDictionary == null) {
                    Log.d(TAG, "No active dictionary, ignoring text selection")
                    return@launch
                }
                
                val sourceLang = activeDictionary.sourceLanguage
                Log.d(TAG, "Active dictionary: ${activeDictionary.name}, source language: $sourceLang")
                
                // Check if text matches the active dictionary's language
                if (LanguageDetector.matchesLanguage(fullText, sourceLang)) {
                    Log.d(TAG, "$sourceLang text detected (length ${fullText.length}): ${fullText.take(50)}...")
                    
                    // Find the tap position using fromIndex/toIndex on the FULL text
                    val tapPosition = findTapPosition(event, fullText)
                    
                    if (tapPosition >= 0) {
                        Log.d(TAG, "✓ Tap detected at position $tapPosition: '${fullText.getOrNull(tapPosition)}'")
                        // Pass fullText as the sentence context
                        processTextFromPosition(fullText, tapPosition, sourceLang, fullSentence = fullText)
                    } else {
                        Log.d(TAG, "⊘ Could not determine tap position - showing clickable sentence")
                        // Show clickable sentence popup when position is unknown
                        overlayManager.showClickableSentence(fullText, sourceLang, depth = 0)
                    }
                } else {
                    Log.d(TAG, "Text language doesn't match active dictionary ($sourceLang)")
                }
            }
        } else {
            // Don't auto-hide the popup when selection is cleared
            Log.d(TAG, "Text selection cleared, but keeping popup visible")
        }
    }
    
    /**
     * Find the tap position from the event's fromIndex/toIndex
     * These indices refer to positions in the full text
     */
    private fun findTapPosition(event: AccessibilityEvent, fullText: String): Int {
        try {
            val fromIndex = event.fromIndex
            val toIndex = event.toIndex
            
            Log.d(TAG, "Selection indices: from=$fromIndex, to=$toIndex, textLength=${fullText.length}")
            
            // Case 1: Valid range selection (e.g., user selected multiple characters)
            if (fromIndex >= 0 && toIndex > fromIndex && toIndex <= fullText.length) {
                Log.d(TAG, "Range selection [$fromIndex-$toIndex]: '${fullText.substring(fromIndex, toIndex)}'")
                return fromIndex
            }
            
            // Case 2: Cursor position (fromIndex == toIndex)
            // This happens when user taps and Chrome sets cursor at that position
            if (fromIndex >= 0 && fromIndex == toIndex && fromIndex < fullText.length) {
                Log.d(TAG, "Cursor at position $fromIndex: '${fullText[fromIndex]}'")
                return fromIndex
            }
            
            // Case 3: fromIndex valid but toIndex is 0 or invalid
            if (fromIndex >= 0 && fromIndex < fullText.length) {
                Log.d(TAG, "Using fromIndex $fromIndex: '${fullText[fromIndex]}'")
                return fromIndex
            }
            
            Log.d(TAG, "No valid indices found")
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Error finding tap position", e)
            return -1
        }
    }
    
    /**
     * Check if app has unreliable tap position tracking
     * Apps like WhatsApp always return position 0 regardless of where the user taps
     */
    private fun isAppWithUnreliablePositioning(packageName: String): Boolean {
        return packageName in setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android"
        )
    }
    
    /**
     * Extract the FULL text from the event/node (not just the selected substring)
     * This is critical because fromIndex/toIndex refer to the full text
     */
    private fun extractFullText(event: AccessibilityEvent): String? {
        // Get full text from event.text (not the selected portion)
        val text = event.text.firstOrNull()?.toString() ?: event.contentDescription?.toString()
        
        if (text != null && text.isNotBlank()) {
            return text
        }
        
        // Try to get text from source node
        event.source?.let { node ->
            val nodeText = extractTextFromNode(node)
            node.recycle()
            return nodeText
        }
        
        return null
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        // Don't process if from our own app
        if (event.packageName == packageName) return
        
        // Don't process sensitive contexts
        if (!shouldProcessText(event)) return
        
        val rootNode = event.source
        if (rootNode == null) {
            Log.d(TAG, "Click event has no source node")
            return
        }
        
        try {
            // Get click coordinates from the event
            val clickRect = Rect()
            rootNode.getBoundsInScreen(clickRect)
            
            Log.d(TAG, "═══════════════════════════════════════════════")
            Log.d(TAG, "CLICK EVENT DETECTED")
            Log.d(TAG, "Click at bounds: $clickRect (pkg: ${event.packageName})")
            Log.d(TAG, "Root node class: ${rootNode.className}")
            Log.d(TAG, "═══════════════════════════════════════════════")
            
            // Log the entire tree structure
            logNodeTree(rootNode, clickRect, 0)
            
            Log.d(TAG, "═══════════════════════════════════════════════")
            Log.d(TAG, "SEARCHING FOR BEST TEXT NODE...")
            Log.d(TAG, "═══════════════════════════════════════════════")
            
            // Find the most specific child node containing text at the click location
            val clickedTextNode = findClickedTextNode(rootNode, clickRect)
            
            if (clickedTextNode != null) {
                val text = clickedTextNode.text?.toString() ?: clickedTextNode.contentDescription?.toString()
                
                if (!text.isNullOrBlank()) {
                    val nodeRect = Rect()
                    clickedTextNode.getBoundsInScreen(nodeRect)
                    val x = nodeRect.centerX()
                    val y = nodeRect.bottom
                    
                    Log.d(TAG, "✓ SELECTED TEXT NODE: '$text'")
                    Log.d(TAG, "  Class: ${clickedTextNode.className}")
                    Log.d(TAG, "  Bounds: $nodeRect")
                    
                    serviceScope.launch {
                        val activeDictionary = dictionaryManager.getActiveDictionary()
                        if (activeDictionary == null) {
                            Log.d(TAG, "No active dictionary, ignoring click")
                            return@launch
                        }
                        
                        val sourceLang = activeDictionary.sourceLanguage
                        
                        // Check if text matches the active dictionary's language
                        if (LanguageDetector.matchesLanguage(text, sourceLang)) {
                            Log.d(TAG, "$sourceLang text detected in click (length ${text.length}): ${text.take(50)}...")
                            
                            // Check if this app has unreliable position tracking (like WhatsApp)
                            val hasUnreliablePositioning = isAppWithUnreliablePositioning(event.packageName.toString())
                            
                            if (hasUnreliablePositioning && text.length > 20) {
                                // For apps like WhatsApp that always return position 0,
                                // show the full clickable sentence instead of trying to guess the word
                                Log.d(TAG, "⚠️ App has unreliable positioning (${event.packageName}), showing full clickable sentence")
                                overlayManager.showClickableSentence(text, sourceLang, depth = 0)
                                return@launch
                            }
                            
                            // For single-line text nodes, process from beginning
                            // For multi-line or long text, try to estimate click position
                            val position = if (text.length < 50 && !text.contains('\n')) {
                                0
                            } else {
                                // Estimate position based on click location within the node bounds
                                val estimatedPos = estimateClickPosition(text, clickRect, nodeRect)

                                // For long text with clicks, if estimated position is in the middle,
                                // also try positions near the end (where "ayudarte" would be)
                                if (text.length > 50 && estimatedPos < text.length * 0.6f) {
                                    Log.d(TAG, "Long text detected, trying alternative positions for click")
                                    // Try positions near the end of the text
                                    val altPositions = listOf(
                                        text.length - 10, // Near the end
                                        text.length - 20, // A bit further back
                                        estimatedPos      // Original estimate
                                    )

                                    // Find the best position that gives us a valid word
                                    for (pos in altPositions) {
                                        val safePos = pos.coerceIn(0, text.length - 1)
                                        val wordAtPos = extractWordAtPosition(text, safePos, sourceLang)
                                        if (wordAtPos != null && wordAtPos.length > 2) {
                                            Log.d(TAG, "Found valid word '$wordAtPos' at alternative position $safePos, using it")
                                            processTextFromPosition(text, safePos, sourceLang, x, y)
                                            return@launch
                                        }
                                    }
                                }

                                estimatedPos
                            }
                            
                            processTextFromPosition(text, position, sourceLang, x, y)
                        } else {
                            Log.d(TAG, "⊘ Text language doesn't match active dictionary ($sourceLang)")
                            Log.d(TAG, "  Detected text: '$text'")
                        }
                    }
                } else {
                    Log.d(TAG, "⊘ Clicked node has no text content")
                }
                
                if (clickedTextNode != rootNode) {
                    clickedTextNode.recycle()
                }
            } else {
                Log.d(TAG, "⊘ No text node found at click location")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling click event", e)
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * Log the entire node tree for debugging
     */
    private fun logNodeTree(node: AccessibilityNodeInfo?, clickRect: Rect, depth: Int) {
        if (node == null) return
        
        try {
            val indent = "  ".repeat(depth)
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            val className = node.className?.toString() ?: "null"
            val childCount = node.childCount
            
            val containsClick = nodeRect.contains(clickRect) || Rect.intersects(nodeRect, clickRect)
            val marker = if (containsClick) "✓" else "✗"
            
            val textInfo = when {
                !text.isNullOrBlank() -> "TEXT='$text'"
                !contentDesc.isNullOrBlank() -> "DESC='$contentDesc'"
                else -> "NO_TEXT"
            }
            
            Log.d(TAG, "$indent$marker [$depth] $className | $textInfo")
            Log.d(TAG, "$indent    Bounds: $nodeRect | Children: $childCount | ContainsClick: $containsClick")
            
            // Recursively log children
            for (i in 0 until childCount) {
                try {
                    node.getChild(i)?.let { child: AccessibilityNodeInfo ->
                        logNodeTree(child, clickRect, depth + 1)
                        child.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${indent}Error logging child $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in logNodeTree", e)
        }
    }
    
    /**
     * Recursively find the most specific (deepest) child node that contains text
     * at or near the click location. This is crucial for WhatsApp where messages
     * are grouped in a parent container but individual message nodes exist.
     */
    private fun findClickedTextNode(node: AccessibilityNodeInfo?, clickRect: Rect): AccessibilityNodeInfo? {
        if (node == null) return null
        
        try {
            // Check if this node has text
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()
            val hasText = !nodeText.isNullOrBlank() || !nodeDesc.isNullOrBlank()
            
            // Get this node's bounds
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            
            // Check if click is within this node's bounds
            val containsClick = nodeRect.contains(clickRect) || Rect.intersects(nodeRect, clickRect)
            
            if (!containsClick) {
                return null
            }
            
            // Try to find a more specific child node
            var bestChild: AccessibilityNodeInfo? = null
            var bestChildScore = Int.MIN_VALUE
            
            for (i in 0 until node.childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        val childResult = findClickedTextNode(child, clickRect)
                        if (childResult != null) {
                            val childText = childResult.text?.toString() ?: childResult.contentDescription?.toString() ?: ""
                            val childRect = Rect()
                            childResult.getBoundsInScreen(childRect)
                            
                            // Score based on content type and size
                            val score = calculateNodeScore(childText, childRect)
                            
                            Log.d(TAG, "    Candidate: '$childText' | Score: $score")
                            
                            // Prefer higher scores (message text over timestamps/metadata)
                            if (score > bestChildScore) {
                                bestChild?.recycle()
                                bestChild = childResult
                                bestChildScore = score
                            } else {
                                if (childResult != child) childResult.recycle()
                            }
                        }
                        if (child != childResult) child.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing child node $i", e)
                }
            }
            
            // Return the best child, or this node if no better child found
            return if (bestChild != null) {
                bestChild
            } else if (hasText) {
                // Check if this node itself should be excluded
                val thisText = nodeText ?: nodeDesc ?: ""
                if (shouldExcludeText(thisText)) {
                    Log.d(TAG, "    Excluding node with metadata text: '$thisText'")
                    null
                } else {
                    node
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findClickedTextNode", e)
            return null
        }
    }
    
    /**
     * Calculate a score for a text node to determine if it's likely message content
     * Higher score = more likely to be the actual message
     */
    private fun calculateNodeScore(text: String, bounds: Rect): Int {
        if (text.isBlank()) return Int.MIN_VALUE
        
        var score = 0
        
        // Penalize metadata patterns
        if (shouldExcludeText(text)) {
            score -= 10000  // Heavy penalty for timestamps/dates
        }
        
        // Prefer longer text (more likely to be message content)
        score += text.length * 10
        
        // Prefer larger bounds (message text is usually larger than timestamps)
        val area = bounds.width() * bounds.height()
        score += area / 100
        
        // Prefer text with actual words (not just numbers/punctuation)
        val wordCount = text.split(Regex("\\s+")).filter { it.matches(Regex(".*[a-zA-ZáéíóúñÑ\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF\\uAC00-\\uD7AF]+.*")) }.size
        score += wordCount * 100
        
        return score
    }
    
    /**
     * Check if text is likely metadata (timestamp, date, etc.) that should be excluded
     */
    private fun shouldExcludeText(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        
        // Time patterns: "10:57 p. m.", "6:23 p. m.", "3:43 p. m."
        if (lowerText.matches(Regex("\\d{1,2}:\\d{2}\\s*(a\\.|p\\.|am|pm|a\\.\\s*m\\.|p\\.\\s*m\\.).*"))) {
            return true
        }
        
        // Date patterns: "hace 2 años", "hace 3 días", "ayer", "hoy"
        if (lowerText.matches(Regex("hace\\s+\\d+\\s+(año|años|día|días|hora|horas|minuto|minutos).*"))) {
            return true
        }
        
        if (lowerText in setOf("ayer", "hoy", "yesterday", "today", "now", "ahora")) {
            return true
        }
        
        // Short time-like patterns
        if (lowerText.matches(Regex("\\d{1,2}:\\d{2}"))) {
            return true
        }
        
        return false
    }
    
    /**
     * Estimate the character position of a click within a text node
     * based on relative position within the node's bounds
     * IMPROVED: Use left edge of click rect instead of center for better accuracy
     */
    private fun estimateClickPosition(text: String, clickRect: Rect, nodeRect: Rect): Int {
        // Use the left edge of the click rect (where the finger actually touched)
        // instead of center, for more accurate character positioning
        val clickX = clickRect.left

        // Calculate relative position within the node (0.0 to 1.0)
        val relativeX = ((clickX - nodeRect.left).toFloat() / nodeRect.width()).coerceIn(0f, 1f)

        // For very long text, be more conservative with position estimation
        val estimatedPos = if (text.length > 20) {
            // For long text, assume click is in the latter half and search backwards from there
            val startSearchFrom = (text.length * 0.7f).toInt().coerceAtMost(text.length - 1)
            val forwardEstimate = (text.length * relativeX).toInt()

            // Use the more conservative estimate (closer to start of word)
            minOf(startSearchFrom, forwardEstimate)
        } else {
            // For short text, use direct proportional estimation
            (text.length * relativeX).toInt()
        }

        val finalPos = estimatedPos.coerceIn(0, text.length - 1)

        Log.d(TAG, "Click position estimation: clickX=$clickX, nodeRect=$nodeRect, relativeX=$relativeX, estimatedPos=$estimatedPos, finalPos=$finalPos")

        return finalPos
    }
    
    private fun handleViewLongClicked(event: AccessibilityEvent) {
        // Handle long click for potential text selection
        Log.d(TAG, "Long click detected")
    }
    

    
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String? {
        // Try text field
        val text = node.text?.toString()
        if (text != null) return text
        
        // Try content description
        val contentDesc = node.contentDescription?.toString()
        if (contentDesc != null) return contentDesc
        
        // Try to find selected text from children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val childText = extractTextFromNode(child)
                child.recycle()
                if (childText != null) return childText
            }
        }
        
        return null
    }
    
    private fun shouldProcessText(event: AccessibilityEvent): Boolean {
        val packageName = event.packageName?.toString() ?: return false
        
        // Ignore our own package to prevent loops from popup
        if (packageName == this.packageName) {
            return false
        }
        
        // Check app filter settings
        if (!appFilterManager.shouldProcessApp(packageName)) {
            Log.d(TAG, "App filtered out: $packageName")
            return false
        }
        
        // Ignore password fields
        if (event.isPassword) {
            Log.d(TAG, "Ignoring password field")
            return false
        }
        
        // Ignore keyboard apps
        if (packageName.contains("keyboard", ignoreCase = true) ||
            packageName.contains("inputmethod", ignoreCase = true)) {
            Log.d(TAG, "Ignoring keyboard app")
            return false
        }
        
        // Ignore system UI
        if (packageName == "com.android.systemui") {
            Log.d(TAG, "Ignoring system UI")
            return false
        }
        
        return true
    }
    
    /**
     * Lookup a word and show popup (used for popup word clicks)
     * @param word The word to look up
     * @param x Screen X coordinate for popup positioning (-1 for default)
     * @param y Screen Y coordinate for popup positioning (-1 for default)
     * @param languageCode ISO 639-1 language code (ja, es, ko, etc.)
     * @param depth Current popup depth (for nested popups)
     */
    private suspend fun lookupWord(word: String, x: Int = -1, y: Int = -1, languageCode: String, depth: Int = 0) {
        try {
            Log.d(TAG, "lookupWord() called: '$word', lang: $languageCode, depth: $depth")
            
            // Check depth limit
            if (depth >= 2) {
                Log.w(TAG, "Max popup depth reached, ignoring")
                return
            }
            
            // Create language-specific lookup
            val dictionaryLookup = DictionaryLookup(dictionaryRepository, languageCode)
            
            // Show loading popup
            overlayManager.showLoadingPopup(word, x, y, languageCode)
            
            // Lookup the word
            val lookupResult = dictionaryLookup.lookup(word)
            
            if (lookupResult != null) {
                val entry = lookupResult.entry
                Log.d(TAG, "✓ Found entry for '$word'")
                
                // Get primary kanji and reading
                val kanji = entry.getPrimaryKanji()
                val reading = entry.primaryReading
                val rawGlosses = entry.getAllGlosses()
                val partsOfSpeech = entry.getPartsOfSpeech()
                
                // Parse Yomichan structured content (JSON format)
                val glosses = rawGlosses.map { parseYomichanGloss(it) }
                
                // Format the word display
                val wordDisplay = if (kanji != null) {
                    "$kanji ($reading)"
                } else {
                    reading
                }
                
                // Format the translation
                val translation = buildString {
                    val isApiTranslated = entry.senses.any { sense -> 
                        sense.info.contains("Auto-translated via API")
                    }
                    
                    if (isApiTranslated) {
                        append("[Auto-translated]\n")
                    } else if (partsOfSpeech.isNotEmpty()) {
                        append("[${partsOfSpeech.first()}]\n")
                    }
                    append(glosses.joinToString("; "))
                }
                
                overlayManager.showPopup(wordDisplay, translation, entry.lookupCount + 1, x, y, languageCode, null, depth)
            } else {
                Log.d(TAG, "⊘ No dictionary entry found for '$word'")
                
                // Try online translation
                val activeDictionary = dictionaryManager.getActiveDictionary()
                val targetLang = activeDictionary?.targetLanguage ?: "en"
                val translatedText = translationService.translate(word, languageCode, targetLang)
                
                if (translatedText != null && translatedText.isNotBlank()) {
                    Log.d(TAG, "✓ Fallback translation: '$word' -> '$translatedText'")
                    
                    // Create dictionary entry
                    createDictionaryEntryForApiTranslation(word, translatedText, languageCode)
                    
                    val translation = "[Auto-translated]\n$translatedText"
                    overlayManager.showPopup(word, translation, 1, x, y, languageCode, null, depth)
                } else {
                    overlayManager.showPopup(word, getString(R.string.popup_no_translation), 0, x, y, languageCode, null, depth)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up word", e)
            overlayManager.showPopup(word, "Error: ${e.message}", 0, x, y, languageCode, null, depth)
        }
    }
    
    /**
     * Process text starting from a specific character position
     * This is critical: scan from WHERE the user tapped, not from position 0!
     * @param text The full text
     * @param startPosition Position where user tapped
     * @param languageCode ISO 639-1 language code (ja, es, ko, etc.)
     * @param fullSentence Optional full sentence context to show in popup
     */
    private fun processTextFromPosition(text: String, startPosition: Int, languageCode: String, x: Int = -1, y: Int = -1, fullSentence: String? = null) {
        serviceScope.launch {
            try {
                // Debounce: ignore rapid repeated selections within 500ms
                val currentTime = System.currentTimeMillis()
                if (text == lastProcessedText && (currentTime - lastProcessedTime) < 500) {
                    Log.d(TAG, "⊘ Ignoring duplicate selection: $text")
                    return@launch
                }
                lastProcessedText = text
                lastProcessedTime = currentTime
                
                // Create language-specific lookup
                val dictionaryLookup = DictionaryLookup(dictionaryRepository, languageCode)
                
                // For space-delimited languages (Spanish, Korean), extract word at tap position
                // For Japanese, use substring from tap position
                val textToLookup = if (languageCode in listOf("es", "ko", "zh")) {
                    extractWordAtPosition(text, startPosition, languageCode)
                } else {
                    // Japanese: substring from tap position
                    val safeStartPos = startPosition.coerceIn(0, text.length)
                    text.substring(safeStartPos)
                }
                
                Log.d(TAG, "EXTRACTED WORD: '$textToLookup' at position $startPosition in text: '${text.take(100)}...'")
                
                if (textToLookup.isNullOrEmpty()) {
                    Log.d(TAG, "⊘ No word found at position $startPosition")
                    return@launch
                }
                
                Log.d(TAG, "Processing from position $startPosition: '$textToLookup' (full text: '$text')")
                
                // Show loading popup IMMEDIATELY while we search
                overlayManager.showLoadingPopup(textToLookup, x, y, languageCode)
                
                // Use Yomitan-style progressive substring matching for Japanese
                // Or word lookup for space-delimited languages
                val lookupResult = dictionaryLookup.lookup(textToLookup)
                
                if (lookupResult != null) {
                    val entry = lookupResult.entry
                    Log.d(TAG, "✓ Found dictionary entry for '$textToLookup': ${entry.primaryExpression ?: entry.primaryReading} (entryId=${entry.id})")
                    Log.d(TAG, "✓ Found match: '${lookupResult.matchedText}' (length: ${lookupResult.matchLength}) at position $startPosition")
                    
                    // Get primary kanji and reading
                    val kanji = entry.getPrimaryKanji()
                    val reading = entry.primaryReading
                    val rawGlosses = entry.getAllGlosses()
                    val partsOfSpeech = entry.getPartsOfSpeech()
                    
                    // Parse Yomichan structured content (JSON format)
                    val glosses = rawGlosses.map { parseYomichanGloss(it) }
                    
                    Log.d(TAG, "Dictionary entry: ${kanji ?: reading} -> ${glosses.joinToString(", ")}")
                    
                    // Format the word display
                    val wordDisplay = if (kanji != null) {
                        "$kanji ($reading)"
                    } else {
                        reading
                    }
                    
                    // Format the translation
                    val translation = buildString {
                        // Check if this is an API-translated entry
                        val isApiTranslated = entry.senses.any { sense -> 
                            sense.info.contains("Auto-translated via API")
                        }
                        
                        if (isApiTranslated) {
                            append("[Auto-translated]\n")
                        } else if (partsOfSpeech.isNotEmpty()) {
                            append("[${partsOfSpeech.first()}]\n")
                        }
                        append(glosses.joinToString("; "))
                    }
                    
                    // Pass lookup count + 1 (since we just incremented it in the repository)
                    Log.d(TAG, "  Displaying with lookupCount: ${entry.lookupCount + 1} (entry.lookupCount=${entry.lookupCount} + 1)")
                    overlayManager.showPopup(wordDisplay, translation, entry.lookupCount + 1, x, y, languageCode, fullSentence, depth = 0)
                } else {
                    Log.d(TAG, "⊘ No dictionary entry found from position $startPosition: '$textToLookup'")
                    
                    // Fallback: Try online translation
                    Log.d(TAG, "Attempting fallback translation via API...")
                    val activeDictionary = dictionaryManager.getActiveDictionary()
                    val targetLang = activeDictionary?.targetLanguage ?: "en"
                    Log.d(TAG, "Using target language: $targetLang (from active dictionary: ${activeDictionary?.name})")
                    val translatedText = translationService.translate(textToLookup, languageCode, targetLang)
                    
                    if (translatedText != null && translatedText.isNotBlank()) {
                        Log.d(TAG, "✓ Fallback translation successful: '$textToLookup' -> '$translatedText'")
                        
                        // Create dictionary entry for future lookups
                        serviceScope.launch {
                            try {
                                createDictionaryEntryForApiTranslation(textToLookup, translatedText, languageCode)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to create dictionary entry for '$textToLookup'", e)
                            }
                        }
                        
                        val translation = buildString {
                            append("[Auto-translated]\n")
                            append(translatedText)
                        }
                        overlayManager.showPopup(textToLookup, translation, 1, x, y, languageCode, fullSentence, depth = 0)
                    } else {
                        Log.d(TAG, "⊘ Fallback translation failed")
                        overlayManager.showPopup(textToLookup, getString(R.string.popup_no_translation), 0, x, y, languageCode, fullSentence, depth = 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing text", e)
                overlayManager.showPopup(text, "Error: ${e.message}", 0, x, y, languageCode, fullSentence, depth = 0)
            }
        }
    }
    
    /**
     * Extract word at position for space-delimited languages (Spanish, Korean, Chinese)
     */
    private fun extractWordAtPosition(text: String, position: Int, languageCode: String): String? {
        if (position < 0 || position >= text.length) return null
        
        val wordSeparators = setOf(' ', '\n', '\t', '.', ',', ';', ':', '!', '¡', '?', '¿',
                                     '(', ')', '[', ']', '{', '}', '"', '\'', '—', '/', '-')
        
        // Find start of word (scan backward from position)
        var start = position
        while (start > 0 && text[start - 1] !in wordSeparators) {
            start--
        }
        
        // Find end of word (scan forward from position)
        var end = position
        while (end < text.length && text[end] !in wordSeparators) {
            end++
        }
        
        if (start >= end) return null
        
        val word = text.substring(start, end).trim()
        Log.d(TAG, "Extracted word at position $position: '$word' (bounds: $start-$end)")
        
        return if (word.isEmpty()) null else word
    }
    
    /**
     * Parse Yomichan structured content JSON to plain text
     * Filters out example sentences and keeps only actual definitions
     */
    private fun parseYomichanGloss(gloss: String): String {
        return try {
            // List of tags to exclude (examples, notes, metadata)
            val excludedTags = setOf(
                "details-entry-examples", "example-sentence", "example-sentence-a",
                "extra-info", "backlink", "usage-note", "note", "example",
                "details", "usage", "see-also", "related"
            )
            
            // Yomichan uses structured-content format with nested JSON
            // Extract just the text content, excluding examples
            if (gloss.startsWith("[") || gloss.startsWith("{")) {
                // Extract content, but skip excluded sections
                val parts = mutableListOf<String>()
                
                // Split by common delimiters and filter
                val segments = gloss.split(Regex("[;,]"))
                for (segment in segments) {
                    val trimmed = segment.trim()
                    
                    // Skip if it matches excluded tags
                    val isExcluded = excludedTags.any { tag -> 
                        trimmed.contains(tag, ignoreCase = true) ||
                        trimmed.contains("\"tag\":\"$tag\"", ignoreCase = true)
                    }
                    
                    if (isExcluded) {
                        continue
                    }
                    
                    // Extract "content" fields
                    val contentRegex = "\"content\":\"([^\"]+)\"".toRegex()
                    val matches = contentRegex.findAll(trimmed)
                    val extracted = matches.map { it.groupValues[1] }
                        .filter { it.isNotBlank() && !isLikelyExample(it) }
                        .toList()
                    
                    parts.addAll(extracted)
                }
                
                if (parts.isNotEmpty()) {
                    // Remove duplicates and join
                    parts.distinct().joinToString("; ")
                } else {
                    // Fallback: try to extract any plain text definitions
                    val plainTextRegex = "\"text\":\"([^\"]+)\"".toRegex()
                    val plainMatches = plainTextRegex.findAll(gloss)
                        .map { it.groupValues[1] }
                        .filter { !isLikelyExample(it) }
                        .toList()
                    
                    if (plainMatches.isNotEmpty()) {
                        plainMatches.distinct().joinToString("; ")
                    } else {
                        gloss // Fallback to original
                    }
                }
            } else {
                // Plain text gloss - filter if it looks like an example
                if (isLikelyExample(gloss)) {
                    "" // Return empty for examples
                } else {
                    gloss
                }
            }
        } catch (e: Exception) {
            gloss // Fallback to original on error
        }
    }
    
    /**
     * Check if text looks like an example sentence rather than a definition
     */
    private fun isLikelyExample(text: String): Boolean {
        if (text.isBlank()) return true
        
        // Too long (likely a full sentence example)
        if (text.length > 100) return true
        
        // Contains source language mixed with target language (e.g., "de forma triangular 삼각형 모양으로")
        // Spanish characters followed by Korean/Chinese characters
        if (text.matches(Regex(".*[a-zA-Z\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00d1\u00bf\u00a1]+.*[\u3131-\u3163\uAC00-\uD7A3\u4E00-\u9FFF]+.*"))) {
            return true
        }
        
        // Contains example markers
        val exampleMarkers = listOf(
            "example:", "e.g.", "ex:", "ejemplo:", "ej.:",
            "예:", "예문:", "용법:", "usage:"
        )
        val lowerText = text.lowercase()
        if (exampleMarkers.any { lowerText.contains(it) }) {
            return true
        }
        
        // Too many words (likely a sentence, not a definition)
        if (text.count { it == ' ' } > 8) return true
        
        return false
    }
    
    /**
     * Create a dictionary entry for words translated via API
     * This allows future lookups to have counters
     */
    private suspend fun createDictionaryEntryForApiTranslation(word: String, translation: String, sourceLang: String) {
        val activeDictionary = dictionaryManager.getActiveDictionary()
        if (activeDictionary == null) {
            Log.w(TAG, "No active dictionary, skipping entry creation for '$word'")
            return
        }
        
        // Generate unique entryId (use negative numbers for API-generated entries)
        val entryId = System.currentTimeMillis() * -1
        
        // Create basic structures
        val readingElements = listOf(
            ReadingElement(
                reading = word,
                noKanji = true // API translations are typically for alphabetic languages
            )
        )
        
        val senses = listOf(
            Sense(
                glosses = listOf(translation),
                partsOfSpeech = listOf("unknown"), // We don't know POS from API
                info = listOf("Auto-translated via API")
            )
        )
        
        val entry = DictionaryEntry(
            entryId = entryId,
            dictionaryId = activeDictionary.dictionaryId,  // Use dictionaryId (String), not id (Long)
            primaryExpression = null, // No kanji for API translations
            primaryReading = word,
            kanjiElements = emptyList(),
            readingElements = readingElements,
            senses = senses,
            frequency = 0,
            jlptLevel = null,
            isCommon = false,
            lookupCount = 1 // First lookup
        )
        
        try {
            // Insert the entry
            AppDatabase.getDatabase(this).dictionaryDao().insert(entry)
            Log.d(TAG, "✓ Created dictionary entry for API-translated word '$word' (entryId=$entryId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert dictionary entry for '$word'", e)
        }
    }
    
    /**
     * Render underlines for visible words (debounced for scrolling)
     */
    private fun renderUnderlines(event: AccessibilityEvent) {
        // Don't process our own app to avoid loops
        if (event.packageName == packageName) return
        
        val rootNode = rootInActiveWindow ?: event.source ?: return
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val visibleWords = TextExtractor.extractVisibleWords(rootNode)
                underlineRenderer.requestRender(visibleWords, dictionaryRepository)
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering underlines", e)
            } finally {
                if (rootNode != event.source) {
                    rootNode.recycle()
                }
            }
        }
    }
    
    /**
     * Force render underlines immediately (for screen changes)
     */
    private fun forceRenderUnderlines(event: AccessibilityEvent) {
        // Don't process our own app to avoid loops
        if (event.packageName == packageName) return
        
        val rootNode = rootInActiveWindow ?: event.source ?: return
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val visibleWords = TextExtractor.extractVisibleWords(rootNode)
                underlineRenderer.forceRender(visibleWords, dictionaryRepository)
            } catch (e: Exception) {
                Log.e(TAG, "Error force rendering underlines", e)
            } finally {
                if (rootNode != event.source) {
                    rootNode.recycle()
                }
            }
        }
    }

    /**
     * Update notification state (enabled/disabled)
     */
    fun updateNotificationState(enabled: Boolean) {
        isServiceEnabled = enabled
        sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        Log.d(TAG, "Service state updated: ${if (enabled) "ENABLED" else "DISABLED"}")
        showNotification()
    }
    
    /**
     * Update underline state (enabled/disabled)
     */
    fun updateUnderlineState(enabled: Boolean) {
        isUnderlineEnabled = enabled
        underlineRenderer.isEnabled = enabled
        sharedPreferences.edit().putBoolean(KEY_UNDERLINE_ENABLED, enabled).apply()
        Log.d(TAG, "Underline state updated: ${if (enabled) "ENABLED" else "DISABLED"}")
        
        // Clear underlines if disabled
        if (!enabled) {
            underlineRenderer.clearAllUnderlines()
        }
        
        showNotification()
    }
    
    private fun showNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create toggle service action
        val toggleIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_SERVICE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Create toggle underline action
        val toggleUnderlineIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_UNDERLINE
        }
        val toggleUnderlinePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleUnderlineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Native gestures are always enabled - no need for gesture intent
        
        val statusText = if (isServiceEnabled) {
            getString(R.string.notification_status_enabled)
        } else {
            getString(R.string.notification_status_disabled)
        }
        
        val toggleText = if (isServiceEnabled) {
            getString(R.string.notification_action_disable)
        } else {
            getString(R.string.notification_action_enable)
        }
        
        val underlineText = if (isUnderlineEnabled) "Underline: ON" else "Underline: OFF"
        
        val notification: Notification = NotificationCompat.Builder(this, DictionaryApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_dictionary)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (isServiceEnabled) R.drawable.ic_power_off else R.drawable.ic_power_on,
                toggleText,
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                underlineText,
                toggleUnderlinePendingIntent
            )
            // Native gestures are always enabled (API 30+) - no need for gesture toggle button
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    // onMotionEvent approach removed - using native Android gesture detection instead
    // Native gestures work better with touch exploration mode enabled
    
    override fun onInterrupt() {
        Log.w(TAG, "⚠️ SERVICE INTERRUPTED ⚠️")
        Log.w(TAG, "This may affect gesture detection and text selection monitoring")
    }
    
    /**
     * Handle gestures (deprecated method for API 30-33 compatibility)
     * This is the reliable method for API 33 devices
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        val gestureName = when (gestureId) {
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> "3-FINGER SWIPE RIGHT"
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> "3-FINGER SWIPE LEFT"
            AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> "3-FINGER SWIPE UP"
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP -> "3-FINGER TAP"
            else -> "UNKNOWN($gestureId)"
        }
        
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "GESTURE DETECTED (deprecated method): $gestureName")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        handleGesture(gestureId)
        
        // CRITICAL: Return false to not consume the gesture
        // This allows touch events to pass through to the app underneath
        return false
    }
    
    /**
     * Handle gestures (new method for API 30+)
     * This method is called on newer devices
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        val gestureId = gestureEvent.gestureId
        val gestureName = when (gestureId) {
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> "3-FINGER SWIPE RIGHT"
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> "3-FINGER SWIPE LEFT"
            AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> "3-FINGER SWIPE UP"
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP -> "3-FINGER TAP"
            else -> "UNKNOWN($gestureId)"
        }
        
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "GESTURE DETECTED (new method): $gestureName")
        Log.i(TAG, "Display ID: ${gestureEvent.displayId}")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        handleGesture(gestureId)
        
        // CRITICAL: Return false to not consume the gesture
        // This allows touch events to pass through to the app underneath
        return false
    }
    
    /**
     * Common gesture handling logic
     */
    private fun handleGesture(gestureId: Int) {
        when (gestureId) {
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> {
                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 3-FINGER SWIPE RIGHT → OCR MODE        ║")
                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                activateOcrMode()
            }
            
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> {
                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 3-FINGER SWIPE LEFT → TOGGLE UNDERLINE ║")
                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                toggleUnderlineState()
            }
            
            AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> {
                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 3-FINGER SWIPE UP → TOGGLE ON/OFF      ║")
                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                toggleServiceState()
            }
            
            AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP -> {
                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                Log.i(TAG, "║ ✓ 3-FINGER TAP → TOGGLE ON/OFF           ║")
                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                toggleServiceState()
            }
            
            else -> {
                Log.d(TAG, "→ Unhandled gesture: ID=$gestureId")
            }
        }
    }
    
    /**
     * Handle accessibility button click
     * This is triggered when user taps the accessibility button in navigation bar
     * OR when they hold both volume buttons for 3 seconds
     */
    fun onAccessibilityButtonClicked() {
        Log.i(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.i(TAG, "║ 🎯 ACCESSIBILITY SHORTCUT ACTIVATED!                 ║")
        Log.i(TAG, "║    Launching OCR Mode...                             ║")
        Log.i(TAG, "╚═══════════════════════════════════════════════════════╝")
        
        // Activate OCR mode when accessibility button is pressed
        activateOcrMode()
    }
    
    /**
     * Handle availability change of accessibility button
     */
    fun onAccessibilityButtonAvailabilityChanged(available: Boolean) {
        if (available) {
            Log.i(TAG, "✅ Accessibility button is available")
        } else {
            Log.w(TAG, "⚠️ Accessibility button not available (might be used by another service)")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "══════════════════════════════════════════════════════")
        Log.w(TAG, "SERVICE DESTROYED")
        Log.w(TAG, "══════════════════════════════════════════════════════")
        
        // Unregister preference listener
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        
        serviceScope.cancel()
        overlayManager.cleanup() // Cleanup TTS and hide popup
        underlineRenderer.destroy()
        ocrSelectionOverlay.destroy()
        floatingButton.hide()
        
        isRunning = false
        serviceInstance = null
    }
    
    // ============ Gesture Actions ============
    
    /**
     * Take screenshot for OCR using AccessibilityService API (Android 11+)
     */
    private fun takeScreenshotForOcr(rect: Rect, callback: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            Log.e(TAG, "Screenshot API requires Android 11+")
            callback(null)
            return
        }
        
        try {
            Log.d(TAG, "Taking screenshot for rect: $rect")
            
            // Take full screenshot using AccessibilityService API
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                { it.run() },  // executor
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            
                            // Convert HardwareBuffer to Bitmap
                            val fullBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                ?: throw Exception("Failed to wrap hardware buffer")
                            
                            // Crop to selected rect
                            val croppedBitmap = Bitmap.createBitmap(
                                fullBitmap,
                                rect.left.coerceAtLeast(0),
                                rect.top.coerceAtLeast(0),
                                rect.width().coerceAtMost(fullBitmap.width - rect.left),
                                rect.height().coerceAtMost(fullBitmap.height - rect.top)
                            )
                            
                            hardwareBuffer.close()
                            fullBitmap.recycle()
                            
                            Log.d(TAG, "Screenshot captured successfully: ${croppedBitmap.width}x${croppedBitmap.height}")
                            callback(croppedBitmap)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot", e)
                            callback(null)
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot", e)
            callback(null)
        }
    }
    
    /**
     * Activate OCR mode - show selection overlay
     */
    private fun activateOcrMode() {
        Log.d(TAG, "Activating OCR mode")
        ocrSelectionOverlay.show()
    }
    
    /**
     * Toggle service enabled/disabled state via gesture
     */
    private fun toggleServiceState() {
        val newState = !isServiceEnabled
        Log.d(TAG, "Toggling service state via gesture: $isServiceEnabled -> $newState")
        
        isServiceEnabled = newState
        sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, newState).apply()
        updateNotificationState(newState)
    }
    
    /**
     * Toggle underline enabled/disabled state via gesture
     */
    private fun toggleUnderlineState() {
        val newState = !isUnderlineEnabled
        Log.d(TAG, "Toggling underline state via gesture: $isUnderlineEnabled -> $newState")
        
        // Use the centralized update method
        updateUnderlineState(newState)
    }
    
    // Native gesture detection (onGesture) is always enabled - no need for start/stop functions
}
