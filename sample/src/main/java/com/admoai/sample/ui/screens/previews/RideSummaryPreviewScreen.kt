package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.unit.dp
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.components.AdCard
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.delay

/**
 * Ride Summary preview screen
 *
 * Features:
 * - White background with receipt-style list
 * - Ad card positioned 16dp above bottom safe area
 * - Five rating stars 12dp below nav bar
 * - Card slides in from right on first load
 */
@Composable
fun RideSummaryPreviewScreen(
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAdClick: (AdData) -> Unit = {},
    onTrackEvent: (String, String) -> Unit = {_, _ -> },
    @Suppress("UNUSED_PARAMETER") onThemeToggle: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    // Card entry animation (slide in from right)
    val cardOffsetX by animateDpAsState(
        targetValue = if (isCardVisible) 0.dp else 300.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_offset_x"
    )
    
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
    
    // Animate card in on first composition
    LaunchedEffect(Unit) {
        delay(200)
        isCardVisible = true
    }

    // Receipt items for the summary
    val receiptItems = remember {
        listOf(
            ReceiptItem("Base Fare", "$12.50"),
            ReceiptItem("Time", "$3.75"),
            ReceiptItem("Distance", "$8.25"),
            ReceiptItem("Subtotal", "$24.50"),
            ReceiptItem("Service Fee", "$2.00"),
            ReceiptItem("Total", "$26.50", isTotal = true)
        )
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
            
            // Rating stars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Five stars, all filled for 5-star rating
                for (i in 1..5) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Star $i",
                        tint = Color(0xFFFFC107), // Amber
                        modifier = Modifier
                            .size(28.dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }
            
            Text(
                text = "Thanks for riding with us!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // Receipt list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Trip Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Origin-destination info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PICKUP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "123 Main St",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DROPOFF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "456 Oak Ave",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Receipt items
                    receiptItems.forEach { item ->
                        ReceiptItemRow(item)
                    }
                }
            }
            
            // Payment info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Payment Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    
                    Text(
                        text = "Charged to Visa •••• 1234",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Add spacing before ad card
            Spacer(modifier = Modifier.height(24.dp))
            
            // Ad card with slide-in animation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer(
                        alpha = cardAlpha,
                        translationX = with(density) { cardOffsetX.toPx() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AdCard(
                    adData = adData,
                    placementKey = placement.key,
                    onAdClick = { clickedAdData -> 
                        onAdClick(clickedAdData)
                    },
                    onTrackImpression = { url ->
                        onTrackEvent("impression", url)
                    },
                    onTrackClick = { url ->
                        onTrackEvent("click", url)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Spacer at bottom for padding
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class ReceiptItem(
    val description: String,
    val amount: String,
    val isTotal: Boolean = false
)

@Composable
fun ReceiptItemRow(item: ReceiptItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.isTotal) FontWeight.SemiBold else FontWeight.Normal
        )
        
        Text(
            text = item.amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.isTotal) FontWeight.SemiBold else FontWeight.Normal
        )
    }
    
    if (item.isTotal) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 2.dp
        )
    }
}
