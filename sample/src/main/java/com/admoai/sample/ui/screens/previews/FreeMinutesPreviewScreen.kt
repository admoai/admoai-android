package com.admoai.sample.ui.screens.previews

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.admoai.sdk.model.response.AdData
import com.admoai.sample.ui.MainViewModel
import com.admoai.sample.ui.components.PreviewNavigationBar
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.delay

/**
 * Free Minutes placement preview screen
 * 
 * Features:
 * - Prize boxes at the top with notification badges (indicating available video ads)
 * - Ride history section at the bottom with greyed-out mock UI
 * - Demonstrates video ad reward mechanism for publishers
 */
@Composable
fun FreeMinutesPreviewScreen(
    viewModel: MainViewModel,
    placement: PlacementItem,
    adData: AdData?,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onAdClick: (AdData) -> Unit,
    onTrackEvent: (String, String) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(adData != null) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    
    // Animation for content visibility
    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "content_alpha"
    )

    // Handle refresh button click
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            isContentVisible = false
            delay(300)
            onRefreshClick()
        }
    }
    
    // Reset isRefreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }
    
    // Show content when ad data becomes available
    LaunchedEffect(adData) {
        if (adData != null && !isLoading && !isRefreshing) {
            delay(300)
            isContentVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Navigation bar (no refresh button - replaced by clicking prize boxes)
            PreviewNavigationBar(
                placement = placement,
                onBackClick = onBackClick,
                onDetailsClick = onDetailsClick,
                onRefreshClick = { isRefreshing = true },
                isRefreshing = isRefreshing,
                showRefreshButton = false
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = contentAlpha)
            ) {
                // Subtitle above prize boxes
                Text(
                    text = "Tap any box to watch a video and earn free minutes!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
                
                // Prize boxes row with notification badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) { index ->
                        PrizeBox(
                            hasNotification = true, // All boxes show badge
                            onClick = {
                                // Trigger new ad request before showing video player
                                isRefreshing = true
                                // Show fullscreen video player after refresh starts
                                showVideoPlayer = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Ride History section (greyed-out mock UI)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = "Ride History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // Grid of ride history cards
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(6) { index ->
                            RideHistoryCard(index = index)
                        }
                    }
                }
            }
        }
        
        // Fullscreen video player overlay
        if (showVideoPlayer && adData != null) {
            FullscreenVideoPlayer(
                adData = adData,
                viewModel = viewModel,
                onClose = { showVideoPlayer = false }
            )
        }
    }
}

