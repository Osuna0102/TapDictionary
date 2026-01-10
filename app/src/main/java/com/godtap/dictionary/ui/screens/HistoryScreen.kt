package com.godtap.dictionary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class HistoryItem(
    val word: String,
    val reading: String,
    val translation: String,
    val time: String,
    val count: Int
)

@Composable
fun HistoryScreen(
    onWordClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Load real data from database
    var historyData by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var totalLookups by remember { mutableStateOf(0) }
    var mostLooked by remember { mutableStateOf<HistoryItem?>(null) }
    var todayCount by remember { mutableStateOf(0) }
    var dayStreak by remember { mutableStateOf(0) }
    
    val repository = remember { 
        val database = com.godtap.dictionary.database.AppDatabase.getDatabase(context)
        com.godtap.dictionary.repository.DictionaryRepository(database)
    }
    
    // Load data on mount
    LaunchedEffect(Unit) {
        scope.launch {
            // Get recent lookups
            val entries = repository.getRecentLookups(20)
            historyData = entries.map { entry ->
                HistoryItem(
                    word = entry.primaryExpression ?: entry.primaryReading,
                    reading = entry.primaryReading,
                    translation = entry.getPrimaryGloss(),
                    time = "Recently",
                    count = entry.lookupCount
                )
            }
            
            // Get total lookup count
            totalLookups = repository.getTotalLookupCount()
            
            // Get most looked up word
            val mostLookedEntry = repository.getMostLookedUp()
            if (mostLookedEntry != null) {
                mostLooked = HistoryItem(
                    word = mostLookedEntry.primaryExpression ?: mostLookedEntry.primaryReading,
                    reading = mostLookedEntry.primaryReading,
                    translation = mostLookedEntry.getPrimaryGloss(),
                    time = "Most popular",
                    count = mostLookedEntry.lookupCount
                )
            }
            
            // Today count (approximation - count of recent entries)
            todayCount = historyData.size
            
            // Day streak (placeholder - would need proper date tracking)
            dayStreak = if (totalLookups > 0) 1 else 0
        }
    }

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
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your learning progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        icon = Icons.Filled.DateRange,
                        value = todayCount.toString(),
                        label = "Today",
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        icon = Icons.Filled.Star,
                        value = totalLookups.toString(),
                        label = "Lookups",
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        icon = Icons.Default.Favorite,
                        value = dayStreak.toString(),
                        label = "Day Streak",
                        iconColor = Color(0xFF4CAF50), // Green
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Most Looked Up Card
            mostLooked?.let { item ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Most Looked Up",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.word,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = item.translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${item.count}×",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Recent Lookups Title
            item {
                Text(
                    text = "RECENT LOOKUPS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // History List
            items(historyData) { item ->
                HistoryListItem(
                    item = item,
                    onClick = { onWordClick(item.word, item.translation) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatsCard(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.1f)
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryListItem(
    item: HistoryItem,
    onClick: () -> Unit
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.reading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.translation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${item.count}×",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
