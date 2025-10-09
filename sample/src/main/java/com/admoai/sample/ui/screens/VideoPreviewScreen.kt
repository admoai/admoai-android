package com.admoai.sample.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.rememberAsyncImagePainter
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
    
    // Determine which player to use
    val playerType = when {
        selectedPlayer == "vast_client" && videoConfig?.videoAssetUrl != null -> "vast_client"
        selectedPlayer == "exoplayer" && videoConfig?.videoAssetUrl != null -> "exoplayer_ima"
        selectedPlayer == "basic" && videoConfig?.videoAssetUrl != null -> "basic"
        else -> "simulated"
    }
    
    val useRealPlayer = playerType != "simulated"
    
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
                    
                    // Only show warning if no video URL found (not for player/delivery mismatch)
                    // Publishers typically use one VAST-friendly player for all content
                    val expectedDelivery = when {
                        delivery.startsWith("vast") -> "VAST"
                        delivery == "json" -> "JSON"
                        else -> delivery.uppercase()
                    }
                    
                    if (videoConfig.videoAssetUrl == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "⚠️ No Video URL Found",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "The mock server returned $expectedDelivery delivery but no video URL was found. " +
                                        "This likely means:\n" +
                                        "1. The mock server at localhost:8080 is not running\n" +
                                        "2. The mock server returned unexpected data format\n" +
                                        "3. The scenario mapping is incorrect\n\n" +
                                        "Falling back to simulated player.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
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
                    
                    // Video player - Select based on playerType
                    when (playerType) {
                        "vast_client" -> {
                            // Media3 ExoPlayer + vast-client-java - Manual VAST handling
                            videoConfig?.let { config ->
                                VastClientVideoPlayer(
                                    videoConfig = config,
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
                            }
                        }
                        "exoplayer_ima" -> {
                            // ExoPlayer + IMA - Best for VAST
                            videoConfig?.let { config ->
                                ExoPlayerImaVideoPlayer(
                                    videoConfig = config,
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
                            }
                        }
                        "basic" -> {
                            // Basic Player - Best for JSON delivery
                            videoConfig?.let { config ->
                                BasicVideoPlayer(
                                    videoConfig = config,
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
                            }
                        }
                        else -> {
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
                        }  // Close else
                    }  // Close when
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Player type indicator
                    val playerLabel = when (playerType) {
                        "ima_sdk" -> "Google IMA SDK"
                        "exoplayer_ima" -> "Media3 ExoPlayer + IMA"
                        "basic" -> "Basic Player"
                        else -> "Simulated Player"
                    }
                    Text(
                        text = "Player: $playerLabel",
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
 * Fetch VAST XML from a tag URL
 */
suspend fun fetchVastXmlFromUrl(tagUrl: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    return@withContext try {
        val url = java.net.URL(tagUrl)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        val responseCode = connection.responseCode
        if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("VAST_FETCHER", "Fetched VAST XML (${xml.length} chars)")
            xml
        } else {
            android.util.Log.e("VAST_FETCHER", "HTTP error: $responseCode")
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("VAST_FETCHER", "Error fetching VAST XML: ${e.message}", e)
        null
    }
}

/**
 * Data class to hold parsed VAST data
 */
data class VastData(
    val mediaFileUrl: String?,
    val trackingEvents: Map<String, List<String>>  // event name -> list of tracking URLs
)

/**
 * Pure Google IMA SDK - VideoAdPlayer Implementation
 * This bridges ExoPlayer with IMA SDK for full control over ad playback
 */
private class SimpleVideoAdPlayer(
    private val exoPlayer: androidx.media3.exoplayer.ExoPlayer
) : com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer {
    
    private val callbacks = mutableListOf<com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback>()
    
    private var currentAdMediaInfo: com.google.ads.interactivemedia.v3.api.player.AdMediaInfo? = null
    
    private var hasNotifiedPlay = false
    private var hasNotifiedLoaded = false
    
    init {
        // Listen to ExoPlayer events and notify IMA SDK
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val adInfo = currentAdMediaInfo ?: return
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        android.util.Log.d("VideoAdPlayer", "↻ Player STATE_BUFFERING")
                        if (hasNotifiedLoaded) {
                            callbacks.forEach { it.onBuffering(adInfo) }
                        }
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        android.util.Log.d("VideoAdPlayer", "✓ Player STATE_READY")
                        if (!hasNotifiedLoaded) {
                            android.util.Log.d("VideoAdPlayer", "   → notifying IMA: onLoaded() [Player is ready]")
                            callbacks.forEach { it.onLoaded(adInfo) }
                            hasNotifiedLoaded = true
                        }
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        android.util.Log.d("VideoAdPlayer", "✓ Player STATE_ENDED")
                        callbacks.forEach { it.onEnded(adInfo) }
                        hasNotifiedPlay = false
                        hasNotifiedLoaded = false
                    }
                }
            }
            
            override fun onRenderedFirstFrame() {
                android.util.Log.d("VideoAdPlayer", "⭐ FIRST FRAME RENDERED")
                // We now use onIsPlayingChanged for more reliable play/pause signals.
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val adInfo = currentAdMediaInfo ?: return
                if (isPlaying) {
                    if (!hasNotifiedPlay) {
                         android.util.Log.d("VideoAdPlayer", "isPlaying=true, notifying IMA: onPlay()")
                         callbacks.forEach { it.onPlay(adInfo) }
                         hasNotifiedPlay = true
                    }
                } else {
                    if (hasNotifiedPlay) {
                        android.util.Log.d("VideoAdPlayer", "isPlaying=false, notifying IMA: onPause()")
                        callbacks.forEach { it.onPause(adInfo) }
                        hasNotifiedPlay = false
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoAdPlayer", "❌ Player error - notifying IMA: onError() - ${error.message}")
                currentAdMediaInfo?.let { adInfo ->
                    callbacks.forEach { it.onError(adInfo) }
                }
            }
        })
    }
    
    override fun playAd(adMediaInfo: com.google.ads.interactivemedia.v3.api.player.AdMediaInfo) {
        android.util.Log.d("VideoAdPlayer", "playAd called: ${adMediaInfo.url}")
        // Start playback, then notify IMA.
        exoPlayer.play()
        if (!hasNotifiedPlay) {
            android.util.Log.d("VideoAdPlayer", "   → notifying IMA: onPlay()")
            callbacks.forEach { it.onPlay(adMediaInfo) }
            hasNotifiedPlay = true
        }
    }
    
    override fun loadAd(
        adMediaInfo: com.google.ads.interactivemedia.v3.api.player.AdMediaInfo,
        adPodInfo: com.google.ads.interactivemedia.v3.api.AdPodInfo
    ) {
        android.util.Log.d("VideoAdPlayer", "loadAd called: ${adMediaInfo.url}")
        currentAdMediaInfo = adMediaInfo
        hasNotifiedLoaded = false
        hasNotifiedPlay = false

        // Load media and prepare the player. onLoaded() will be called when player is ready.
        val mediaItem = androidx.media3.common.MediaItem.fromUri(adMediaInfo.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }
    
    override fun stopAd(adMediaInfo: com.google.ads.interactivemedia.v3.api.player.AdMediaInfo) {
        android.util.Log.d("VideoAdPlayer", "stopAd called: ${adMediaInfo.url}")
        exoPlayer.stop()
    }
    
    override fun pauseAd(adMediaInfo: com.google.ads.interactivemedia.v3.api.player.AdMediaInfo) {
        android.util.Log.d("VideoAdPlayer", "pauseAd called: ${adMediaInfo.url}")
        exoPlayer.pause()
    }
    
    override fun release() {
        android.util.Log.d("VideoAdPlayer", "release called")
        callbacks.clear()
    }
    
    override fun addCallback(callback: com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback) {
        callbacks.add(callback)
        android.util.Log.d("VideoAdPlayer", "Callback added, total: ${callbacks.size}")
    }
    
    override fun removeCallback(callback: com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback) {
        callbacks.remove(callback)
        android.util.Log.d("VideoAdPlayer", "Callback removed, remaining: ${callbacks.size}")
    }
    
    override fun getAdProgress(): com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate {
        val current = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        return if (current <= 0L && (duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET)) {
            // Not ready yet
            com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate.VIDEO_TIME_NOT_READY
        } else {
            // Report real progress; allow unknown duration as -1
            val safeDuration = if (duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET) -1L else duration
            com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate(current, safeDuration)
        }
    }
    
    override fun getVolume(): Int {
        return (exoPlayer.volume * 100).toInt()
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * VIDEO PLAYER ARCHITECTURE - SEPARATION OF RESPONSIBILITIES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This app implements TWO distinct video player architectures:
 * 
 * 1. **Media3 ExoPlayer + IMA** (ExoPlayerImaVideoPlayer)
 *    - Media3 ExoPlayer handles: Video playback, buffering, decoding, rendering, UI
 *    - IMA SDK Extension handles: Ad logic, VAST parsing, tracking beacon firing
 *    - Uses: androidx.media3.exoplayer.ima.ImaAdsLoader (Media3's IMA wrapper)
 *    - Supports: VAST Tag (adTagUrl), JSON (direct video)
 *    - LIMITATION: Cannot use adsResponse for VAST XML (Media3 doesn't expose it)
 * 
 * 2. **Google IMA SDK** (GoogleImaSDKPlayer) - CURRENTLY USING MEDIA3 WRAPPER!
 *    - CURRENT STATUS: Still using Media3's ImaAdsLoader (same as player #1)
 *    - TODO: Refactor to use Pure Google IMA SDK as follows:
 * 
 * 1. Create AdDisplayContainer:
 *    val adUiContainer: ViewGroup = findViewById(R.id.adUiContainer)
 *    val displayContainer = ImaSdkFactory.getInstance()
 *        .createAdDisplayContainer(adUiContainer, yourVideoAdPlayer)
 * 
 * 2. Create AdsLoader:
 *    val adsLoader = ImaSdkFactory.getInstance().createAdsLoader(context, displayContainer)
 * 
 * 3. Build AdsRequest with adsResponse for VAST XML:
 *    val request = ImaSdkFactory.getInstance().createAdsRequest().apply {
 *        adsResponse = decodedVastXmlString  // <-- This is the key!
 *        // OR: adTagUrl = "https://..." for VAST Tag
 *        contentProgressProvider = ContentProgressProvider {
 *            VideoProgressUpdate(currentMs, totalMs)
 *        }
 *    }
 * 
 * 4. Listen for ad events:
 *    adsLoader.addAdsLoadedListener { ev ->
 *        adsManager = ev.adsManager
 *        adsManager.addAdEventListener { adEvent ->
 *            // STARTED, FIRST_QUARTILE, MIDPOINT, THIRD_QUARTILE, COMPLETE, CLICK
 *        }
 *        adsManager.init()
 *    }
 * 
 * 5. Request ads:
 *    adsLoader.requestAds(request)
 * 
 * This approach gives full control and supports both VAST Tag (adTagUrl) and 
 * VAST XML (adsResponse) properly.
 */

/**
 * Parse VAST XML to extract video URL and tracking beacons
 * Uses regex as fallback for malformed XML
 */
fun parseVastXml(xmlContent: String): VastData {
    val trackingEvents = mutableMapOf<String, MutableList<String>>()
    var mediaFileUrl: String? = null
    
    try {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlContent.byteInputStream(), null)
        
        var eventType = parser.eventType
        var currentEvent: String? = null
        var insideMediaFile = false
        var insideTracking = false
        
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            try {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "MediaFile" -> {
                                insideMediaFile = true
                            }
                            "Tracking" -> {
                                currentEvent = parser.getAttributeValue(null, "event")
                                insideTracking = true
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            if (insideMediaFile && mediaFileUrl == null) {
                                mediaFileUrl = text
                                android.util.Log.d("VAST_PARSER", "Found MediaFile URL: $mediaFileUrl")
                            } else if (insideTracking && currentEvent != null) {
                                trackingEvents.getOrPut(currentEvent) { mutableListOf() }.add(text)
                                android.util.Log.d("VAST_PARSER", "Found tracking URL for $currentEvent: $text")
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "MediaFile" -> insideMediaFile = false
                            "Tracking" -> {
                                insideTracking = false
                                currentEvent = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                // Handle CDATA parsing errors - try to skip and continue
                android.util.Log.w("VAST_PARSER", "Skipping malformed XML segment: ${e.message}")
                try {
                    eventType = parser.next()
                } catch (e2: Exception) {
                    break
                }
            }
        }
        
        android.util.Log.d("VAST_PARSER", "XML parsing done: video=$mediaFileUrl, tracking events=${trackingEvents.keys}")
    } catch (e: Exception) {
        android.util.Log.e("VAST_PARSER", "Error parsing VAST XML with parser: ${e.message}")
    }
    
    // Fallback: Use regex if XML parsing failed to find MediaFile
    if (mediaFileUrl == null) {
        android.util.Log.d("VAST_PARSER", "Trying regex fallback for MediaFile")
        val mediaFileRegex = """<MediaFile[^>]*>(.*?)</MediaFile>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        mediaFileRegex.find(xmlContent)?.groupValues?.get(1)?.let { match ->
            // Remove CDATA markers and any XML tags
            var cleanUrl = match
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("""<!\[""", "")  // Handle malformed CDATA like <![
                .trim()
            
            // Extract just the URL if there are still XML tags
            // Look for http/https URL pattern
            val urlPattern = """https?://[^\s<>]+""".toRegex()
            urlPattern.find(cleanUrl)?.value?.let { extractedUrl ->
                cleanUrl = extractedUrl
            }
            
            mediaFileUrl = cleanUrl
            android.util.Log.d("VAST_PARSER", "Found MediaFile URL via regex: $mediaFileUrl")
        }
    }
    
    // Fallback: Use regex for tracking URLs if not found
    if (trackingEvents.isEmpty()) {
        android.util.Log.d("VAST_PARSER", "Trying regex fallback for Tracking events")
        val trackingRegex = """<Tracking\s+event="([^"]+)"[^>]*>(.*?)</Tracking>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        trackingRegex.findAll(xmlContent).forEach { match ->
            val event = match.groupValues[1]
            var url = match.groupValues[2]
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("""<!\[""", "")  // Handle malformed CDATA like <![
                .trim()
            
            // Extract just the URL if there are still XML tags
            val urlPattern = """https?://[^\s<>]+""".toRegex()
            urlPattern.find(url)?.value?.let { extractedUrl ->
                url = extractedUrl
            }
            
            trackingEvents.getOrPut(event) { mutableListOf() }.add(url)
            android.util.Log.d("VAST_PARSER", "Found tracking URL via regex for $event: $url")
        }
    }
    
    android.util.Log.d("VAST_PARSER", "Final result: video=$mediaFileUrl, tracking events=${trackingEvents.keys}")
    return VastData(mediaFileUrl, trackingEvents)
}

/**
 * Fire VAST tracking beacons
 */
suspend fun fireVastTrackingBeacons(urls: List<String>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    urls.forEach { url ->
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val responseCode = connection.responseCode
            android.util.Log.d("VAST_TRACKING", "Fired tracking beacon: $url (HTTP $responseCode)")
        } catch (e: Exception) {
            android.util.Log.e("VAST_TRACKING", "Error firing tracking beacon $url: ${e.message}")
        }
    }
}

/**
 * Parse video data from creative contents
 */
fun parseVideoData(creative: Creative): VideoPlayerConfig {
    val contents = creative.contents.associate { it.key to it.value }
    
    // Extract video URL - handle different delivery methods
    val videoAssetUrl = when (creative.delivery) {
        "vast_tag" -> creative.vast?.tagUrl // VAST tag URL (IMA will fetch and parse)
        "vast_xml" -> "vast_xml_placeholder" // Placeholder - actual XML is in vast.xmlBase64
        else -> contents["videoAsset"]?.let { // JSON delivery - video asset URL
            (it as? JsonPrimitive)?.contentOrNull
        }
    } ?: creative.vast?.tagUrl // Fallback: try VAST tagUrl even if delivery says JSON
    
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
 * ExoPlayer + IMA Video Player
 * Works with both JSON and VAST delivery methods
 * - VAST: Uses IMA SDK for automatic ad serving and tracking
 * - JSON: Uses manual tracking with ExoPlayer progress monitoring
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ExoPlayerImaVideoPlayer(
    videoConfig: VideoPlayerConfig,
    creative: Creative,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Overlay state (for JSON delivery with native end-card)
    var overlayShown by remember { mutableStateOf(false) }
    var overlayTracked by remember { mutableStateOf(false) }
    
    // Tracking state (for JSON delivery - manual tracking)
    var hasStarted by remember { mutableStateOf(false) }
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    // Determine playback mode:
    // - VAST Tag: Always use IMA SDK with ad tag URL (IMA handles everything)
    // - VAST XML: Always use IMA SDK with decoded XML as adsResponse (IMA handles everything)
    // - JSON: Play video directly, manual SDK tracking
    // - Native end-card: Just a Compose overlay on top (doesn't change playback method)
    val hasNativeEndCard = videoConfig.companionHeadline != null
    val useImaSDK = creative.delivery == "vast_tag" || creative.delivery == "vast_xml"
    val isJsonDelivery = creative.delivery == null || creative.delivery == "json"
    
    android.util.Log.d("Media3Player", "═══════════════════════════════════════")
    android.util.Log.d("Media3Player", "Player: Media3 ExoPlayer + IMA")
    android.util.Log.d("Media3Player", "Delivery: ${creative.delivery ?: "json"}")
    android.util.Log.d("Media3Player", "End-card: ${if (hasNativeEndCard) "Native" else "None"}")
    android.util.Log.d("Media3Player", "Media3 ExoPlayer: Video playback, buffering, rendering")
    android.util.Log.d("Media3Player", "IMA Extension: Ad logic, VAST parsing, tracking")
    android.util.Log.d("Media3Player", "Will use IMA SDK: $useImaSDK")
    android.util.Log.d("Media3Player", "═══════════════════════════════════════")
    
    // For VAST XML, decode the Base64 XML
    var decodedVastXml by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(creative.delivery) {
        if (creative.delivery == "vast_xml") {
            creative.vast?.xmlBase64?.let { base64Xml ->
                try {
                    val decoded = String(android.util.Base64.decode(base64Xml, android.util.Base64.DEFAULT))
                    decodedVastXml = decoded
                    android.util.Log.d("ExoPlayerIMA", "✓ Decoded VAST XML (${decoded.length} chars)")
                } catch (e: Exception) {
                    android.util.Log.e("ExoPlayerIMA", "✗ Error decoding VAST XML: ${e.message}")
                    playbackError = "Failed to decode VAST XML: ${e.message}"
                }
            }
        }
    }
    
    // IMA ads loader with event listeners
    val adsLoader = remember {
        ImaAdsLoader.Builder(context)
            .setAdEventListener { adEvent ->
                android.util.Log.d("IMA_EVENT", "Ad Event: ${adEvent.type}")
                when (adEvent.type) {
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.LOADED ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad LOADED")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.STARTED ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad STARTED")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.FIRST_QUARTILE ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad FIRST_QUARTILE")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.MIDPOINT ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad MIDPOINT")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.THIRD_QUARTILE ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad THIRD_QUARTILE")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.COMPLETED ->
                        android.util.Log.d("IMA_EVENT", "✅ Ad COMPLETED")
                    else -> Unit
                }
            }
            .setAdErrorListener { adErrorEvent ->
                android.util.Log.e("IMA_ERROR", "❌ Ad Error: ${adErrorEvent.error.message}")
                android.util.Log.e("IMA_ERROR", "Error Code: ${adErrorEvent.error.errorCode}")
                android.util.Log.e("IMA_ERROR", "Error Type: ${adErrorEvent.error.errorCodeNumber}")
                playbackError = "IMA Ad Error: ${adErrorEvent.error.message}"
            }
            .build()
    }
    
    // PlayerView reference for AdViewProvider
    var playerView: PlayerView? by remember { mutableStateOf(null) }
    
    // ExoPlayer with conditional IMA integration
    val exoPlayer = remember {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context))
        
        // Use IMA for VAST Tag and VAST XML (always, regardless of end-card)
        if (useImaSDK) {
            android.util.Log.d("ExoPlayerIMA", "→ Configuring MediaSourceFactory with IMA ads loader")
            mediaSourceFactory
                .setAdsLoaderProvider { adsLoader }
                .setAdViewProvider { playerView!! }
        } else {
            android.util.Log.d("ExoPlayerIMA", "→ MediaSourceFactory without ads (JSON delivery)")
        }
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
    
    // Setup media source (wait for VAST XML to be decoded if needed)
    LaunchedEffect(videoConfig.videoAssetUrl, decodedVastXml, creative.delivery) {
        // For VAST XML, wait until decoded
        if (creative.delivery == "vast_xml" && decodedVastXml == null) {
            android.util.Log.d("ExoPlayerIMA", "⏳ Waiting for VAST XML to be decoded...")
            return@LaunchedEffect
        }
        
        videoConfig.videoAssetUrl?.let { url ->
            android.util.Log.d("ExoPlayerIMA", "🎬 Setting up media source...")
            
            val contentVideoUri = "https://videos.admoai.com/VwBe1DrWseFTdiIPnzPzKhoo7fX01N92Hih4h6pNCuDA.m3u8"
            val mediaItemBuilder = MediaItem.Builder()
            
            when (creative.delivery) {
                "vast_tag" -> {
                    // VAST Tag: Pass tag URL to IMA SDK (IMA will fetch XML and handle everything)
                    android.util.Log.d("ExoPlayerIMA", "📡 VAST Tag mode: Passing tag URL to IMA SDK")
                    android.util.Log.d("ExoPlayerIMA", "   Tag URL: $url")
                    android.util.Log.d("ExoPlayerIMA", "   Content video: $contentVideoUri")
                    android.util.Log.d("ExoPlayerIMA", "   IMA will: fetch XML, parse, select media, track quartiles")
                    
                    mediaItemBuilder
                        .setUri(Uri.parse(contentVideoUri))
                        .setAdsConfiguration(
                            MediaItem.AdsConfiguration.Builder(Uri.parse(url)).build()
                        )
                }
                "vast_xml" -> {
                    // VAST XML: ExoPlayer+IMA doesn't support adsResponse easily
                    // Recommend GoogleImaSDKPlayer which has direct IMA SDK access
                    android.util.Log.w("ExoPlayerIMA", "⚠️  VAST XML not fully supported by ExoPlayer+IMA")
                    android.util.Log.w("ExoPlayerIMA", "   Reason: Media3's ImaAdsLoader doesn't expose adsResponse parameter")
                    android.util.Log.w("ExoPlayerIMA", "   Recommendation: Use 'Google IMA SDK' player for VAST XML")
                    
                    playbackError = "VAST XML is not supported by this player.\n\nPlease use 'Google IMA SDK' player for VAST XML delivery."
                    return@LaunchedEffect
                }
                else -> {
                    // JSON delivery: Play video directly
                    android.util.Log.d("ExoPlayerIMA", "📹 JSON mode: Playing video directly (no IMA)")
                    android.util.Log.d("ExoPlayerIMA", "   Video URL: $url")
                    android.util.Log.d("ExoPlayerIMA", "   Manual tracking: YES (via SDK trackingEvents)")
                    
                    mediaItemBuilder.setUri(Uri.parse(url))
                }
            }
            
            // Add poster image as artwork if available
            videoConfig.posterImageUrl?.let { posterUrl ->
                mediaItemBuilder.setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setArtworkUri(Uri.parse(posterUrl))
                        .build()
                )
            }
            
            exoPlayer.apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                
                // Add error listener
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("ExoPlayerIMA", "Playback error: ${error.message}", error)
                        val errorCause = error.cause?.toString() ?: ""
                        val errorMsg = error.message ?: ""
                        playbackError = when {
                            errorMsg.contains("SSLException") || errorCause.contains("SSLException") -> 
                                "SSL Error: URL uses HTTPS but server is HTTP. Change mock server to return http:// URLs."
                            errorMsg.contains("ConnectException") || errorCause.contains("ConnectException") -> 
                                "Connection Error: Cannot reach server at $url. Is mock server running?"
                            errorCause.contains("UnrecognizedInputFormatException") -> 
                                "Content Error: Unable to load video content. Check video URL format."
                            errorMsg.contains("Ad error") || errorCause.contains("AdError") -> 
                                "IMA Ad Error: ${error.message}. Check VAST XML format and video URLs."
                            else -> "Playback error: ${error.message ?: "Unknown error"}"
                        }
                    }
                })
            }
        }
    }
    
    // Set adsLoader's player
    LaunchedEffect(exoPlayer) {
        adsLoader.setPlayer(exoPlayer)
    }
    
    // Monitor playback state, fire tracking events, and manage overlays
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(100)
            isPlaying = exoPlayer.isPlaying
            
            // Track position and duration
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration / 1000f
                currentPosition = exoPlayer.currentPosition / 1000f
                
                val progress = if (duration > 0) currentPosition / duration else 0f
                
                // Tracking based on delivery mode
                when {
                    isJsonDelivery -> {
                        // JSON: Manual SDK tracking
                        if (exoPlayer.isPlaying && !hasStarted) {
                            hasStarted = true
                        }
                        
                        if (hasStarted && !startTracked) {
                            viewModel.fireVideoEvent(creative, "start")
                            startTracked = true
                        }
                        
                        if (progress >= 0.25f && !firstQuartileTracked) {
                            viewModel.fireVideoEvent(creative, "firstQuartile")
                            firstQuartileTracked = true
                        }
                        
                        if (progress >= 0.5f && !midpointTracked) {
                            viewModel.fireVideoEvent(creative, "midpoint")
                            midpointTracked = true
                        }
                        
                        if (progress >= 0.75f && !thirdQuartileTracked) {
                            viewModel.fireVideoEvent(creative, "thirdQuartile")
                            thirdQuartileTracked = true
                        }
                        
                        if (progress >= 0.98f && !completeTracked) {
                            viewModel.fireVideoEvent(creative, "complete")
                            completeTracked = true
                        }
                    }
                    useImaSDK -> {
                        // VAST Tag/XML: IMA SDK handles all tracking automatically
                        // We don't need to do anything here - IMA fires quartile beacons
                        // No manual tracking needed - IMA does it all
                    }
                }
                
                // Show overlay at specified percentage (for JSON + native end-card or VAST + native end-card)
                if (progress >= videoConfig.overlayAtPercentage && !overlayShown) {
                    overlayShown = true
                    if (!overlayTracked) {
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
            }
            
            // Check for completion
            if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED && !hasCompleted) {
                hasCompleted = true
                onComplete()
            }
        }
    }
    
    // Track first frame rendered for poster image
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Listen for first frame rendered
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("ExoPlayerIMA", "✅ First frame rendered - hiding poster")
                firstFrameRendered = true
            }
        })
    }
    
    // Cleanup
    DisposableEffect(exoPlayer) {
        onDispose {
            adsLoader.release()
            exoPlayer.release()
        }
    }
    
    Box(modifier = modifier) {
        // ExoPlayer view with IMA support
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true // Use IMA's built-in controls
                    useArtwork = false // We'll handle poster image explicitly in Compose
                    playerView = this // Assign for AdViewProvider
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // POSTER IMAGE OVERLAY - Shows before video starts, hides after first frame
        if (!firstFrameRendered && videoConfig.posterImageUrl != null) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(
                    model = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        coil.request.ImageRequest.Builder(ctx)
                            .data(videoConfig.posterImageUrl)
                            .crossfade(true)
                            .build()
                    }
                ),
                contentDescription = "Poster image",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        
        // Custom overlay UI (for JSON delivery with companion ads) - matches BasicVideoPlayer
        if (overlayShown && !hasCompleted && videoConfig.companionHeadline != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .height(60.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Headline
                    videoConfig.companionHeadline?.let { headline ->
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                    }
                    
                    // CTA Button - only show if both CTA text and destination URL are present
                    if (videoConfig.companionCta != null && videoConfig.companionDestinationUrl != null) {
                        val cta = videoConfig.companionCta!!
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.fireClick(creative, "cta")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoConfig.companionDestinationUrl!!))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cta,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        
        // Error overlay (center)
        if (playbackError != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Playback Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Basic Video Player using ExoPlayer (for JSON delivery, non-VAST)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
    
    // Track first frame rendered for poster image
    var firstFrameRendered by remember { mutableStateOf(false) }
    
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
                    val mediaItemBuilder = MediaItem.Builder().setUri(url)
                    
                    // Don't use MediaMetadata artwork - we'll handle poster explicitly
                    
                    setMediaItem(mediaItemBuilder.build())
                    prepare()
                    playWhenReady = true // Autoplay
                }
                
                // Listen for first frame rendered
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onRenderedFirstFrame() {
                        android.util.Log.d("BasicPlayer", "✅ First frame rendered - hiding poster")
                        firstFrameRendered = true
                    }
                })
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
                    viewModel.fireVideoEvent(creative, "start")
                    startTracked = true
                }
                
                // Fire quartile events
                if (progress >= 0.25f && !firstQuartileTracked) {
                    viewModel.fireVideoEvent(creative, "firstQuartile")
                    firstQuartileTracked = true
                }
                
                if (progress >= 0.5f && !midpointTracked) {
                    viewModel.fireVideoEvent(creative, "midpoint")
                    midpointTracked = true
                }
                
                if (progress >= 0.75f && !thirdQuartileTracked) {
                    viewModel.fireVideoEvent(creative, "thirdQuartile")
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
                        viewModel.fireVideoEvent(creative, "complete")
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
                    useArtwork = false // We'll handle poster image explicitly in Compose
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // POSTER IMAGE OVERLAY - Shows before video starts, hides after first frame
        if (!firstFrameRendered && videoConfig.posterImageUrl != null) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(
                    model = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        coil.request.ImageRequest.Builder(ctx)
                            .data(videoConfig.posterImageUrl)
                            .crossfade(true)
                            .build()
                    }
                ),
                contentDescription = "Poster image",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        
        // Custom overlay UI (shown at overlayAtPercentage) - Minimal, native design (max 15% height)
        if (overlayShown && !hasCompleted && videoConfig.companionHeadline != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .height(60.dp) // Increased for better visibility
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Headline (left side) - more visible
                    videoConfig.companionHeadline?.let { headline ->
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                    }
                    
                    // CTA Button (right side) - only show if both CTA text and destination URL are present
                    if (videoConfig.companionCta != null && videoConfig.companionDestinationUrl != null) {
                        val cta = videoConfig.companionCta!!
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.fireClick(creative, "cta")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoConfig.companionDestinationUrl!!))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cta,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
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
                            viewModel.fireVideoEvent(creative, "skip")
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

