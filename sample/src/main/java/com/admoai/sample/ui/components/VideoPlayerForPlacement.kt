package com.admoai.sample.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.admoai.sample.ui.MainViewModel
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.mapper.AdTemplateMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL

/**
 * Video player component for displaying video ads in placement previews.
 * Uses Media3 ExoPlayer with manual tracking (Player 2 implementation).
 *
 * @param creative The creative containing video data
 * @param viewModel ViewModel for tracking events
 * @param modifier Modifier for the player container
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerForPlacement(
    creative: Creative,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Log creative details for debugging
    android.util.Log.d("VideoPlayerPlacement", "Creative delivery: ${creative.delivery}")
    android.util.Log.d("VideoPlayerPlacement", "Creative vast: ${creative.vast}")
    android.util.Log.d("VideoPlayerPlacement", "Creative contents size: ${creative.contents.size}")
    
    // Extract video data from creative
    // Note: video_asset and poster_image are snake_case, companion* fields are camelCase
    val videoAssetUrl = AdTemplateMapper.getContentValue(creative, "video_asset")
    val posterImageUrl = AdTemplateMapper.getContentValue(creative, "poster_image")
    
    android.util.Log.d("VideoPlayerPlacement", "videoAssetUrl: $videoAssetUrl")
    android.util.Log.d("VideoPlayerPlacement", "posterImageUrl: $posterImageUrl")
    val companionHeadline = AdTemplateMapper.getContentValue(creative, "companionHeadline")
    val companionCta = AdTemplateMapper.getContentValue(creative, "companionCta")
    val companionDestinationUrl = AdTemplateMapper.getContentValue(creative, "companionDestinationUrl")
    
    // Parse is_skippable (can be boolean or integer 0/1)
    val isSkippableValue = AdTemplateMapper.getContentValue(creative, "is_skippable")
    val isSkippable = when (isSkippableValue) {
        "true", "1" -> true
        else -> false
    }
    
    // Parse skip_offset
    val skipOffsetValue = AdTemplateMapper.getContentValue(creative, "skip_offset")
    val skipOffset = skipOffsetValue?.toIntOrNull() ?: 5
    
    // Parse overlayAtPercentage (0.0 to 1.0) - camelCase
    val overlayAtValue = AdTemplateMapper.getContentValue(creative, "overlayAtPercentage")
    val overlayAtPercentage = overlayAtValue?.toFloatOrNull() ?: 1.0f
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    
    // Overlay state
    var overlayShown by remember { mutableStateOf(false) }
    var overlayTracked by remember { mutableStateOf(false) }
    
    // Tracking state
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    // Skip button state
    var showSkipButton by remember { mutableStateOf(false) }
    var skipCountdown by remember { mutableIntStateOf(skipOffset) }
    
    // ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    
    // VAST parsing state
    var vastVideoUrl by remember { mutableStateOf<String?>(null) }
    var vastTrackingUrls by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var vastParseError by remember { mutableStateOf<String?>(null) }
    var vastSkipOffset by remember { mutableIntStateOf(skipOffset) }
    var vastIsSkippable by remember { mutableStateOf(isSkippable) }
    
    // For VAST deliveries, fetch and parse the VAST XML
    val isVastDelivery = creative.delivery == "vast_tag" || creative.delivery == "vast_xml"
    
    LaunchedEffect(creative.delivery) {
        if (creative.delivery == "vast_tag") {
            // Fetch VAST XML from tagUrl
            creative.vast?.tagUrl?.let { tagUrl ->
                android.util.Log.d("VideoPlayerPlacement", "Fetching VAST XML from: $tagUrl")
                
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL(tagUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 10000
                        
                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val xmlContent = connection.inputStream.bufferedReader().use { it.readText() }
                            android.util.Log.d("VideoPlayerPlacement", "Fetched VAST XML (${xmlContent.length} chars)")
                            
                            // Parse VAST XML
                            val parsedData = parseVastXml(xmlContent)
                            vastVideoUrl = parsedData.mediaFileUrl
                            vastTrackingUrls = parsedData.trackingEvents
                            vastSkipOffset = parsedData.skipOffset ?: skipOffset
                            vastIsSkippable = parsedData.isSkippable
                            
                            android.util.Log.d("VideoPlayerPlacement", "Parsed VAST: video=${parsedData.mediaFileUrl}, skip=${parsedData.isSkippable}")
                        } else {
                            vastParseError = "HTTP $responseCode"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayerPlacement", "Error fetching VAST", e)
                        vastParseError = e.message
                    }
                }
            }
        } else if (creative.delivery == "vast_xml") {
            // Parse embedded VAST XML
            creative.vast?.xmlBase64?.let { base64Xml ->
                try {
                    val decoded = String(android.util.Base64.decode(base64Xml, android.util.Base64.DEFAULT))
                    android.util.Log.d("VideoPlayerPlacement", "Decoding VAST XML (${decoded.length} chars)")
                    
                    val parsedData = parseVastXml(decoded)
                    vastVideoUrl = parsedData.mediaFileUrl
                    vastTrackingUrls = parsedData.trackingEvents
                    vastSkipOffset = parsedData.skipOffset ?: skipOffset
                    vastIsSkippable = parsedData.isSkippable
                    
                    android.util.Log.d("VideoPlayerPlacement", "Parsed VAST XML: video=${parsedData.mediaFileUrl}")
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerPlacement", "Error parsing VAST XML", e)
                    vastParseError = e.message
                }
            }
        }
    }
    
    // Determine video URL
    val finalVideoUrl = if (isVastDelivery) vastVideoUrl else videoAssetUrl
    
    // Show error if VAST parsing failed
    if (vastParseError != null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "VAST Error: $vastParseError",
                color = Color.Red,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    
    // Wait for VAST parsing to complete
    if (isVastDelivery && finalVideoUrl == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White)
        }
        return
    }
    
    // Check if we have a video URL
    if (finalVideoUrl == null) {
        android.util.Log.e("VideoPlayerPlacement", "No video URL available")
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No video URL available",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    
    // Setup video
    LaunchedEffect(finalVideoUrl) {
        android.util.Log.d("VideoPlayer", "Setting up video: $finalVideoUrl")
        
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(finalVideoUrl))
        
        // Add poster image
        posterImageUrl?.let { posterUrl ->
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
    }
    
    // Player listener for tracking
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    hasCompleted = true
                    if (!completeTracked) {
                        android.util.Log.d("Tracking", "[MANUAL] Firing 'complete' event")
                        if (isVastDelivery) {
                            vastTrackingUrls["complete"]?.let { urls ->
                                scope.launch(Dispatchers.IO) {
                                    urls.forEach { url ->
                                        try {
                                            val connection = URL(url).openConnection() as HttpURLConnection
                                            connection.requestMethod = "GET"
                                            connection.connectTimeout = 3000
                                            connection.readTimeout = 3000
                                            connection.responseCode
                                            android.util.Log.d("Tracking", "Fired VAST 'complete' beacon")
                                        } catch (e: Exception) {
                                            android.util.Log.e("Tracking", "Error firing complete beacon", e)
                                        }
                                    }
                                }
                            }
                        } else {
                            viewModel.fireVideoEvent(creative, "complete")
                        }
                        completeTracked = true
                    }
                }
            }
        })
    }
    
    // Progress tracking
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.duration > 0) {
                currentPosition = exoPlayer.currentPosition.toFloat()
                duration = exoPlayer.duration.toFloat()
                val progress = currentPosition / duration
                
                // Fire tracking events at quartiles
                if (!startTracked && progress >= 0.0f) {
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'start' event")
                    if (isVastDelivery) {
                        // VAST: Fire HTTP beacons
                        vastTrackingUrls["start"]?.let { urls ->
                            scope.launch(Dispatchers.IO) {
                                urls.forEach { url ->
                                    try {
                                        val connection = URL(url).openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 3000
                                        connection.readTimeout = 3000
                                        connection.responseCode
                                        android.util.Log.d("Tracking", "Fired VAST 'start' beacon")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "Error firing start beacon", e)
                                    }
                                }
                            }
                        }
                    } else {
                        // JSON: Use SDK method
                        viewModel.fireVideoEvent(creative, "start")
                    }
                    startTracked = true
                }
                
                if (!firstQuartileTracked && progress >= 0.25f) {
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'first_quartile' event")
                    if (isVastDelivery) {
                        vastTrackingUrls["firstQuartile"]?.let { urls ->
                            scope.launch(Dispatchers.IO) {
                                urls.forEach { url ->
                                    try {
                                        val connection = URL(url).openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 3000
                                        connection.readTimeout = 3000
                                        connection.responseCode
                                        android.util.Log.d("Tracking", "Fired VAST 'firstQuartile' beacon")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "Error firing firstQuartile beacon", e)
                                    }
                                }
                            }
                        }
                    } else {
                        viewModel.fireVideoEvent(creative, "first_quartile")
                    }
                    firstQuartileTracked = true
                }
                
                if (!midpointTracked && progress >= 0.50f) {
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'midpoint' event")
                    if (isVastDelivery) {
                        vastTrackingUrls["midpoint"]?.let { urls ->
                            scope.launch(Dispatchers.IO) {
                                urls.forEach { url ->
                                    try {
                                        val connection = URL(url).openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 3000
                                        connection.readTimeout = 3000
                                        connection.responseCode
                                        android.util.Log.d("Tracking", "Fired VAST 'midpoint' beacon")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "Error firing midpoint beacon", e)
                                    }
                                }
                            }
                        }
                    } else {
                        viewModel.fireVideoEvent(creative, "midpoint")
                    }
                    midpointTracked = true
                }
                
                if (!thirdQuartileTracked && progress >= 0.75f) {
                    android.util.Log.d("Tracking", "[MANUAL] Firing 'third_quartile' event")
                    if (isVastDelivery) {
                        vastTrackingUrls["thirdQuartile"]?.let { urls ->
                            scope.launch(Dispatchers.IO) {
                                urls.forEach { url ->
                                    try {
                                        val connection = URL(url).openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 3000
                                        connection.readTimeout = 3000
                                        connection.responseCode
                                        android.util.Log.d("Tracking", "Fired VAST 'thirdQuartile' beacon")
                                    } catch (e: Exception) {
                                        android.util.Log.e("Tracking", "Error firing thirdQuartile beacon", e)
                                    }
                                }
                            }
                        }
                    } else {
                        viewModel.fireVideoEvent(creative, "third_quartile")
                    }
                    thirdQuartileTracked = true
                }
                
                // Show overlay at configured percentage
                if (companionHeadline != null && !overlayShown && progress >= overlayAtPercentage) {
                    overlayShown = true
                    exoPlayer.pause()
                }
                
                // Handle skip countdown (use VAST skip values if available)
                val actualSkipOffset = if (isVastDelivery) vastSkipOffset else skipOffset
                val actualIsSkippable = if (isVastDelivery) vastIsSkippable else isSkippable
                
                if (actualIsSkippable && !showSkipButton && progress > 0.01f) {
                    val elapsed = (currentPosition / 1000).toInt()
                    if (elapsed >= actualSkipOffset) {
                        showSkipButton = true
                    } else {
                        skipCountdown = actualSkipOffset - elapsed
                    }
                }
            }
            delay(100)
        }
    }
    
    // Cleanup
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Video player
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Skip button
        val actualIsSkippable = if (isVastDelivery) vastIsSkippable else isSkippable
        if (actualIsSkippable && !hasCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                if (showSkipButton) {
                    Button(
                        onClick = {
                            android.util.Log.d("Tracking", "[MANUAL] Firing 'skip' event")
                            if (isVastDelivery) {
                                vastTrackingUrls["skip"]?.let { urls ->
                                    scope.launch(Dispatchers.IO) {
                                        urls.forEach { url ->
                                            try {
                                                val connection = URL(url).openConnection() as HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                connection.responseCode
                                                android.util.Log.d("Tracking", "Fired VAST 'skip' beacon")
                                            } catch (e: Exception) {
                                                android.util.Log.e("Tracking", "Error firing skip beacon", e)
                                            }
                                        }
                                    }
                                }
                            } else {
                                viewModel.fireVideoEvent(creative, "skip")
                            }
                            exoPlayer.pause()
                            hasCompleted = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text("Skip Ad", color = Color.White)
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Skip in $skipCountdown",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        // Native end-card overlay
        if (overlayShown && companionHeadline != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable {
                        companionDestinationUrl?.let { url ->
                            android.util.Log.d("Tracking", "[MANUAL] Firing companion_click event")
                            viewModel.fireCustomEvent(creative, "companion_click")
                            // Open URL
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    Uri.parse(url)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("VideoPlayer", "Failed to open URL: $url", e)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Close button
                IconButton(
                    onClick = {
                        android.util.Log.d("Tracking", "[MANUAL] Firing companion_close event")
                        viewModel.fireCustomEvent(creative, "companion_close")
                        overlayShown = false
                        hasCompleted = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(4.dp)
                    )
                }
                
                // End-card content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = companionHeadline,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    companionCta?.let { cta ->
                        Button(
                            onClick = {
                                companionDestinationUrl?.let { url ->
                                    android.util.Log.d("Tracking", "[MANUAL] Firing companion_click event")
                                    viewModel.fireCustomEvent(creative, "companion_click")
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse(url)
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("VideoPlayer", "Failed to open URL: $url", e)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(cta, fontSize = 18.sp)
                        }
                    }
                }
                
                // Track overlay shown
                LaunchedEffect(Unit) {
                    if (!overlayTracked) {
                        android.util.Log.d("Tracking", "[MANUAL] Firing companion_view event")
                        viewModel.fireCustomEvent(creative, "companion_view")
                        overlayTracked = true
                    }
                }
            }
        }
    }
}

/**
 * Data class to hold parsed VAST data
 */
