package com.admoai.sample.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admoai.sample.ui.MainViewModel

/**
 * Section to configure video ad options (shown only when Format = Video)
 */
@Composable
fun VideoOptionsSection(
    viewModel: MainViewModel
) {
    val videoDelivery by viewModel.videoDelivery.collectAsState()
    val videoEndCard by viewModel.videoEndCard.collectAsState()
    val isSkippable by viewModel.isSkippable.collectAsState()
    
    // Get available end card options based on delivery method
    val availableEndCards = when (videoDelivery) {
        "json" -> listOf("none", "native_endcard")
        "vast_tag", "vast_xml" -> listOf("none", "native_endcard", "vast_companion")
        else -> listOf("none")
    }
    
    // Auto-adjust end card when delivery method changes
    LaunchedEffect(videoDelivery) {
        if (videoEndCard !in availableEndCards) {
            viewModel.setVideoEndCard(availableEndCards.first())
        }
    }
    
    SectionContainer(title = "Video Options") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Info disclaimer
            Text(
                text = "ℹ️ These options control how the Video Demo preview renders. They are not sent to the ad server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Delivery method
            Text(
                text = "Delivery Method",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("vast_tag", "vast_xml", "json").forEach { delivery ->
                    FilterChip(
                        selected = videoDelivery == delivery,
                        onClick = { viewModel.setVideoDelivery(delivery) },
                        label = { 
                            Text(
                                when (delivery) {
                                    "vast_tag" -> "VAST Tag"
                                    "vast_xml" -> "VAST XML"
                                    "json" -> "JSON"
                                    else -> delivery
                                }
                            ) 
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // End-card mode
            Text(
                text = "End-card Mode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "none" to "None",
                    "native_endcard" to "Native End-card",
                    "vast_companion" to "VAST Companion"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = videoEndCard == value,
                        onClick = { viewModel.setVideoEndCard(value) },
                        label = { Text(label) },
                        enabled = value in availableEndCards
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Skippable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Skippable",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = isSkippable,
                    onCheckedChange = { viewModel.setSkippable(it) }
                )
            }
            
            // Skip offset (shown only when skippable) - fixed at 5 seconds
            if (isSkippable) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Skip Offset",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "5 seconds",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Help text
            Text(
                text = "For VAST (tag/xml), impression and quartile tracking are handled by the player. " +
                      "You'll fire overlay trackers (overlayShown, closeBtn, button_cta) from your app. " +
                      "For JSON delivery, all tracking is app-driven.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