@Composable
private fun RowScope.PrizeBox(
    hasNotification: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE8DEF8)) // More vibrant purple/lavender
            .clickable(onClick = onClick) // Always clickable
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Gift icon
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = "Free minutes prize",
                modifier = Modifier.size(36.dp),
                tint = Color(0xFF6750A4) // Vibrant purple
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Text
            Text(
                text = "Free\nMinutes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1B20), // Dark text for contrast
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        
        // Notification badge (only on first box)
        if (hasNotification) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RideHistoryCard(index: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Route visualization (mock)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                // Map icon placeholder
                Icon(
                    imageVector = Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date mock
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Route info mock
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .background(Color.LightGray.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Price mock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(16.dp)
                        .background(Color.LightGray.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                )
                
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/**
 * Fullscreen video player for rewarded video ads
 * Displays companionHeadline while video plays
 * Shows endcard with companionEndcard* fields when video completes (overlayAtPercentage = 1.0)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullscreenVideoPlayer(
    adData: AdData,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCloseButton by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }
    var hasCompleted by remember { mutableStateOf(false) }
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Extract creative and content from ad data
    val creative = adData.creatives.firstOrNull() ?: return
    val companionHeadline = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "companionHeadline")
    val overlayPercentage = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "overlayAtPercentage")?.toFloatOrNull() ?: 1.0f
    val posterImageUrl = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "poster_image")
    
    // Extract video URL from creative
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingVideo by remember { mutableStateOf(true) }
    
    // Load video URL from VAST tag if present
    LaunchedEffect(creative.vast?.tagUrl) {
        creative.vast?.tagUrl?.let { tagUrl ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val connection = java.net.URL(tagUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()
                    
                    val vastXml = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // Parse VAST XML to extract video URL
                    val mediaFileUrl = Regex("<MediaFile[^>]*>\\s*<!\\[CDATA\\[([^\\]]+)\\]\\]>\\s*</MediaFile>", RegexOption.IGNORE_CASE)
                        .find(vastXml)?.groupValues?.get(1)?.trim()
                        ?: Regex("<MediaFile[^>]*>([^<]+)</MediaFile>", RegexOption.IGNORE_CASE)
                            .find(vastXml)?.groupValues?.get(1)?.trim()
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        videoUrl = mediaFileUrl
                        isLoadingVideo = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FreeMinutes", "Error fetching VAST", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isLoadingVideo = false
                    }
                }
            }
        } ?: run {
            // If no VAST tag, use video_asset from contents
            videoUrl = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "video_asset")
            isLoadingVideo = false
        }
    }
    
    // Create ExoPlayer with stable key to prevent recomposition
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }
    
    // Setup media source when videoUrl is available
    LaunchedEffect(videoUrl) {
        videoUrl?.let { url ->
            val mediaItem = MediaItem.Builder()
                .setUri(android.net.Uri.parse(url))
                .build()
            
            exoPlayer.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }
    
    // Back button is shown immediately (no delay)
    
    // Track video progress and completion
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    hasCompleted = true
                }
            }
        })
        
        while (true) {
            delay(100)
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration / 1000f
                currentPosition = exoPlayer.currentPosition / 1000f
            }
        }
    }
    
    // Clean up player when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide default controls
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Zoom to fill entire screen
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Poster image overlay - shows before video starts, hides after first frame
        if (!firstFrameRendered && posterImageUrl != null) {
            android.util.Log.d("FreeMinutes", "Showing poster overlay (firstFrameRendered=$firstFrameRendered): $posterImageUrl")
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(posterImageUrl)
                        .crossfade(true)
                        .build()
                ),
                contentDescription = "Video poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Back button with message (hide when video completes)
        if (showCloseButton && !hasCompleted) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 80.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button with grey background - Using Box + clickable for reliable clicks
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.7f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Message text from companionHeadline
                if (!companionHeadline.isNullOrBlank()) {
                    Text(
                        text = companionHeadline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        // Progress bar at the bottom (hide when video completes)
        if (!hasCompleted) {
            LinearProgressIndicator(
                progress = { if (duration > 0) currentPosition / duration else 0f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 64.dp)
                    .height(4.dp),
                color = Color(0xFFFF6B35), // Orange color like in the image
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
        
        // End-card (shows when video completes)
        if (hasCompleted) {
            EndCard(
                creative = creative,
                onClose = onClose
            )
        }
    }
}

/**
 * End-card displayed after video completion
 * Uses companionEndcard* fields from ad response
 */
@Composable
private fun EndCard(
    creative: com.admoai.sdk.model.response.Creative,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // Extract endcard content from creative
    val endcardImage = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "companionEndcardImage")
    val endcardHeadline = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "companionEndcardHeadline")
    val endcardCta = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "companionEndcardCta")
    val endcardDestinationUrl = com.admoai.sample.ui.mapper.AdTemplateMapper.getContentValue(creative, "companionEndcardDestinationUrl")
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image from companionEndcardImage
        if (!endcardImage.isNullOrBlank()) {
            AsyncImage(
                model = endcardImage,
                contentDescription = "End card",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // X close button with helper text at top
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 80.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Helper text from companionEndcardHeadline (LEFT side)
            if (!endcardHeadline.isNullOrBlank()) {
                Text(
                    text = endcardHeadline,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // X close button (RIGHT side)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.7f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // CTA button at bottom-center from companionEndcardCta
        if (!endcardCta.isNullOrBlank() && !endcardDestinationUrl.isNullOrBlank()) {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(endcardDestinationUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("FreeMinutes", "Error opening URL: $endcardDestinationUrl", e)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = endcardCta,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
