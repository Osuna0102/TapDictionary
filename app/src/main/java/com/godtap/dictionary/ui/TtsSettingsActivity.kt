package com.godtap.dictionary.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import com.godtap.dictionary.util.TtsManager
import com.godtap.dictionary.util.VoiceInfo
import com.godtap.dictionary.manager.DictionaryManager
import kotlinx.coroutines.runBlocking

class TtsSettingsActivity : ComponentActivity() {
    
    private lateinit var ttsManager: TtsManager
    private lateinit var dictionaryManager: DictionaryManager
    private val PREFS_NAME = "tts_settings"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ttsManager = TtsManager(applicationContext)
        dictionaryManager = DictionaryManager(applicationContext)
        
        setContent {
            GodTapDictionaryTheme {
                TtsSettingsScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TtsSettingsScreen() {
        val prefs = remember { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        val supportedLanguages = remember { ttsManager.getSupportedLanguages() }
        
        // Auto-detect language from active dictionary
        val defaultLanguage = remember {
            runBlocking {
                dictionaryManager.getActiveDictionary()?.sourceLanguage ?: "ja"
            }
        }
        
        var selectedLanguage by remember { mutableStateOf(defaultLanguage) }
        var availableVoices by remember { mutableStateOf<List<VoiceInfo>>(emptyList()) }
        var selectedVoice by remember { 
            mutableStateOf(prefs.getString("voice_$selectedLanguage", null)) 
        }
        var showEngineSettings by remember { mutableStateOf(false) }
        
        // Load voices when language changes
        LaunchedEffect(selectedLanguage) {
            availableVoices = ttsManager.getAvailableVoicesForLanguage(selectedLanguage)
                .distinctBy { it.name } // Remove duplicates
            selectedVoice = prefs.getString("voice_$selectedLanguage", null)
        }
        
        // Ensure supported languages list is not empty
        val languagesToShow = if (supportedLanguages.isEmpty()) {
            listOf("ja", "es", "ko", "en") // Fallback list
        } else {
            supportedLanguages
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Text-to-Speech Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // Button to open system TTS settings
                        TextButton(
                            onClick = {
                                val intent = Intent()
                                intent.action = "com.android.settings.TTS_SETTINGS"
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            }
                        ) {
                            Text("System Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Language selection section
                item {
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            languagesToShow.forEach { langCode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLanguage = langCode }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getLanguageName(langCode),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (selectedLanguage == langCode) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Voice selection section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Available Voices for ${getLanguageName(selectedLanguage)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (availableVoices.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "No voices available for this language.\nTry installing additional voices from System Settings.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(availableVoices) { voice ->
                        VoiceCard(
                            voice = voice,
                            isSelected = selectedVoice == voice.name,
                            onSelect = {
                                ttsManager.setVoice(voice.name)
                                prefs.edit().putString("voice_$selectedLanguage", voice.name).apply()
                                selectedVoice = voice.name
                            },
                            onTest = {
                                ttsManager.setVoice(voice.name)
                                ttsManager.speak(getTestPhrase(selectedLanguage), selectedLanguage)
                            }
                        )
                    }
                }
                
                // Help text
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ℹ️ About TTS Voices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Some voices may require internet connection\n" +
                                       "• Voice quality and latency vary by device\n" +
                                       "• Install additional voices from Android System Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun VoiceCard(
        voice: VoiceInfo,
        isSelected: Boolean,
        onSelect: () -> Unit,
        onTest: () -> Unit
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() },
            shape = RoundedCornerShape(12.dp),
            colors = if (isSelected) {
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                CardDefaults.elevatedCardColors()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = voice.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Quality: ${voice.quality} | Latency: ${voice.latency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (voice.isNetworkBased) {
                        Text(
                            text = "⚠️ Requires internet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // Test button
                FilledTonalButton(
                    onClick = onTest,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("🔊 Test")
                }
            }
        }
    }
    
    private fun getLanguageName(code: String): String {
        return when (code) {
            "ja" -> "Japanese (日本語)"
            "es" -> "Spanish (Español)"
            "ko" -> "Korean (한국어)"
            "en" -> "English"
            "zh" -> "Chinese (中文)"
            "fr" -> "French (Français)"
            "de" -> "German (Deutsch)"
            "it" -> "Italian (Italiano)"
            "pt" -> "Portuguese (Português)"
            "ru" -> "Russian (Русский)"
            else -> code.uppercase()
        }
    }
    
    private fun getTestPhrase(languageCode: String): String {
        return when (languageCode) {
            "ja" -> "こんにちは、これはテストです"
            "es" -> "Hola, esto es una prueba"
            "ko" -> "안녕하세요, 이것은 테스트입니다"
            "en" -> "Hello, this is a test"
            "zh" -> "你好，这是一个测试"
            "fr" -> "Bonjour, ceci est un test"
            "de" -> "Hallo, das ist ein Test"
            "it" -> "Ciao, questo è un test"
            "pt" -> "Olá, este é um teste"
            "ru" -> "Привет, это тест"
            else -> "Hello, this is a test"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Note: Don't shutdown TTS here as it's used by the service
        // The service will manage TTS lifecycle
    }
}
