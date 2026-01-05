package com.godtap.dictionary.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import com.godtap.dictionary.service.TextSelectionAccessibilityService
import com.godtap.dictionary.ui.theme.GodTapDictionaryTheme
import kotlinx.coroutines.delay

/**
 * Gesture Testing Sandbox
 * 
 * Tests all 3 gestures with visual feedback and comprehensive logging:
 * - 2-finger swipe right
 * - 3-finger tap
 * - 3-finger swipe right
 * 
 * Shows real-time touch events on screen and in logcat
 */
class GestureTestActivity : ComponentActivity() {
    companion object {
        private const val TAG = "GestureTest"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GodTapDictionaryTheme {
                var touchEvents by remember { mutableStateOf(listOf<String>()) }
                var lastGesture by remember { mutableStateOf("None") }
                var isTestingActive by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Gesture Testing Sandbox") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Card
                        StatusCard(lastGesture)

                        // Auto-Test Buttons
                        Text(
                            "Auto-Test Gestures",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = {
                                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                                Log.i(TAG, "║ SIMULATING 2-FINGER SWIPE RIGHT          ║")
                                Log.i(TAG, "║ → OCR MODE                               ║")
                                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                                lastGesture = "2-Finger Swipe Right → OCR (Simulated)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test: 2-Finger Swipe RIGHT → OCR")
                        }

                        Button(
                            onClick = {
                                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                                Log.i(TAG, "║ SIMULATING 2-FINGER SWIPE LEFT           ║")
                                Log.i(TAG, "║ → TOGGLE UNDERLINING                     ║")
                                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                                lastGesture = "2-Finger Swipe Left → Underline (Simulated)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test: 2-Finger Swipe LEFT → Underline")
                        }

                        Button(
                            onClick = {
                                Log.i(TAG, "╔═══════════════════════════════════════════╗")
                                Log.i(TAG, "║ SIMULATING 2-FINGER SWIPE UP             ║")
                                Log.i(TAG, "║ → TOGGLE SERVICE ON/OFF                  ║")
                                Log.i(TAG, "╚═══════════════════════════════════════════╝")
                                lastGesture = "2-Finger Swipe Up → On/Off (Simulated)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test: 2-Finger Swipe UP → On/Off")
                        }

                        Divider()

                        // Real Gesture Testing Area
                        Text(
                            "Real Gesture Testing Area",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            "Touch this area with multiple fingers to test real gestures",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Interactive touch area
                        TouchTestArea { event ->
                            val eventLog = formatTouchEvent(event)
                            touchEvents = (touchEvents + eventLog).takeLast(30)
                            
                            // Log to console
                            Log.i(TAG, eventLog)
                        }

                        Divider()

                        // Touch Events Log
                        Text(
                            "Touch Events Log (Last 30)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = { touchEvents = emptyList() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Log")
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (touchEvents.isEmpty()) {
                                    Text(
                                        "No touch events yet...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                } else {
                                    touchEvents.forEach { event ->
                                        Text(
                                            event,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Service Status
                        ServiceStatusCard()

                        // Instructions
                        InstructionsCard()
                    }
                }
            }
        }
    }

    private fun formatTouchEvent(event: MotionEvent): String {
        val actionName = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_POINTER_DOWN -> "PTR_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "PTR_UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "UNKNOWN"
        }
        
        val pointerInfo = (0 until event.pointerCount).joinToString(", ") { i ->
            "(%.0f,%.0f)".format(event.getX(i), event.getY(i))
        }
        
        return "[${System.currentTimeMillis() % 100000}] $actionName: ${event.pointerCount} fingers $pointerInfo"
    }

    @Composable
    private fun StatusCard(lastGesture: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Last Detected Gesture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    lastGesture,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    private fun TouchTestArea(onTouchEvent: (MotionEvent) -> Unit) {
        AndroidView(
            factory = { context ->
                View(context).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                    setOnTouchListener { _, event ->
                        onTouchEvent(event)
                        true // Consume the event
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        )
    }

    @Composable
    private fun ServiceStatusCard() {
        val isServiceRunning = TextSelectionAccessibilityService.isRunning
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) 
                    MaterialTheme.colorScheme.tertiaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Accessibility Service Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isServiceRunning) "✓ Running - Gestures Always Active" else "✗ Not Running",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceRunning) 
                        MaterialTheme.colorScheme.tertiary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                
                if (isServiceRunning) {
                    Text(
                        "System-wide gesture detection is enabled and running in the background. Touch gestures work across all apps without blocking normal screen interactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }

    @Composable
    private fun InstructionsCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "How to Test Gestures",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                InstructionItem("1", "Enable the Accessibility Service in Android Settings")
                InstructionItem("2", "Gestures are ALWAYS ACTIVE - no need to activate them")
                InstructionItem("3", "Use the blue test area to perform 2-finger swipes in any direction")
                InstructionItem("4", "Watch the Touch Events Log to see what the phone detects")
                InstructionItem("5", "Check logcat with: adb logcat GestureTest:I GestureOverlay:I '*:S'")
                
                Divider()
                
                Text(
                    "Available Gestures:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• 2-Finger Swipe RIGHT → Activate OCR Mode",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• 2-Finger Swipe LEFT → Toggle Word Underlining",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• 2-Finger Swipe UP → Toggle Service On/Off",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Divider()
                
                Text(
                    "Expected Touch Events:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• ACTION_DOWN: First finger touches",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ACTION_POINTER_DOWN: Additional fingers touch",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ACTION_MOVE: Fingers move (many events)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ACTION_POINTER_UP: One finger lifts",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ACTION_UP: Last finger lifts",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    private fun InstructionItem(number: String, text: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                number,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
