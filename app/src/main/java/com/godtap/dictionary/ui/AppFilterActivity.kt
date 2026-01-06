package com.godtap.dictionary.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import com.godtap.dictionary.util.AppFilterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for managing app filtering settings
 * Allows users to enable/disable dictionary for specific apps
 */
@OptIn(ExperimentalMaterial3Api::class)
class AppFilterActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "AppFilterActivity"
    }
    
    private lateinit var appFilterManager: AppFilterManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appFilterManager = AppFilterManager(this)
        
        setContent {
            GodTapDictionaryTheme {
                AppFilterScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppFilterScreen() {
        var filterMode by remember { mutableStateOf(appFilterManager.getFilterMode()) }
        var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var searchQuery by remember { mutableStateOf("") }
        var filterStateTrigger by remember { mutableStateOf(0) } // Trigger for filter state changes
        val scope = rememberCoroutineScope()
        
        // Load installed apps
        LaunchedEffect(Unit) {
            installedApps = loadInstalledApps()
            isLoading = false
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("App Filter Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
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
                    .padding(16.dp)
            ) {
                // Filter Mode Selection
                Text(
                    "Filter Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Filter modes in horizontal row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // All Apps Mode
                    FilterModeCard(
                        title = "All Apps",
                        description = "Dictionary works in all apps",
                        isSelected = filterMode == AppFilterManager.MODE_ALL_APPS,
                        onClick = {
                            filterMode = AppFilterManager.MODE_ALL_APPS
                            appFilterManager.setFilterMode(filterMode)
                            Toast.makeText(this@AppFilterActivity, "Dictionary enabled in all apps", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Whitelist Mode
                    FilterModeCard(
                        title = "Whitelist Mode",
                        description = "Dictionary only works in selected apps",
                        isSelected = filterMode == AppFilterManager.MODE_WHITELIST,
                        onClick = {
                            filterMode = AppFilterManager.MODE_WHITELIST
                            appFilterManager.setFilterMode(filterMode)
                            Toast.makeText(this@AppFilterActivity, "Whitelist mode enabled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Blacklist Mode
                    FilterModeCard(
                        title = "Blacklist Mode",
                        description = "Dictionary works except in blocked apps",
                        isSelected = filterMode == AppFilterManager.MODE_BLACKLIST,
                        onClick = {
                            filterMode = AppFilterManager.MODE_BLACKLIST
                            appFilterManager.setFilterMode(filterMode)
                            Toast.makeText(this@AppFilterActivity, "Blacklist mode enabled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // App List
                if (filterMode != AppFilterManager.MODE_ALL_APPS) {
                    Text(
                        if (filterMode == AppFilterManager.MODE_WHITELIST) "Allowed Apps" else "Blocked Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search apps...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Filter apps based on search query
                        val filteredApps = if (searchQuery.isEmpty()) {
                            installedApps
                        } else {
                            installedApps.filter { app ->
                                app.name.contains(searchQuery, ignoreCase = true) ||
                                app.packageName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps) { app ->
                                AppListItem(
                                    app = app,
                                    isEnabled = (filterStateTrigger >= 0) && (if (filterMode == AppFilterManager.MODE_WHITELIST) {
                                        appFilterManager.getAllowedApps().contains(app.packageName)
                                    } else {
                                        appFilterManager.getBlockedApps().contains(app.packageName)
                                    }),
                                    onToggle = { isEnabled ->
                                        scope.launch {
                                            if (filterMode == AppFilterManager.MODE_WHITELIST) {
                                                if (isEnabled) {
                                                    appFilterManager.addAllowedApp(app.packageName)
                                                } else {
                                                    appFilterManager.removeAllowedApp(app.packageName)
                                                }
                                            } else {
                                                if (isEnabled) {
                                                    appFilterManager.addBlockedApp(app.packageName)
                                                } else {
                                                    appFilterManager.removeBlockedApp(app.packageName)
                                                }
                                            }
                                            filterStateTrigger = (filterStateTrigger + 1) % 1000 // Cycle the trigger
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun FilterModeCard(
        title: String,
        description: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        ElevatedCard(
            onClick = onClick,
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surface
            ),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp), // Reduced padding for horizontal layout
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall, // Smaller text
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    
    @Composable
    private fun AppListItem(
        app: AppInfo,
        isEnabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        ElevatedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
    
    private suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES)
        
        Log.d(TAG, "Total packages found: ${packages.size}")
        
        // Log ALL packages to see if WhatsApp is there
        val allPackageNames = packages.map { it.packageName }.sorted()
        Log.d(TAG, "ALL packages: $allPackageNames")
        
        val filteredApps = packages
            .filter { app ->
                val packageName = app.packageName
                
                // Show user-installed apps OR updated system apps (like Chrome, WhatsApp pre-installed but updated)
                val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // Log ALL apps for debugging
                Log.v(TAG, "App: $packageName, isUserApp: $isUserApp, isUpdatedSystemApp: $isUpdatedSystemApp")
                
                // Log important apps with more detail
                if (packageName.contains("whatsapp", ignoreCase = true) ||
                    packageName.contains("discord", ignoreCase = true) ||
                    packageName.contains("chrome", ignoreCase = true) ||
                    packageName.contains("telegram", ignoreCase = true) ||
                    packageName.contains("facebook", ignoreCase = true) ||
                    packageName.contains("twitter", ignoreCase = true) ||
                    packageName.contains("instagram", ignoreCase = true)) {
                    Log.d(TAG, "⭐ Found app: $packageName, isUserApp: $isUserApp, isUpdatedSystemApp: $isUpdatedSystemApp, flags: ${app.flags}")
                }
                
                // Special logging for WhatsApp
                if (packageName == "com.whatsapp") {
                    Log.d(TAG, "WHATSAPP FOUND: $packageName, isUserApp: $isUserApp, isUpdatedSystemApp: $isUpdatedSystemApp, flags: ${app.flags}, included: ${isUserApp || isUpdatedSystemApp}")
                }
                
                // Show user-installed apps OR updated system apps
                isUserApp || isUpdatedSystemApp
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    name = pm.getApplicationLabel(app).toString()
                )
            }
            .sortedBy { it.name.lowercase() }
        
        Log.d(TAG, "Filtered apps count: ${filteredApps.size}")
        Log.d(TAG, "First 10 apps: ${filteredApps.take(10).map { it.name }}")
        
        return@withContext filteredApps
    }
    
    private fun isCoreSystemApp(packageName: String): Boolean {
        // Only exclude truly system-level apps that users shouldn't interact with
        val coreSystemApps = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.contacts",
            "com.android.providers.calendar",
            "com.android.providers.downloads",
            "com.android.providers.telephony",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.google.android.ext.services",
            "com.google.android.ext.shared"
        )
        
        // Also exclude packages that start with these prefixes (system components)
        val systemPrefixes = listOf(
            "com.android.server",
            "com.android.internal",
            "com.google.android.providers"
        )
        
        val isCore = coreSystemApps.contains(packageName) || 
                     systemPrefixes.any { packageName.startsWith(it) }
        
        if (isCore) {
            Log.v(TAG, "Excluding core system app: $packageName")
        }
        
        return isCore
    }
    
    private fun hasLaunchableActivity(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isPopularApp(packageName: String): Boolean {
        val popularApps = listOf(
            "com.android.chrome",
            "com.android.vending", // Play Store
            "com.google.android.gm", // Gmail
            "com.google.android.youtube",
            "com.amazon.kindle",
            "com.twitter.android",
            "com.facebook.katana",
            "com.instagram.android",
            "com.whatsapp",
            "com.discord",
            "org.telegram.messenger",
            "com.google.android.apps.docs", // Google Docs
            "com.microsoft.office.word",
            "com.adobe.reader"
        )
        return popularApps.contains(packageName)
    }
    
    data class AppInfo(
        val packageName: String,
        val name: String
    )
}
