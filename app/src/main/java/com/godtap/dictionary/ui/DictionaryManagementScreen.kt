package com.godtap.dictionary.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.godtap.dictionary.database.DictionaryFormat
import com.godtap.dictionary.database.DictionaryMetadata
import com.godtap.dictionary.manager.DictionaryManager
import com.godtap.dictionary.manager.MultiDictionaryDownloader
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Dictionary Management Screen
 * Browse, download, enable/disable dictionaries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryManagementScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { DictionaryManager(context) }
    val downloader = remember { MultiDictionaryDownloader(context) }
    
    var dictionaries by remember { mutableStateOf<List<DictionaryMetadata>>(emptyList()) }
    var selectedLanguagePair by remember { mutableStateOf<String?>(null) }
    var downloadingDictId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importingFile by remember { mutableStateOf(false) }
    
    // File picker for importing local .zip files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importingFile = true
                downloadStage = "Importing local file..."
                try {
                    // Copy file to app cache
                    val cacheFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Import the file
                    importDictionaryFromFile(
                        cacheFile = cacheFile,
                        manager = manager,
                        downloader = downloader,
                        onProgress = { progress, stage ->
                            downloadProgress = progress
                            downloadStage = stage
                        },
                        onComplete = {
                            importingFile = false
                            downloadStage = ""
                            // Refresh dictionary list
                            scope.launch {
                                dictionaries = manager.getAllDictionaries()
                            }
                        },
                        onError = { error ->
                            importingFile = false
                            showError = error
                        }
                    )
                } catch (e: Exception) {
                    importingFile = false
                    showError = "Failed to import: ${e.message}"
                }
            }
        }
    }
    
    // Load dictionaries
    LaunchedEffect(Unit) {
        manager.initializeAvailableDictionaries()
        dictionaries = manager.getAllDictionaries()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionary Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Import local file button
                    IconButton(onClick = { filePickerLauncher.launch("application/zip") }) {
                        Icon(Icons.Default.Add, "Import .zip file", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Help card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column {
                        Text(
                            text = "How to Use",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Download dictionaries from the list below\n• Import your own .zip files using the + button\n• Enable/disable dictionaries as needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // Import progress card
            if (importingFile) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Importing Dictionary...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = downloadStage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Language pair filter
            LanguagePairFilter(
                dictionaries = dictionaries,
                selectedPair = selectedLanguagePair,
                onPairSelected = { selectedLanguagePair = it }
            )
            
            Divider()
            
            // Dictionary list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filtered = if (selectedLanguagePair != null) {
                    dictionaries.filter { 
                        "${it.sourceLanguage}-${it.targetLanguage}" == selectedLanguagePair 
                    }
                } else {
                    dictionaries
                }
                
                items(filtered) { dict ->
                    DictionaryCard(
                        dictionary = dict,
                        isDownloading = dict.dictionaryId == downloadingDictId,
                        downloadProgress = if (dict.dictionaryId == downloadingDictId) downloadProgress else 0f,
                        downloadStage = if (dict.dictionaryId == downloadingDictId) downloadStage else "",
                        onDownload = {
                            downloadingDictId = dict.dictionaryId
                            scope.launch {
                                try {
                                    downloader.downloadAndImport(
                                        dictionaryId = dict.dictionaryId,
                                        listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                                            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                                                downloadProgress = if (totalBytes > 0) {
                                                    bytesDownloaded.toFloat() / totalBytes.toFloat()
                                                } else 0f
                                                downloadStage = stage
                                            }
                                            
                                            override fun onComplete() {
                                                downloadingDictId = null
                                                scope.launch {
                                                    dictionaries = manager.getAllDictionaries()
                                                }
                                            }
                                            
                                            override fun onError(error: Exception) {
                                                downloadingDictId = null
                                                showError = error.message
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    downloadingDictId = null
                                    showError = e.message
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                downloader.deleteDictionary(dict.dictionaryId)
                                dictionaries = manager.getAllDictionaries()
                            }
                        },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                manager.setDictionaryEnabled(dict.dictionaryId, enabled)
                                dictionaries = manager.getAllDictionaries()
                            }
                        }
                    )
                }
            }
            
            // Statistics
            DictionaryStats(
                dictionaries = dictionaries
            )
        }
        
        // Error dialog
        showError?.let { error ->
            AlertDialog(
                onDismissRequest = { showError = null },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { showError = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePairFilter(
    dictionaries: List<DictionaryMetadata>,
    selectedPair: String?,
    onPairSelected: (String?) -> Unit
) {
    val pairs = remember(dictionaries) {
        dictionaries
            .map { "${it.sourceLanguage}-${it.targetLanguage}" to getLanguagePairName(it.sourceLanguage, it.targetLanguage) }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedPair == null,
            onClick = { onPairSelected(null) },
            label = { Text("All") }
        )
        
        pairs.forEach { (pair, name) ->
            FilterChip(
                selected = selectedPair == pair,
                onClick = { onPairSelected(pair) },
                label = { Text(name) }
            )
        }
    }
}

@Composable
fun DictionaryCard(
    dictionary: DictionaryMetadata,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStage: String,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dictionary.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getLanguagePairName(dictionary.sourceLanguage, dictionary.targetLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Format badge
                AssistChip(
                    onClick = {},
                    label = { Text(dictionary.format.name) }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            dictionary.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Stats
            if (dictionary.installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "${dictionary.entryCount} entries",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${dictionary.fileSize / 1_000_000} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Download progress
            if (isDownloading) {
                Column {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadStage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dictionary.installed) {
                    // Enable/Disable switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Switch(
                            checked = dictionary.enabled,
                            onCheckedChange = onToggleEnabled
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enabled")
                    }
                    
                    // Delete button
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isDownloading
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                } else {
                    // Download button
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download (${dictionary.fileSize / 1_000_000} MB)")
                    }
                }
            }
        }
    }
}

@Composable
fun DictionaryStats(dictionaries: List<DictionaryMetadata>) {
    val installed = dictionaries.count { it.installed }
    val enabled = dictionaries.count { it.enabled }
    val totalEntries = dictionaries.filter { it.enabled }.sumOf { it.entryCount }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Total", dictionaries.size.toString())
            StatItem("Installed", installed.toString())
            StatItem("Enabled", enabled.toString())
            StatItem("Entries", formatNumber(totalEntries))
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getLanguagePairName(source: String, target: String): String {
    val names = mapOf(
        "ja" to "Japanese",
        "en" to "English",
        "es" to "Spanish",
        "ko" to "Korean",
        "de" to "German",
        "fr" to "French",
        "ru" to "Russian",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "hu" to "Hungarian",
        "sl" to "Slovenian"
    )
    return "${names[source] ?: source} → ${names[target] ?: target}"
}

fun formatNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> "%.1fM".format(num / 1_000_000.0)
        num >= 1_000 -> "%.1fK".format(num / 1_000.0)
        else -> num.toString()
    }
}

/**
 * Import a dictionary from a local .zip file
 */
suspend fun importDictionaryFromFile(
    cacheFile: File,
    manager: DictionaryManager,
    downloader: MultiDictionaryDownloader,
    onProgress: (Float, String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        onProgress(0.1f, "Reading dictionary file...")
        
        // Parse the dictionary to detect its metadata
        val metadata = detectDictionaryMetadata(cacheFile)
        
        onProgress(0.3f, "Detected: ${metadata.name}")
        
        // Add metadata to database if not exists
        manager.addCustomDictionary(metadata)
        
        // Import the dictionary
        downloader.downloadAndImport(
            dictionaryId = metadata.dictionaryId,
            listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                    val progress = 0.3f + (0.6f * (bytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)))
                    onProgress(progress, stage)
                }
                
                override fun onComplete() {
                    onProgress(1f, "Import complete!")
                    onComplete()
                }
                
                override fun onError(error: Exception) {
                    onError(error.message ?: "Unknown error")
                }
            }
        )
    } catch (e: Exception) {
        onError(e.message ?: "Failed to import dictionary")
    } finally {
        // Clean up cache file
        cacheFile.delete()
    }
}

