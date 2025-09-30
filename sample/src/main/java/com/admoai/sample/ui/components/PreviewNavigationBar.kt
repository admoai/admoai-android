package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import com.admoai.sample.ui.model.PlacementItem

/**
 * Standard navigation bar for preview screens
 *
 * Features:
 * - Standard height (56dp + top safe area)
 * - Left: Back button (blue text)
 * - Center: Two-line stack with placement name and key
 * - Right: Icons - video demo (if hasVideo), document (response details), and refresh
 */
@Composable
fun PreviewNavigationBar(
    placement: PlacementItem,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    isRefreshing: Boolean = false,
    hasVideoCreative: Boolean = false,
    onVideoClick: () -> Unit = {}
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
            // Video demo icon (shown only if hasVideoCreative)
            if (hasVideoCreative) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text(
                                text = "Demo",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                ) {
                    IconButton(onClick = onVideoClick) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Video Demo - Uses Video Options settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Document icon for response details
            IconButton(onClick = onDetailsClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article, 
                    contentDescription = "Response Details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Refresh icon with animation
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
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
