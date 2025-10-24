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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
                    val delivery = crtv.delivery ?: "json"
                    
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
                    
                    // Implementation details card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Implementation Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Get end card mode for context
                            val endCardMode = crtv.contents.find { it.key == "endcard" }?.value?.let {
                                (it as? JsonPrimitive)?.contentOrNull
                            } ?: "none"
                            
                            val implementationText = when (playerType) {
                                "exoplayer_ima" -> when (delivery) {
                                    "vast_tag" -> "Media3 ExoPlayer + IMA SDK: Passing VAST Tag URL directly to IMA SDK enables automatic ad tracking and skip functionality. IMA handles impression/quartile events automatically.${if (endCardMode == "native_endcard") " Custom UI companion rendered manually by app." else ""}${if (endCardMode == "vast_companion") " VAST Companion parsed and rendered manually." else ""} Regardless of using the tag URL, you can still handle tracking, companions, and skip manually whenever needed."
                                    "vast_xml" -> "Media3 ExoPlayer + IMA SDK: VAST XML passed to IMA via adsResponse. Manual companion handling${if (videoConfig.isSkippable) ", manual skip implementation" else ""}. All overlay trackers (overlayShown, closeBtn, button_cta) fired by app."
                                    else -> "Media3 ExoPlayer + IMA SDK: JSON delivery uses direct video URL. All tracking is app-driven including impressions, quartiles, and overlay events.${if (endCardMode == "native_endcard") " Custom UI companion fully controlled by app." else ""}"
                                }
                                "vast_client" -> when (delivery) {
                                    "vast_tag" -> "Media3 ExoPlayer: Manual VAST parsing from tag URL. Full control over ad lifecycle. Manual tracking for all events (impressions, quartiles, clicks).${if (videoConfig.isSkippable) " Skip functionality implemented manually." else ""}${if (endCardMode == "native_endcard") " Custom UI companion handled by app." else ""}${if (endCardMode == "vast_companion") " VAST Companion extracted from XML and rendered manually." else ""} This demonstrates complete ad management without SDK dependencies."
                                    "vast_xml" -> "Media3 ExoPlayer: Direct VAST XML parsing. Complete manual control over ad serving. All tracking events fired by app code.${if (videoConfig.isSkippable) " Skip button logic implemented manually." else ""}${if (endCardMode != "none") " Companion ads handled entirely by app." else ""} Ideal for custom ad experiences."
                                    else -> "Media3 ExoPlayer: JSON delivery with direct video playback. All aspects controlled manually: video loading, playback management, tracking pixels, user interactions.${if (endCardMode == "native_endcard") " Custom UI companion fully app-controlled." else ""} Complete flexibility for custom implementations."
                                }
                                else -> "Player configuration details unavailable."
                            }
                            
                            Text(
                                text = implementationText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Video player - Select based on playerType
                    when (playerType) {
                        "vast_client" -> {
                            // Media3 ExoPlayer - Manual VAST handling
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
                            // Media3 ExoPlayer + IMA
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
                                    text = "Simulated Video Player",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else if (hasCompleted && !showEndCard) {
                                Text(
                                    text = "Video Completed",
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
                            val canSkip = currentProgress >= (skipOffsetSeconds / videoDuration)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                if (canSkip) {
                                    Button(
                                        onClick = { hasCompleted = true; showEndCard = true },
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
                        "vast_client" -> "Media3 ExoPlayer"
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
    val trackingEvents: Map<String, List<String>>,  // event name -> list of tracking URLs
    val skipOffset: Int? = null,  // Skip offset in seconds
    val isSkippable: Boolean = false
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
                        android.util.Log.d("VideoAdPlayer", "Player STATE_BUFFERING")
                        if (hasNotifiedLoaded) {
                            callbacks.forEach { it.onBuffering(adInfo) }
                        }
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        android.util.Log.d("VideoAdPlayer", "Player STATE_READY")
                        if (!hasNotifiedLoaded) {
                            android.util.Log.d("VideoAdPlayer", "Notifying IMA: onLoaded()")
                            callbacks.forEach { it.onLoaded(adInfo) }
                            hasNotifiedLoaded = true
                        }
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        android.util.Log.d("VideoAdPlayer", "Player STATE_ENDED")
                        callbacks.forEach { it.onEnded(adInfo) }
                        hasNotifiedPlay = false
                        hasNotifiedLoaded = false
                    }
                }
            }
            
            override fun onRenderedFirstFrame() {
                android.util.Log.d("VideoAdPlayer", "First frame rendered")
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
                android.util.Log.e("VideoAdPlayer", "Player error - notifying IMA: onError() - ${error.message}")
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
            android.util.Log.d("VideoAdPlayer", "Notifying IMA: onPlay()")
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
    var skipOffset: Int? = null
    var isSkippable = false
    
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
                            "Linear" -> {
                                // Check for skipoffset attribute on Linear element
                                parser.getAttributeValue(null, "skipoffset")?.let { offset ->
                                    android.util.Log.d("VAST_PARSER", "Found skipoffset attribute: $offset")
                                    // Parse "00:00:05" format or plain number
                                    skipOffset = if (offset.contains(":")) {
                                        val parts = offset.split(":")
                                        parts.lastOrNull()?.toIntOrNull()
                                    } else {
                                        offset.toIntOrNull()
                                    }
                                    isSkippable = skipOffset != null
                                    android.util.Log.d("VAST_PARSER", "Parsed skip: isSkippable=$isSkippable, offset=$skipOffset")
                                }
                            }
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
    
    android.util.Log.d("VAST_PARSER", "Final result: video=$mediaFileUrl, tracking events=${trackingEvents.keys}, isSkippable=$isSkippable, skipOffset=$skipOffset")
    return VastData(mediaFileUrl, trackingEvents, skipOffset, isSkippable)
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
 * Rewrite localhost URLs to 10.0.2.2 for Android emulator
 * Android emulator uses 10.0.2.2 to access host machine's localhost
 */
fun rewriteLocalhostUrl(url: String?): String? {
    if (url == null) return null
    
    return url
        .replace("http://localhost:", "http://10.0.2.2:")
        .replace("https://localhost:", "https://10.0.2.2:")
        .replace("http://127.0.0.1:", "http://10.0.2.2:")
        .replace("https://127.0.0.1:", "https://10.0.2.2:")
}

/**
 * Parse video data from creative contents
 */
fun parseVideoData(creative: Creative): VideoPlayerConfig {
    val contents = creative.contents.associate { it.key to it.value }
    
    // Extract video URL - handle different delivery methods
    // Rewrite localhost URLs to 10.0.2.2 for Android emulator compatibility
    val videoAssetUrl = when (creative.delivery) {
        "vast_tag" -> rewriteLocalhostUrl(creative.vast?.tagUrl) // VAST tag URL (IMA will fetch and parse)
        "vast_xml" -> "vast_xml_placeholder" // Placeholder - actual XML is in vast.xmlBase64
        else -> contents["video_asset"]?.let { // JSON delivery - video asset URL
            (it as? JsonPrimitive)?.contentOrNull
        }
    } ?: rewriteLocalhostUrl(creative.vast?.tagUrl) // Fallback: try VAST tagUrl even if delivery says JSON
    
    // Extract poster image
    val posterImageUrl = contents["poster_image"]?.let {
        (it as? JsonPrimitive)?.contentOrNull
    }
    
    // Extract skippable settings
    // First try from contents (JSON delivery), then fallback to SDK utility functions (VAST deliveries)
    val isSkippable = contents["is_skippable"]?.let {
        val value = (it as? JsonPrimitive)?.contentOrNull
        android.util.Log.d("AdResponse", "[Content Mapping] is_skippable: $value")
        // Support both integer (1/0) and text ("true"/"false") formats
        when (value) {
            "1" -> true
            "0" -> false
            else -> value?.toBooleanStrictOrNull() ?: false
        }
    } ?: run {
        // Fallback: use SDK utility function (parses from VAST XML for VAST Tag/XML deliveries)
        val fromSdk = creative.isSkippable()
        android.util.Log.d("AdResponse", "[Content Mapping] is_skippable (from VAST): $fromSdk")
        fromSdk
    }
    
    val skipOffsetSeconds = contents["skip_offset"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.let { offset ->
            android.util.Log.d("AdResponse", "[Content Mapping] skip_offset: $offset")
            // Parse "00:00:05" format or plain number
            if (offset.contains(":")) {
                val parts = offset.split(":")
                parts.lastOrNull()?.toIntOrNull() ?: 5
            } else {
                offset.toIntOrNull() ?: 5
            }
        }
    } ?: run {
        // Fallback: use SDK utility function (parses from VAST XML)
        val fromSdk = creative.getSkipOffset()?.toIntOrNull() ?: 5
        android.util.Log.d("AdResponse", "[Content Mapping] skip_offset (from VAST): ${fromSdk}s")
        fromSdk
    }
    
    android.util.Log.d("AdResponse", "[Skip Configuration] Skippable: $isSkippable, Skip Offset: ${skipOffsetSeconds}s")
    
    // Extract overlay settings
    val overlayAtPercentage = contents["overlayAtPercentage"]?.let {
        (it as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
    } ?: 0.5f
    android.util.Log.d("AdResponse", "[Companion Configuration] overlayAtPercentage: ${(overlayAtPercentage * 100).toInt()}%")
    
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
    
    if (companionHeadline != null) {
        android.util.Log.d("AdResponse", "[Companion Configuration] Headline: $companionHeadline")
        android.util.Log.d("AdResponse", "[Companion Configuration] CTA: ${companionCta ?: "(none)"}")
        android.util.Log.d("AdResponse", "[Companion Configuration] Destination URL: ${companionDestinationUrl ?: "(none)"}")
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
    var isPlayingAd by remember { mutableStateOf(false) }  // Track if currently playing ad (not content)
    var hasAdCompleted by remember { mutableStateOf(false) }  // Track if ad has finished
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
    // - VAST Tag: Use IMA SDK with ad tag URL (IMA handles everything automatically)
    // - VAST XML: Manual parsing + manual tracking (IMA doesn't support adsResponse in Media3)
    // - JSON: Play video directly, manual SDK tracking
    // - Native end-card: Just a Compose overlay on top (doesn't change playback method)
    val hasNativeEndCard = videoConfig.companionHeadline != null
    val useImaSDK = creative.delivery == "vast_tag"  // Only VAST Tag uses IMA automatically
    val isJsonDelivery = creative.delivery == null || creative.delivery == "json"
    
    android.util.Log.d("Player", "[Setup] Media3 ExoPlayer + IMA")
    android.util.Log.d("Player", "[Delivery] ${creative.delivery ?: "json"}")
    android.util.Log.d("Player", "[Configuration] IMA SDK enabled: $useImaSDK, Custom UI: ${hasNativeEndCard || !useImaSDK}")
    
    // For VAST XML, decode the Base64 XML
    var decodedVastXml by remember { mutableStateOf<String?>(null) }
    
    // VAST-parsed data (overrides videoConfig for VAST deliveries)
    var vastTrackingUrls by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var vastSkipOffset by remember { mutableStateOf<Int?>(null) }
    var vastIsSkippable by remember { mutableStateOf(false) }
    
    // Coroutine scope for firing tracking URLs
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(creative.delivery, videoConfig.videoAssetUrl) {
        when (creative.delivery) {
            "vast_xml" -> {
                creative.vast?.xmlBase64?.let { base64Xml ->
                    try {
                        val decoded = String(android.util.Base64.decode(base64Xml, android.util.Base64.DEFAULT))
                        decodedVastXml = decoded
                        android.util.Log.d("AdResponse", "[VAST XML] Decoded successfully (${decoded.length} characters)")
                        
                        // Parse VAST XML to extract tracking URLs and skip info
                        val parsedData = parseVastXml(decoded)
                        vastTrackingUrls = parsedData.trackingEvents
                        vastSkipOffset = parsedData.skipOffset
                        vastIsSkippable = parsedData.isSkippable
                        android.util.Log.d("AdResponse", "[VAST XML] Skip configuration: isSkippable=${parsedData.isSkippable}, offset=${parsedData.skipOffset}s")
                    } catch (e: Exception) {
                        android.util.Log.e("AdResponse", "[VAST XML] Decoding error: ${e.message}")
                        playbackError = "Failed to decode VAST XML: ${e.message}"
                    }
                }
            }
            "vast_tag" -> {
                // For VAST Tag, fetch and parse the XML to extract skip info
                val tagUrl = rewriteLocalhostUrl(videoConfig.videoAssetUrl)
                if (tagUrl != null) {
                    android.util.Log.d("AdResponse", "[VAST Tag] Fetching XML to parse skip configuration: $tagUrl")
                    withContext(Dispatchers.IO) {
                        try {
                            val url = java.net.URL(tagUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 10000
                            
                            val responseCode = connection.responseCode
                            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                val xmlContent = connection.inputStream.bufferedReader().use { it.readText() }
                                android.util.Log.d("AdResponse", "[VAST Tag] Fetched XML successfully (${xmlContent.length} characters)")
                                
                                val parsedData = parseVastXml(xmlContent)
                                vastSkipOffset = parsedData.skipOffset
                                vastIsSkippable = parsedData.isSkippable
                                android.util.Log.d("AdResponse", "[VAST Tag] Skip configuration: isSkippable=${parsedData.isSkippable}, offset=${parsedData.skipOffset}s")
                            } else {
                                android.util.Log.e("AdResponse", "[VAST Tag] HTTP error: $responseCode")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AdResponse", "[VAST Tag] Fetch error: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    // VAST Companion ad state
    var companionContainerView by remember { mutableStateOf<android.view.ViewGroup?>(null) }
    
    // Get screen dimensions for companion slot configuration
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    // Calculate companion slot dimensions to match VAST companions (1920x1080 = 16:9 landscape)
    // IMA SDK only fills companions when slot aspect matches VAST companions
    val screenWidthPx = (screenWidthDp * density).toInt()
    val screenHeightPx = (screenHeightDp * density).toInt()
    
    // Calculate slot size: 16:9 landscape to match 1920x1080 companion
    // Use full width, calculate height for 16:9 aspect
    var companionWidthPx = (screenWidthPx * 0.95f).toInt()
    var companionHeightPx = (companionWidthPx * 9f / 16f).toInt() // 16:9 landscape aspect
    
    // Ensure height doesn't exceed available space
    val maxHeightPx = (screenHeightPx * 0.50f).toInt()
    if (companionHeightPx > maxHeightPx) {
        val scale = maxHeightPx.toFloat() / companionHeightPx
        companionHeightPx = maxHeightPx
        companionWidthPx = (companionWidthPx * scale).toInt()
    }
    
    android.util.Log.d("IMA_COMPANION", "Screen: ${screenWidthPx}x${screenHeightPx}px")
    android.util.Log.d("IMA_COMPANION", "Calculated companion slot: ${companionWidthPx}x${companionHeightPx}px")
    
    // Track when to show companion end-card
    var showCompanionEndCard by remember { mutableStateOf(false) }
    var companionAdAvailable by remember { mutableStateOf(false) }
    var companionRenderingMode by remember { mutableStateOf<String?>(null) } // "end-card" or "concurrent"
    
    // IMA ads loader - PRE-REGISTER companion slots for proper rendering
    // This MUST happen before the ad request for IMA to fill the companions
    val adsLoader = remember(companionContainerView) {
        val builder = ImaAdsLoader.Builder(context)
            .setAdEventListener { adEvent ->
                when (adEvent.type) {
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.LOADED -> {
                        android.util.Log.d("IMA", "[Event] Ad LOADED by IMA SDK")
                        // Check if ad has companions
                        adEvent.ad?.let { ad ->
                            val hasCompanions = ad.companionAds != null && !ad.companionAds.isEmpty()
                            if (hasCompanions) {
                                android.util.Log.d("IMA", "[Companion] Detected ${ad.companionAds.size} companion ad(s)")
                                ad.companionAds.forEach { companion ->
                                    android.util.Log.d("IMA", "[Companion] Size: ${companion.width}x${companion.height}px")
                                }
                            }
                            companionAdAvailable = hasCompanions
                        }
                    }
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.STARTED -> {
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'start' tracking beacon")
                        isPlayingAd = true
                    }
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.FIRST_QUARTILE ->
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'firstQuartile' tracking beacon at 25% progress")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.MIDPOINT ->
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'midpoint' tracking beacon at 50% progress")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.THIRD_QUARTILE ->
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'thirdQuartile' tracking beacon at 75% progress")
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.COMPLETED -> {
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'complete' tracking beacon at 100% progress")
                        isPlayingAd = false
                        hasAdCompleted = true
                        // Show companion end-card when video completes if renderingMode="end-card"
                        // For "concurrent" mode, companion is already visible during playback
                        if (companionAdAvailable && useImaSDK && companionRenderingMode == "end-card") {
                            showCompanionEndCard = true
                            android.util.Log.d("IMA", "[Companion] Displaying end-card overlay after video completion")
                        }
                    }
                    com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.SKIPPED -> {
                        android.util.Log.d("Tracking", "[AUTOMATIC] IMA SDK fired 'skip' tracking beacon")
                        isPlayingAd = false
                        hasAdCompleted = true
                    }
                    else -> {
                        // Log unhandled events for debugging
                        android.util.Log.d("IMA", "[Event] Unhandled IMA event: ${adEvent.type}")
                    }
                }
            }
            .setAdErrorListener { adErrorEvent ->
                android.util.Log.e("IMA", "[Error] Ad Error: ${adErrorEvent.error.message}")
                android.util.Log.e("IMA", "[Error] Error Code: ${adErrorEvent.error.errorCode}")
                android.util.Log.e("IMA", "[Error] Error Type: ${adErrorEvent.error.errorCodeNumber}")
                playbackError = "IMA Ad Error: ${adErrorEvent.error.message}"
            }
        
        // PRE-REGISTER companion slot if container is ready
        // This is CRITICAL for IMA to fill companions - must happen before ad request
        val container = companionContainerView
        if (container != null && useImaSDK) {
            try {
                val imaFactory = com.google.ads.interactivemedia.v3.api.ImaSdkFactory.getInstance()
                val companionSlot = imaFactory.createCompanionAdSlot()
                companionSlot.setSize(companionWidthPx, companionHeightPx)
                companionSlot.container = container
                
                android.util.Log.d("IMA", "[Companion] Pre-registering companion slot: ${companionWidthPx}x${companionHeightPx}px")
                builder.setCompanionAdSlots(listOf(companionSlot))
                
                // Assume end-card mode for now (will support concurrent in next iteration)
                companionRenderingMode = "end-card"
            } catch (e: Exception) {
                android.util.Log.e("IMA_COMPANION", "Error pre-registering companion slot: ${e.message}", e)
            }
        } else if (useImaSDK) {
            android.util.Log.w("IMA", "[Companion] Container not ready - will recreate ads loader when ready")
        }
        
        builder.build()
    }
    
    // PlayerView reference for AdViewProvider - MUST be created before player
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    
    // ExoPlayer with conditional IMA integration
    val exoPlayer = remember(adsLoader, playerViewRef) {
        // If using IMA, wait for playerView to be ready
        if (useImaSDK && playerViewRef == null) {
            android.util.Log.d("Player", "[Setup] Waiting for PlayerView initialization...")
            return@remember null
        }
        
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context))
        
        // Use IMA for VAST Tag and VAST XML (always, regardless of end-card)
        if (useImaSDK) {
            android.util.Log.d("Player", "[Setup] Configuring MediaSourceFactory with IMA ads loader")
            mediaSourceFactory
                .setAdsLoaderProvider { adsLoader }
                .setAdViewProvider { playerViewRef!! }
        } else {
            android.util.Log.d("Player", "[Setup] MediaSourceFactory without ads (JSON delivery)")
        }
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
    
    // Setup media source (wait for VAST XML to be decoded if needed)
    LaunchedEffect(exoPlayer, videoConfig.videoAssetUrl, decodedVastXml, creative.delivery) {
        val player = exoPlayer
        if (player == null) {
            android.util.Log.d("Player", "[Setup] Player not ready yet, waiting...")
            return@LaunchedEffect
        }
        
        // For VAST XML, wait until decoded
        if (creative.delivery == "vast_xml" && decodedVastXml == null) {
            android.util.Log.d("Player", "[Setup] Waiting for VAST XML decoding...")
            return@LaunchedEffect
        }
        
        videoConfig.videoAssetUrl?.let { url ->
            android.util.Log.d("Player", "[Setup] Configuring media source...")
            
            val contentVideoUri = "https://videos.admoai.com/02jJM5N02pffMDDei8s5EncgbBUJYMbNweR7Zwikeqtq00.m3u8"
            val mediaItemBuilder = MediaItem.Builder()
            
            when (creative.delivery) {
                "vast_tag" -> {
                    // VAST Tag: Pass tag URL to IMA SDK (IMA will fetch XML and handle everything)
                    // Rewrite localhost to 10.0.2.2 for Android emulator
                    val rewrittenUrl = rewriteLocalhostUrl(url) ?: url
                    android.util.Log.d("Player", "[Delivery Method] VAST Tag - URL will be passed to IMA SDK")
                    android.util.Log.d("Player", "[Configuration] Original Tag URL: $url")
                    if (rewrittenUrl != url) {
                        android.util.Log.d("Player", "[Configuration] Rewritten Tag URL: $rewrittenUrl (localhost → 10.0.2.2)")
                    }
                    android.util.Log.d("Player", "[Configuration] IMA SDK will handle: XML fetch, parsing, media selection, automatic tracking")
                    
                    mediaItemBuilder
                        .setUri(Uri.parse(contentVideoUri))
                        .setAdsConfiguration(
                            MediaItem.AdsConfiguration.Builder(Uri.parse(rewrittenUrl)).build()
                        )
                }
                "vast_xml" -> {
                    // VAST XML: Media3's IMA doesn't expose adsResponse, so handle manually
                    android.util.Log.d("Player", "[Delivery Method] VAST XML - Manual parsing required")
                    android.util.Log.d("Player", "[Configuration] Extracting video URL from embedded VAST XML")
                    android.util.Log.d("Player", "[Configuration] Tracking: Manual (via Admoai SDK)")
                    
                    val xmlContent = decodedVastXml
                    if (xmlContent != null) {
                        // Extract video URL from VAST XML
                        val mediaFileRegex = "<MediaFile[^>]*>\\s*<!\\[CDATA\\[([^\\]]+)\\]\\]>".toRegex()
                        val match = mediaFileRegex.find(xmlContent)
                        
                        if (match != null) {
                            val vastVideoUrl = match.groupValues[1].trim()
                            android.util.Log.d("Player", "[Configuration] Extracted video URL: $vastVideoUrl")
                            mediaItemBuilder.setUri(Uri.parse(vastVideoUrl))
                        } else {
                            android.util.Log.e("Player", "[Error] No MediaFile found in VAST XML")
                            playbackError = "Could not extract video URL from VAST XML"
                            return@LaunchedEffect
                        }
                    } else {
                        android.util.Log.e("Player", "[Error] VAST XML not decoded")
                        playbackError = "VAST XML not available"
                        return@LaunchedEffect
                    }
                }
                else -> {
                    // JSON delivery: Play video directly
                    android.util.Log.d("Player", "[Delivery Method] JSON - Direct video playback (no IMA SDK)")
                    android.util.Log.d("Player", "[Configuration] Video URL: $url")
                    android.util.Log.d("Player", "[Configuration] Tracking: Manual (via Admoai SDK trackingEvents)")
                    
                    mediaItemBuilder.setUri(Uri.parse(url))
                }
            }
            
            // Add poster image as artwork if available
            videoConfig.posterImageUrl?.let { posterUrl ->
                try {
                    mediaItemBuilder.setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setArtworkUri(Uri.parse(posterUrl))
                            .build()
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ExoPlayerIMA", "Error setting artwork: ${e.message}")
                }
            }
            
            player.apply {
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
        exoPlayer?.let { adsLoader.setPlayer(it) }
    }
    
    // Control companion container alpha (not visibility) based on rendering mode
    // Keep VISIBLE for IMA detection but control opacity
    LaunchedEffect(showCompanionEndCard, companionContainerView, companionRenderingMode) {
        val container = companionContainerView ?: return@LaunchedEffect
        
        when (companionRenderingMode) {
            "end-card" -> {
                // For end-card mode: show companion only after video completes
                container.alpha = if (showCompanionEndCard) {
                    android.util.Log.d("IMA", "[Companion] Showing end-card companion (alpha=1)")
                    1f
                } else {
                    android.util.Log.d("IMA", "[Companion] Hiding end-card companion (alpha=0)")
                    0f
                }
            }
            "concurrent" -> {
                // For concurrent mode: companion visible during playback
                // Will be implemented in next iteration
                container.alpha = 1f
            }
            else -> {
                container.alpha = 0f
            }
        }
    }
    
    // Monitor playback state, fire tracking events, and manage overlays
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        
        while (isActive) {
            delay(100)
            isPlaying = player.isPlaying
            
            // Track ad vs content playback for VAST Tag
            if (useImaSDK) {
                val currentlyPlayingAd = player.isPlayingAd
                if (currentlyPlayingAd != isPlayingAd) {
                    isPlayingAd = currentlyPlayingAd
                    android.util.Log.d("Media3Player", "Playback mode changed: isPlayingAd=$isPlayingAd")
                }
            }
            
            // Track position and duration
            if (player.duration > 0) {
                duration = player.duration / 1000f
                currentPosition = player.currentPosition / 1000f
                
                val progress = if (duration > 0) currentPosition / duration else 0f
                
                // Tracking based on delivery mode
                when {
                    isJsonDelivery || creative.delivery == "vast_xml" -> {
                        // JSON or VAST XML: Manual SDK tracking
                        if (player.isPlaying && !hasStarted) {
                            hasStarted = true
                        }
                        
                        if (hasStarted && !startTracked) {
                            when {
                                creative.delivery == "vast_xml" -> {
                                    vastTrackingUrls["start"]?.forEach { trackingUrl ->
                                        android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'start' tracking")
                                        android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                val responseCode = connection.responseCode
                                                android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // JSON delivery: Use SDK tracking
                                    val event = creative.tracking?.videoEvents?.find { it.key == "start" }
                                    android.util.Log.d("Tracking", "[MANUAL] Firing 'start' event")
                                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                                    viewModel.fireVideoEvent(creative, "start")
                                }
                            }
                            startTracked = true
                        }
                        
                        if (progress >= 0.25f && !firstQuartileTracked) {
                            when {
                                creative.delivery == "vast_xml" -> {
                                    vastTrackingUrls["firstQuartile"]?.forEach { trackingUrl ->
                                        android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'firstQuartile' tracking beacon at 25% progress")
                                        android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                val responseCode = connection.responseCode
                                                android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // JSON delivery: Use SDK tracking
                                    val event = creative.tracking?.videoEvents?.find { it.key == "first_quartile" }
                                    android.util.Log.d("Tracking", "[MANUAL] Firing 'first_quartile' event at 25% progress")
                                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                                    viewModel.fireVideoEvent(creative, "first_quartile")
                                }
                            }
                            firstQuartileTracked = true
                        }
                        
                        if (progress >= 0.5f && !midpointTracked) {
                            when {
                                creative.delivery == "vast_xml" -> {
                                    vastTrackingUrls["midpoint"]?.forEach { trackingUrl ->
                                        android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'midpoint' tracking beacon at 50% progress")
                                        android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                val responseCode = connection.responseCode
                                                android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // JSON delivery: Use SDK tracking
                                    val event = creative.tracking?.videoEvents?.find { it.key == "midpoint" }
                                    android.util.Log.d("Tracking", "[MANUAL] Firing 'midpoint' event at 50% progress")
                                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                                    viewModel.fireVideoEvent(creative, "midpoint")
                                }
                            }
                            midpointTracked = true
                        }
                        
                        if (progress >= 0.75f && !thirdQuartileTracked) {
                            when {
                                creative.delivery == "vast_xml" -> {
                                    vastTrackingUrls["thirdQuartile"]?.forEach { trackingUrl ->
                                        android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'thirdQuartile' tracking beacon at 75% progress")
                                        android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                val responseCode = connection.responseCode
                                                android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // JSON delivery: Use SDK tracking
                                    val event = creative.tracking?.videoEvents?.find { it.key == "third_quartile" }
                                    android.util.Log.d("Tracking", "[MANUAL] Firing 'third_quartile' event at 75% progress")
                                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                                    viewModel.fireVideoEvent(creative, "third_quartile")
                                }
                            }
                            thirdQuartileTracked = true
                        }
                        
                        if (progress >= 0.98f && !completeTracked) {
                            when {
                                creative.delivery == "vast_xml" -> {
                                    vastTrackingUrls["complete"]?.forEach { trackingUrl ->
                                        android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'complete' tracking beacon at 100% progress")
                                        android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                val responseCode = connection.responseCode
                                                android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // JSON delivery: Use SDK tracking
                                    val event = creative.tracking?.videoEvents?.find { it.key == "complete" }
                                    android.util.Log.d("Tracking", "[MANUAL] Firing 'complete' event at 98% progress")
                                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                                    viewModel.fireVideoEvent(creative, "complete")
                                }
                            }
                            completeTracked = true
                        }
                    }
                    useImaSDK -> {
                        // VAST Tag: IMA SDK handles all tracking automatically
                        // No manual tracking needed - IMA SDK fires tracking beacons automatically
                    }
                }
                
                // Show overlay at specified percentage (for JSON + native end-card or VAST + native end-card)
                // For VAST Tag, only show during ad playback, not content video
                val shouldShowOverlay = if (useImaSDK) isPlayingAd else true
                if (shouldShowOverlay && progress >= videoConfig.overlayAtPercentage && !overlayShown) {
                    overlayShown = true
                    android.util.Log.d("CustomUI", "[Companion Overlay] Displaying at ${(videoConfig.overlayAtPercentage * 100).toInt()}% progress")
                    if (!overlayTracked) {
                        android.util.Log.d("Tracking", "[MANUAL] Firing 'overlayShown' custom event")
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
            }
            
            // Check for completion
            if (player.playbackState == androidx.media3.common.Player.STATE_ENDED && !hasCompleted) {
                hasCompleted = true
                onComplete()
            }
        }
    }
    
    // Track first frame rendered for poster image
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Listen for first frame rendered
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("Player", "[Rendering] First frame rendered - hiding poster")
                firstFrameRendered = true
            }
        })
    }
    
    // Cleanup
    DisposableEffect(exoPlayer) {
        onDispose {
            adsLoader.release()
            exoPlayer?.release()
        }
    }
    
    // PLAYER 1 UI STRATEGY (Per player description):
    // - IMA SDK handles: Auto-tracking (impression, quartiles) for VAST Tag only
    // - Manual handling: Skip button, end-cards, and tracking for VAST XML/JSON
    // 
    // LIMITATION: Media3's ImaAdsLoader doesn't support programmatic skip for video ads.
    // See: https://github.com/androidx/media/issues/913
    // 
    // SOLUTION: For VAST Tag without Custom UI, let IMA show its native skip button.
    // For everything else (VAST XML, JSON, Custom UI), use manual skip button.
    val useCustomOverlays = hasNativeEndCard || !useImaSDK  // IMA native UI for VAST Tag without Custom UI

    android.util.Log.d("Media3Player", "useCustomOverlays=$useCustomOverlays (IMA native skip for VAST Tag w/o Custom UI, manual elsewhere)")
    
    // Use Box layout for all cases to support proper overlay behavior
    Box(modifier = modifier) {
        // COMPANION CONTAINER - Created FIRST (behind video) so it can be pre-registered
        // CRITICAL: Must be VISIBLE (not GONE) for IMA to detect during ad load
        // Kept transparent behind video, will show content after ad completes
        if (useImaSDK) {
            AndroidView(
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        id = android.view.View.generateViewId()
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Keep VISIBLE but transparent so IMA can detect it
                        // DO NOT set to GONE - IMA won't detect the slot
                        visibility = android.view.View.VISIBLE
                        alpha = 0f // Invisible but still measurable
                        
                        // Store reference for IMA SDK pre-registration
                        companionContainerView = this
                        android.util.Log.d("IMA", "[Companion] Container created: ${companionWidthPx}x${companionHeightPx}px")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // ExoPlayer view - Full screen (on top of companion container)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // For pure IMA (VAST Tag without custom overlays), disable controller
                    // to let IMA render its native UI (watermarks, skip button)
                    useController = useCustomOverlays
                    controllerAutoShow = false  // Don't show controls automatically at start
                    controllerHideOnTouch = true  // Hide/show controls on tap
                    useArtwork = !useCustomOverlays && videoConfig.posterImageUrl != null
                    
                    playerViewRef = this
                    android.util.Log.d("Player", "[Setup] PlayerView created - useController=$useCustomOverlays, useArtwork=${!useCustomOverlays}")
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                        android.util.Log.d("Media3Player", "PlayerView.player updated: ${exoPlayer != null}")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // POSTER IMAGE OVERLAY - Only show custom poster if using custom overlays
            // (Otherwise IMA will handle poster via useArtwork)
            if (useCustomOverlays && !firstFrameRendered && videoConfig.posterImageUrl != null) {
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
        
        // CUSTOM SKIP BUTTON - Only render when NOT using VAST Tag
        // VAST Tag: IMA SDK shows native skip button (no custom skip needed)
        // VAST XML/JSON: Manual skip button required
        val isVastDelivery = creative.delivery == "vast_tag" || creative.delivery == "vast_xml"
        val isSkippable = if (isVastDelivery) vastIsSkippable else videoConfig.isSkippable
        val skipOffsetSeconds = if (isVastDelivery) (vastSkipOffset ?: videoConfig.skipOffsetSeconds) else videoConfig.skipOffsetSeconds
        val shouldShowSkip = if (useImaSDK) isPlayingAd else !hasCompleted
        val showCustomSkip = creative.delivery != "vast_tag"  // Hide for VAST Tag (IMA native skip)
        
        if (useCustomOverlays && isSkippable && shouldShowSkip && showCustomSkip) {
            val canSkip = currentPosition >= skipOffsetSeconds
            
            if (canSkip) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                // Fire skip tracking event
                                android.util.Log.d("UserInteraction", "[Skip Button] User clicked skip button")
                                
                                // CRITICAL: Set all tracking flags to true FIRST to prevent any future tracking
                                // This stops midpoint/thirdQuartile/complete from firing after skip
                                startTracked = true
                                firstQuartileTracked = true
                                midpointTracked = true
                                thirdQuartileTracked = true
                                completeTracked = true
                                
                                when {
                                    creative.delivery == "vast_xml" -> {
                                        vastTrackingUrls["skip"]?.forEach { trackingUrl ->
                                            android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'skip' tracking")
                                            android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val connection = java.net.URL(trackingUrl).openConnection() as java.net.HttpURLConnection
                                                    connection.requestMethod = "GET"
                                                    connection.connectTimeout = 3000
                                                    connection.readTimeout = 3000
                                                    val responseCode = connection.responseCode
                                                    android.util.Log.d("Tracking", "[HTTP] Fired tracking beacon (HTTP $responseCode)")
                                                } catch (e: Exception) {
                                                    android.util.Log.e("Tracking", "[HTTP] Error firing beacon: ${e.message}")
                                                }
                                            }
                                        }
                                        // Terminate playback - pause and seek to end
                                        exoPlayer?.let { player ->
                                            player.pause()
                                            player.seekTo(player.duration)
                                            hasCompleted = true
                                        }
                                    }
                                    isJsonDelivery -> {
                                        android.util.Log.d("Tracking", "[MANUAL] Firing 'skip' event")
                                        viewModel.fireVideoEvent(creative, "skip")
                                        // Terminate playback - pause and seek to end
                                        exoPlayer?.let { player ->
                                            player.pause()
                                            player.seekTo(player.duration)
                                            hasCompleted = true
                                        }
                                    }
                                    creative.delivery == "vast_tag" -> {
                                        // For VAST Tag with IMA: Skip ad and continue to content video
                                        android.util.Log.d("Tracking", "[MANUAL] Skipping ad (VAST Tag)")
                                        exoPlayer?.let { player ->
                                            // IMA SDK handles skip tracking automatically
                                            // Skip to end of current ad window to trigger content playback
                                            if (player.isPlayingAd) {
                                                player.seekTo(player.currentTimeline.getWindow(player.currentMediaItemIndex, androidx.media3.common.Timeline.Window()).durationMs)
                                                isPlayingAd = false
                                                android.util.Log.d("Player", "[Playback] Skipped to content video")
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        // NATIVE END-CARD OVERLAY - Only render when using custom overlays
        // This triggers the hybrid strategy (useCustomOverlays = true when this exists)
        // For VAST Tag: Only show during ad playback, not content video
        val shouldShowCompanion = if (useImaSDK) isPlayingAd else !hasCompleted
        if (useCustomOverlays && overlayShown && shouldShowCompanion && videoConfig.companionHeadline != null) {
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
        
        // VAST Companion End-Card - IMA renders companion directly in the pre-registered container
        // We just add a close button overlay when the companion is showing
        if (showCompanionEndCard && useImaSDK && companionAdAvailable) {
            // Close button overlay (top-right corner)
            IconButton(
                onClick = {
                    showCompanionEndCard = false
                    hasCompleted = true
                    android.util.Log.d("IMA_COMPANION", "Companion end-card closed by user")
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close companion",
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
                        android.util.Log.d("Player", "[Rendering] First frame rendered - hiding poster")
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
                    val event = creative.tracking?.videoEvents?.find { it.key == "start" }
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'start' event")
                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                    viewModel.fireVideoEvent(creative, "start")
                    startTracked = true
                }
                
                // Fire quartile events
                if (progress >= 0.25f && !firstQuartileTracked) {
                    val eventKey = if (creative.delivery == "vast_xml") "firstQuartile" else "first_quartile"
                    val event = creative.tracking?.videoEvents?.find { it.key == eventKey }
                    android.util.Log.d("Tracking", "[MANUAL] Firing '$eventKey' event at 25% progress")
                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                    viewModel.fireVideoEvent(creative, eventKey)
                    firstQuartileTracked = true
                }
                
                if (progress >= 0.5f && !midpointTracked) {
                    val event = creative.tracking?.videoEvents?.find { it.key == "midpoint" }
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'midpoint' event at 50% progress")
                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                    viewModel.fireVideoEvent(creative, "midpoint")
                    midpointTracked = true
                }
                
                if (progress >= 0.75f && !thirdQuartileTracked) {
                    val eventKey = if (creative.delivery == "vast_xml") "thirdQuartile" else "third_quartile"
                    val event = creative.tracking?.videoEvents?.find { it.key == eventKey }
                    android.util.Log.d("Tracking", "[MANUAL] Firing '$eventKey' event at 75% progress")
                    event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                    viewModel.fireVideoEvent(creative, eventKey)
                    thirdQuartileTracked = true
                }
                
                // Show overlay at specified percentage
                if (progress >= videoConfig.overlayAtPercentage && !overlayShown) {
                    overlayShown = true
                    android.util.Log.d("CustomUI", "[Companion Overlay] Displaying at ${(videoConfig.overlayAtPercentage * 100).toInt()}% progress")
                    if (!overlayTracked) {
                        android.util.Log.d("Tracking", "[MANUAL] Firing 'overlayShown' custom event")
                        viewModel.fireCustomEvent(creative, "overlayShown")
                        overlayTracked = true
                    }
                }
                
                // Fire complete event
                if (progress >= 0.98f && !hasCompleted) {
                    hasCompleted = true
                    if (!completeTracked) {
                        val event = creative.tracking?.videoEvents?.find { it.key == "complete" }
                        android.util.Log.d("Tracking", "[MANUAL] Firing 'complete' event at 98% progress")
                        event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
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
                                android.util.Log.d("UserInteraction", "[Companion CTA] User clicked CTA button: $cta")
                                android.util.Log.d("Tracking", "[MANUAL] Firing 'companion_cta' custom event")
                                android.util.Log.d("UserInteraction", "[Navigation] Opening URL: ${videoConfig.companionDestinationUrl}")
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
                            android.util.Log.d("UserInteraction", "[Skip Button] User clicked skip button")
                            
                            // CRITICAL: Set all tracking flags to true FIRST to prevent any future tracking
                            // This stops midpoint/thirdQuartile/complete from firing after skip
                            startTracked = true
                            firstQuartileTracked = true
                            midpointTracked = true
                            thirdQuartileTracked = true
                            completeTracked = true
                            
                            android.util.Log.d("Tracking", "[MANUAL] Firing 'skip' event")
                            val skipEvent = creative.tracking?.videoEvents?.find { it.key == "skip" }
                            skipEvent?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
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
    
    // Coroutine scope for firing tracking URLs
    val scope = rememberCoroutineScope()
    
    android.util.Log.d("Player", "[Setup] Media3 ExoPlayer (Manual VAST parsing mode)")
    android.util.Log.d("Player", "[Delivery] ${creative.delivery ?: "json"}")
    android.util.Log.d("Player", "[Configuration] Manual tracking, custom UI overlays")
    
    // For VAST XML, decode the Base64 XML
    var decodedVastXml by remember { mutableStateOf<String?>(null) }
    
    // VAST parsing state
    var vastVideoUrl by remember { mutableStateOf<String?>(null) }
    var vastTrackingUrls by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var vastParseError by remember { mutableStateOf<String?>(null) }
    var vastSkipOffset by remember { mutableStateOf<Int?>(null) }
    var vastIsSkippable by remember { mutableStateOf(false) }
    
    // Decode VAST XML if needed
    LaunchedEffect(creative.delivery) {
        if (creative.delivery == "vast_xml") {
            creative.vast?.xmlBase64?.let { base64Xml ->
                try {
                    val decoded = String(android.util.Base64.decode(base64Xml, android.util.Base64.DEFAULT))
                    decodedVastXml = decoded
                    android.util.Log.d("AdResponse", "[VAST XML] Decoded successfully (${decoded.length} characters)")
                } catch (e: Exception) {
                    android.util.Log.e("AdResponse", "[VAST XML] Decoding error: ${e.message}")
                    vastParseError = "Failed to decode VAST XML: ${e.message}"
                }
            }
        }
    }
    
    // Parse VAST with vast-client-java
    LaunchedEffect(creative.delivery, videoConfig.videoAssetUrl, decodedVastXml) {
        if (isVastDelivery) {
            try {
                android.util.Log.d("AdResponse", "[VAST] Starting VAST parsing")
                
                when (creative.delivery) {
                    "vast_tag" -> {
                        // For VAST Tag: Fetch XML from the tag URL, then parse it
                        val tagUrl = videoConfig.videoAssetUrl
                        android.util.Log.d("AdResponse", "[VAST Tag] Fetching XML from: $tagUrl")
                        
                        withContext(Dispatchers.IO) {
                            try {
                                val url = java.net.URL(tagUrl)
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = "GET"
                                connection.connectTimeout = 5000
                                connection.readTimeout = 10000
                                
                                val responseCode = connection.responseCode
                                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                    val xmlContent = connection.inputStream.bufferedReader().use { it.readText() }
                                    android.util.Log.d("AdResponse", "[VAST Tag] Fetched XML successfully (${xmlContent.length} characters)")
                                    
                                    // Use XmlPullParser to parse VAST XML (proper Android XML parsing)
                                    val parsedData = parseVastXml(xmlContent)
                                    
                                    vastVideoUrl = parsedData.mediaFileUrl
                                    vastTrackingUrls = parsedData.trackingEvents
                                    vastSkipOffset = parsedData.skipOffset
                                    vastIsSkippable = parsedData.isSkippable
                                    
                                    android.util.Log.d("AdResponse", "[VAST Tag] Parsed: video=${parsedData.mediaFileUrl}, skip=${parsedData.isSkippable}, offset=${parsedData.skipOffset}s")
                                    
                                    if (parsedData.mediaFileUrl == null) {
                                        android.util.Log.e("AdResponse", "[VAST Tag] No MediaFile found in VAST XML")
                                        vastParseError = "No MediaFile found in VAST XML"
                                    }
                                } else {
                                    android.util.Log.e("AdResponse", "[VAST Tag] HTTP error: $responseCode")
                                    vastParseError = "HTTP error $responseCode fetching VAST XML"
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AdResponse", "[VAST Tag] Fetch error: ${e.message}", e)
                                vastParseError = "Error fetching VAST XML: ${e.message}"
                            }
                        }
                    }
                    "vast_xml" -> {
                        val xmlContent = decodedVastXml
                        if (xmlContent != null) {
                            android.util.Log.d("AdResponse", "[VAST XML] Parsing embedded XML")
                            
                            // Use XmlPullParser to parse VAST XML (proper Android XML parsing)
                            val parsedData = parseVastXml(xmlContent)
                            
                            vastVideoUrl = parsedData.mediaFileUrl
                            vastTrackingUrls = parsedData.trackingEvents
                            vastSkipOffset = parsedData.skipOffset
                            vastIsSkippable = parsedData.isSkippable
                            
                            android.util.Log.d("AdResponse", "[VAST XML] Parsed: video=${parsedData.mediaFileUrl}, skip=${parsedData.isSkippable}, offset=${parsedData.skipOffset}s")
                            
                            if (parsedData.mediaFileUrl == null) {
                                android.util.Log.e("AdResponse", "[VAST XML] No MediaFile found in VAST XML")
                                vastParseError = "No MediaFile found in VAST XML"
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AdResponse", "[VAST] Parsing error: ${e.message}", e)
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
            
            android.util.Log.d("Player", "[Setup] Video loaded and playing")
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
                            // Fire VAST tracking URLs manually via HTTP GET
                            vastTrackingUrls["start"]?.forEach { trackingUrl ->
                                android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'start' tracking")
                                android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = java.net.URL(trackingUrl)
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 3000
                                        connection.readTimeout = 3000
                                        val responseCode = connection.responseCode
                                        android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "[Error] Failed to fire tracking: ${e.message}")
                                    }
                                }
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "start")
                            android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'start' tracking")
                        }
                    }
                    startTracked = true
                }
                
                if (progress >= 0.25f && !firstQuartileTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["firstQuartile"]?.forEach { trackingUrl ->
                                android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'firstQuartile' tracking beacon at 25% progress")
                                android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = java.net.URL(trackingUrl)
                                        (url.openConnection() as java.net.HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 3000
                                            readTimeout = 3000
                                            android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "[Error] Failed to fire tracking: ${e.message}")
                                    }
                                }
                            }
                        }
                        isJsonDelivery -> {
                            val event = creative.tracking?.videoEvents?.find { it.key == "first_quartile" }
                            android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'first_quartile' tracking at 25% progress")
                            event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                            viewModel.fireVideoEvent(creative, "first_quartile")
                        }
                    }
                    firstQuartileTracked = true
                }
                
                if (progress >= 0.5f && !midpointTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["midpoint"]?.forEach { trackingUrl ->
                                android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'midpoint' tracking beacon at 50% progress")
                                android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = java.net.URL(trackingUrl)
                                        (url.openConnection() as java.net.HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 3000
                                            readTimeout = 3000
                                            android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "[Error] Failed to fire tracking: ${e.message}")
                                    }
                                }
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "midpoint")
                            android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'midpoint' tracking")
                        }
                    }
                    midpointTracked = true
                }
                
                if (progress >= 0.75f && !thirdQuartileTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["thirdQuartile"]?.forEach { trackingUrl ->
                                android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'thirdQuartile' tracking beacon at 75% progress")
                                android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = java.net.URL(trackingUrl)
                                        (url.openConnection() as java.net.HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 3000
                                            readTimeout = 3000
                                            android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "[Error] Failed to fire tracking: ${e.message}")
                                    }
                                }
                            }
                        }
                        isJsonDelivery -> {
                            val event = creative.tracking?.videoEvents?.find { it.key == "third_quartile" }
                            android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'third_quartile' tracking at 75% progress")
                            event?.url?.let { url -> android.util.Log.d("Tracking", "[URL] $url") }
                            viewModel.fireVideoEvent(creative, "third_quartile")
                        }
                    }
                    thirdQuartileTracked = true
                }
                
                if (progress >= 0.98f && !completeTracked) {
                    when {
                        isVastDelivery -> {
                            vastTrackingUrls["complete"]?.forEach { trackingUrl ->
                                android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'complete' tracking beacon at 100% progress")
                                android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = java.net.URL(trackingUrl)
                                        (url.openConnection() as java.net.HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            connectTimeout = 3000
                                            readTimeout = 3000
                                            android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "[Error] Failed to fire tracking: ${e.message}")
                                    }
                                }
                            }
                        }
                        isJsonDelivery -> {
                            viewModel.fireVideoEvent(creative, "complete")
                            android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'complete' tracking")
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
                android.util.Log.d("Player", "[Rendering] First frame rendered - hiding poster")
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
                    controllerAutoShow = false  // Don't show controls automatically at start
                    controllerHideOnTouch = true  // Hide/show controls on tap
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
        
        // Skip button (top-right corner badge bubble) - only shows when skip is available
        // For VAST deliveries, use parsed skip info; for JSON, use videoConfig
        val isSkippable = if (isVastDelivery) vastIsSkippable else videoConfig.isSkippable
        val skipOffsetSeconds = if (isVastDelivery) (vastSkipOffset ?: videoConfig.skipOffsetSeconds) else videoConfig.skipOffsetSeconds
        
        if (isSkippable && !hasCompleted) {
            val canSkip = currentPosition >= skipOffsetSeconds
            
            // Log only when skip becomes available (state change)
            LaunchedEffect(canSkip) {
                if (canSkip) {
                    val source = if (isVastDelivery) "VAST XML" else "JSON"
                    android.util.Log.d("CustomUI", "[Skip Button] Skip button now available ($source delivery)")
                }
            }
            
            if (canSkip) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                // Fire skip tracking event
                                android.util.Log.d("UserInteraction", "[Skip Button] User clicked skip button")
                                
                                // CRITICAL: Set all tracking flags to true FIRST to prevent any future tracking
                                // This stops midpoint/thirdQuartile/complete from firing after skip
                                startTracked = true
                                firstQuartileTracked = true
                                midpointTracked = true
                                thirdQuartileTracked = true
                                completeTracked = true
                                
                                when {
                                    isJsonDelivery -> {
                                        android.util.Log.d("Tracking", "[MANUAL] Firing JSON 'skip' event")
                                        viewModel.fireVideoEvent(creative, "skip")
                                    }
                                    isVastDelivery -> {
                                        // For VAST XML, fire skip tracking URL via HTTP GET
                                        vastTrackingUrls["skip"]?.forEach { trackingUrl ->
                                            android.util.Log.d("Tracking", "[MANUAL] Firing VAST 'skip' tracking")
                                            android.util.Log.d("Tracking", "[URL] $trackingUrl")
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val url = java.net.URL(trackingUrl)
                                                    (url.openConnection() as java.net.HttpURLConnection).apply {
                                                        requestMethod = "GET"
                                                        connectTimeout = 3000
                                                        readTimeout = 3000
                                                        android.util.Log.d("Tracking", "[Response] HTTP $responseCode")
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("Tracking", "[Error] Failed to fire skip tracking: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                }
                                // Terminate playback - pause and seek to end
                                exoPlayer.pause()
                                exoPlayer.seekTo(exoPlayer.duration)
                                hasCompleted = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
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
