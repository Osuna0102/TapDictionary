package com.godtap.dictionary.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.repository.DictionaryRepository
import kotlinx.coroutines.launch

data class RecentLookup(
    val word: String,
    val reading: String,
    val translation: String,
    val count: Int
)

/**
 * Parse Yomichan structured content JSON to plain text
 */
private fun parseYomichanGloss(gloss: String): String {
    return try {
        if (gloss.startsWith("[") || gloss.startsWith("{")) {
            // Extract content fields from structured JSON
            val contentRegex = "\"content\":\"([^\"]+)\"".toRegex()
            val matches = contentRegex.findAll(gloss)
            val extracted = matches.map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            
            if (extracted.isNotEmpty()) {
                return extracted.joinToString("; ")
            }
            
            // Fallback: try to extract text fields
            val plainTextRegex = "\"text\":\"([^\"]+)\"".toRegex()
            val plainMatches = plainTextRegex.findAll(gloss)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            
            if (plainMatches.isNotEmpty()) {
                return plainMatches.joinToString("; ")
            }
        }
        
        // Return as-is if not structured content
        gloss
    } catch (e: Exception) {
        gloss // Return original on error
    }
}

@Composable
fun HomeScreen(
    onTestPopup: (String, String) -> Unit,
    overlayManager: com.godtap.dictionary.overlay.OverlayManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var searchValue by remember { mutableStateOf("") }
    var fromLanguage by remember { mutableStateOf("ja") }
    var toLanguage by remember { mutableStateOf("en") }
    
    // Load real data from database
    var recentLookups by remember { mutableStateOf<List<RecentLookup>>(emptyList()) }
    
    val repository = remember { 
        val database = com.godtap.dictionary.database.AppDatabase.getDatabase(context)
        com.godtap.dictionary.repository.DictionaryRepository(database)
    }
    
    val dictionaryManager = remember {
        com.godtap.dictionary.manager.DictionaryManager(context)
    }
    
    // Load recent lookups on mount
    LaunchedEffect(Unit) {
        scope.launch {
            val entries = repository.getRecentLookups(10)
            recentLookups = entries.map { entry ->
                RecentLookup(
                    word = entry.primaryExpression ?: entry.primaryReading,
                    reading = entry.primaryReading,
                    translation = entry.getPrimaryGloss(),
                    count = entry.lookupCount
                )
            }
            
            // Get source language from active dictionary
            val activeDict = dictionaryManager.getActiveDictionary()
            if (activeDict != null) {
                fromLanguage = activeDict.sourceLanguage
                toLanguage = activeDict.targetLanguage
            }
        }
    }
    
    // Search function with API fallback - searches active dictionaries and triggers popup
    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        scope.launch {
            // Use DictionaryLookup to search active dictionaries
            val dictionaryLookup = com.godtap.dictionary.lookup.DictionaryLookup(repository, fromLanguage)
            val lookupResult = dictionaryLookup.lookup(query)
            
            android.util.Log.d("HomeScreen", "Search query: '$query', fromLanguage: '$fromLanguage'")
            android.util.Log.d("HomeScreen", "Lookup result: ${lookupResult != null}")
            
            if (lookupResult != null) {
                // Found in dictionary - trigger overlay popup
                val entry = lookupResult.entry
                val word = entry.primaryExpression ?: entry.primaryReading
                
                // Get translation with multiple fallbacks, parsing structured content
                val rawGlosses = entry.getAllGlosses()
                android.util.Log.d("HomeScreen", "Raw glosses count: ${rawGlosses.size}")
                rawGlosses.forEachIndexed { i, g -> 
                    android.util.Log.d("HomeScreen", "  Gloss $i: ${g.take(100)}")
                }
                
                // Parse Yomichan structured content
                val parsedGlosses = rawGlosses.map { parseYomichanGloss(it) }.filter { it.isNotBlank() }
                android.util.Log.d("HomeScreen", "Parsed glosses: ${parsedGlosses.joinToString("; ")}")
                
                val translation = when {
                    parsedGlosses.isNotEmpty() -> parsedGlosses.joinToString("; ")
                    rawGlosses.isNotEmpty() -> rawGlosses.joinToString("; ") // Fallback: use raw if parsing failed
                    entry.getPrimaryGloss().isNotBlank() -> parseYomichanGloss(entry.getPrimaryGloss())
                    entry.senses.isNotEmpty() -> {
                        // Fallback: extract from senses with parsing
                        entry.senses.joinToString("\n") { sense ->
                            val pos = if (sense.partsOfSpeech.isNotEmpty()) 
                                "[${sense.partsOfSpeech.joinToString(", ")}] " 
                            else ""
                            val gloss = sense.glosses.firstOrNull()?.let { parseYomichanGloss(it) } ?: "No definition"
                            "$pos$gloss"
                        }
                    }
                    else -> "No translation available"
                }
                
                android.util.Log.d("HomeScreen", "Found in dictionary - word: '$word', translation: '$translation'")
                
                // Get part of speech for display
                val partsOfSpeech = entry.getPartsOfSpeech()
                val posDisplay = if (partsOfSpeech.isNotEmpty()) {
                    partsOfSpeech.first().split(",").first().trim().capitalize()
                } else {
                    null
                }
                
                // Show popup using OverlayManager
                overlayManager.showPopup(
                    word = word,
                    translation = translation,
                    lookupCount = entry.lookupCount,
                    sourceLanguage = fromLanguage,
                    partOfSpeech = posDisplay
                )
                
                // Refresh recent lookups after search
                kotlinx.coroutines.delay(100)
                val updated = repository.getRecentLookups(10)
                recentLookups = updated.map { e ->
                    RecentLookup(
                        word = e.primaryExpression ?: e.primaryReading,
                        reading = e.primaryReading,
                        translation = e.getPrimaryGloss(),
                        count = e.lookupCount
                    )
                }
            } else {
                // Dictionary miss - try API fallback and save to DB
                val translationService = com.godtap.dictionary.api.TranslationService()
                val translated = translationService.translate(query, fromLanguage, toLanguage)
                
                if (translated != null) {
                    // Save API translation to DB for future use
                    try {
                        repository.saveApiTranslation(
                            query = query,
                            translation = translated,
                            sourceLanguage = fromLanguage,
                            targetLanguage = toLanguage
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Failed to save API translation", e)
                    }
                    
                    // Show popup with API translation
                    overlayManager.showPopup(
                        word = query,
                        translation = "$translated\n\n[Auto-translated via API]",
                        sourceLanguage = fromLanguage
                    )
                } else {
                    overlayManager.showPopup(
                        word = query,
                        translation = "No translation found",
                        sourceLanguage = fromLanguage
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header with app icon
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "📖",
                                fontSize = 24.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TapDictionary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Quick Translate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Language Selector
            item {
                LanguageSelector(
                    fromLanguage = fromLanguage,
                    toLanguage = toLanguage,
                    onSwap = {
                        val temp = fromLanguage
                        fromLanguage = toLanguage
                        toLanguage = temp
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Search Bar
            item {
                SearchBar(
                    value = searchValue,
                    onValueChange = { searchValue = it },
                    onSearch = {
                        if (searchValue.isNotBlank()) {
                            performSearch(searchValue)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Empty state or Recent section
            if (searchValue.isEmpty()) {
                item {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(40.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search for a word or select text in any app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Recent lookups
                if (recentLookups.isNotEmpty()) {
                    item {
                        Text(
                            text = "RECENT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    items(recentLookups) { lookup ->
                        RecentLookupCard(
                            lookup = lookup,
                            onClick = {
                                searchValue = lookup.word
                                onTestPopup(lookup.word, lookup.translation)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    fromLanguage: String,
    toLanguage: String,
    onSwap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageButton(
                language = getLanguageDisplayName(fromLanguage),
                modifier = Modifier.weight(1f)
            )
            
            // Disabled swap button (one-way translation)
            IconButton(
                onClick = { /* Disabled */ },
                enabled = false,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "One-way translation only",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            LanguageButton(
                language = getLanguageDisplayName(toLanguage),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LanguageButton(
    language: String,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = { /* Open language selector */ },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = language,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Enter word or phrase...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true
            )
            
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentLookupCard(
    lookup: RecentLookup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Frequency badge
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${lookup.count}×",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
            
            // Word and reading
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lookup.word,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lookup.reading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Translation
            Text(
                text = lookup.translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getLanguageDisplayName(code: String): String {
    return when (code) {
        "ja" -> "Japanese"
        "en" -> "English"
        "es" -> "Spanish"
        "ko" -> "Korean"
        else -> code.toUpperCase(java.util.Locale.ROOT)
    }
}
