package com.admoai.sample.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.components.AdCard
import kotlin.math.min

/**
 * Full-screen creative detail modal dialog shown when an ad is clicked.
 * Designed to match the iOS implementation with:
 * - Large cover image at top
 * - Headline and body text
 * - CTA button with custom colors
 * - Publisher info row with AdMoai and Ad badge
 * - Blurred background and spring animations
 * - Tracking for companionOpened event
 */
@Composable
fun CreativeDetailScreen(
    adData: AdData?,
    onDismiss: () -> Unit,
    onTrackEvent: (String, String) -> Unit
) {
    // Extract ad content from the first creative
    val firstCreative = adData?.creatives?.firstOrNull()
    
    // Find specific content items by their keys (mapping API response keys to our expected keys)
    val headline = firstCreative?.contents?.find { it.key == "headline" }
    val body = firstCreative?.contents?.find { it.key == "body" }
    val coverImage = firstCreative?.contents?.find { it.key == "coverImage" } 
        ?: firstCreative?.contents?.find { it.key == "wideImage" }
    val cta = firstCreative?.contents?.find { it.key == "cta" }
    val buttonColor = firstCreative?.contents?.find { it.key == "buttonColor" }
    val buttonTextColor = firstCreative?.contents?.find { it.key == "buttonTextColor" }
    val clickThroughURL = firstCreative?.contents?.find { it.key == "clickThroughURL" }
    
    // Extract string values and clean them up
    val headlineText = headline?.value.toString().removeSurrounding("\"")
    val bodyText = body?.value.toString().removeSurrounding("\"")
    val imageUrlText = coverImage?.value.toString().removeSurrounding("\"")
    val ctaText = cta?.value.toString().removeSurrounding("\"")
    val buttonColorValue = buttonColor?.value.toString().removeSurrounding("\"")
    val buttonTextColorValue = buttonTextColor?.value.toString().removeSurrounding("\"")
    val clickThroughURLText = clickThroughURL?.value.toString().removeSurrounding("\"")
    
    // Parse colors, defaulting to primary if invalid
    val accentColor = try {
        Color(android.graphics.Color.parseColor(buttonColorValue))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
    
    val ctaTextColor = try {
        Color(android.graphics.Color.parseColor(buttonTextColorValue))
    } catch (e: Exception) {
        Color.White
    }
    
    val uriHandler = LocalUriHandler.current
    
    // Track companion opened event when first shown
    LaunchedEffect(adData) {
        if (adData != null) {
            // Find the companionOpened tracking URL in the first creative
            val companionOpenedTracking = firstCreative?.tracking?.custom?.find { it.key == "companionOpened" }
            companionOpenedTracking?.url?.let { url ->
                onTrackEvent("companionOpened", url)
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Blurred background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .blur(4.dp)
                    .clickable { onDismiss() }
            )
            
            // Creative detail card with spring animation
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = false) { /* Consume clicks */ },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Cover image
                        if (imageUrlText.isNotBlank() && imageUrlText != "null") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(Color.LightGray)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = imageUrlText),
                                    contentDescription = "Ad Cover Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        // Text content and CTA
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Headline
                            if (headlineText.isNotBlank() && headlineText != "null") {
                                Text(
                                    text = headlineText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            
                            // Body text
                            if (bodyText.isNotBlank() && bodyText != "null") {
                                Text(
                                    text = bodyText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                            
                            // CTA button
                            if (ctaText.isNotBlank() && ctaText != "null") {
                                Button(
                                    onClick = {
                                        // Handle click-through URL
                                        if (clickThroughURLText.isNotBlank() && clickThroughURLText != "null") {
                                            try {
                                                // Find the click tracking URL in the first creative
                                                val clickTracking = firstCreative?.tracking?.clicks?.find { it.key == "default" }
                                                clickTracking?.url?.let { url ->
                                                    onTrackEvent("click", url)
                                                }
                                                
                                                // Open the click-through URL
                                                uriHandler.openUri(clickThroughURLText)
                                            } catch (e: Exception) {
                                                // Handle error opening URL
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = ctaTextColor
                                    )
                                ) {
                                    Text(text = ctaText, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            
                            // Publisher info row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Building icon
                                Icon(
                                    imageVector = Icons.Default.Domain,
                                    contentDescription = "Publisher",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                // Publisher name
                                Text(
                                    text = "AdMoai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Ad pill
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.1f),
                                    modifier = Modifier.height(16.dp)
                                ) {
                                    Text(
                                        text = "Ad",
                                        style = MaterialTheme.typography.labelSmall,
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
    }
}
