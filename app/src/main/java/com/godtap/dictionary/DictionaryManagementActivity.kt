package com.godtap.dictionary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.godtap.dictionary.ui.DictionaryManagementScreen
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme

/**
 * Activity for managing dictionaries
 * Allows users to download, enable/disable, and delete dictionaries
 */
class DictionaryManagementActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GodTapDictionaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DictionaryManagementScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
