package com.godtap.dictionary.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onDictionaryManagementClick: () -> Unit,
    onAppFilterClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("dictionary_prefs", Context.MODE_PRIVATE)
    
    var autoPlayAudio by remember { mutableStateOf(sharedPrefs.getBoolean("auto_play_audio", true)) }
    var popupAnimation by remember { mutableStateOf(sharedPrefs.getBoolean("popup_animation", true)) }
    var dailyReminders by remember { mutableStateOf(sharedPrefs.getBoolean("daily_reminders", false)) }
    var underlineWords by remember { mutableStateOf(sharedPrefs.getBoolean("underline_enabled", false)) }
    var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Customize your experience",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // App Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "📖", fontSize = 32.sp)
                            }
                        }
                        Column {
                            Text(
                                text = "TapDictionary",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Version 1.0.0",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "All Features Enabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // General Section
            item {
                SectionTitle(text = "GENERAL")
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingItem(
                        icon = Icons.Filled.Star,
                        title = "Languages",
                        subtitle = "Japanese → English",
                        onClick = { /* Language selector */ }
                    )
                    SettingItem(
                        icon = Icons.Filled.Add,
                        title = "Dictionaries",
                        subtitle = "Manage dictionaries",
                        onClick = onDictionaryManagementClick
                    )
                    SettingToggleItem(
                        icon = Icons.Filled.Favorite,
                        title = "Auto-Play Audio",
                        subtitle = "Play pronunciation automatically",
                        checked = autoPlayAudio,
                        onCheckedChange = {
                            autoPlayAudio = it
                            sharedPrefs.edit().putBoolean("auto_play_audio", it).apply()
                        }
                    )
                    SettingItem(
                        icon = Icons.Filled.Settings,
                        title = "Text-to-Speech",
                        subtitle = "Configure TTS settings",
                        onClick = onTtsSettingsClick
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Appearance Section
            item {
                SectionTitle(text = "APPEARANCE")
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingItem(
                        icon = Icons.Filled.Settings,
                        title = "Theme",
                        subtitle = if (isDarkTheme) "Dark" else "Light",
                        onClick = {
                            isDarkTheme = !isDarkTheme
                            sharedPrefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                            // Note: Full theme switching requires MainActivity restart
                            android.widget.Toast.makeText(context, 
                                "Theme preference saved. Restart app to apply.", 
                                android.widget.Toast.LENGTH_SHORT).show()
                        },
                        iconColor = MaterialTheme.colorScheme.tertiary
                    )
                    SettingToggleItem(
                        icon = Icons.Filled.Star,
                        title = "Popup Animation",
                        subtitle = "Enable smooth animations",
                        checked = popupAnimation,
                        onCheckedChange = {
                            popupAnimation = it
                            sharedPrefs.edit().putBoolean("popup_animation", it).apply()
                        },
                        iconColor = MaterialTheme.colorScheme.tertiary
                    )
                    SettingToggleItem(
                        icon = Icons.Filled.Edit,
                        title = "Underline Words",
                        subtitle = "Show underlines on lookups",
                        checked = underlineWords,
                        onCheckedChange = {
                            underlineWords = it
                            sharedPrefs.edit().putBoolean("underline_enabled", it).apply()
                        },
                        iconColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Behavior Section
            item {
                SectionTitle(text = "BEHAVIOR")
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingItem(
                        icon = Icons.Filled.Settings,
                        title = "App Filter",
                        subtitle = "Choose where dictionary works",
                        onClick = onAppFilterClick,
                        iconColor = Color(0xFF9C27B0)
                    )
                    SettingToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Daily Reminders",
                        subtitle = "Get daily learning reminders",
                        checked = dailyReminders,
                        onCheckedChange = {
                            dailyReminders = it
                            sharedPrefs.edit().putBoolean("daily_reminders", it).apply()
                        },
                        iconColor = Color(0xFF4CAF50)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // About Section
            item {
                SectionTitle(text = "ABOUT")
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingItem(
                        icon = Icons.Filled.Lock,
                        title = "Privacy Policy",
                        subtitle = "How we protect your data",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
                                android.net.Uri.parse("https://github.com/Osuna0102/TapDictionary/blob/main/privacy-policy.html"))
                            context.startActivity(intent)
                        },
                        iconColor = Color(0xFF9C27B0)
                    )
                    SettingItem(
                        icon = Icons.Default.Info,
                        title = "About TapDictionary",
                        subtitle = "Learn more about this app",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
                                android.net.Uri.parse("https://github.com/Osuna0102/TapDictionary"))
                            context.startActivity(intent)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
