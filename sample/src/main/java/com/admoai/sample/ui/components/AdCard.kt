package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
// Removing duplicate background import as it's already imported on line 16
import coil.compose.rememberAsyncImagePainter
import com.admoai.sample.R
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.components.SearchAdCard
import com.admoai.sample.ui.components.MenuAdCard
import com.admoai.sample.ui.mapper.AdTemplateMapper
import com.admoai.sample.ui.model.AdContent
import android.util.Log

/**
 * Empty ad card placeholder shown when no ad data is available
 */
@Composable
fun EmptyAdCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(enabled = true) {
                // Handle click event
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "Image placeholder",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Placeholder headline
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Placeholder description
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Placeholder CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Footer with brand and Ad pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AdMoai",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Ad",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shared Ad Card component used across all placement previews
 * 
 * Features:
 * - Delegates to appropriate specialized ad card component based on template key and style
 * - Uses AdTemplateMapper to determine the correct composable to render
 * - Supports different layouts for different placements
 * - Handles impression tracking automatically
 */
@Composable
fun AdCard(
    adData: AdData?,
    placementKey: String = "",
    modifier: Modifier = Modifier,
    onAdClick: (AdData) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTrackImpression: (String) -> Unit = {}
) {
    if (adData == null) {
        // Display placeholder card if no ad data is available
        EmptyAdCard(modifier = modifier)
        return
    }
    
    // Extract first creative if available
    val firstCreative = adData.creatives.firstOrNull()
    
    // Use LaunchedEffect to ensure impression is tracked only once per ad instance
    // Using placement + creative metadata ID as stable keys to ensure we only track when the ad changes
    val adPlacement = adData.placement
    val creativeId = firstCreative?.metadata?.creativeId
    
    // Create a stable key for impression tracking
    val impressionKey = remember(adPlacement, creativeId) {
        "${adPlacement}_${creativeId ?: "unknown"}"
    }
    
    LaunchedEffect(impressionKey) {
        // Track impression when card is first displayed - will only execute once per unique ad
        firstCreative?.tracking?.impressions?.find { it.key == "default" }?.let { impression ->
            println("Tracking impression for placement $adPlacement creative $creativeId")
            onTrackImpression(impression.url)
        }
    }
    
    // Use AdTemplateMapper to determine which card type to use
    val templateKey = AdTemplateMapper.getTemplateKey(adData) // This returns String?
    
    // Select the appropriate ad card type based on template key and style
    when {
        // Handle home placement (wideWithCompanion template)
        // Use equality check that handles null safely
        (templateKey == "wideWithCompanion") || placementKey == "home" -> {
            HorizontalAdCard(
                adData = adData,
                placementKey = placementKey,
                modifier = modifier,
                onAdClick = onAdClick,
                onTrackClick = onTrackClick,
                onTrackImpression = onTrackImpression
            )
        }
        // Handle search placement
        placementKey == "search" -> {
            SearchAdCard(
                adData = adData,
                onImpressionTracked = {
                    // Handle impression tracking for search placement
                    firstCreative?.tracking?.impressions?.find { it.key == "default" }?.let { impression ->
                        onTrackImpression(impression.url)
                    }
                },
                modifier = modifier
                // Note: No click handler for search placement per requirements
            )
        }
        
        // Handle menu placement
        placementKey == "menu" || AdTemplateMapper.isTextOnlyTemplate(adData) -> {
            MenuAdCard(
                adData = adData,
                onImpressionTracked = {
                    // Handle impression tracking for menu placement
                    firstCreative?.tracking?.impressions?.find { it.key == "default" }?.let { impression ->
                        onTrackImpression(impression.url)
                    }
                },
                modifier = modifier
                // Note: No click handler for menu placement per requirements
            )
        }
        // Handle promotions placement with carousel
        placementKey == "promotions" || AdTemplateMapper.isCarouselTemplate(adData) -> {
            PromotionsCarouselCard(
                adData = adData,
                onTrackImpression = { trackingUrl ->
                    onTrackImpression(trackingUrl)
                },
                modifier = modifier
                // Note: No click handler for promotions placement per requirements
            )
        }
        // Handle rideSummary placement
        placementKey == "rideSummary" || AdTemplateMapper.isStandardTemplate(adData) -> {
            HorizontalAdCard(
                adData = adData,
                placementKey = placementKey,
                modifier = modifier,
                onAdClick = onAdClick,
                onTrackClick = onTrackClick,
                onTrackImpression = onTrackImpression
            )
        }
        // Add additional template mappings here as they are defined
        // Example: templateKey == "searchResult" || placementKey == "search" -> { SearchResultAdCard(...) }
        
        // Default fallback to standard vertical card layout
        else -> {
            // Use a vertical card as fallback (the old implementation would go here)
            // For now, use a placeholder indicating we need a new component
            EmptyAdCard(modifier = modifier)
        }
    }
}