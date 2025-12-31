package com.godtap.dictionary.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme

/**
 * Activity for managing dictionaries
 */
class DictionaryManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GodTapDictionaryTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DictionaryManagementScreenNew(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
