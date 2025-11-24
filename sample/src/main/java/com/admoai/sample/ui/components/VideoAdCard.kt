package com.admoai.sample.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.mapper.AdTemplateMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * VideoAdCard component that displays video ad with companion content in a unified card.
 * Used for "normal_videos" template with VAST Tag delivery.
 * 
 * Layout:
 * - Video player at top
 * - Companion content below (headline, subtext, CTA button)
 * - "Ad" watermark badge on bottom-right corner
 * - All contained in one Material3 card
 *
 * @param adData The ad data containing video creative
 * @param viewModel ViewModel for tracking events
 * @param modifier Modifier for the card
 * @param placementKey The placement key for tracking
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoAdCard(
    adData: AdData,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    placementKey: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Extract creative from ad data
    val creative = adData.creatives.firstOrNull() ?: return
    
    // Extract companion content (camelCase per requirements)
    val companionHeadline = AdTemplateMapper.getContentValue(creative, "companionHeadline")
    val companionSubtext = AdTemplateMapper.getContentValue(creative, "companionSubtext")
    val companionCta = AdTemplateMapper.getContentValue(creative, "companionCta")
    val companionDestinationUrl = AdTemplateMapper.getContentValue(creative, "companionDestinationUrl")
    
    // Video player state
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vastTrackingUrls by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Tracking state
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    // Load VAST tag and extract video URL
    LaunchedEffect(creative.vast?.tagUrl) {
        creative.vast?.tagUrl?.let { tagUrl ->
            scope.launch(Dispatchers.IO) {
                try {
                    val connection = URL(tagUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()
                    
                    val vastXml = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseVastXmlForCard(vastXml)
                    withContext(Dispatchers.Main) {
                        videoUrl = parsed.mediaFileUrl
                        vastTrackingUrls = parsed.trackingEvents
                        isLoading = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoAdCard", "Error fetching VAST", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error loading video: ${e.message}"
                        isLoading = false
                    }
                }
            }
        }
    }
    
    // Wrap Card in Box to position "Ad" watermark at card level
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Video player section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Color.Black)
                ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: "Error",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    videoUrl != null -> {
                        // Video player
                        val exoPlayer = remember {
                            ExoPlayer.Builder(context).build().apply {
                                val mediaItem = MediaItem.fromUri(videoUrl!!)
                                setMediaItem(mediaItem)
                                prepare()
                                playWhenReady = true // Auto-play when ready
                                
                                // Add listener for tracking
                                addListener(object : Player.Listener {
                                    override fun onRenderedFirstFrame() {
                                        firstFrameRendered = true
                                    }
                                    
                                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        if (isPlaying) {
                                            // Fire start tracking
                                            if (!startTracked) {
                                                startTracked = true
                                                scope.launch(Dispatchers.IO) {
                                                    vastTrackingUrls["start"]?.forEach { url ->
                                                        android.util.Log.d("VideoAdCard", "[MANUAL] Firing VAST 'start' tracking")
                                                        try {
                                                            val conn = URL(url).openConnection() as HttpURLConnection
                                                            conn.requestMethod = "GET"
                                                            conn.connect()
                                                            conn.inputStream.close()
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("VideoAdCard", "Error firing start tracking", e)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_READY) {
                                            val position = currentPosition
                                            val duration = this@apply.duration
                                            
                                            if (duration > 0) {
                                                val progress = position.toFloat() / duration.toFloat()
                                                
                                                // Fire quartile tracking
                                                when {
                                                    progress >= 0.98f && !completeTracked -> {
                                                        completeTracked = true
                                                        scope.launch(Dispatchers.IO) {
                                                            vastTrackingUrls["complete"]?.forEach { url ->
                                                                android.util.Log.d("VideoAdCard", "[MANUAL] Firing VAST 'complete' tracking")
                                                                try {
                                                                    val conn = URL(url).openConnection() as HttpURLConnection
                                                                    conn.requestMethod = "GET"
                                                                    conn.connect()
                                                                    conn.inputStream.close()
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e("VideoAdCard", "Error firing complete tracking", e)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    progress >= 0.75f && !thirdQuartileTracked -> {
                                                        thirdQuartileTracked = true
                                                        scope.launch(Dispatchers.IO) {
                                                            vastTrackingUrls["thirdQuartile"]?.forEach { url ->
                                                                android.util.Log.d("VideoAdCard", "[MANUAL] Firing VAST 'thirdQuartile' tracking")
                                                                try {
                                                                    val conn = URL(url).openConnection() as HttpURLConnection
                                                                    conn.requestMethod = "GET"
                                                                    conn.connect()
                                                                    conn.inputStream.close()
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e("VideoAdCard", "Error firing thirdQuartile tracking", e)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    progress >= 0.50f && !midpointTracked -> {
                                                        midpointTracked = true
                                                        scope.launch(Dispatchers.IO) {
                                                            vastTrackingUrls["midpoint"]?.forEach { url ->
                                                                android.util.Log.d("VideoAdCard", "[MANUAL] Firing VAST 'midpoint' tracking")
                                                                try {
                                                                    val conn = URL(url).openConnection() as HttpURLConnection
                                                                    conn.requestMethod = "GET"
                                                                    conn.connect()
                                                                    conn.inputStream.close()
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e("VideoAdCard", "Error firing midpoint tracking", e)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    progress >= 0.25f && !firstQuartileTracked -> {
                                                        firstQuartileTracked = true
                                                        scope.launch(Dispatchers.IO) {
                                                            vastTrackingUrls["firstQuartile"]?.forEach { url ->
                                                                android.util.Log.d("VideoAdCard", "[MANUAL] Firing VAST 'firstQuartile' tracking")
                                                                try {
                                                                    val conn = URL(url).openConnection() as HttpURLConnection
                                                                    conn.requestMethod = "GET"
                                                                    conn.connect()
                                                                    conn.inputStream.close()
                                                                } catch (e: Exception) {
                                                                    android.util.Log.e("VideoAdCard", "Error firing firstQuartile tracking", e)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                })
                            }
                        }
                        
                        DisposableEffect(Unit) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }
                        
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    controllerAutoShow = false
                                    controllerHideOnTouch = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // Companion content section - ALWAYS show below video
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Headline - ALWAYS show if present
                if (!companionHeadline.isNullOrBlank()) {
                    Text(
                        text = companionHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                // Subtext - ALWAYS show if present
                if (!companionSubtext.isNullOrBlank()) {
                    Text(
                        text = companionSubtext,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                }
                
                // CTA Button
                if (companionCta != null && companionDestinationUrl != null) {
                    Button(
                        onClick = {
                            // Open destination URL in browser
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(companionDestinationUrl))
                                context.startActivity(intent)
                                
                                // Track companion click
                                scope.launch(Dispatchers.IO) {
                                    creative.tracking?.custom?.find { it.key == "companionClick" }?.let { event ->
                                        try {
                                            val conn = URL(event.url).openConnection() as HttpURLConnection
                                            conn.requestMethod = "GET"
                                            conn.connect()
                                            conn.inputStream.close()
                                            android.util.Log.d("VideoAdCard", "Tracked companion click")
                                        } catch (e: Exception) {
                                            android.util.Log.e("VideoAdCard", "Error tracking companion click", e)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("VideoAdCard", "Error opening URL: $companionDestinationUrl", e)
                            }
                        },
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = companionCta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
            }
        }
        
        // "Ad" watermark badge positioned at card level (bottom-right, outside video but inside card container)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(3.dp),
            color = Color(0xFFF5F5F5)
        ) {
            Text(
                text = "Ad",
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Parse VAST XML to extract video URL and tracking URLs
 */
private data class VastCardData(
    val mediaFileUrl: String?,
    val trackingEvents: Map<String, List<String>>
)

private fun parseVastXmlForCard(xml: String): VastCardData {
    val mediaFileUrl = Regex("<MediaFile[^>]*>\\s*<!\\[CDATA\\[([^\\]]+)\\]\\]>\\s*</MediaFile>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.get(1)?.trim()
        ?: Regex("<MediaFile[^>]*>([^<]+)</MediaFile>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.trim()
    
    val trackingEvents = mutableMapOf<String, MutableList<String>>()
    val trackingPattern = Regex("<Tracking[^>]+event=\"([^\"]+)\"[^>]*>\\s*<!\\[CDATA\\[([^\\]]+)\\]\\]>\\s*</Tracking>", RegexOption.IGNORE_CASE)
    val trackingPattern2 = Regex("<Tracking[^>]+event=\"([^\"]+)\"[^>]*>([^<]+)</Tracking>", RegexOption.IGNORE_CASE)
    
    trackingPattern.findAll(xml).forEach { match ->
        val event = match.groupValues[1]
        val url = match.groupValues[2].trim()
        trackingEvents.getOrPut(event) { mutableListOf() }.add(url)
    }
    
    trackingPattern2.findAll(xml).forEach { match ->
        val event = match.groupValues[1]
        val url = match.groupValues[2].trim()
        if (trackingEvents[event]?.contains(url) != true) {
            trackingEvents.getOrPut(event) { mutableListOf() }.add(url)
        }
    }
    
    return VastCardData(mediaFileUrl, trackingEvents)
}
