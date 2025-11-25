package com.admoai.sample.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.mapper.AdTemplateMapper

/**
 * Horizontal ad card layout specifically for Home placement
 * 
 * This matches the iOS layout with two styles:
 * 1. Standard style:
 *  - Thin horizontal card (not tall vertical)
 *  - Small square image on left
 *  - Single-line headline, no description text
 *  - No CTA button
 *  - Publisher info line with building icon, "AdMoai" and "Ad" pill
 * 2. WideImageOnly style:
 *  - Full-width image card
 *  - AdMoai branding and Ad pill overlaid on bottom of image
 *  - No headline or text content displayed
 */

/**
 * Placeholder version of HorizontalAdCard when no ad data is available
 */
@Composable
fun HorizontalPlaceholderAdCard(
    modifier: Modifier = Modifier,
    isWideImageOnly: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        // Use different layouts based on style
        if (isWideImageOnly) {
            // WideImageOnly placeholder - full width image with branding overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Placeholder background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Image placeholder",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Bottom footer overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // AdMoai branding
                        Text(
                            text = "AdMoai",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        
                        // Ad pill
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.height(16.dp)
                        ) {
                            Text(
                                text = "Ad",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Standard horizontal card layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left placeholder image
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Image placeholder",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Placeholder headline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Placeholder publisher row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main HorizontalAdCard component that renders ad content based on style
 */
@Composable
fun HorizontalAdCard(
    adData: AdData?,
    placementKey: String = "", // Add placementKey parameter with default value
    modifier: Modifier = Modifier,
    onAdClick: (AdData) -> Unit = {},
    onTrackClick: (String) -> Unit = {}, // Add dedicated click tracking
    onTrackImpression: (String) -> Unit = {}
) {
    if (adData == null) {
        // Display placeholder card if no ad data is available
        HorizontalPlaceholderAdCard(modifier = modifier)
        return
    }
    
    // Extract creative from ad data (used throughout the component)
    val firstCreative = adData.creatives.firstOrNull()
    val adPlacement = adData.placement
    val creativeId = firstCreative?.metadata?.creativeId
    
    // Create a stable key for impression tracking
    val impressionKey = remember(adPlacement, creativeId) {
        "${adPlacement}_${creativeId ?: "unknown"}"
    }
    
    // Use LaunchedEffect to ensure impression is tracked only once per ad instance
    LaunchedEffect(impressionKey) {
        firstCreative?.tracking?.impressions?.find { it.key == "default" }?.let { impression ->
            onTrackImpression(impression.url)
        }
    }
    
    // Use template mapper to determine style
    val isWideImageOnly = AdTemplateMapper.isWideImageOnlyStyle(adData)
    
    // For wideImageOnly style, prioritize wideImage over other image types
    val coverImage = if (isWideImageOnly) {
        firstCreative?.contents?.find { it.key == "wideImage" }
            ?: firstCreative?.contents?.find { it.key == "coverImage" }
            ?: firstCreative?.contents?.find { it.key == "squareImage" }
    } else {
        firstCreative?.contents?.find { it.key == "coverImage" }
            ?: firstCreative?.contents?.find { it.key == "squareImage" }
            ?: firstCreative?.contents?.find { it.key == "wideImage" }
    }
    
    // Extract string values using the helper
    val headlineText = AdTemplateMapper.getContentValue(firstCreative, "headline")
    val imageUrlText = coverImage?.value?.toString()?.removeSurrounding("\"")
    
    // Use different layouts based on the template style
    if (isWideImageOnly) {
        // WideImageOnly style - full width image with branding overlay
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .height(70.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    // Track click event first
                    firstCreative?.tracking?.clicks?.find { it.key == "default" }?.let { click ->
                        onTrackImpression(click.url) // Reusing the impression handler for clicks 
                    }
                    onAdClick(adData)
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // Full-width image layout with branding overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Background image
                // Background image with SubcomposeAsyncImage for better error handling
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (imageUrlText != null && imageUrlText.isNotBlank() && imageUrlText != "null") {
                        SubcomposeAsyncImage(
                            model = imageUrlText,
                            contentDescription = headlineText ?: "Ad Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                                is coil.compose.AsyncImagePainter.State.Error -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Error loading image",
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = "Image placeholder",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Bottom footer overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // AdMoai branding
                        Text(
                            text = "AdMoai",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        
                        // Ad pill
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.height(16.dp)
                        ) {
                            Text(
                                text = "Ad",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Standard horizontal card layout (Prime Selection in reference image)
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .height(70.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { 
                    if (AdTemplateMapper.supportsClickthrough(placementKey)) {
                        // Track click event first
                        firstCreative?.tracking?.clicks?.find { it.key == "default" }?.let { click ->
                            onTrackClick(click.url) // Use dedicated click tracking handler
                        }
                        // Then trigger the click action (open modal)
                        onAdClick(adData)
                    }
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White) // Match exact white from iOS reference
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp), // Match iOS padding exactly
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left image square
                Box(
                    modifier = Modifier
                        .size(50.dp) // Match exact iOS reference size
                        .clip(RoundedCornerShape(4.dp)) // Slightly less rounded in iOS reference
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrlText != null && imageUrlText.isNotBlank() && imageUrlText != "null") {
                        SubcomposeAsyncImage(
                            model = imageUrlText,
                            contentDescription = "Ad Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    }
                                }
                                is coil.compose.AsyncImagePainter.State.Error -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Error loading image",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "Image placeholder",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Headline text - Prime Selection in reference image
                    Text(
                        text = if (headlineText != null && headlineText.isNotBlank() && headlineText != "null") 
                                headlineText else "Prime Selection",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), // Match iOS font size
                        fontWeight = FontWeight.SemiBold, // Match iOS font weight
                        maxLines = 1,
                        color = Color.Black, // Ensure text is black for matching reference
                        overflow = TextOverflow.Ellipsis
                    )
                                // Publisher row with icon, name, and Ad pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Building icon (uses browser icon in iOS reference)
                        Icon(
                            imageVector = Icons.Default.Domain,
                            contentDescription = "Publisher",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        // Publisher name
                        Text(
                            text = "AdMoai",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Surface(... Ad pill)
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier.height(16.dp)
                        ) {
                            Text(
                                text = "Ad",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), // Match iOS size
                                color = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
