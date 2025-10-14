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
                    "native_endcard" to "Native",
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Clarification about end-card modes
            Text(
                text = "💡 None/Native modes are for demo purposes. In production, end-cards are determined by template configuration and how the publisher interprets template fields. Only VAST Companion requires explicit template-level configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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

/**
 * Section to select video player implementation
 */
@Composable
fun VideoPlayerSection(
    viewModel: MainViewModel,
    onOpenUrl: (String) -> Unit = {}
) {
    val videoPlayer by viewModel.videoPlayer.collectAsState()
    
    SectionContainer(title = "Video Player") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Info disclaimer
            Text(
                text = "ℹ️ Select which video player implementation to use in the demo. This represents the app owner's choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Video Player selection - Vertical layout for better fit
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data class PlayerOption(
                    val value: String, 
                    val label: String, 
                    val description: String,
                    val isCommercial: Boolean = false
                )
                
                listOf(
                    PlayerOption(
                        "exoplayer", 
                        "Media3 ExoPlayer + IMA", 
                        "VAST Tag: auto-tracking. The rest is manual: tracking, end-cards, and skip"
                    ),
                    PlayerOption(
                        "vast_client", 
                        "Media3 ExoPlayer", 
                        "Manual VAST parsing - All delivery methods with full control"
                    ),
                    PlayerOption(
                        "jwplayer", 
                        "JW Player", 
                        "Professional-grade player (Commercial license required)",
                        isCommercial = true
                    )
                ).forEach { option ->
                    FilterChip(
                        selected = videoPlayer == option.value,
                        onClick = { viewModel.setVideoPlayer(option.value) },
                        label = { 
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (option.isCommercial) {
                                        Text(
                                            text = "💼",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // JW Player commercial notice
            if (videoPlayer == "jwplayer") {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "📺 About JW Player",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "JW Player is a professional-grade player that handles VAST ads, is OM SDK certified, and provides UI out-of-the-box. This requires a commercial license from JW Player. This option is for demonstration purposes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                onOpenUrl("https://docs.admoai.com")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("View Integration Guide")
                        }
                    }
                }
            }
        }
    }
}
