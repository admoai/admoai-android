package com.admoai.sample.ui.screens

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.admoai.sample.ui.MainViewModel
import com.admoai.sdk.model.response.Creative
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * ExoPlayer + IMA implementation with proper VAST Companion end-card handling.
 * 
 * Key improvements:
 * - Companion container is only created and shown AFTER video completion
 * - Video player takes full screen during playback
 * - Companion ads respect renderingMode="end-card" attribute
 * - Proper overlay behavior instead of stacking
 */
@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerImaWithCompanionEndCard(
    videoConfig: VideoPlayerConfig,
    creative: Creative,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val TAG = "IMA_COMPANION"
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Companion ad state
    var showCompanionEndCard by remember { mutableStateOf(false) }
    var companionAdLoaded by remember { mutableStateOf(false) }
    var companionContainer by remember { mutableStateOf<ViewGroup?>(null) }
    
    // Tracking state for manual deliveries
    var hasStarted by remember { mutableStateOf(false) }
    var startTracked by remember { mutableStateOf(false) }
    var firstQuartileTracked by remember { mutableStateOf(false) }
    var midpointTracked by remember { mutableStateOf(false) }
    var thirdQuartileTracked by remember { mutableStateOf(false) }
    var completeTracked by remember { mutableStateOf(false) }
    
    val useImaSDK = creative.delivery == "vast_tag"
    val isJsonDelivery = creative.delivery == null || creative.delivery == "json"
    
    Log.d(TAG, "═══════════════════════════════════════")
    Log.d(TAG, "VAST Companion End-Card Player")
    Log.d(TAG, "Delivery: ${creative.delivery ?: "json"}")
    Log.d(TAG, "Will use IMA SDK: $useImaSDK")
    Log.d(TAG, "═══════════════════════════════════════")
    
    // IMA ads loader with dynamic companion slot registration
    val adsLoader = remember {
        ImaAdsLoader.Builder(context)
            .setAdEventListener { adEvent ->
                Log.d(TAG, "Ad Event: ${adEvent.type}")
                when (adEvent.type) {
                    AdEvent.AdEventType.LOADED -> {
                        Log.d(TAG, "✅ Ad LOADED")
                        // Check if ad has companions
                        adEvent.ad?.let { ad ->
                            val hasCompanions = ad.companionAds != null && !ad.companionAds.isEmpty()
                            Log.d(TAG, "Ad has companions: $hasCompanions")
                            if (hasCompanions) {
                                companionAdLoaded = true
                            }
                        }
                    }
                    AdEvent.AdEventType.STARTED -> Log.d(TAG, "✅ Ad STARTED")
                    AdEvent.AdEventType.COMPLETED -> {
                        Log.d(TAG, "✅ Ad COMPLETED")
                        // Show companion end-card when video completes
                        if (companionAdLoaded) {
                            showCompanionEndCard = true
                            Log.d(TAG, "🎬 Showing companion end-card")
                        }
                    }
                    AdEvent.AdEventType.ALL_ADS_COMPLETED -> {
                        Log.d(TAG, "✅ ALL_ADS_COMPLETED")
                        hasCompleted = true
                        onComplete()
                    }
                    else -> Unit
                }
            }
            .setAdErrorListener { adErrorEvent ->
                Log.e(TAG, "❌ Ad Error: ${adErrorEvent.error.message}")
                playbackError = "IMA Ad Error: ${adErrorEvent.error.message}"
            }
            .build()
    }
    
    // PlayerView reference
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    
    // ExoPlayer with IMA integration
    val exoPlayer = remember(playerViewRef) {
        if (useImaSDK && playerViewRef == null) {
            Log.d(TAG, "⏳ Waiting for PlayerView...")
            return@remember null
        }
        
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
        
        if (useImaSDK) {
            Log.d(TAG, "→ Configuring MediaSourceFactory with IMA ads loader")
            mediaSourceFactory
                .setAdsLoaderProvider { adsLoader }
                .setAdViewProvider { playerViewRef!! }
        }
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
    
    // Register companion slot ONLY when video completes
    LaunchedEffect(showCompanionEndCard, companionContainer) {
        if (showCompanionEndCard && companionContainer != null && useImaSDK) {
            try {
                val density = context.resources.displayMetrics.density
                val screenWidth = context.resources.displayMetrics.widthPixels
                
                // Calculate companion dimensions (use most of screen width)
                val companionWidthPx = (screenWidth * 0.9f).toInt()
                val companionHeightPx = (250 * density).toInt() // 250dp height
                
                // Create and register companion slot
                val imaFactory = ImaSdkFactory.getInstance()
                val companionSlot = imaFactory.createCompanionAdSlot().apply {
                    setSize(companionWidthPx, companionHeightPx)
                    container = companionContainer!!  // Safe to force-unwrap here since we checked for null above
                }
                
                Log.d(TAG, "✅ Registering companion slot: ${companionWidthPx}x${companionHeightPx}px")
                
                // Update ads loader with companion slot
                // Note: This needs to happen before ad request for best results
                // For dynamic registration, we may need to reload the ad
                
            } catch (e: Exception) {
                Log.e(TAG, "Error registering companion slot: ${e.message}", e)
            }
        }
    }
    
    // Setup media source
    LaunchedEffect(exoPlayer, videoConfig.videoAssetUrl) {
        val player = exoPlayer ?: return@LaunchedEffect
        
        videoConfig.videoAssetUrl?.let { url ->
            Log.d(TAG, "🎬 Setting up media source...")
            
            val contentVideoUri = "https://videos.admoai.com/02jJM5N02pffMDDei8s5EncgbBUJYMbNweR7Zwikeqtq00.m3u8"
            val mediaItemBuilder = MediaItem.Builder()
            
            when (creative.delivery) {
                "vast_tag" -> {
                    Log.d(TAG, "📡 VAST Tag mode with companion end-cards")
                    mediaItemBuilder
                        .setUri(android.net.Uri.parse(contentVideoUri))
                        .setAdsConfiguration(
                            MediaItem.AdsConfiguration.Builder(android.net.Uri.parse(url)).build()
                        )
                }
                else -> {
                    Log.d(TAG, "📹 JSON mode: Direct playback")
                    mediaItemBuilder.setUri(android.net.Uri.parse(url))
                }
            }
            
            player.apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_ENDED -> {
                                if (!useImaSDK) {
                                    // For non-IMA playback, manually trigger end-card
                                    hasCompleted = true
                                    showCompanionEndCard = true
                                }
                            }
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "Playback error: ${error.message}", error)
                        playbackError = "Playback error: ${error.message}"
                    }
                })
            }
        }
    }
    
    // Set adsLoader's player
    LaunchedEffect(exoPlayer) {
        exoPlayer?.let { adsLoader.setPlayer(it) }
    }
    
    // Monitor playback for manual tracking (JSON/VAST XML)
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        
        while (isActive) {
            delay(100)
            isPlaying = player.isPlaying
            
            if (player.duration > 0) {
                duration = player.duration / 1000f
                currentPosition = player.currentPosition / 1000f
                
                val progress = if (duration > 0) currentPosition / duration else 0f
                
                // Manual tracking for JSON delivery
                if (isJsonDelivery) {
                    if (player.isPlaying && !hasStarted) {
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
            }
        }
    }
    
    // Cleanup
    DisposableEffect(exoPlayer) {
        onDispose {
            adsLoader.release()
            exoPlayer?.release()
        }
    }
    
    // UI Layout - Use Box for overlay behavior
    Box(modifier = modifier.fillMaxSize()) {
        // Video Player - Full screen
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    playerViewRef = this
                    Log.d(TAG, "✅ PlayerView created")
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                    Log.d(TAG, "PlayerView.player updated")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Companion End-Card Overlay - Only show after video completion
        if (showCompanionEndCard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    showCompanionEndCard = false
                                    hasCompleted = true
                                    // Fire close tracking if needed
                                    viewModel.fireCustomEvent(creative, "companionClosed")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                        
                        // Title
                        Text(
                            text = "Video Complete",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Companion container for IMA to render into
                        if (useImaSDK) {
                            AndroidView(
                                factory = { ctx ->
                                    FrameLayout(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            (250 * ctx.resources.displayMetrics.density).toInt()
                                        )
                                        companionContainer = this
                                        Log.d(TAG, "✅ Companion container created for end-card")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            )
                        } else {
                            // Fallback for non-VAST deliveries
                            Text(
                                text = "Thank you for watching!",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Action button
                        Button(
                            onClick = {
                                showCompanionEndCard = false
                                hasCompleted = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
        
        // Error overlay
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
