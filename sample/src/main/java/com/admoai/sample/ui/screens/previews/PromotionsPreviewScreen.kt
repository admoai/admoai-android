package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Promotions placement preview screen
 *
 * Features:
 * - Carousel at the top with auto-scrolling
 * - Grid layout of promotional cards
 * - AdCard inserted in grid layout
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromotionsPreviewScreen(
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAdClick: (AdData) -> Unit = {},
    onTrackEvent: (String, String) -> Unit = {_, _ -> }
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }
    
    // Auto-scrolling carousel pager state
    val pagerState = rememberPagerState(pageCount = { 5 })
    
    // Auto-scroll the carousel
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
            pagerState.animateScrollToPage(nextPage)
        }
    }
    
    // Animation values for ad card refresh effect
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                // Title with iOS-matching style
                Text(
                    text = "Promotions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black, // Use solid color matching iOS style
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                
                // AdCard is the main element - everything else is greyed-out mock UI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer(alpha = cardAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    AdCard(
                        adData = adData,
                        placementKey = "promotions",
                        onAdClick = { clickedAdData -> 
                            onAdClick(clickedAdData)
                        },
                        onTrackImpression = { url ->
                            onTrackEvent("impression", url)
                        }
                    )
                }
                
                // Grey separator
                Spacer(modifier = Modifier.height(16.dp))
                
                // Greyed-out mock categories section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                }
                
                // Greyed-out chip row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) { index ->
                        CategoryChip(
                            text = getCategoryName(index),
                            selected = index == 0
                        )
                    }
                }
                
                // Simplified grid section with fewer mock items
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp)
                ) {
                    // Just show a few greyed-out promotion cards
                    items(4) { index ->
                        PromotionCard(
                            title = "Offer ${index + 1}",
                            description = "Limited time promotion",
                            rating = 4.5f,
                            color = getPromotionColor(index)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselItem() {
    // Greyed-out mock UI style to match iOS reference
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.LightGray.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                // Mock headline - grey box instead of text
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(20.dp)
                        .background(Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Mock description - grey box instead of text
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(14.dp)
                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    // Parameters kept for compatibility with existing calls
    @Suppress("UNUSED_PARAMETER") text: String,
    selected: Boolean
) {
    // Greyed-out mock UI style to match iOS reference
    FilterChip(
        selected = selected,
        onClick = { /* Non-functional in example */ },
        label = { 
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(16.dp)
                    .background(Color.Transparent)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.LightGray.copy(alpha = 0.08f),
            labelColor = Color.Gray.copy(alpha = 0.7f),
            selectedContainerColor = Color.LightGray.copy(alpha = 0.15f),
            selectedLabelColor = Color.Gray.copy(alpha = 0.8f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.LightGray.copy(alpha = 0.2f),
            selectedBorderColor = Color.LightGray.copy(alpha = 0.3f),
            borderWidth = 0.5.dp
        )
    )
}

@Composable
private fun PromotionCard(
    // Parameters kept for compatibility with existing calls
    @Suppress("UNUSED_PARAMETER") title: String,
    @Suppress("UNUSED_PARAMETER") description: String,
    @Suppress("UNUSED_PARAMETER") rating: Float,
    @Suppress("UNUSED_PARAMETER") color: Color
) {
    // Greyed-out mock UI style to match iOS reference
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Mock image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                // Image icon placeholder
                Icon(
                    imageVector = Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mock text content
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Mock rating
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 5 star rating
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Rating count
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(10.dp)
                        .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

// Helper functions for mock UI elements
private fun getPromotionColor(index: Int): Color {
    // Use different shades of grey for mock placeholders
    return when (index % 3) {
        0 -> Color.LightGray.copy(alpha = 0.2f)
        1 -> Color.LightGray.copy(alpha = 0.15f)
        else -> Color.LightGray.copy(alpha = 0.25f)
    }
}

private fun getCategoryName(index: Int): String {
    return when (index) {
        0 -> "Popular"
        1 -> "Nearby"
        2 -> "Trending"
        3 -> "New"
        else -> "Category ${index + 1}"
    }
}
