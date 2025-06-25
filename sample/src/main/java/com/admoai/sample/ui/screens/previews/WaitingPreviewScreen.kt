package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.delay

/**
 * Waiting placement preview screen
 * 
 * Shows:
 * - Grey background in main area (simulating map)
 * - Bottom sheet with loading indicator and ad card
 * - Theme toggle circles at top corners
 */
@Composable
fun WaitingPreviewScreen(
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAdClick: (AdData) -> Unit = {},
    onTrackEvent: (String, String) -> Unit = {_, _ -> },
    onThemeToggle: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(0) }
    val pageCount = 3 // Number of carousel pages
    
    // Auto advance carousel pages
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentPage = (currentPage + 1) % pageCount
        }
    }
    
    // Animation for ad card visibility
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCardVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "card_alpha"
    )

    // Handle refresh animation and observe loading state
    LaunchedEffect(isRefreshing, isLoading) {
        if (isRefreshing) {
            // Hide the card first
            isCardVisible = false
            // Wait for the card to animate out
            delay(300)
            // Trigger ad request via callback
            onRefreshClick()
            // Don't show card until loading completes (handled by next condition)
        } else if (!isLoading && !isCardVisible) {
            // Wait a moment for animation smoothness after loading completes
            delay(300)
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

    // Main container
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF9E9E9E))) {
        // Main content layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            // Wait background area (empty grey area)
            Spacer(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            )
        }
        
        // Navigation bar - placed with high z-index to ensure it's clickable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            PreviewNavigationBar(
                placement = placement,
                onBackClick = onBackClick,
                onDetailsClick = onDetailsClick,
                onRefreshClick = { isRefreshing = true },
                isRefreshing = isRefreshing
            )
        }
        
        // Theme toggle circles in top corners
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(top = 56.dp) // Add padding for the TopAppBar
                .zIndex(5f), // Ensure clickable but below nav
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

        // Fixed bottom layout - not a modal sheet
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sheet handle
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.LightGray)
                )
                
                // Loading indicator row
                Row(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small linear progress indicator
                    LoadingProgressBar(
                        modifier = Modifier
                            .width(60.dp)
                            .padding(end = 12.dp)
                    )
                    
                    // Looking for a driver text
                    Text(
                        text = "Looking for a driver...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                
                // Ad card with animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .graphicsLayer { alpha = cardAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    adData?.let {
                        AdCard(
                            adData = it,
                            onAdClick = { clickedAdData -> 
                                onAdClick(clickedAdData)
                            },
                            onTrackImpression = { url ->
                                onTrackEvent("impression", url)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                
                // Page indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pageCount) { i ->
                        val isSelected = i == currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.LightGray
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated loading progress indicator that mimics iOS design
 */
@Composable
fun LoadingProgressBar(modifier: Modifier = Modifier) {
    // Create an animatable to handle the animation
    val progress = remember { Animatable(0f) }
    
    // Animation effect
    LaunchedEffect(Unit) {
        // Continuous animation loop
        while (true) {
            // Animate to end value (full width)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(3000, easing = LinearEasing)
            )
            
            // Reset to start
            progress.snapTo(0f)
            
            // Small pause before restarting
            delay(50)
        }
    }
    
    Box(modifier = modifier
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(progress.value)
            .background(Color.White.copy(alpha = 0.8f))
        )
    }
}
