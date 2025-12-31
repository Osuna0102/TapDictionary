package com.godtap.dictionary.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.godtap.dictionary.DictionaryApp
import com.godtap.dictionary.MainActivity
import com.godtap.dictionary.R
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.lookup.DictionaryLookup
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.repository.DictionaryRepository
import com.godtap.dictionary.util.JapaneseTextDetector
import com.godtap.dictionary.util.LanguageDetector
import com.godtap.dictionary.manager.DictionaryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TextSelectionAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TextSelectionService"
        private const val NOTIFICATION_ID = 1001
        var isRunning = false
            private set
    }
    
    private lateinit var overlayManager: OverlayManager
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var dictionaryManager: DictionaryManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastProcessedText: String? = null
    private var lastProcessedTime: Long = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        overlayManager = OverlayManager(this)
        
        // Initialize database and repository
        val database = AppDatabase.getDatabase(this)
        dictionaryRepository = DictionaryRepository(database)
        // DictionaryLookup will be created per-lookup with the appropriate language
        dictionaryManager = DictionaryManager(this)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d(TAG, "Service connected")
        showNotification()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
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
        // For future implementation: detect tap on text
        if (event.packageName == packageName) return
        
        val text = event.text.firstOrNull()?.toString()
        if (text != null) {
            val detectedLang = LanguageDetector.detectLanguage(text)
            if (detectedLang != null) {
                Log.d(TAG, "Clicked on $detectedLang text: $text")
            }
        }
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
    private fun processTextFromPosition(text: String, startPosition: Int, languageCode: String) {
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
                        if (partsOfSpeech.isNotEmpty()) {
                            append("[${partsOfSpeech.first()}]\n")
                        }
                        append(glosses.joinToString("; "))
                    }
                    
                    overlayManager.showPopup(wordDisplay, translation)
                } else {
                    Log.d(TAG, "⊘ No dictionary entry found from position $startPosition: '$textToLookup'")
                    overlayManager.showPopup(textToLookup, getString(R.string.popup_no_translation))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing text", e)
                overlayManager.showPopup(text, "Error: ${e.message}")
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
     */
    private fun parseYomichanGloss(gloss: String): String {
        return try {
            // Yomichan uses structured-content format with nested JSON
            // Extract just the text content
            if (gloss.startsWith("[") || gloss.startsWith("{")) {
                // Try to extract "content" fields
                val contentRegex = "\"content\":\"([^\"]+)\"".toRegex()
                val matches = contentRegex.findAll(gloss)
                val extracted = matches.map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
                if (extracted.isNotEmpty()) {
                    extracted.joinToString("; ")
                } else {
                    gloss // Fallback to original
                }
            } else {
                gloss // Plain text gloss
            }
        } catch (e: Exception) {
            gloss // Fallback to original on error
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        overlayManager.hidePopup()
        Log.d(TAG, "Service destroyed")
    }
    
    private fun showNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification: Notification = NotificationCompat.Builder(this, DictionaryApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_dictionary)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
}
