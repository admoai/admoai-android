package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.HorizontalAdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem

/**
 * Home placement preview screen
 *
 * Features:
 * - Gray background simulating a map
 * - Ad card positioned below navigation bar
 * - Theme toggle circles in top corners
 * - Page control dots at bottom (center one filled)
 */
@Composable
fun HomePreviewScreen(
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onAdClick: (AdData) -> Unit,
    onTrackEvent: (String, String) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    
    // Animation values for card refresh effect
    val cardOffsetY by animateDpAsState(
        targetValue = if (isCardVisible) 0.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_offset"
    )
    
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCardVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "card_alpha"
    )

    // Handle refresh animation and observe loading state
    LaunchedEffect(isRefreshing, isLoading) {
        if (isRefreshing) {
            // Hide the card first
            isCardVisible = false
            // Wait for the card to animate out
            kotlinx.coroutines.delay(300)
            // Trigger ad request via callback
            onRefreshClick()
            // Don't show card until loading completes (handled by next condition)
        } else if (!isLoading && !isCardVisible) {
            // Wait a moment for animation smoothness after loading completes
            kotlinx.coroutines.delay(300)
            // Show the card with the new data
            isCardVisible = true
        }
    }
    
    // Reset isRefreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)) // Match iOS systemGray4 color
    ) {
        // Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Navigation bar
            PreviewNavigationBar(
                placement = placement,
                onBackClick = onBackClick,
                onDetailsClick = onDetailsClick,
                onRefreshClick = { isRefreshing = true },
                isRefreshing = isRefreshing
            )
            
            // Horizontal Ad Card with animation - iOS style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp) // Match iOS padding of 70 pts
                    .graphicsLayer(
                        alpha = cardAlpha,
                        translationY = with(density) { cardOffsetY.toPx() }
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                HorizontalAdCard(
                    adData = adData,
                    placementKey = placement.key, // Pass the placement key for click support check
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onAdClick = { clickedAdData ->
                        // Open the full screen creative modal
                        onAdClick(clickedAdData)
                    },
                    onTrackClick = { url ->
                        // Track click events
                        onTrackEvent("click", url)
                    },
                    onTrackImpression = { url ->
                        // Track impression
                        onTrackEvent("impression", url)
                    }
                )
            }
        }
        
        // Theme toggle circles in top corners
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(top = 56.dp), // Add padding for the TopAppBar
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // White circle (light theme)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onThemeToggle() }
            )
            
            // Black circle (dark theme)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .clickable { onThemeToggle() }
            )
        }
        
        // Bottom navigation bar with 4 circles (iOS style)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 4 circles with second one filled (iOS style)
                    for (i in 0 until 4) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == 1) Color.Black else Color(0xFFE0E0E0) // systemGray5 in iOS
                                )
                        )
                    }
                }
            }
        }
        
        // Two stacked floating buttons in bottom right (iOS style)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (i in 0 until 2) {
                Surface(
                    modifier = Modifier
                        .size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
