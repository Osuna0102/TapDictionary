package com.godtap.dictionary.overlay

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.godtap.dictionary.R
import com.godtap.dictionary.util.TtsManager
import com.godtap.dictionary.util.RomajiConverter
import com.google.android.flexbox.FlexboxLayout

class OverlayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OverlayManager"
        private const val AUTO_DISMISS_DELAY = 30000L // 30 seconds for testing
        private const val TTS_PREFS_NAME = "tts_settings"
        private const val MAX_POPUP_DEPTH = 2 // Maximum nested popups
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    private val ttsManager = TtsManager(context)
    private val ttsPrefs: SharedPreferences = context.getSharedPreferences(TTS_PREFS_NAME, Context.MODE_PRIVATE)
    
    // Store current word and language for TTS
    private var currentWord: String = ""
    private var currentLanguage: String = "ja" // Default to Japanese
    
    // Track popup depth to prevent infinite recursion
    private var currentPopupDepth = 0
    
    // Track last phrase for "Back to Phrase" navigation
    private var lastPhraseLookup: String? = null
    private var lastPhraseLanguage: String? = null
    private var lastPhraseDepth: Int = 0
    
    /**
     * Show popup immediately with loading indicator
     * This will be updated with actual content when lookup completes
     */
    fun showLoadingPopup(word: String, x: Int = -1, y: Int = -1, sourceLanguage: String = "ja") {
        Log.d(TAG, "showLoadingPopup() called for: $word, language: $sourceLanguage")
        
        // Store word and language for TTS
        currentWord = word
        currentLanguage = sourceLanguage
        Log.d(TAG, "Stored currentLanguage as: $currentLanguage")
        
        handler.post {
            try {
                // Remove existing popup if any
                hidePopupInternal()
                
                // Inflate the popup view
                val themedContext = ContextThemeWrapper(context, R.style.Theme_GodTapDictionary)
                val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set loading content
                view.findViewById<TextView>(R.id.wordText).text = word
                view.findViewById<TextView>(R.id.translationText).text = "🔍 Searching..."
                view.findViewById<TextView>(R.id.lookupCountText).text = "..."
                
                // Disable speaker button while loading
                view.findViewById<View>(R.id.speakerButton)?.apply {
                    isEnabled = false
                    alpha = 0.5f
                }
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Dismiss on background click
                view.setOnClickListener {
                    hidePopup()
                }
                
                // Don't dismiss when clicking the card itself
                view.findViewById<View>(R.id.popupCard)?.setOnClickListener {
                    // Consume click - don't dismiss
                }
                
                // Create layout params
                val params = createLayoutParams(x, y)
                
                // Add view to window
                windowManager.addView(view, params)
                overlayView = view
                
                Log.d(TAG, "Loading popup shown for: $word")
                
                // Auto dismiss after delay (longer for loading)
                scheduleAutoDismiss()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing loading popup", e)
            }
        }
    }
    
    fun showPopup(
        word: String, 
        translation: String, 
        lookupCount: Int = 0, 
        x: Int = -1, 
        y: Int = -1, 
        sourceLanguage: String = "ja",
        fullSentence: String? = null,
        depth: Int = 0,
        partOfSpeech: String? = null,
        example: String? = null,
        sense: com.godtap.dictionary.database.Sense? = null  // NEW: Full sense object with all data
    ) {
        Log.d(TAG, "showPopup() called for: $word (count: $lookupCount), language: $sourceLanguage, depth: $depth, hasEnhancedData: ${sense != null}")
        
        // Check depth limit
        if (depth >= MAX_POPUP_DEPTH) {
            Log.w(TAG, "Max popup depth ($MAX_POPUP_DEPTH) reached, ignoring")
            return
        }
        
        currentPopupDepth = depth
        
        // Store word and language for TTS
        currentWord = word
        currentLanguage = sourceLanguage
        Log.d(TAG, "Stored currentLanguage as: $currentLanguage")
        
        handler.post {
            try {
                // Check if we have an existing view to update
                val existingView = overlayView
                
                if (existingView != null) {
                    // Update existing popup instead of creating a new one
                    Log.d(TAG, "Updating existing popup with translation")
                    existingView.findViewById<TextView>(R.id.wordText).text = word
                    existingView.findViewById<TextView>(R.id.translationText).text = translation
                    
                    // Update lookup count badge
                    val countBadge = existingView.findViewById<TextView>(R.id.lookupCountText)
                    countBadge.text = when {
                        lookupCount == 0 -> "New word"
                        lookupCount == 1 -> "1x looked up"
                        else -> "${lookupCount}x looked up"
                    }
                    
                    // Update part of speech badge
                    val posBadge = existingView.findViewById<TextView>(R.id.partOfSpeechBadge)
                    if (!partOfSpeech.isNullOrBlank()) {
                        posBadge.text = partOfSpeech
                        posBadge.visibility = View.VISIBLE
                    } else {
                        posBadge.visibility = View.GONE
                    }
                    
                    // Update example section
                    val exampleSection = existingView.findViewById<LinearLayout>(R.id.exampleSection)
                    val exampleText = existingView.findViewById<TextView>(R.id.exampleText)
                    if (!example.isNullOrBlank()) {
                        exampleText.text = example
                        exampleSection.visibility = View.VISIBLE
                    } else {
                        exampleSection.visibility = View.GONE
                    }
                    
                    // Enable speaker button
                    existingView.findViewById<View>(R.id.speakerButton)?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        setOnClickListener {
                            Log.d(TAG, "TTS button clicked for: $currentWord in $currentLanguage")
                            
                            // Load saved voice preference for this language
                            val savedVoice = ttsPrefs.getString("voice_$currentLanguage", null)
                            if (savedVoice != null) {
                                ttsManager.setVoice(savedVoice)
                            }
                            
                            val success = ttsManager.speak(currentWord, currentLanguage)
                            if (!success) {
                                Log.w(TAG, "TTS failed or not available")
                            }
                        }
                    }
                    
                    // Reset auto-dismiss timer
                    scheduleAutoDismiss()
                    return@post
                }
                
                // No existing view - create new popup
                // Inflate the popup view with themed context to resolve theme attributes
                val themedContext = ContextThemeWrapper(context, R.style.Theme_GodTapDictionary)
                val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set content with romaji for Japanese words
                val displayText = if (sourceLanguage == "ja" && word.contains("(") && word.contains(")")) {
                    // Format: "食べる (たべる)" -> "食べる たべる (taberu)"
                    val hiragana = RomajiConverter.extractHiragana(word)
                    val romaji = hiragana?.let { RomajiConverter.toRomaji(it) }
                    
                    if (hiragana != null && romaji != null) {
                        val kanji = word.substringBefore("(").trim()
                        "$kanji $hiragana ($romaji)"
                    } else {
                        word
                    }
                } else {
                    word
                }
                
                view.findViewById<TextView>(R.id.wordText).text = displayText
                view.findViewById<TextView>(R.id.translationText).text = translation
                
                // Set lookup count badge
                val countBadge = view.findViewById<TextView>(R.id.lookupCountText)
                countBadge.text = when {
                    lookupCount == 0 -> "New word"
                    lookupCount == 1 -> "1x looked up"
                    else -> "${lookupCount}x looked up"
                }
                
                // Set part of speech badge
                val posBadge = view.findViewById<TextView>(R.id.partOfSpeechBadge)
                if (!partOfSpeech.isNullOrBlank()) {
                    posBadge.text = partOfSpeech
                    posBadge.visibility = View.VISIBLE
                } else {
                    posBadge.visibility = View.GONE
                }
                
                // Set example section
                val exampleSection = view.findViewById<LinearLayout>(R.id.exampleSection)
                val exampleText = view.findViewById<TextView>(R.id.exampleText)
                if (!example.isNullOrBlank()) {
                    exampleText.text = example
                    exampleSection.visibility = View.VISIBLE
                } else {
                    exampleSection.visibility = View.GONE
                }
                
                // ENHANCED: Populate ALL data sections if sense object is provided
                if (sense != null) {
                    populateEnhancedData(view, sense)
                } else {
                    // Hide all enhanced sections if no data
                    view.findViewById<View>(R.id.examplesContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.notesContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.referencesContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.antonymsContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.etymologyContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.toggleInfoButton)?.visibility = View.GONE
                }
                
                // Show clickable sentence section if fullSentence is provided
                val sentenceSection = view.findViewById<LinearLayout>(R.id.sentenceSection)
                if (fullSentence != null && fullSentence.isNotBlank() && depth < MAX_POPUP_DEPTH) {
                    Log.d(TAG, "Showing sentence section with: $fullSentence (depth: $depth)")
                    sentenceSection?.visibility = View.VISIBLE
                    val sentenceContainer = view.findViewById<LinearLayout>(R.id.sentenceContainer)
                    sentenceContainer?.removeAllViews()
                    
                    // Tokenize and create clickable words
                    val words = tokenizeSentence(fullSentence, sourceLanguage)
                    Log.d(TAG, "Adding clickable sentence with ${words.size} words")
                    
                    for (wordText in words) {
                        if (wordText.isBlank()) continue
                        
                        val wordView = TextView(context).apply {
                            text = wordText
                            textSize = 16f
                            setPadding(8, 6, 8, 6)
                            
                            // Make it look clickable but NOT like a web link
                            // Subtle gray text that gets darker on press
                            setTextColor(context.getColor(android.R.color.black))
                            textSize = 17f
                            
                            // Add ripple effect for feedback
                            val outValue = TypedValue()
                            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                            setBackgroundResource(outValue.resourceId)
                            
                            setOnClickListener {
                                Log.d(TAG, "Word clicked in sentence: $wordText, depth: $depth")
                                onWordClickCallback?.invoke(wordText, sourceLanguage, depth + 1)
                            }
                        }
                        
                        sentenceContainer?.addView(wordView)
                    }
                } else {
                    sentenceSection?.visibility = View.GONE
                }
                
                // Speaker button for TTS
                view.findViewById<View>(R.id.speakerButton)?.apply {
                    isEnabled = true
                    alpha = 1.0f
                    setOnClickListener {
                        Log.d(TAG, "TTS button clicked for: $currentWord in $currentLanguage")
                        
                        // Load saved voice preference for this language
                        val savedVoice = ttsPrefs.getString("voice_$currentLanguage", null)
                        if (savedVoice != null) {
                            ttsManager.setVoice(savedVoice)
                        }
                        
                        val success = ttsManager.speak(currentWord, currentLanguage)
                        if (!success) {
                            Log.w(TAG, "TTS failed or not available")
                        }
                    }
                }
                
                // Copy button to copy word to clipboard
                view.findViewById<View>(R.id.copyButton)?.apply {
                    setOnClickListener {
                        Log.d(TAG, "Copy button clicked for: $currentWord")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Translated word", currentWord)
                        clipboard.setPrimaryClip(clip)
                        
                        // Show brief feedback (you could also use a Toast)
                        android.widget.Toast.makeText(context, "Copied: $currentWord", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Toggle info button to show/hide enhanced sections
                view.findViewById<View>(R.id.toggleInfoButton)?.apply {
                    var enhancedSectionsVisible = true
                    setOnClickListener {
                        enhancedSectionsVisible = !enhancedSectionsVisible
                        Log.d(TAG, "Toggle info button clicked, showing: $enhancedSectionsVisible")
                        
                        val visibility = if (enhancedSectionsVisible) View.VISIBLE else View.GONE
                        view.findViewById<View>(R.id.examplesContainer)?.visibility = visibility
                        view.findViewById<View>(R.id.notesContainer)?.visibility = visibility
                        view.findViewById<View>(R.id.referencesContainer)?.visibility = visibility
                        view.findViewById<View>(R.id.antonymsContainer)?.visibility = visibility
                        view.findViewById<View>(R.id.etymologyContainer)?.visibility = visibility
                        
                        // Change icon tint to indicate state
                        val tint = if (enhancedSectionsVisible) 
                            context.getColor(android.R.color.holo_blue_dark) 
                        else 
                            context.getColor(android.R.color.darker_gray)
                        (this as? android.widget.ImageView)?.setColorFilter(tint)
                    }
                }
                
                // Add "Back to Phrase" button if we came from a phrase lookup
                if (depth > 0 && lastPhraseLookup != null) {
                    // Show a back button in the translation text area
                    val translationText = view.findViewById<TextView>(R.id.translationText)
                    val currentTranslation = translationText.text
                    
                    val backButton = android.widget.Button(context).apply {
                        text = "← Back to Phrase"
                        setOnClickListener {
                            Log.d(TAG, "Back button clicked, returning to phrase")
                            lastPhraseLookup?.let { phrase ->
                                showClickableSentence(phrase, lastPhraseLanguage ?: sourceLanguage, lastPhraseDepth)
                            }
                        }
                    }
                    
                    // Insert back button before the translation text
                    val parent = translationText.parent as? android.view.ViewGroup
                    parent?.let { viewGroup ->
                        val index = viewGroup.indexOfChild(translationText)
                        viewGroup.addView(backButton, index)
                    }
                }
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Detect clicks on transparent background (outside the card)
                view.setOnClickListener {
                    // Click on transparent background - dismiss
                    hidePopup()
                }
                
                // Don't dismiss when clicking the card itself
                view.findViewById<View>(R.id.popupCard)?.setOnClickListener {
                    // Consume click - don't dismiss
                    // This prevents the click from bubbling to parent view
                }
                
                // Create layout params - position BELOW selected text or at specified coordinates
                val params = createLayoutParams(x, y)
                
                // Add view to window
                windowManager.addView(view, params)
                overlayView = view
                
                Log.d(TAG, "Popup shown: $word -> $translation")
                
                // Auto dismiss after delay
                scheduleAutoDismiss()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing popup", e)
            }
        }
    }
    
    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (x >= 0 && y >= 0) {
                // Position at top-right of the tap/selection coordinates
                gravity = Gravity.TOP or Gravity.START
                // Convert dp to pixels for consistent positioning
                val offsetDp = 8 // 8dp offset from selection
                val scale = context.resources.displayMetrics.density
                val offsetPx = (offsetDp * scale + 0.5f).toInt()
                
                // Position at top-right with small offset
                this.x = x + offsetPx
                this.y = y - offsetPx // Slightly above to avoid covering selection
            } else {
                // Default centered position
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.x = 0
                this.y = 300
            }
        }
    }
    
    fun hidePopup() {
        Log.d(TAG, "hidePopup() called")
        handler.post {
            hidePopupInternal()
        }
    }
    
    private fun hidePopupInternal() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                currentPopupDepth = 0 // Reset depth
                Log.d(TAG, "Popup hidden, depth reset")
            }
            // Stop TTS when popup is dismissed
            ttsManager.stop()
            cancelAutoDismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding popup", e)
        }
    }
    
    private fun scheduleAutoDismiss() {
        cancelAutoDismiss()
        autoDismissRunnable = Runnable {
            hidePopup()
        }.also {
            handler.postDelayed(it, AUTO_DISMISS_DELAY)
        }
    }
    
    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let {
            handler.removeCallbacks(it)
            autoDismissRunnable = null
        }
    }
    
    fun isShowing(): Boolean = overlayView != null
    
    // Callback for word lookup from clickable sentence - set by service
    var onWordClickCallback: ((String, String, Int) -> Unit)? = null
    
    /**
     * Show popup with clickable sentence when tap position is unknown
     * Each word in the sentence is clickable and underlined
     * Depth prevents infinite recursion (max 2 levels)
     */
    fun showClickableSentence(
        sentence: String,
        sourceLanguage: String = "ja",
        depth: Int = 0
    ) {
        Log.d(TAG, "showClickableSentence() called, depth: $depth, sentence: $sentence")
        
        // Check depth limit
        if (depth >= MAX_POPUP_DEPTH) {
            Log.w(TAG, "Max popup depth ($MAX_POPUP_DEPTH) reached, showing normal popup")
            showPopup("...", "Maximum popup depth reached", depth = depth)
            return
        }
        
        currentPopupDepth = depth
        currentLanguage = sourceLanguage
        
        // Store the sentence for navigation back
        lastPhraseLookup = sentence
        lastPhraseLanguage = sourceLanguage
        lastPhraseDepth = depth
        
        handler.post {
            try {
                // Remove existing popup if any
                hidePopupInternal()
                
                // Use the SAME layout as normal word popup for consistency
                val themedContext = ContextThemeWrapper(context, R.style.Theme_GodTapDictionary)
                val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_dictionary_popup, null)
                
                // Set short title
                view.findViewById<TextView>(R.id.wordText).text = "Tap Phrase"
                view.findViewById<TextView>(R.id.lookupCountText).visibility = View.GONE
                
                // Set the sentence as main content with clickable spans
                val translationTextView = view.findViewById<TextView>(R.id.translationText)
                val words = tokenizeSentence(sentence, sourceLanguage)
                
                translationTextView.apply {
                    // Create spannable text
                    val spannableText = android.text.SpannableString(sentence)
                    
                    // Add click spans to tokenized words
                    var currentIndex = 0
                    
                    words.forEach { word ->
                        if (word.isBlank()) {
                            currentIndex += word.length
                            return@forEach
                        }
                        
                        // Find word position in original sentence
                        val start = sentence.indexOf(word, currentIndex)
                        if (start >= 0) {
                            val end = start + word.length
                            
                            // Create clickable span
                            val clickSpan = object : android.text.style.ClickableSpan() {
                                override fun onClick(widget: View) {
                                    Log.d(TAG, "Word clicked in phrase: $word, depth: $depth")
                                    onWordClickCallback?.invoke(word, sourceLanguage, depth + 1)
                                }
                                
                                override fun updateDrawState(ds: android.text.TextPaint) {
                                    // Make it bold, black, no underline
                                    ds.color = android.graphics.Color.BLACK
                                    ds.isUnderlineText = false
                                    ds.isFakeBoldText = true
                                }
                            }
                            
                            spannableText.setSpan(
                                clickSpan,
                                start,
                                end,
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            
                            currentIndex = end
                        }
                    }
                    
                    text = spannableText
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    textSize = 18f
                    setTextColor(android.graphics.Color.BLACK)
                }
                
                // Hide sentence section (we're using the main content area)
                view.findViewById<LinearLayout>(R.id.sentenceSection)?.visibility = View.GONE
                
                // Hide TTS button for phrase view
                view.findViewById<View>(R.id.speakerButton)?.visibility = View.GONE
                
                // Close button
                view.findViewById<View>(R.id.closeButton).setOnClickListener {
                    hidePopup()
                }
                
                // Dismiss on background click
                view.setOnClickListener {
                    hidePopup()
                }
                
                // Don't dismiss when clicking the card itself
                view.findViewById<View>(R.id.popupCard)?.setOnClickListener {
                    // Consume click
                }
                
                // Create layout params - centered
                val params = createLayoutParams(-1, -1)
                
                // Add view to window
                windowManager.addView(view, params)
                overlayView = view
                
                Log.d(TAG, "Clickable sentence popup shown with ${words.size} words")
                
                // Auto dismiss after delay
                scheduleAutoDismiss()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing clickable sentence popup", e)
            }
        }
    }
    
    /**
     * Populate enhanced data sections (examples, notes, references, etc.)
     */
    private fun populateEnhancedData(view: View, sense: com.godtap.dictionary.database.Sense) {
        var hasAnyEnhancedData = false
        
        // Examples
        if (sense.examples.isNotEmpty()) {
            hasAnyEnhancedData = true
            val examplesContainer = view.findViewById<LinearLayout>(R.id.examplesContainer)
            val examplesList = view.findViewById<LinearLayout>(R.id.examplesList)
            val examplesTtsButton = view.findViewById<android.widget.ImageView>(R.id.examplesTtsButton)
            examplesContainer?.visibility = View.VISIBLE
            examplesList?.removeAllViews()
            
            Log.d(TAG, "Populating ${sense.examples.size} examples")
            
            // Set up TTS button for examples - reads first example
            examplesTtsButton?.setOnClickListener {
                val firstExample = sense.examples.firstOrNull()
                if (firstExample != null) {
                    Log.d(TAG, "Example TTS clicked: ${firstExample.source}")
                    val success = ttsManager.speak(firstExample.source, currentLanguage)
                    if (!success) {
                        Log.w(TAG, "TTS failed for example")
                    }
                }
            }
            
            for ((index, example) in sense.examples.take(3).withIndex()) {  // Limit to 3 examples
                val exampleView = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, if (index > 0) 12 else 0, 0, 0)
                }
                
                // Source sentence (Japanese, etc.)
                val sourceText = TextView(context).apply {
                    text = example.source
                    textSize = 14f
                    setTextColor(context.getColor(android.R.color.black))
                }
                exampleView.addView(sourceText)
                
                // Translation (English, etc.)
                val translationText = TextView(context).apply {
                    text = "→ ${example.translation}"
                    textSize = 13f
                    setTextColor(context.getColor(android.R.color.darker_gray))
                    setPadding(0, 4, 0, 0)
                }
                exampleView.addView(translationText)
                
                examplesList?.addView(exampleView)
            }
        } else {
            view.findViewById<View>(R.id.examplesContainer)?.visibility = View.GONE
        }
        
        // Notes
        if (sense.notes.isNotEmpty()) {
            hasAnyEnhancedData = true
            val notesContainer = view.findViewById<LinearLayout>(R.id.notesContainer)
            val notesList = view.findViewById<LinearLayout>(R.id.notesList)
            notesContainer?.visibility = View.VISIBLE
            notesList?.removeAllViews()
            
            Log.d(TAG, "Populating ${sense.notes.size} notes")
            for (note in sense.notes) {
                val noteText = TextView(context).apply {
                    text = "• $note"
                    textSize = 13f
                    setTextColor(context.getColor(android.R.color.darker_gray))
                    setPadding(0, 0, 0, 8)
                }
                notesList?.addView(noteText)
            }
        } else {
            view.findViewById<View>(R.id.notesContainer)?.visibility = View.GONE
        }
        
        // References
        if (sense.references.isNotEmpty()) {
            hasAnyEnhancedData = true
            val referencesContainer = view.findViewById<LinearLayout>(R.id.referencesContainer)
            val referencesList = view.findViewById<FlexboxLayout>(R.id.referencesList)
            referencesContainer?.visibility = View.VISIBLE
            referencesList?.removeAllViews()
            
            Log.d(TAG, "Populating ${sense.references.size} references")
            for (ref in sense.references.take(8)) {  // Limit to 8 references
                val refChip = TextView(context).apply {
                    text = ref.text
                    textSize = 12f
                    setTextColor(context.getColor(android.R.color.holo_blue_dark))
                    setPadding(12, 6, 12, 6)
                    background = context.getDrawable(R.drawable.badge_background)
                    
                    // Make clickable if we have href
                    if (ref.href.isNotBlank()) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            // Look up the referenced word
                            onWordClickCallback?.invoke(ref.text, currentLanguage, currentPopupDepth + 1)
                        }
                    }
                }
                
                val layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 8)
                }
                refChip.layoutParams = layoutParams
                
                referencesList?.addView(refChip)
            }
        } else {
            view.findViewById<View>(R.id.referencesContainer)?.visibility = View.GONE
        }
        
        // Antonyms
        if (sense.antonyms.isNotEmpty()) {
            hasAnyEnhancedData = true
            val antonymsContainer = view.findViewById<LinearLayout>(R.id.antonymsContainer)
            val antonymsList = view.findViewById<FlexboxLayout>(R.id.antonymsList)
            antonymsContainer?.visibility = View.VISIBLE
            antonymsList?.removeAllViews()
            
            Log.d(TAG, "✓ Populating ${sense.antonyms.size} antonyms")
            for (ant in sense.antonyms) {
                Log.d(TAG, "  - Antonym: ${ant.text} (href: ${ant.href})")
                val antChip = TextView(context).apply {
                    text = ant.text
                    textSize = 12f
                    setTextColor(context.getColor(android.R.color.holo_red_dark))
                    setPadding(12, 6, 12, 6)
                    background = context.getDrawable(R.drawable.badge_background)
                    
                    // Make clickable
                    if (ant.href.isNotBlank()) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            onWordClickCallback?.invoke(ant.text, currentLanguage, currentPopupDepth + 1)
                        }
                    }
                }
                
                val layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 8)
                }
                antChip.layoutParams = layoutParams
                
                antonymsList?.addView(antChip)
            }
        } else {
            view.findViewById<View>(R.id.antonymsContainer)?.visibility = View.GONE
        }
        
        // Etymology / Source Languages
        if (sense.sourceLanguages.isNotEmpty()) {
            hasAnyEnhancedData = true
            val etymologyContainer = view.findViewById<LinearLayout>(R.id.etymologyContainer)
            val etymologyText = view.findViewById<TextView>(R.id.etymologyText)
            etymologyContainer?.visibility = View.VISIBLE
            etymologyText?.text = sense.sourceLanguages.joinToString("\n")
            Log.d(TAG, "Populating etymology: ${sense.sourceLanguages.joinToString()}")
        } else {
            view.findViewById<View>(R.id.etymologyContainer)?.visibility = View.GONE
        }
        
        // Show/hide toggle button based on whether we have any enhanced data
        val toggleButton = view.findViewById<android.widget.ImageView>(R.id.toggleInfoButton)
        if (hasAnyEnhancedData) {
            toggleButton?.visibility = View.VISIBLE
            // Set initial state - blue tint indicates sections are visible
            toggleButton?.setColorFilter(context.getColor(android.R.color.holo_blue_dark))
            Log.d(TAG, "Enhanced data populated, toggle button enabled")
        } else {
            toggleButton?.visibility = View.GONE
        }
    }
    
    /**
     * Simple word tokenization for clickable sentence
     * Splits on whitespace and common punctuation
     */
    private fun tokenizeSentence(sentence: String, language: String): List<String> {
        return when (language) {
            "ja" -> {
                // Japanese: treat each character as potential word
                // Or split by particles/common separators
                sentence.chunked(1) // Simple char-by-char for now
            }
            else -> {
                // Other languages: split on whitespace and punctuation
                sentence.split(Regex("[\\s、。，．,.\\u200b]+"))
                    .filter { it.isNotBlank() }
            }
        }
    }
    
    /**
     * Cleanup resources
     * Call this when the service is destroyed
     */
    fun cleanup() {
        hidePopup()
        ttsManager.shutdown()
    }
}
