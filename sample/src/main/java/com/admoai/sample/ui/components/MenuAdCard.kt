package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.MetadataPriority

/**
 * MenuAdCard composable for rendering text-only ad in menu placement.
 * This card displays only a text message with advertiser name.
 * No click handling as per requirements.
 * Layout matches the lateral menu design from Flutter/iOS example.
 * No images are shown since this is a textOnly template.
 */
@Composable
fun MenuAdCard(
    adData: AdData,
    onImpressionTracked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Extract content from ad data
    val creative = adData.creatives.firstOrNull() ?: return
    
    // Extract text content directly
    val textContent = creative.contents.find { it.key == "text" }
    val text = textContent?.value?.toString()?.trim('"') ?: "Advertisement"
    
    // Extract advertiser info (advertiser is non-nullable in Creative class)
    val advertiserName = creative.advertiser.name ?: "Sponsored"
    
    // Determine if "Sponsored" label should be shown based on priority
    val showSponsoredLabel = creative.metadata.priority == MetadataPriority.SPONSORSHIP
    
    // Create a stable key for impression tracking based on placement and creative ID
    val adPlacement = adData.placement
    val creativeId = creative.metadata.creativeId
    val impressionKey = remember(adPlacement, creativeId) {
        "${adPlacement}_${creativeId}"
    }
    
    // Use LaunchedEffect to ensure impression is tracked only once per unique ad
    LaunchedEffect(impressionKey) {
        println("MenuAdCard: Tracking impression for placement $adPlacement creative $creativeId")
        onImpressionTracked()
    }
    
    // Card with text-only layout styled as a lateral menu item (no images)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text content column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Main text content
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Only show advertiser name if we have one
                if (advertiserName != "Sponsored") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = advertiserName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Sponsored badge - only show when priority is SPONSORSHIP
            if (showSponsoredLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sponsored",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
