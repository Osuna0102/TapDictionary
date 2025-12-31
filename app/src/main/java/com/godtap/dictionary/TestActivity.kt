package com.godtap.dictionary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme

class TestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GodTapDictionaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestScreen()
                }
            }
        }
    }
    
    @Composable
    fun TestScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Test Dictionary",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Selecciona cualquier palabra japonesa:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            
            // Sample Japanese text - wrapped in SelectionContainer to make text selectable
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "食べる",
                            fontSize = 32.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = "私は毎日寿司を食べます。",
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = "本を読む",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = "学校に行く",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = "日本語を勉強する",
                            fontSize = 24.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Instrucciones:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "1. Mantén presionado sobre una palabra\n" +
                               "2. Selecciona el texto\n" +
                               "3. El diccionario aparecerá automáticamente",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
