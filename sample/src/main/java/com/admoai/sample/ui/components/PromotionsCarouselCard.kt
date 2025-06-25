package com.admoai.sample.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sample.ui.model.AdContent
import com.admoai.sample.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A carousel composable for the promotions placement showing 3 slides
 * with auto-advance and manual swipe capabilities
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromotionsCarouselCard(
    adData: AdData,
    onTrackImpression: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Extract creative
    val creative = adData.creatives.firstOrNull() ?: return
    
    // Create a stable key for the main impression tracking
    val adPlacement = adData.placement
    val creativeId = creative.metadata.creativeId
    val mainImpressionKey = remember(adPlacement, creativeId) {
        "${adPlacement}_${creativeId}_main"
    }
    
    // Track main impression when carousel is first displayed - executes only once
    LaunchedEffect(mainImpressionKey) {
        creative.tracking.impressions?.find { it.key == "default" }?.let { impression ->
            println("PromotionsCarousel: Tracking main impression for placement $adPlacement creative $creativeId")
            onTrackImpression(impression.url)
        }
    }
    
    // Prepare carousel data for 3 slides
    val slides = remember(creative) {
        (1..3).map { index ->
            CarouselSlide(
                index = index,
                imageUrl = AdContent.extractUrlContent(creative, "imageSlide$index") ?: "",
                headline = AdContent.extractTextContent(creative, "headlineSlide$index") ?: "",
                cta = AdContent.extractTextContent(creative, "ctaSlide$index") ?: "",
                trackingKey = "slide$index"
            )
        }
    }
    
    // Set up pager state
    val pagerState = rememberPagerState { slides.size }
    
    // Auto-advance setup (3 seconds interval as in iOS)
    var isUserSwiping by remember { mutableStateOf(false) }
    
    // More reliable auto-scroll implementation with state machine approach
    LaunchedEffect(Unit) {
        while (true) {
            // Only advance if user isn't manually swiping
            if (!isUserSwiping) {
                // Wait on each page
                delay(3000)
                
                // Calculate next page and animate
                val nextPage = (pagerState.currentPage + 1) % slides.size
                
                // Use stronger animation to ensure complete transition
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
                
                // Wait for animation to fully complete before continuing cycle
                delay(500)
            } else {
                // If user is swiping, just do a short check before trying again
                delay(100)
            }
        }
    }
    
    Column(modifier = modifier) {
        // Carousel pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val slide = slides[page]
            // Create a stable key combining placement, creative ID, and slide tracking key
            val currentSlide = slides[page]
            val slideImpressionKey = remember(adPlacement, creativeId, currentSlide.trackingKey) {
                "${adPlacement}_${creativeId}_${currentSlide.trackingKey}"
            }
            
            // Track impression once per unique slide view
            LaunchedEffect(slideImpressionKey, pagerState.currentPage) {
                // Only track if this is the current visible slide
                if (pagerState.currentPage == page) {
                    // Track impression for the current slide
                    creative.tracking.impressions?.find { it.key == currentSlide.trackingKey }?.let { impression ->
                        println("PromotionsCarousel: Tracking slide impression for $currentSlide.trackingKey")
                        onTrackImpression(impression.url)
                    }
                }
            }
            CarouselSlideContent(
                slide = slide,
                // No click handler as per requirements
            )
        }
        
        // Pagination indicators
        CarouselIndicators(
            count = slides.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp)
        )
    }
}

/**
 * Data class representing a slide in the carousel
 */
private data class CarouselSlide(
    val index: Int,
    val imageUrl: String,
    val headline: String,
    val cta: String,
    val trackingKey: String
)

/**
 * Composable for a single carousel slide
 */
@Composable
private fun CarouselSlideContent(
    slide: CarouselSlide,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Image with 16:9 aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SubcomposeAsyncImage(
                    model = slide.imageUrl,
                    contentDescription = slide.headline,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (painter.state) {
                        is coil.compose.AsyncImagePainter.State.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        }
                        is coil.compose.AsyncImagePainter.State.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.BrokenImage,
                                    contentDescription = "Error loading image",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
            
            // Headline text
            Text(
                text = slide.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            
            // CTA row with chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Text(
                    text = slide.cta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Composable for carousel pagination indicators
 */
@Composable
private fun CarouselIndicators(
    count: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            val isSelected = index == currentPage
            val color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
