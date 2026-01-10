package com.godtap.dictionary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Feature(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val gradientColors: Pair<Color, Color>
)

@Composable
fun FeaturesScreen(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    val features = listOf(
        Feature(
            icon = Icons.Filled.Star,
            title = "Multi-Language",
            description = "Japanese • Spanish • Korean",
            gradientColors = primaryColor to primaryColor.copy(alpha = 0.7f)
        ),
        Feature(
            icon = Icons.Filled.Star,
            title = "Instant Lookup",
            description = "Tap any word",
            gradientColors = tertiaryColor to tertiaryColor.copy(alpha = 0.7f)
        ),
        Feature(
            icon = Icons.Filled.Favorite,
            title = "Text-to-Speech",
            description = "Hear pronunciation",
            gradientColors = Color(0xFF4CAF50) to Color(0xFF66BB6A)
        ),
        Feature(
            icon = Icons.Filled.Check,
            title = "Learning Stats",
            description = "Track progress",
            gradientColors = Color(0xFF9C27B0) to Color(0xFFBA68C8)
        ),
        Feature(
            icon = Icons.Filled.Phone,
            title = "Works Everywhere",
            description = "All your apps",
            gradientColors = Color(0xFFE91E63) to Color(0xFFF06292)
        ),
        Feature(
            icon = Icons.Filled.Lock,
            title = "Privacy First",
            description = "100% offline",
            gradientColors = Color(0xFF607D8B) to Color(0xFF90A4AE)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        Color(0xFF1565C0)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Powerful Features",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Everything you need to learn languages",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Features Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(features) { feature ->
                    FeatureCard(feature = feature)
                }
            }
        }

        // Bottom caption
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Text(
                text = "All features included free",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun FeatureCard(
    feature: Feature
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon with gradient background
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    feature.gradientColors.first,
                                    feature.gradientColors.second
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Title
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 20.sp
            )

            // Description
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
        }
    }
}
