package com.admoai.sample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.mapper.AdTemplateMapper

/**
 * Composable for rendering search placement ads.
 * 
 * Supports two styles:
 * - imageLeft: Square image on the left, headline text on the right
 * - imageRight: Square image on the right, headline text on the left
 *
 * @param adData AdData containing the creative to render
 * @param onImpressionTracked Callback for when impression is tracked
 * @param modifier Modifier for styling
 */
@Composable
fun SearchAdCard(
    adData: AdData?,
    onImpressionTracked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (adData == null) return
    
    val creative = adData.creatives.firstOrNull() ?: return
    val isImageRightStyle = AdTemplateMapper.isImageRightStyle(adData)
    
    // Get creative contents
    val imageUrl = AdTemplateMapper.getContentValue(creative, "squareImage")
    val headline = AdTemplateMapper.getContentValue(creative, "headline")
    val advertiserName = creative.advertiser.name
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Create a stable key for impression tracking based on placement and creative ID
        val adPlacement = adData.placement
        val creativeId = creative.metadata?.creativeId ?: "unknown"
        val impressionKey = remember(adPlacement, creativeId) {
            "${adPlacement}_${creativeId}"
        }
        
        LaunchedEffect(impressionKey) {
            onImpressionTracked()
        }
        
        if (isImageRightStyle) {
            // Layout for imageRight style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Content (Left)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Headline
                    headline?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Advertiser with Ad badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        advertiserName?.let { name ->
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        // Ad badge
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Ad",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Image (Right)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant) // Background color makes boundaries visible
                ) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "Ad Image",
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
                                        imageVector = androidx.compose.material.icons.Icons.Filled.BrokenImage,
                                        contentDescription = "Error loading image",
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
        } else {
            // Layout for imageLeft style (default)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Image (Left)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant) // Background color makes boundaries visible
                ) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = "Ad Image",
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
                                        imageVector = androidx.compose.material.icons.Icons.Filled.BrokenImage,
                                        contentDescription = "Error loading image",
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }
                
                // Content (Right)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Headline
                    headline?.let { text ->
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Advertiser with Ad badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        advertiserName?.let { name ->
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        // Ad badge
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Ad",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