/**
 * Detect dictionary metadata from a .zip file
 */
suspend fun detectDictionaryMetadata(zipFile: File): DictionaryMetadata {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Try to read index.json from zip
            val indexJson = java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "index.json") {
                        return@use zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
                null
            }
            
            if (indexJson != null) {
                // Parse Yomitan format
                val index = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<com.godtap.dictionary.manager.DictionaryIndex>(indexJson)
                
                val sourceLang = index.sourceLanguage ?: "ja"
                val targetLang = index.targetLanguage ?: "en"
                val title = index.title ?: "Imported Dictionary"
                val dictionaryId = "custom_${sourceLang}_${targetLang}_${System.currentTimeMillis()}"
                
                DictionaryMetadata(
                    name = title,
                    dictionaryId = dictionaryId,
                    version = index.revision ?: "1.0",
                    format = DictionaryFormat.YOMICHAN,
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang,
                    downloadUrl = "file://${zipFile.absolutePath}",
                    fileSize = zipFile.length(),
                    description = index.description ?: "Imported from local file",
                    author = index.author ?: "Unknown",
                    license = index.attribution ?: "Unknown",
                    website = index.url,
                    tags = ""
                )
            } else {
                // Fallback: create generic metadata
                val dictionaryId = "custom_unknown_${System.currentTimeMillis()}"
                DictionaryMetadata(
                    name = "Imported Dictionary",
                    dictionaryId = dictionaryId,
                    version = "1.0",
                    format = DictionaryFormat.YOMICHAN,
                    sourceLanguage = "ja",
                    targetLanguage = "en",
                    downloadUrl = "file://${zipFile.absolutePath}",
                    fileSize = zipFile.length(),
                    description = "Imported from local file",
                    author = "Unknown",
                    license = "Unknown",
                    website = null,
                    tags = ""
                )
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to read dictionary file: ${e.message}")
        }
    }
}