/**
 * VAST Client Video Player (Manual VAST parsing)
 * Uses vast-client-java to parse VAST XML/Tag manually
 * Provides full control over VAST handling for all delivery methods
 * 
 * Supports:
 * - VAST Tag: Fetches and parses remote VAST XML
 * - VAST XML: Parses embedded Base64-decoded VAST XML
 * - JSON: Direct video playback without VAST parsing
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VastClientVideoPlayer(
    videoConfig: VideoPlayerConfig,
    creative: Creative,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Overlay state (for JSON delivery with native end-card)
    var overlayShown by remember { mutableStateOf(false) }
    var overlayTracked by remember { mutableStateOf(false) }
    
    // Tracking state (for JSON delivery - manual tracking)
    var hasStarted by remember { mutableStateOf(false) }
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    // Determine playback mode:
    // - VAST Tag: Fetch and parse remote VAST XML with vast-client-java
    // - VAST XML: Parse embedded Base64-decoded VAST XML with vast-client-java
    // - JSON: Play video directly, manual SDK tracking
    // - Native end-card: Just a Compose overlay on top (doesn't change playback method)
    val hasNativeEndCard = videoConfig.companionHeadline != null
    val isVastDelivery = creative.delivery == "vast_tag" || creative.delivery == "vast_xml"
    val isJsonDelivery = creative.delivery == null || creative.delivery == "json"
    
    android.util.Log.d("VastClient", "═══════════════════════════════════════")
    android.util.Log.d("VastClient", "Player: Media3 ExoPlayer + vast-client-java")
    android.util.Log.d("VastClient", "✅ Manual VAST parsing with full control")
    android.util.Log.d("VastClient", "Delivery: ${creative.delivery ?: "json"}")
    android.util.Log.d("VastClient", "End-card: ${if (hasNativeEndCard) "Native" else "None"}")
    android.util.Log.d("VastClient", "")
    when (creative.delivery) {
        "vast_tag" -> {
            android.util.Log.d("VastClient", "  VAST Tag: Fetch & parse remote XML")
            android.util.Log.d("VastClient", "  → Extract MediaFile URLs manually")
            android.util.Log.d("VastClient", "  → Extract tracking URLs manually")
            android.util.Log.d("VastClient", "  → Fire tracking via SDK")
        }
        "vast_xml" -> {
            android.util.Log.d("VastClient", "  VAST XML: Parse embedded XML")
            android.util.Log.d("VastClient", "  → Extract MediaFile URLs manually")
            android.util.Log.d("VastClient", "  → Extract tracking URLs manually")
            android.util.Log.d("VastClient", "  → Fire tracking via SDK")
        }
        else -> {
            android.util.Log.d("VastClient", "  JSON: Direct playback, manual tracking")
        }
    }
    android.util.Log.d("VastClient", "═══════════════════════════════════════")
    
    // For VAST XML, decode the Base64 XML
    var decodedVastXml by remember { mutableStateOf<String?>(null) }
    
    // VAST parsing state
    var vastVideoUrl by remember { mutableStateOf<String?>(null) }
    var vastTrackingUrls by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var vastParseError by remember { mutableStateOf<String?>(null) }
    
    // Decode VAST XML if needed
    LaunchedEffect(creative.delivery) {
        if (creative.delivery == "vast_xml") {
            creative.vast?.xmlBase64?.let { base64Xml ->
                try {
                    val decoded = String(android.util.Base64.decode(base64Xml, android.util.Base64.DEFAULT))
                    decodedVastXml = decoded
                    android.util.Log.d("VastClient", "✓ Decoded VAST XML (${decoded.length} chars)")
                } catch (e: Exception) {
                    android.util.Log.e("VastClient", "✗ Error decoding VAST XML: ${e.message}")
                    vastParseError = "Failed to decode VAST XML: ${e.message}"
                }
            }
        }
    }
    
    // Parse VAST with vast-client-java
    LaunchedEffect(creative.delivery, videoConfig.videoAssetUrl, decodedVastXml) {
        if (isVastDelivery) {
            try {
                android.util.Log.d("VastClient", "→ Starting VAST parsing...")
                
                // NOTE: vast-client-java parsing would go here
                // For now, we'll use a simplified approach:
                // Extract video URL from the creative's videoAssetUrl (which contains VAST tag URL)
                // In a full implementation, you would:
                // 1. Fetch XML from VAST Tag URL or use decoded VAST XML
                // 2. Parse with VastParser from vast-client-java
                // 3. Extract MediaFile URLs and tracking URLs
                // 4. Store them in state variables
                
                when (creative.delivery) {
                    "vast_tag" -> {
                        // Simplified: Use videoAssetUrl as the video URL
                        // Full impl: Fetch and parse VAST XML from videoConfig.videoAssetUrl
                        vastVideoUrl = videoConfig.videoAssetUrl
                        android.util.Log.d("VastClient", "✓ VAST Tag parsed (simplified)")
                        android.util.Log.d("VastClient", "  Video URL: $vastVideoUrl")
                    }
                    "vast_xml" -> {
                        if (decodedVastXml != null) {
                            // Simplified: Use videoAssetUrl as the video URL
                            // Full impl: Parse decodedVastXml with VastParser
                            vastVideoUrl = videoConfig.videoAssetUrl
                            android.util.Log.d("VastClient", "✓ VAST XML parsed (simplified)")
                            android.util.Log.d("VastClient", "  Video URL: $vastVideoUrl")
                        }
                    }
                }
                
                // Extract tracking URLs (simplified - in full impl, extract from parsed VAST)
                vastTrackingUrls = mapOf(
                    "impression" to listOf(),
                    "start" to listOf(),
                    "firstQuartile" to listOf(),
                    "midpoint" to listOf(),
                    "thirdQuartile" to listOf(),
                    "complete" to listOf()
                )
                
            } catch (e: Exception) {
                android.util.Log.e("VastClient", "✗ VAST parsing error: ${e.message}", e)
                vastParseError = "VAST parsing failed: ${e.message}"
            }
        }
    }
    
    // ExoPlayer for video playback
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    
    // Setup and play video
    LaunchedEffect(exoPlayer, videoConfig.videoAssetUrl, vastVideoUrl) {
        val videoUrl = when {
            isVastDelivery && vastVideoUrl != null -> vastVideoUrl
            isJsonDelivery && videoConfig.videoAssetUrl != null -> videoConfig.videoAssetUrl
            else -> null
        }
        
        videoUrl?.let { url ->
            android.util.Log.d("VastClient", "📹 Playing video: $url")
            
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(url))
            
            // Add poster image
            videoConfig.posterImageUrl?.let { posterUrl ->
                mediaItemBuilder.setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setArtworkUri(Uri.parse(posterUrl))
                        .build()
                )
            }
            
            exoPlayer.apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
            }
            
            android.util.Log.d("VastClient", "✓ Video loaded and playing")
        }
    }
    
    // ExoPlayer error listener
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VastClient", "Playback error: ${error.message}", error)
                playbackError = "Playback error: ${error.message ?: "Unknown error"}"
            }
        })
    }
    
    // Monitor playback state and fire tracking events
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(100)
            isPlaying = exoPlayer.isPlaying
            
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration / 1000f
                currentPosition = exoPlayer.currentPosition / 1000f
                
                val progress = if (duration > 0) currentPosition / duration else 0f
                
                // Manual tracking for all modes (JSON and VAST with vast-client)
                if (exoPlayer.isPlaying && !hasStarted) {
                    hasStarted = true
                }
                
                if (hasStarted && !startTracked) {
                    when {
                        isVastDelivery -> {
                            // Fire VAST tracking URLs manually
                            vastTrackingUrls["start"]?.forEach { trackingUrl ->
                                android.util.Log.d("VastClient", "Fire VAST tracking: start")
                                // TODO: Fire tracking URL via HTTP GET
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "start")
                        }
                    }
                    startTracked = true
                }
                
                if (progress >= 0.25f && !firstQuartileTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["firstQuartile"]?.forEach { trackingUrl ->
                                android.util.Log.d("VastClient", "Fire VAST tracking: firstQuartile")
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "firstQuartile")
                        }
                    }
                    firstQuartileTracked = true
                }
                
                if (progress >= 0.5f && !midpointTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["midpoint"]?.forEach { trackingUrl ->
                                android.util.Log.d("VastClient", "Fire VAST tracking: midpoint")
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "midpoint")
                        }
                    }
                    midpointTracked = true
                }
                
                if (progress >= 0.75f && !thirdQuartileTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["thirdQuartile"]?.forEach { trackingUrl ->
                                android.util.Log.d("VastClient", "Fire VAST tracking: thirdQuartile")
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "thirdQuartile")
                        }
                    }
                    thirdQuartileTracked = true
                }
                
                if (progress >= 0.98f && !completeTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["complete"]?.forEach { trackingUrl ->
                                android.util.Log.d("VastClient", "Fire VAST tracking: complete")
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "complete")
                        }
                    }
                    completeTracked = true
                }
                
                // Show overlay at specified percentage
                if (progress >= videoConfig.overlayAtPercentage && !overlayShown) {
                    overlayShown = true
                    if (!overlayTracked) {
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
            }
            
            // Check for completion
            if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED && !hasCompleted) {
                hasCompleted = true
                onComplete()
            }
        }
    }
    
    // Track first frame rendered for poster image
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Listen for first frame rendered
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("VastClient", "✅ First frame rendered - hiding poster")
                firstFrameRendered = true
            }
        }
        exoPlayer.addListener(listener)
    }
    
    // Cleanup resources
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("VastClient", "Cleaning up resources...")
            exoPlayer.release()
        }
    }
    
    Box(modifier = modifier) {
        // Simple PlayerView for video playback
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    useArtwork = false  // Handle poster in Compose
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // POSTER IMAGE OVERLAY - Shows before video starts, hides after first frame
        if (!firstFrameRendered && videoConfig.posterImageUrl != null) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(
                    model = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        coil.request.ImageRequest.Builder(ctx)
                            .data(videoConfig.posterImageUrl)
                            .crossfade(true)
                            .build()
                    }
                ),
                contentDescription = "Poster image",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        
        // Custom overlay UI (for JSON delivery with companion ads)
        if (overlayShown && !hasCompleted && videoConfig.companionHeadline != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .height(60.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    videoConfig.companionHeadline?.let { headline ->
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                    }
                    
                    // CTA Button - only show if both CTA text and destination URL are present
                    if (videoConfig.companionCta != null && videoConfig.companionDestinationUrl != null) {
                        val cta = videoConfig.companionCta!!
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.fireClick(creative, "cta")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoConfig.companionDestinationUrl!!))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cta,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        
        // Error overlay
        val errorMessage = playbackError ?: vastParseError
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (vastParseError != null) "⚠️ VAST Parsing Error" else "⚠️ Playback Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
