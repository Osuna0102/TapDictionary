package com.godtap.dictionary.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.godtap.dictionary.DictionaryApp
import com.godtap.dictionary.MainActivity
import com.godtap.dictionary.R
import com.godtap.dictionary.database.AppDatabase
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.repository.DictionaryRepository
import com.godtap.dictionary.tokenizer.JapaneseTokenizer
import com.godtap.dictionary.util.JapaneseTextDetector
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
    private val tokenizer = JapaneseTokenizer()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        overlayManager = OverlayManager(this)
        
        // Initialize database and repository
        val database = AppDatabase.getDatabase(this)
        dictionaryRepository = DictionaryRepository(database)
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
        
        val selectedText = extractSelectedText(event)
        
        if (selectedText != null && selectedText.isNotBlank()) {
            Log.d(TAG, "Text selected: $selectedText")
            
            // Check if it contains Japanese
            if (JapaneseTextDetector.containsJapanese(selectedText)) {
                Log.d(TAG, "Japanese text detected: $selectedText")
                processJapaneseText(selectedText, event)
            }
        }
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        // For future implementation: detect tap on text
        if (event.packageName == packageName) return
        
        val text = event.text.firstOrNull()?.toString()
        if (text != null && JapaneseTextDetector.containsJapanese(text)) {
            Log.d(TAG, "Clicked on Japanese text: $text")
        }
    }
    
    private fun handleViewLongClicked(event: AccessibilityEvent) {
        // Handle long click for potential text selection
        Log.d(TAG, "Long click detected")
    }
    
    private fun extractSelectedText(event: AccessibilityEvent): String? {
        // Try to get text from event
        val text = event.text.firstOrNull()?.toString() ?: event.contentDescription?.toString()
        
        if (text != null) {
            val start = event.fromIndex
            val end = event.toIndex
            
            // If we have valid selection indices
            if (start >= 0 && end > start && end <= text.length) {
                return text.substring(start, end)
            }
            
            // If no valid indices, return the whole text if it's short
            if (text.length <= 50) {
                return text
            }
        }
        
        // Try to get text from source node
        event.source?.let { node ->
            val nodeText = extractTextFromNode(node)
            node.recycle()
            return nodeText
        }
        
        return null
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
    
    private fun processJapaneseText(text: String, event: AccessibilityEvent) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Processing Japanese text: $text")
                
                // Tokenize the text
                val tokens = tokenizer.tokenizeSmarter(text)
                Log.d(TAG, "Generated ${tokens.size} tokens: ${tokens.take(10)}")
                
                // Search for dictionary entry
                val entry = dictionaryRepository.searchMultiple(tokens)
                
                if (entry != null) {
                    Log.d(TAG, "Found entry: ${entry.kanji ?: entry.reading} -> ${entry.glosses}")
                    
                    // Format the word display
                    val wordDisplay = if (entry.kanji != null) {
                        "${entry.kanji} (${entry.reading})"
                    } else {
                        entry.reading
                    }
                    
                    // Format the translation
                    val translation = buildString {
                        entry.partOfSpeech?.let { append("[$it]\n") }
                        append(entry.glosses)
                    }
                    
                    overlayManager.showPopup(wordDisplay, translation)
                } else {
                    Log.d(TAG, "No dictionary entry found for: $text")
                    overlayManager.showPopup(text, getString(R.string.popup_no_translation))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing text", e)
                overlayManager.showPopup(text, "Error: ${e.message}")
            }
        }
    }           // Later we'll add dictionary lookup
    
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
