package com.godtap.dictionary

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import com.godtap.dictionary.ui.DictionaryManagementActivity
import com.godtap.dictionary.ui.components.BottomNavigation
import com.godtap.dictionary.ui.screens.*
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        overlayManager = OverlayManager(applicationContext)
        
        setContent {
            val sharedPrefs = getSharedPreferences("dictionary_prefs", MODE_PRIVATE)
            val isDarkTheme = remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }
            
            GodTapDictionaryTheme(darkTheme = isDarkTheme.value) {
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
        
        var currentRoute by remember { mutableStateOf("home") }
        var showOnboarding by remember { 
            mutableStateOf(!overlayPermissionGranted || !accessibilityEnabled || !dictionaryImported)
        }
        
        // Check dictionary status and start download if needed
        LaunchedEffect(Unit) {
            dictionaryImported = downloader.isDictionaryImported()
            
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
                
                // Auto-dismiss onboarding if all setup is complete
                if (overlayPermissionGranted && accessibilityEnabled && dictionaryImported) {
                    showOnboarding = false
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (showOnboarding) {
                OnboardingScreen(
                    overlayPermissionGranted = overlayPermissionGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    dictionaryImported = dictionaryImported,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onSkip = { showOnboarding = false }
                )
            } else {
                // Main app with bottom navigation
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentRoute) {
                        "home" -> HomeScreen(
                            onTestPopup = { word, translation ->
                                overlayManager.showPopup(word, translation)
                            }
                        )
                        "history" -> HistoryScreen(
                            onWordClick = { word, translation ->
                                overlayManager.showPopup(word, translation)
                            }
                        )
                        "features" -> FeaturesScreen()
                        "settings" -> SettingsScreen(
                            onDictionaryManagementClick = { launchDictionaryManagement() },
                            onAppFilterClick = { launchAppFilterSettings() },
                            onTtsSettingsClick = { launchTtsSettings() }
                        )
                    }
                    
                    BottomNavigation(
                        currentRoute = currentRoute,
                        onNavigate = { route -> currentRoute = route },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    )
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
    
    private fun launchDictionaryManagement() {
        val intent = Intent(this, DictionaryManagementActivity::class.java)
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
}
