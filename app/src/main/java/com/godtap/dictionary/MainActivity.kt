package com.godtap.dictionary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import com.godtap.dictionary.util.PermissionHelper
import com.godtap.dictionary.overlay.OverlayManager

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
    fun MainScreen() {
        var overlayPermissionGranted by remember { 
            mutableStateOf(PermissionHelper.hasOverlayPermission(this))
        }
        var accessibilityEnabled by remember { 
            mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(this))
        }
        
        LaunchedEffect(Unit) {
            // Refresh permission status periodically
            while (true) {
                kotlinx.coroutines.delay(1000)
                overlayPermissionGranted = PermissionHelper.hasOverlayPermission(this@MainActivity)
                accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header
            Text(
                text = getString(R.string.welcome_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = getString(R.string.welcome_subtitle),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = getString(R.string.setup_complete_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = getString(R.string.setup_complete_description),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { testPopup() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test Popup")
                            }
                            
                            Button(
                                onClick = { showTestText() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(getString(R.string.test_button))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    @Composable
    fun PermissionCard(
        title: String,
        description: String,
        isGranted: Boolean,
        buttonText: String,
        onButtonClick: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                
                Button(
                    onClick = onButtonClick,
                    enabled = !isGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
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
}
