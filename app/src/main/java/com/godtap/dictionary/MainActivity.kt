package com.godtap.dictionary

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import com.godtap.dictionary.ui.DictionaryManagementActivity
import com.godtap.dictionary.util.PermissionHelper
import com.godtap.dictionary.overlay.OverlayManager
import com.godtap.dictionary.downloader.DictionaryDownloader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var overlayManager: OverlayManager
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Refresh UI after permission request
    }
    
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") || 
               manufacturer.contains("poco") || brand.contains("poco") ||
               manufacturer.contains("redmi") || brand.contains("redmi")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        overlayManager = OverlayManager(applicationContext)
        
        setContent {
            GodTapDictionaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    @Composable
    private fun PermissionCard(
        title: String,
        description: String,
        isGranted: Boolean,
        buttonText: String,
        onButtonClick: () -> Unit
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isGranted) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp, start = 40.dp)
                )
                
                Button(
                    onClick = onButtonClick,
                    enabled = !isGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = buttonText,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun MenuItem(text: String, onClick: () -> Unit) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }
    }

    @Composable
    fun MainScreen() {
        val scope = rememberCoroutineScope()
        val downloader = remember { DictionaryDownloader(applicationContext) }
        
        var overlayPermissionGranted by remember { 
            mutableStateOf(PermissionHelper.hasOverlayPermission(this))
        }
        var accessibilityEnabled by remember { 
            mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(this))
        }
        var dictionaryImported by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }
        var downloadStage by remember { mutableStateOf("") }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadError by remember { mutableStateOf<String?>(null) }
        var sidebarOpen by remember { mutableStateOf(false) }
        
        LaunchedEffect(Unit) {
            // Check if dictionary is already imported
            dictionaryImported = downloader.isDictionaryImported()
            
            // If not imported, start download automatically
            if (!dictionaryImported && !isDownloading) {
                isDownloading = true
                downloadStage = "Starting download..."
                
                scope.launch {
                    try {
                        downloader.downloadAndImport(
                            listener = object : DictionaryDownloader.DownloadProgressListener {
                                override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                                    downloadStage = stage
                                    downloadProgress = if (totalBytes > 0) {
                                        (bytesDownloaded.toFloat() / totalBytes.toFloat())
                                    } else {
                                        0.5f
                                    }
                                }
                                
                                override fun onComplete() {
                                    dictionaryImported = true
                                    isDownloading = false
                                    downloadStage = "Dictionary ready!"
                                }
                                
                                override fun onError(error: Exception) {
                                    downloadError = error.message
                                    isDownloading = false
                                }
                            }
                        )
                    } catch (e: Exception) {
                        downloadError = e.message
                        isDownloading = false
                    }
                }
            }
            
            // Refresh permission status periodically
            while (true) {
                kotlinx.coroutines.delay(1000)
                overlayPermissionGranted = PermissionHelper.hasOverlayPermission(this@MainActivity)
                accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar with hamburger menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { sidebarOpen = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            
            // Modern Header with icon
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "📖",
                        fontSize = 48.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Text(
                text = getString(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = getString(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Step 0: Dictionary Download
            if (!dictionaryImported || isDownloading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (downloadError != null) 
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (downloadError != null) 
                                    Icons.Default.Settings 
                                else 
                                    Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (downloadError != null)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Dictionary Setup",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (downloadError != null) {
                            Text(
                                text = "Download failed: $downloadError",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    downloadError = null
                                    isDownloading = true
                                    scope.launch {
                                        try {
                                            downloader.downloadAndImport(
                                                listener = object : DictionaryDownloader.DownloadProgressListener {
                                                    override fun onProgress(bytesDownloaded: Long, totalBytes: Long, stage: String) {
                                                        downloadStage = stage
                                                        downloadProgress = if (totalBytes > 0) {
                                                            (bytesDownloaded.toFloat() / totalBytes.toFloat())
                                                        } else 0.5f
                                                    }
                                                    override fun onComplete() {
                                                        dictionaryImported = true
                                                        isDownloading = false
                                                    }
                                                    override fun onError(error: Exception) {
                                                        downloadError = error.message
                                                        isDownloading = false
                                                    }
                                                }
                                            )
                                        } catch (e: Exception) {
                                            downloadError = e.message
                                            isDownloading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry Download")
                            }
                        } else if (isDownloading) {
                            Text(
                                text = downloadStage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "✓ Dictionary ready!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Step 1: Overlay Permission
            PermissionCard(
                title = getString(R.string.permission_overlay_title),
                description = getString(R.string.permission_overlay_description),
                isGranted = overlayPermissionGranted,
                buttonText = if (overlayPermissionGranted) 
                    getString(R.string.permission_overlay_granted)
                else 
                    getString(R.string.permission_overlay_button),
                onButtonClick = {
                    if (!overlayPermissionGranted) {
                        requestOverlayPermission()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Step 2: Accessibility Service
            PermissionCard(
                title = getString(R.string.permission_accessibility_title),
                description = getString(R.string.permission_accessibility_description),
                isGranted = accessibilityEnabled,
                buttonText = if (accessibilityEnabled) 
                    getString(R.string.permission_accessibility_granted)
                else 
                    getString(R.string.permission_accessibility_button),
                onButtonClick = {
                    if (!accessibilityEnabled) {
                        openAccessibilitySettings()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Setup Complete
            if (overlayPermissionGranted && accessibilityEnabled) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = getString(R.string.setup_complete_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = getString(R.string.setup_complete_description),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { testPopup() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Test Popup")
                            }
                            
                            FilledTonalButton(
                                onClick = { showTestText() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(getString(R.string.test_button))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Divider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Info about managing dictionaries
                        Text(
                            text = "Manage your dictionaries:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Manage Dictionaries button
                        Button(
                            onClick = { launchDictionaryManagement() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manage Dictionaries",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // App Filter Settings button
                        OutlinedButton(
                            onClick = { launchAppFilterSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("⚙️ App Filter Settings")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // TTS Settings button
                        OutlinedButton(
                            onClick = { launchTtsSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔊 Text-to-Speech Settings")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Debug button
                        OutlinedButton(
                            onClick = { launchDebugScreen() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔍 Dictionary Debug")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Gesture Testing button
                        OutlinedButton(
                            onClick = { launchGestureTestActivity() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("✋ Gesture Testing Sandbox")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Sidebar
            if (sidebarOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        // Header
                        Text(
                            text = "Menu",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Menu items
                        MenuItem(
                            text = "Manage Dictionaries",
                            onClick = {
                                sidebarOpen = false
                                launchDictionaryManagement()
                            }
                        )
                        
                        MenuItem(
                            text = "Dictionary Debug",
                            onClick = {
                                sidebarOpen = false
                                launchDebugScreen()
                            }
                        )
                        
                        MenuItem(
                            text = "App Filter Settings",
                            onClick = {
                                sidebarOpen = false
                                launchAppFilterSettings()
                            }
                        )
                        
                        MenuItem(
                            text = "Text-to-Speech Settings",
                            onClick = {
                                sidebarOpen = false
                                launchTtsSettings()
                            }
                        )
                        
                        MenuItem(
                            text = "Probar Diccionario",
                            onClick = {
                                sidebarOpen = false
                                showTestText()
                            }
                        )
                        
                        MenuItem(
                            text = "Test Popup",
                            onClick = {
                                sidebarOpen = false
                                testPopup()
                            }
                        )
                        
                        MenuItem(
                            text = "Toggle Word Underlining",
                            onClick = {
                                sidebarOpen = false
                                toggleWordUnderlining()
                            }
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Close button
                        OutlinedButton(
                            onClick = { sidebarOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun launchDebugScreen() {
        val intent = Intent(this, DictionaryDebugActivity::class.java)
        startActivity(intent)
    }
    
    private fun launchDictionaryManagement() {
        val intent = Intent(this, DictionaryManagementActivity::class.java)
        startActivity(intent)
    }
    
    private fun launchGestureTestActivity() {
        val intent = Intent(this, com.godtap.dictionary.ui.GestureTestActivity::class.java)
        startActivity(intent)
    }
    
    private fun launchAppFilterSettings() {
        val intent = Intent(this, com.godtap.dictionary.ui.AppFilterActivity::class.java)
        startActivity(intent)
    }
    
    private fun launchTtsSettings() {
        val intent = Intent(this, com.godtap.dictionary.ui.TtsSettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun testPopup() {
        // Test the popup with a sample Japanese word
        val testWord = "食べる (たべる)"
        val testTranslation = "[verb]\nto eat, to consume\n\n例文 (example):\n私は寿司を食べる\nI eat sushi"
        overlayManager.showPopup(testWord, testTranslation)
    }
    
    private fun showTestText() {
        // Open a test activity with Japanese text
        val intent = Intent(this, TestActivity::class.java)
        startActivity(intent)
    }
    
    private fun toggleWordUnderlining() {
        val sharedPreferences = getSharedPreferences("dictionary_prefs", MODE_PRIVATE)
        val current = sharedPreferences.getBoolean("underline_enabled", false)
        sharedPreferences.edit().putBoolean("underline_enabled", !current).apply()
        val message = if (!current) "Word underlining enabled" else "Word underlining disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
