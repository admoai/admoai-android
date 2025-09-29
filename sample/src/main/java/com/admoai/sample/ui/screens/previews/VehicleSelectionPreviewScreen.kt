package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.components.SearchAdCard
import com.admoai.sample.ui.mapper.AdTemplateMapper
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vehicle Selection preview screen
 *
 * Features:
 * - Light blue background suggesting a route planner
 * - Support for both imageWithText (imageLeft/imageRight styles) and wideImageOnly templates
 * - Row of selectable vehicle icons with the car selected by default
 * - Route summary card showing trip details
 * - Theme toggle circles at top corners (white/black)
 */
@Composable
fun VehicleSelectionPreviewScreen(
    viewModel: MainViewModel,
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onVideoClick: () -> Unit,
    onAdClick: (AdData) -> Unit,
    onTrackEvent: (String, String) -> Unit,
    onThemeToggle: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    // No need for coroutine scope as we're using LaunchedEffect for animations
    
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
    
    // Pulse animation for vehicle icons
    val vehicleIconAlpha = remember { Animatable(1f) }
    
    // Handle refresh animation with vehicle pulse
    LaunchedEffect(isRefreshing, isLoading, adData) {
        if (isRefreshing) {
            // Hide the card first
            isCardVisible = false
            // Pulse the vehicle icon
            vehicleIconAlpha.animateTo(
                targetValue = 0.3f,
                animationSpec = tween(durationMillis = 200)
            )
            vehicleIconAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200)
            )
            // Wait for the card to animate out
            delay(100)
            // Trigger ad request via callback
            onRefreshClick()
            // Don't show card until loading completes (handled by next condition)
        } else if (!isLoading && !isCardVisible && adData != null) {
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
    
    // Show card on initial load when ad data becomes available
    LaunchedEffect(adData) {
        if (adData != null && !isRefreshing) {
            isCardVisible = true
        }
    }

    // List of vehicle icons to display
    val vehicleIcons = listOf(
        Icons.Filled.DirectionsCar,
        Icons.Filled.TwoWheeler, // Using TwoWheeler instead of Moped
        Icons.AutoMirrored.Filled.DirectionsBike,
        Icons.Filled.Flight,
        Icons.AutoMirrored.Filled.DirectionsWalk
    )
    
    // Vehicle types corresponding to icons
    val vehicleTypes = listOf(
        "Car",
        "Scooter",
        "Bike",
        "Plane",
        "Walk"
    )
    
    // Main screen layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)) // Grey background for mockup
    ) {
        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            // Spacer for the nav bar with extra padding for visual separation
            Spacer(modifier = Modifier.height(82.dp))
            
            // Ad Card with animation - positioned at the top below nav bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .graphicsLayer(
                        alpha = cardAlpha,
                        translationY = with(density) { cardOffsetY.toPx() }
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                adData?.let { ad ->
                    when {
                        // For imageWithText template with either imageLeft or imageRight style
                        AdTemplateMapper.isImageWithTextTemplate(ad) -> {
                            SearchAdCard(
                                adData = ad,
                                onImpressionTracked = {
                                    // Track the first impression URL
                                    ad.creatives.firstOrNull()?.tracking?.impressions?.firstOrNull()?.let { impression ->
                                        onTrackEvent("impression", impression.url)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // For wideImageOnly template 
                        AdTemplateMapper.isWideImageOnlyTemplate(ad) -> {
                            // Custom sized card to ensure consistent size with other templates
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    AdCard(
                                        adData = ad,
                                        onAdClick = onAdClick,
                                        onTrackImpression = { url ->
                                            onTrackEvent("impression", url)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)  // Fixed height to match other templates
                                    )
                                }
                            }
                        }
                        // Default fallback
                        else -> {
                            AdCard(
                                adData = ad,
                                onAdClick = onAdClick,
                                onTrackImpression = { url ->
                                    onTrackEvent("impression", url)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // Vehicle icons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer(alpha = vehicleIconAlpha.value),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                vehicleIcons.forEachIndexed { index, icon ->
                    VehicleIcon(
                        icon = icon,
                        label = vehicleTypes[index],
                        isSelected = index == 0
                    )
                }
            }
            
            // Spacer to push route summary to bottom
            Spacer(modifier = Modifier.weight(1f))
            
            // Route summary card at the bottom
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Route Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Text(
                            text = "Destination",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "20 min",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "7.2 miles",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Navigation bar with title - placed on top with high z-index
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
                isRefreshing = isRefreshing,
                hasVideoCreative = viewModel.hasVideoCreative(adData),
                onVideoClick = onVideoClick
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
    }
}

@Composable
fun VehicleIcon(
    icon: ImageVector,
    label: String,
    isSelected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon with background
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
