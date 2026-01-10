package com.godtap.dictionary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SetupStep(
    val step: Int,
    val title: String,
    val description: String,
    val isCompleted: Boolean
)

@Composable
fun OnboardingScreen(
    overlayPermissionGranted: Boolean,
    accessibilityEnabled: Boolean,
    dictionaryImported: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        SetupStep(1, "Enable Overlay", "Allow popup over apps", overlayPermissionGranted),
        SetupStep(2, "Enable Accessibility", "Detect text selection", accessibilityEnabled),
        SetupStep(3, "Download Dictionary", "One-time, ~5MB", dictionaryImported)
    )
    
    val allCompleted = steps.all { it.isCompleted }
    val currentStep = steps.indexOfFirst { !it.isCompleted }.takeIf { it >= 0 } ?: steps.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.background
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "📖", fontSize = 48.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = "Welcome to TapDictionary",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Setup in 30 seconds",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Steps
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    SetupStepCard(
                        step = step,
                        isActive = index == currentStep && !allCompleted,
                        onClick = {
                            when (index) {
                                0 -> if (!overlayPermissionGranted) onRequestOverlayPermission()
                                1 -> if (!accessibilityEnabled) onOpenAccessibilitySettings()
                                2 -> { /* Dictionary download handled automatically */ }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Button
            if (allCompleted) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = {
                        when (currentStep) {
                            0 -> onRequestOverlayPermission()
                            1 -> onOpenAccessibilitySettings()
                            else -> { /* Dictionary handled automatically */ }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Continue Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Skip Button
            TextButton(
                onClick = onSkip,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom Bar
        if (!allCompleted) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.tertiary,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = "Easy setup in 30 seconds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    step: SetupStep,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!step.isCompleted) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                step.isCompleted -> MaterialTheme.colorScheme.primaryContainer
                isActive -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step indicator
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = when {
                    step.isCompleted -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (step.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = step.step.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Step info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (step.isCompleted)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.isCompleted)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status badge
            if (step.isCompleted) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Done!",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
