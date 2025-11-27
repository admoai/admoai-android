package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import com.admoai.sample.ui.model.PlacementItem

/**
 * Standard navigation bar for preview screens
 *
 * Features:
 * - Standard height (56dp + top safe area)
 * - Left: Back button (blue text)
 * - Center: Two-line stack with placement name and key
 * - Right: Icons - document (response details), and refresh (optional)
 */
@Composable
fun PreviewNavigationBar(
    placement: PlacementItem,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    isRefreshing: Boolean = false,
    showRefreshButton: Boolean = true
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "refresh_rotation"
    )

    @OptIn(ExperimentalMaterial3Api::class)
    CenterAlignedTopAppBar(
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = placement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = placement.key,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            // Document icon for response details
            IconButton(onClick = onDetailsClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article, 
                    contentDescription = "Response Details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Refresh icon with animation (conditionally shown)
            if (showRefreshButton) {
                IconButton(
                    onClick = { if (!isRefreshing) onRefreshClick() },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Ad",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsLayer { 
                            rotationZ = rotation
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