data class VastData(
    val mediaFileUrl: String?,
    val trackingEvents: Map<String, List<String>>,
    val skipOffset: Int? = null,
    val isSkippable: Boolean = false
)

/**
 * Parse VAST XML to extract video URL and tracking beacons
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
                                parser.getAttributeValue(null, "skipoffset")?.let { offset ->
                                    skipOffset = if (offset.contains(":")) {
                                        val parts = offset.split(":")
                                        parts.lastOrNull()?.toIntOrNull()
                                    } else {
                                        offset.toIntOrNull()
                                    }
                                    isSkippable = skipOffset != null
                                }
                            }
                            "MediaFile" -> insideMediaFile = true
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
                            } else if (insideTracking && currentEvent != null) {
                                trackingEvents.getOrPut(currentEvent) { mutableListOf() }.add(text)
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
                try {
                    eventType = parser.next()
                } catch (e2: Exception) {
                    break
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VAST_PARSER", "Error parsing VAST XML: ${e.message}")
    }
    
    // Fallback: Use regex if XML parsing failed
    if (mediaFileUrl == null) {
        val mediaFileRegex = """<MediaFile[^>]*>(.*?)</MediaFile>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        mediaFileRegex.find(xmlContent)?.groupValues?.get(1)?.let { match ->
            var cleanUrl = match
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .trim()
            
            val urlPattern = """https?://[^\s<>]+""".toRegex()
            urlPattern.find(cleanUrl)?.value?.let { extractedUrl ->
                cleanUrl = extractedUrl
            }
            
            mediaFileUrl = cleanUrl
        }
    }
    
    if (trackingEvents.isEmpty()) {
        val trackingRegex = """<Tracking\s+event="([^"]+)"[^>]*>(.*?)</Tracking>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        trackingRegex.findAll(xmlContent).forEach { match ->
            val event = match.groupValues[1]
            var url = match.groupValues[2]
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .trim()
            
            val urlPattern = """https?://[^\s<>]+""".toRegex()
            urlPattern.find(url)?.value?.let { extractedUrl ->
                url = extractedUrl
            }
            
            trackingEvents.getOrPut(event) { mutableListOf() }.add(url)
        }
    }
    
    return VastData(mediaFileUrl, trackingEvents, skipOffset, isSkippable)
}
