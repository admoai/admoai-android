package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem

/**
 * Search placement preview screen
 *
 * Features:
 * - Scrolling background with horizontal gray bars simulating search results
 * - Ad card inserted after the third bar
 */
@Composable
fun SearchPreviewScreen(
    viewModel: MainViewModel,
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    // Removed unused onThemeToggle parameter
    // Removed unused onAdClick parameter as search ads don't handle clicks
    onTrackEvent: (String, String) -> Unit = {_, _ -> }
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(adData != null) }
    // No need for density as we're using Dp values directly
    
    // Animation values for card refresh effect
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCardVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "card_alpha"
    )

    // Handle refresh button click - only trigger once
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            // Hide the card first
            isCardVisible = false
            // Wait for the card to animate out
            kotlinx.coroutines.delay(300)
            // Trigger ad request via callback
            onRefreshClick()
        }
    }
    
    // Reset isRefreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }
    
    // Show/hide card when ad data changes (both initial load and refresh)
    LaunchedEffect(adData) {
        if (adData != null && !isLoading && !isRefreshing) {
            kotlinx.coroutines.delay(300) // Small delay for animation smoothness
            isCardVisible = true
        } else if (adData == null && !isLoading) {
            // Hide card when no ad data is available (e.g., empty creatives)
            isCardVisible = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Navigation bar
        PreviewNavigationBar(
            placement = placement,
            onBackClick = onBackClick,
            onDetailsClick = onDetailsClick,
            onRefreshClick = { isRefreshing = true },
            isRefreshing = isRefreshing
        )
        
        // Scrollable search results with ad inserted after third item
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First 3 search result bars
            items(3) { 
                SearchResultBar()
            }
            
            // Ad card after third item
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .graphicsLayer(alpha = cardAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    AdCard(
                        adData = adData,
                        placementKey = "search", // Explicitly set placement key to search
                        onTrackImpression = { url ->
                            // Search ads only need impression tracking, no click handling
                            onTrackEvent("impression", url)
                        },
                        // No onAdClick parameter as search ads don't respond to clicks
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Remaining search result bars
            items(7) { 
                SearchResultBar()
            }
        }
    }
}

@Composable
private fun SearchResultBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.5f))
    )
}
