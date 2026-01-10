package com.godtap.dictionary.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.godtap.dictionary.overlay.OverlayManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val TAG = "DictManagementScreen"

/**
 * Dictionary Management Screen with Tabs
 * - Active Dictionary tab: Shows current active dictionary with test functionality
 * - Browse tab: Browse and download/import dictionaries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryManagementScreenNew(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { DictionaryManager(context) }
    val downloader = remember { MultiDictionaryDownloader(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    var dictionaries by remember { mutableStateOf<List<DictionaryMetadata>>(emptyList()) }
    var activeDictionary by remember { mutableStateOf<DictionaryMetadata?>(null) }
    
    // Load dictionaries
    LaunchedEffect(Unit) {
        manager.initializeAvailableDictionaries()
        dictionaries = manager.getAllDictionaries()
        activeDictionary = manager.getActiveDictionary()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionary Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active Dictionary") },
                    icon = { Icon(Icons.Default.CheckCircle, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Browse & Import") },
                    icon = { Icon(Icons.Default.List, null) }
                )
            }
            
            // Tab content
            when (selectedTab) {
                0 -> ActiveDictionaryTab(
                    activeDictionary = activeDictionary,
                    manager = manager,
                    onRefresh = {
                        scope.launch {
                            activeDictionary = manager.getActiveDictionary()
                        }
                    }
                )
                1 -> BrowseAndImportTab(
                    dictionaries = dictionaries,
                    activeDictionary = activeDictionary,
                    manager = manager,
                    downloader = downloader,
                    onDictionariesUpdated = {
                        scope.launch {
                            dictionaries = manager.getAllDictionaries()
                            activeDictionary = manager.getActiveDictionary()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ActiveDictionaryTab(
    activeDictionary: DictionaryMetadata?,
    manager: DictionaryManager,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testText by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (activeDictionary != null) {
                // Active dictionary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Active Dictionary",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = activeDictionary.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Divider()
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Dictionary info
                        InfoRow("Language Pair", getLanguagePairName(activeDictionary.sourceLanguage, activeDictionary.targetLanguage))
                        InfoRow("Entries", "${activeDictionary.entryCount}")
                        InfoRow("Format", activeDictionary.format.name)
                        activeDictionary.description?.let {
                            InfoRow("Description", it)
                        }
                    }
                }
            } else {
                // No active dictionary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Active Dictionary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please install and activate a dictionary from the Browse & Import tab",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // Test section
        if (activeDictionary != null && activeDictionary.installed) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Test Dictionary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter text in ${getLanguageName(activeDictionary.sourceLanguage)} to test the dictionary lookup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Sample texts based on language
                        val sampleText = when (activeDictionary.sourceLanguage) {
                            "ja" -> "日本"
                            "es" -> "hola"
                            "ko" -> "안녕"
                            "zh" -> "你好"
                            else -> "hello"
                        }
                        
                        OutlinedButton(
                            onClick = { testText = sampleText },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use sample text: $sampleText")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Test text") },
                            placeholder = { Text("Enter text to test...") },
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isTesting = true
                                    testResult = null
                                    try {
                                        Log.d(TAG, "Testing dictionary with text: $testText")
                                        // Simulate the text selection and overlay popup
                                        val overlayManager = OverlayManager(context)
                                        // For now, just show a message
                                        testResult = "✓ Test triggered! Select this text in any app to see the popup:\n\n$testText"
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Test failed", e)
                                        testResult = "✗ Test failed: ${e.message}"
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = testText.isNotBlank() && !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Dictionary Lookup")
                        }
                        
                        testResult?.let { result ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Test Result",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseAndImportTab(
    dictionaries: List<DictionaryMetadata>,
    activeDictionary: DictionaryMetadata?,
    manager: DictionaryManager,
    downloader: MultiDictionaryDownloader,
    onDictionariesUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadingDictId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }
    var importingFile by remember { mutableStateOf(false) }
    
    // File picker for importing local .zip files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importingFile = true
                downloadStage = "Copying file to cache..."
                Log.i(TAG, "Starting import from URI: $uri")
                try {
                    // Copy file to app cache
                    val cacheFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    Log.d(TAG, "Copying to cache file: ${cacheFile.absolutePath}")
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    Log.i(TAG, "File copied, size: ${cacheFile.length()} bytes")
                    downloadStage = "Extracting and importing..."
                    
                    // Generate a unique dictionary ID
                    val customDictId = "custom_${System.currentTimeMillis()}"
                    
                    // Use the importFromLocalFile method
                    downloader.importFromLocalFile(
                        zipFile = cacheFile,
                        dictionaryId = customDictId,
                        listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                                Log.d(TAG, "Import progress: $stage ($bytesDownloaded/$totalBytes)")
                                downloadProgress = if (totalBytes > 0) {
                                    bytesDownloaded.toFloat() / totalBytes.toFloat()
                                } else 0f
                                downloadStage = stage
                            }
                            
                            override fun onComplete() {
                                Log.i(TAG, "Import completed successfully")
                                importingFile = false
                                downloadStage = ""
                                onDictionariesUpdated()
                                cacheFile.delete()
                            }
                            
                            override fun onError(error: Exception) {
                                Log.e(TAG, "Import failed", error)
                                importingFile = false
                                showError = "Import failed: ${error.message}"
                                cacheFile.delete()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start import", e)
                    importingFile = false
                    showError = "Failed to import: ${e.message}"
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Import progress card
        if (importingFile || downloadingDictId != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (importingFile) "Importing Dictionary..." else "Downloading Dictionary...",
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
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Help card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        text = "• Download from list or import .zip files\n• Only one dictionary can be active\n• Set active to use in text selection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { filePickerLauncher.launch("application/zip") }) {
                    Icon(
                        Icons.Default.Add,
                        "Import .zip",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Dictionary list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(dictionaries) { dict ->
                DictionaryCardWithActivation(
                    dictionary = dict,
                    isActive = activeDictionary?.dictionaryId == dict.dictionaryId,
                    isDownloading = dict.dictionaryId == downloadingDictId,
                    downloadProgress = if (dict.dictionaryId == downloadingDictId) downloadProgress else 0f,
                    downloadStage = if (dict.dictionaryId == downloadingDictId) downloadStage else "",
                    onDownload = {
                        downloadingDictId = dict.dictionaryId
                        Log.i(TAG, "Starting download for: ${dict.name}")
                        scope.launch {
                            try {
                                downloader.downloadAndImport(
                                    dictionaryId = dict.dictionaryId,
                                    listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                                            Log.d(TAG, "Download progress: $stage ($bytesDownloaded/$totalBytes)")
                                            downloadProgress = if (totalBytes > 0) {
                                                bytesDownloaded.toFloat() / totalBytes.toFloat()
                                            } else 0f
                                            downloadStage = stage
                                        }
                                        
                                        override fun onComplete() {
                                            Log.i(TAG, "Download completed: ${dict.name}")
                                            downloadingDictId = null
                                            onDictionariesUpdated()
                                        }
                                        
                                        override fun onError(error: Exception) {
                                            Log.e(TAG, "Download failed: ${dict.name}", error)
                                            downloadingDictId = null
                                            showError = "Download failed: ${error.message}"
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start download", e)
                                downloadingDictId = null
                                showError = e.message
                            }
                        }
                    },
                    onSetActive = {
                        Log.i(TAG, "Setting active dictionary: ${dict.name}")
                        scope.launch {
                            manager.setActiveDictionary(dict.dictionaryId)
                            onDictionariesUpdated()
                        }
                    },
                    onDelete = {
                        Log.i(TAG, "Deleting dictionary: ${dict.name}")
                        scope.launch {
                            downloader.deleteDictionary(dict.dictionaryId)
                            onDictionariesUpdated()
                        }
                    }
                )
            }
        }
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

@Composable
fun DictionaryCardWithActivation(
    dictionary: DictionaryMetadata,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStage: String,
    onDownload: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(if (isActive) 4.dp else 1.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dictionary.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = getLanguagePairName(dictionary.sourceLanguage, dictionary.targetLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        text = "📚 ${dictionary.entryCount} entries",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "💾 ${dictionary.fileSize / 1_000_000} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Download progress
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = downloadStage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!dictionary.installed) {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                } else {
                    if (!isActive) {
                        Button(
                            onClick = onSetActive,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Set Active")
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active")
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// Helper function to get language name from code
private fun getLanguageName(code: String): String {
    return when (code) {
        "ja" -> "Japanese"
        "en" -> "English"
        "es" -> "Spanish"
        "ko" -> "Korean"
        "zh" -> "Chinese"
        "de" -> "German"
        "fr" -> "French"
        "nl" -> "Dutch"
        "ru" -> "Russian"
        "sv" -> "Swedish"
        "hu" -> "Hungarian"
        "sl" -> "Slovenian"
        else -> code
    }
}
