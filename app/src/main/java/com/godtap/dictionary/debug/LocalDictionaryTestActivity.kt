package com.godtap.dictionary.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.godtap.dictionary.manager.DictionaryManager
import com.godtap.dictionary.manager.MultiDictionaryDownloader
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug activity for testing local dictionary import
 * 
 * Usage:
 * 1. Place dictionary ZIP in /sdcard/Download/ or app's external files dir
 * 2. Launch this activity
 * 3. Tap "Import Local Dictionary"
 */
class LocalDictionaryTestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            LocalDictionaryTestScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDictionaryTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { DictionaryManager(context) }
    val downloader = remember { MultiDictionaryDownloader(context) }
    
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    var availableFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        // Scan for dictionary files
        availableFiles = findDictionaryFiles(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Local Dictionary Import Test") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(status)
                    
                    if (stage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stage, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            // Quick test: Import bundled kty-es-ko
            Button(
                onClick = {
                    scope.launch {
                        try {
                            status = "Importing kty-es-ko from project files..."
                            val projectRoot = "/Applications/XAMPP/xamppfiles/htdocs/devs/AndroidGodTap"
                            val zipFile = File(projectRoot, "kty-es-ko.zip")
                            
                            if (zipFile.exists()) {
                                downloader.importFromLocalFile(
                                    zipFile = zipFile,
                                    dictionaryId = "kty_es_ko_local",
                                    listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                                        override fun onProgress(bytes: Long, total: Long, stg: String) {
                                            progress = if (total > 0) bytes.toFloat() / total else 0f
                                            stage = stg
                                        }
                                        override fun onComplete() {
                                            status = "✅ Import complete!"
                                            stage = ""
                                        }
                                        override fun onError(error: Exception) {
                                            status = "❌ Error: ${error.message}"
                                            stage = ""
                                        }
                                    }
                                )
                            } else {
                                status = "❌ File not found: ${zipFile.absolutePath}"
                            }
                        } catch (e: Exception) {
                            status = "❌ Error: ${e.message}"
                            Log.e("LocalDictTest", "Error importing", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import kty-es-ko.zip (Spanish-Korean)")
            }
            
            // Available files
            if (availableFiles.isNotEmpty()) {
                Text("Available dictionary files:", style = MaterialTheme.typography.titleMedium)
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    try {
                                        status = "Importing ${file.name}..."
                                        val dictId = file.nameWithoutExtension.replace("-", "_")
                                        
                                        downloader.importFromLocalFile(
                                            zipFile = file,
                                            dictionaryId = dictId,
                                            listener = object : MultiDictionaryDownloader.DownloadProgressListener {
                                                override fun onProgress(bytes: Long, total: Long, stg: String) {
                                                    progress = if (total > 0) bytes.toFloat() / total else 0f
                                                    stage = stg
                                                }
                                                override fun onComplete() {
                                                    status = "✅ Imported ${file.name}"
                                                    stage = ""
                                                }
                                                override fun onError(error: Exception) {
                                                    status = "❌ Error: ${error.message}"
                                                    stage = ""
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        status = "❌ Error: ${e.message}"
                                    }
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${file.length() / 1024 / 1024} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Text("No dictionary files found")
                Text(
                    text = "Place .zip files in:\n- /sdcard/Download/\n- App external files directory",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun findDictionaryFiles(context: android.content.Context): List<File> {
    val files = mutableListOf<File>()
    
    // Check Downloads folder
    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    )
    downloadsDir.listFiles { file ->
        file.name.endsWith(".zip") && file.name.contains("dict", ignoreCase = true)
    }?.let { files.addAll(it) }
    
    // Check app external files dir
    context.getExternalFilesDir(null)?.listFiles { file ->
        file.name.endsWith(".zip")
    }?.let { files.addAll(it) }
    
    return files.sortedByDescending { it.lastModified() }
}
