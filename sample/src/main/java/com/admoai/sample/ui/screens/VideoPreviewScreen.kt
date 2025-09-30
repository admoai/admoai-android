package com.admoai.sample.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultDataSource
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.utils.getSkipOffset
import com.admoai.sdk.utils.isSkippable
import com.admoai.sample.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Screen to demonstrate video ad overlays with simulated player
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewScreen(
    viewModel: MainViewModel,
    placementKey: String,
    onNavigateBack: () -> Unit = {}
) {
    val response by viewModel.response.collectAsState()
    val adData = response?.data?.find { it.placement == placementKey }
    val creative = adData?.creatives?.firstOrNull { viewModel.isVideoCreative(it) }
    
    // Get selected video player from ViewModel
    val selectedPlayer by viewModel.videoPlayer.collectAsState()
    
    // Parse video configuration
    val videoConfig = creative?.let { remember(it) { parseVideoData(it) } }
    
    // Determine if we should use real player
    val useRealPlayer = selectedPlayer == "basic" && videoConfig?.videoAssetUrl != null
    
    // Simulated player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var hasCompleted by remember { mutableStateOf(false) }
    val videoDuration = 30f // 30 seconds simulated video
    
    // Overlay state
    var overlayShown by remember { mutableStateOf(false) }
    var overlayTracked by remember { mutableStateOf(false) }
    
    // Skip state
    val isSkippable = creative?.isSkippable() ?: false
    val skipOffsetSeconds = creative?.getSkipOffset()?.toFloatOrNull() ?: 5f
    val canSkip = isSkippable && currentProgress >= (skipOffsetSeconds / videoDuration)
    
    // End-card state
    var showEndCard by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Get overlay threshold from response or default to 50%
    val overlayAtPercent = creative?.contents?.find { it.key == "overlayAt" }?.value?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
    } ?: 0.5f
    
    // Simulated playback
    LaunchedEffect(isPlaying) {
        if (isPlaying && !hasCompleted) {
            while (isActive && currentProgress < 1f) {
                delay(100) // Update every 100ms
                currentProgress += (0.1f / videoDuration) // Increment based on duration
                
                // Show overlay at threshold
                if (currentProgress >= overlayAtPercent && !overlayShown) {
                    overlayShown = true
                    if (!overlayTracked && creative != null) {
                        // Fire overlayShown tracking
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
                
                if (currentProgress >= 1f) {
                    currentProgress = 1f
                    isPlaying = false
                    hasCompleted = true
                    
                    // Show end-card based on mode
                    val endCardMode = creative?.contents?.find { it.key == "endcard" }?.value?.let {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: "none"
                    
                    if (endCardMode != "none") {
                        showEndCard = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Ad Demo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Creative info
                creative?.let { crtv ->
                    Text(
                        text = "Placement: $placementKey",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Advertiser: ${crtv.advertiser.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Delivery info
                    val delivery = crtv.delivery ?: "json"
                    Text(
                        text = "Delivery: ${delivery.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Parse video data and display for testing
                    val videoConfig = remember(crtv) { parseVideoData(crtv) }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📊 Parsed Video Configuration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            VideoInfoRow("Video URL", videoConfig.videoAssetUrl ?: "Not set")
                            VideoInfoRow("Poster Image", videoConfig.posterImageUrl ?: "Not set")
                            VideoInfoRow("Skippable", videoConfig.isSkippable.toString())
                            VideoInfoRow("Skip Offset", "${videoConfig.skipOffsetSeconds}s")
                            VideoInfoRow("Overlay At", "${(videoConfig.overlayAtPercentage * 100).toInt()}%")
                            VideoInfoRow("Show Close", videoConfig.showClose.toString())
                            VideoInfoRow("Headline", videoConfig.companionHeadline ?: "Not set")
                            VideoInfoRow("CTA", videoConfig.companionCta ?: "Not set")
                            VideoInfoRow("Destination", videoConfig.companionDestinationUrl ?: "Not set")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Video player - Real or Simulated
                    if (useRealPlayer && videoConfig != null) {
                        // Use real BasicVideoPlayer
                        BasicVideoPlayer(
                            videoConfig = videoConfig,
                            creative = crtv,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                            onComplete = {
                                showEndCard = true
                            }
                        )
                    } else {
                        // Simulated player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                        // Video placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isPlaying && !hasCompleted) {
                                Text(
                                    text = "🎬\nSimulated Video Player",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else if (hasCompleted && !showEndCard) {
                                Text(
                                    text = "✓ Video Completed",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        
                        // Overlay UI (shown at threshold)
                        if (overlayShown && !hasCompleted) {
                            VideoOverlayUI(
                                creative = crtv,
                                onClose = {
                                    overlayShown = false
                                    viewModel.fireCustomEvent(crtv, "closeBtn")
                                },
                                onCtaClick = {
                                    viewModel.fireClick(crtv, "button_cta")
                                    // Open destination URL
                                    val destinationUrl = crtv.contents.find { 
                                        it.key == "overlayDestinationUrl" || it.key == "destinationUrl" 
                                    }?.value?.let {
                                        (it as? JsonPrimitive)?.contentOrNull
                                    }
                                    destinationUrl?.let { url ->
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                        
                        // Skip button (shown when skippable and past offset)
                        if (isSkippable && !hasCompleted) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                if (canSkip) {
                                    Button(
                                        onClick = {
                                            currentProgress = 1f
                                            isPlaying = false
                                            hasCompleted = true
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Skip Ad")
                                    }
                                } else {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Skip in ${(skipOffsetSeconds - (currentProgress * videoDuration)).toInt()}s",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // End-card UI
                        if (showEndCard) {
                            VideoEndCardUI(
                                creative = crtv,
                                onClose = {
                                    showEndCard = false
                                    onNavigateBack()
                                }
                            )
                        }
                        
                        // Play/Pause control overlay
                        if (!hasCompleted && !showEndCard) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable {
                                        isPlaying = !isPlaying
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        }  // Close simulated player Box
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Player type indicator
                    Text(
                        text = "🎮 Player: ${if (useRealPlayer) "Basic Player (Real)" else "Simulated Player"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (useRealPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Progress bar and controls (only for simulated player)
                    if (!useRealPlayer) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentProgress * videoDuration),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = formatTime(videoDuration),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Slider(
                            value = currentProgress,
                            onValueChange = { 
                                currentProgress = it
                                hasCompleted = it >= 1f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        }  // Close progress controls Column
                    }  // Close if (!useRealPlayer)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Info card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Video Ad Configuration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            VideoInfoRow("Delivery", delivery.uppercase())
                            VideoInfoRow("Overlay at", "${(overlayAtPercent * 100).toInt()}%")
                            VideoInfoRow("Skippable", if (isSkippable) "Yes (${skipOffsetSeconds}s)" else "No")
                            
                            val endCardMode = crtv.contents.find { it.key == "endcard" }?.value?.let {
                                (it as? JsonPrimitive)?.contentOrNull
                            } ?: "none"
                            VideoInfoRow("End-card", endCardMode.replace("_", " ").capitalize())
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (delivery in listOf("vast_tag", "vast_xml")) {
                                    "VAST delivery: Impression/quartile tracking handled by player. " +
                                    "Overlay trackers (overlayShown, closeBtn, button_cta) fired by app."
                                } else {
                                    "JSON delivery: All tracking is app-driven, including impressions and quartiles."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Reset button
                    Button(
                        onClick = {
                            currentProgress = 0f
                            isPlaying = false
                            hasCompleted = false
                            overlayShown = false
                            overlayTracked = false
                            showEndCard = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset")
                    }
                    
                } ?: run {
                    // No video creative found
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No Video Creative Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No video creative was found for placement '$placementKey'. " +
                                      "Make sure you have requested video ads by setting Format to 'Video'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoOverlayUI(
    creative: Creative,
    onClose: () -> Unit,
    onCtaClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
    ) {
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close overlay",
                tint = Color.White
            )
        }
        
        // Overlay content (headline + CTA)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Headline
                val headline = creative.contents.find { 
                    it.key == "headline" || it.key == "overlayHeadline" 
                }?.value?.let {
                    (it as? JsonPrimitive)?.contentOrNull
                } ?: creative.advertiser.name
                
                Text(
                    text = headline ?: "Learn More",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description (optional)
                creative.contents.find { 
                    it.key == "description" || it.key == "overlayDescription" 
                }?.value?.let {
                    (it as? JsonPrimitive)?.contentOrNull
                }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // CTA button
                Button(
                    onClick = onCtaClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ctaText = creative.contents.find { 
                        it.key == "callToAction" || it.key == "cta" 
                    }?.value?.let {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: "Learn More"
                    
                    Text(ctaText)
                }
            }
        }
    }
}

@Composable
private fun VideoEndCardUI(
    creative: Creative,
    onClose: () -> Unit
) {
    val endCardMode = creative.contents.find { it.key == "endcard" }?.value?.let {
        (it as? JsonPrimitive)?.contentOrNull
    } ?: "none"
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (endCardMode) {
            "native_endcard" -> {
                // Render native end-card using creative contents
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Thank you for watching!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val headline = creative.contents.find { it.key == "headline" }?.value?.let {
                            (it as? JsonPrimitive)?.contentOrNull
                        }
                        headline?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
            "vast_companion" -> {
                // Show info that companion would be displayed by player
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VAST Companion Ad",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "In a real implementation, the video player (IMA, ExoPlayer) " +
                                  "would display the VAST companion ad here.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
            else -> {
                // No end-card
                Text(
                    text = "Video Completed",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun VideoInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatTime(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%02d:%02d", mins, secs)
}

/**
 * Data class to hold parsed video configuration from creative
 */
data class VideoPlayerConfig(
    val videoAssetUrl: String?,
    val posterImageUrl: String?,
    val isSkippable: Boolean,
    val skipOffsetSeconds: Int,
    val overlayAtPercentage: Float,
    val showClose: Boolean,
    val companionHeadline: String?,
    val companionCta: String?,
    val companionDestinationUrl: String?
)

/**
 * Parse video data from creative contents
 */
fun parseVideoData(creative: Creative): VideoPlayerConfig {
    val contents = creative.contents.associate { it.key to it.value }
    
    // Extract video URL based on delivery method
    val videoAssetUrl = when (creative.delivery) {
        "vast_tag", "vast_xml" -> creative.vast?.tagUrl // VAST tag URL
        else -> contents["videoAsset"]?.let { // JSON delivery - video asset URL
            (it as? JsonPrimitive)?.contentOrNull
        }
    }
    
    // Extract poster image
    val posterImageUrl = contents["posterImage"]?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    
    // Extract skippable settings
    val isSkippable = contents["isSkippable"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toBoolean()
    } ?: false
    
    val skipOffsetSeconds = contents["skipOffset"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.let { offset ->
            // Parse "00:00:05" format or plain number
            if (offset.contains(":")) {
                val parts = offset.split(":")
                parts.lastOrNull()?.toIntOrNull() ?: 5
            } else {
                offset.toIntOrNull() ?: 5
            }
        }
    } ?: 5
    
    // Extract overlay settings
    val overlayAtPercentage = contents["overlayAtPercentage"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
    } ?: 0.5f
    
    val showClose = contents["showClose"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    } == 1
    
    // Extract companion/overlay data
    val companionHeadline = contents["companionHeadline"]?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    
    val companionCta = contents["companionCta"]?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    
    val companionDestinationUrl = contents["companionDestinationUrl"]?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    
    return VideoPlayerConfig(
        videoAssetUrl = videoAssetUrl,
        posterImageUrl = posterImageUrl,
        isSkippable = isSkippable,
        skipOffsetSeconds = skipOffsetSeconds,
        overlayAtPercentage = overlayAtPercentage,
        showClose = showClose,
        companionHeadline = companionHeadline,
        companionCta = companionCta,
        companionDestinationUrl = companionDestinationUrl
    )
}

/**
 * Basic Video Player using ExoPlayer (for JSON delivery, non-VAST)
 */
@Composable
fun BasicVideoPlayer(
    videoConfig: VideoPlayerConfig,
    creative: Creative,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var hasStarted by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }
    
    // Tracking state
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    // Overlay state
    var overlayShown by remember { mutableStateOf(false) }
    var overlayTracked by remember { mutableStateOf(false) }
    
    // ExoPlayer instance with proper data source factory
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(DefaultDataSource.Factory(context))
            )
            .build()
            .apply {
                videoConfig.videoAssetUrl?.let { url ->
                    val mediaItem = MediaItem.fromUri(url)
                    setMediaItem(mediaItem)
                    prepare()
                }
            }
    }
    
    // Update playback state
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(100) // Update every 100ms
            
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration / 1000f // Convert to seconds
                currentPosition = exoPlayer.currentPosition / 1000f
                isPlaying = exoPlayer.isPlaying
                
                val progress = if (duration > 0) currentPosition / duration else 0f
                
                // Fire start event
                if (exoPlayer.isPlaying && !hasStarted) {
                    hasStarted = true
                }
                
                if (hasStarted && !startTracked) {
                    viewModel.fireCustomEvent(creative, "videoStart")
                    startTracked = true
                }
                
                // Fire quartile events
                if (progress >= 0.25f && !firstQuartileTracked) {
                    viewModel.fireCustomEvent(creative, "videoFirstQuartile")
                    firstQuartileTracked = true
                }
                
                if (progress >= 0.5f && !midpointTracked) {
                    viewModel.fireCustomEvent(creative, "videoMidpoint")
                    midpointTracked = true
                }
                
                if (progress >= 0.75f && !thirdQuartileTracked) {
                    viewModel.fireCustomEvent(creative, "videoThirdQuartile")
                    thirdQuartileTracked = true
                }
                
                // Show overlay at specified percentage
                if (progress >= videoConfig.overlayAtPercentage && !overlayShown) {
                    overlayShown = true
                    if (!overlayTracked) {
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
                
                // Fire complete event
                if (progress >= 0.98f && !hasCompleted) {
                    hasCompleted = true
                    if (!completeTracked) {
                        viewModel.fireCustomEvent(creative, "videoComplete")
                        completeTracked = true
                        onComplete()
                    }
                }
            }
        }
    }
    
    // Cleanup
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(modifier = modifier) {
        // ExoPlayer view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use custom controls
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Custom overlay UI (shown at overlayAtPercentage)
        if (overlayShown && !hasCompleted && videoConfig.companionHeadline != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        videoConfig.companionHeadline?.let { headline ->
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        videoConfig.companionCta?.let { cta ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.fireClick(creative, "cta")
                                    videoConfig.companionDestinationUrl?.let { url ->
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(cta)
                            }
                        }
                    }
                }
            }
        }
        
        // Skip button (top-right)
        if (videoConfig.isSkippable && !hasCompleted) {
            val canSkip = currentPosition >= videoConfig.skipOffsetSeconds
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                if (canSkip) {
                    Button(
                        onClick = {
                            viewModel.fireCustomEvent(creative, "videoSkip")
                            exoPlayer.seekTo(exoPlayer.duration)
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Skip Ad")
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Skip in ${(videoConfig.skipOffsetSeconds - currentPosition.toInt()).coerceAtLeast(0)}s",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        
        // Close button (top-left)
        if (videoConfig.showClose && !hasCompleted) {
            IconButton(
                onClick = {
                    viewModel.fireCustomEvent(creative, "closeBtn")
                    exoPlayer.stop()
                    onComplete()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
        
        // Play/Pause overlay (center)
        if (!hasCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Progress bar (bottom)
        if (duration > 0) {
            LinearProgressIndicator(
                progress = (currentPosition / duration).coerceIn(0f, 1f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }
    }
}
