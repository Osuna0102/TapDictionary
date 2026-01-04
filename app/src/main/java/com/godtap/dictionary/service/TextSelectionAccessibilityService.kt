package com.godtap.dictionary.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.godtap.dictionary.DictionaryApp
import com.godtap.dictionary.MainActivity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TextSelectionAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TextSelectionService"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        
        var isRunning = false
            private set
        
        private var serviceInstance: TextSelectionAccessibilityService? = null
        
        /**
         * Update notification from external components (e.g., BroadcastReceiver)
         */
        fun updateNotification(context: Context, enabled: Boolean) {
            serviceInstance?.updateNotificationState(enabled)
        }
    }
    
    private lateinit var overlayManager: OverlayManager
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var translationService: TranslationService
    private lateinit var sharedPreferences: SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastProcessedText: String? = null
    private var lastProcessedTime: Long = 0
    private var isServiceEnabled: Boolean = true
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        serviceInstance = this
        overlayManager = OverlayManager(this)
        
        // Initialize database and repository
        val database = AppDatabase.getDatabase(this)
        dictionaryRepository = DictionaryRepository(database)
        // DictionaryLookup will be created per-lookup with the appropriate language
        dictionaryManager = DictionaryManager(this)
        translationService = TranslationService()
        
        // Load preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isServiceEnabled = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, true)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "Service connected")
        showNotification()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // Check if service is enabled
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled, ignoring event")
            return
        }
        
        // Log all events for debugging
        Log.d(TAG, "Event: ${event.eventType}, Package: ${event.packageName}, Class: ${event.className}")
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelectionChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleViewLongClicked(event)
            }
        }
    }
    
    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        // Don't process if from our own app
        if (event.packageName == packageName) return
        
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
                        processTextFromPosition(fullText, tapPosition, sourceLang)
                    } else {
                        Log.d(TAG, "⊘ Could not determine tap position, scanning from start")
                        processTextFromPosition(fullText, 0, sourceLang)
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
            Log.e(TAG, "Error determining tap position", e)
            return -1
        }
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
                    node.getChild(i)?.let { child ->
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
        node.text?.toString()?.let { return it }
        
        // Try content description
        node.contentDescription?.toString()?.let { return it }
        
        // Try to find selected text from children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
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
     * Process text starting from a specific character position
     * This is critical: scan from WHERE the user tapped, not from position 0!
     * @param text The full text
     * @param startPosition Position where user tapped
     * @param languageCode ISO 639-1 language code (ja, es, ko, etc.)
     */
    private fun processTextFromPosition(text: String, startPosition: Int, languageCode: String, x: Int = -1, y: Int = -1) {
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
                    overlayManager.showPopup(wordDisplay, translation, entry.lookupCount + 1, x, y)
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
                        overlayManager.showPopup(textToLookup, translation, 1, x, y)
                    } else {
                        Log.d(TAG, "⊘ Fallback translation failed")
                        overlayManager.showPopup(textToLookup, getString(R.string.popup_no_translation), 0, x, y)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing text", e)
                overlayManager.showPopup(text, "Error: ${e.message}", 0, x, y)
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
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceInstance = null
        serviceScope.cancel()
        overlayManager.hidePopup()
        Log.d(TAG, "Service destroyed")
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
    
    private fun showNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create toggle action
        val toggleIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_SERVICE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
}
