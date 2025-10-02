# Video Player Implementation Guide

## Current State (What's Implemented)

### ✅ Working Features:
1. **Configuration Screen** - VideoAdDemoScreen with delivery, endCard, skippable options
2. **Mock Server Integration** - Fetches from localhost:8080
3. **JSON Parsing** - Parses DecisionResponse
4. **Navigation** - Routes to VideoPreviewScreen
5. **Simulated Player** - Shows fake video progress bar and overlay

### ❌ Critical Issues:
1. **Using SIMULATED player** - Not loading real videos
2. **Not parsing videoAsset URL** - Ignoring the actual video URL
3. **Not handling VAST** - Ignoring vast.tagUrl and vast.xmlBase64
4. **No tracking implementation** - videoEvents, clicks, impressions not fired
5. **No player selection** - Can't choose between IMA/ExoPlayer/JW Player
6. **Configuration not refreshing** - State doesn't update when navigating back

---

## Content Keys Mapping (From Mock Server Analysis)

### 📊 **Complete Content Keys Reference**

| Key | Type | Purpose | Used In |
|-----|------|---------|---------|
| `videoAsset` | video | Main video URL (HLS .m3u8) | JSON delivery only |
| `posterImage` | image | Thumbnail/poster before video plays | All scenarios |
| `isSkippable` | bool | Whether video can be skipped | JSON + skippable |
| `skipOffset` | text | When skip appears (e.g. "00:00:05") | JSON + skippable |
| `companionHeadline` | text | End card headline text | Native end card |
| `companionCta` | text | End card button text | Native end card |
| `companionDestinationUrl` | url | End card click destination | Native end card |
| `overlayAtPercentage` | float | When to show overlay (0.0-1.0) | VAST + native end card |
| `showClose` | integer | Show close button (0/1) | VAST + native end card |

---

## Mock Server Response Structure (Detailed)

### 1. JSON Delivery - No End Card (`json_none`)
```json
{
  "delivery": "json",
  "vast": null,
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "https://videos.admoai.com/xxx.m3u8" },
    { "key": "posterImage", "type": "image", "value": "..." }
  ],
  "tracking": {
    "impressions": [{"key": "default", "url": "..."}],
    "videoEvents": [
      { "key": "start", "url": "..." },
      { "key": "firstQuartile", "url": "..." },
      { "key": "midpoint", "url": "..." },
      { "key": "thirdQuartile", "url": "..." },
      { "key": "complete", "url": "..." }
    ]
  }
}
```
**Behavior:** Load videoAsset directly, fire tracking manually

---

### 2. JSON Delivery - Skippable (`json_none_skippable`)
```json
{
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "..." },
    { "key": "posterImage", "type": "image", "value": "..." },
    { "key": "isSkippable", "type": "bool", "value": true },
    { "key": "skipOffset", "type": "text", "value": "00:00:05" }
  ],
  "tracking": {
    "videoEvents": [
      /* ... */
      { "key": "skip", "url": "..." }  // Additional event!
    ]
  }
}
```
**Behavior:** Show skip button after 5 seconds, fire "skip" event when clicked

---

### 3. JSON Delivery - Native End Card (`json_native-end-card`)
```json
{
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "..." },
    { "key": "posterImage", "type": "image", "value": "..." },
    { "key": "companionHeadline", "type": "text", "value": "The Best Ride Awaits" },
    { "key": "companionCta", "type": "text", "value": "Book Now" },
    { "key": "companionDestinationUrl", "type": "url", "value": "https://..." }
  ]
}
```
**Behavior:** After video completes, show custom end card with headline + CTA button

---

### 4. VAST Tag - Native End Card (`vasttag_native-end-card`)
```json
{
  "delivery": "vast_tag",
  "vast": {
    "tagUrl": "https://localhost:8080/endpoint?scenario=tagurl_vasttag_native-end-card"
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." },
    { "key": "companionHeadline", "type": "text", "value": "..." },
    { "key": "companionCta", "type": "text", "value": "..." },
    { "key": "companionDestinationUrl", "type": "url", "value": "..." },
    { "key": "overlayAtPercentage", "type": "float", "value": 0.5 },
    { "key": "showClose", "type": "integer", "value": 1 }
  ],
  "tracking": {
    "clicks": [{ "key": "cta", "url": "..." }],
    "custom": [{ "key": "closeBtn", "url": "..." }],
    "videoEvents": []  // Empty - handled by VAST
  }
}
```
**Behavior:** 
- IMA SDK loads VAST tag (handles video + tracking)
- Show overlay at 50% progress
- Show native end card after complete
- Fire custom tracking for overlay close/cta

---

### 5. VAST Tag - VAST Companion (`vasttag_vast-companion`)
```json
{
  "delivery": "vast_tag",
  "vast": {
    "tagUrl": "..."
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." }
    // No companion keys - end card is IN the VAST XML
  ]
}
```
**Behavior:** IMA SDK handles everything including companion ads from VAST

---

### 6. VAST XML - VAST Companion (`vastxml_vast-companion`)
```json
{
  "delivery": "vast_xml",  // Note: mock shows "json" but should be vast_xml
  "vast": {
    "xmlBase64": "CjxWQVNUIHZlcnNpb249IjQuMiI..."
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." }
  ]
}
```
**Behavior:** Decode Base64 → Parse VAST XML → Pass to IMA SDK

---

## Required Implementation

### 1. Video Player Selection (4 Options)

**Player Types:**
1. **Basic Player** (Current simulated) - For non-VAST use cases
2. **ExoPlayer + IMA** (Recommended) - Most common Android solution, VAST-friendly
3. **Google IMA SDK** - Pure IMA implementation, VAST-only
4. **JW Player** - Commercial option, full VAST support

```kotlin
// New StateFlow in MainViewModel
val videoPlayer = MutableStateFlow("basic") // "basic", "exoplayer", "ima", "jwplayer"

// New UI in VideoOptionsSection
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
        selected = videoPlayer == "basic",
        onClick = { viewModel.setVideoPlayer("basic") },
        label = { Text("Basic (Non-VAST)") }
    )
    FilterChip(
        selected = videoPlayer == "exoplayer",
        onClick = { viewModel.setVideoPlayer("exoplayer") },
        label = { Text("ExoPlayer + IMA ⭐") }
    )
    FilterChip(
        selected = videoPlayer == "ima",
        onClick = { viewModel.setVideoPlayer("ima") },
        label = { Text("Google IMA") }
    )
    FilterChip(
        selected = videoPlayer == "jwplayer",
        onClick = { viewModel.setVideoPlayer("jwplayer") },
        label = { Text("JW Player") }
    )
}
```

### 2. Real Video Loading Logic

#### For JSON Delivery:
```kotlin
val videoAssetUrl = creative.contents.find { it.key == "videoAsset" }?.value as? String

when (selectedPlayer) {
    "exoplayer" -> {
        // Use ExoPlayer with IMA SDK
        val player = ExoPlayer.Builder(context).build()
        val mediaItem = MediaItem.fromUri(videoAssetUrl)
        player.setMediaItem(mediaItem)
        
        // Fire tracking events manually
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> fireVideoEvent("start")
                    Player.STATE_ENDED -> fireVideoEvent("complete")
                }
            }
        })
    }
    
    "ima" -> {
        // Use Google IMA SDK directly
        val adsLoader = ImaAdsLoader.Builder(context).build()
        val imaAdDisplayContainer = AdDisplayContainer.createAdDisplayContainer(...)
        // Configure IMA with ad tag URL
    }
    
    "jwplayer" -> {
        // Use JW Player SDK
        val playerView = JWPlayerView(context, ...)
        playerView.setup(PlaylistItem.Builder()
            .file(videoAssetUrl)
            .build())
    }
}
```

#### For VAST Tag:
```kotlin
val vastTagUrl = creative.vast?.tagUrl

when (selectedPlayer) {
    "exoplayer", "ima" -> {
        // Load VAST tag URL into IMA SDK
        val adsLoader = ImaAdsLoader.Builder(context)
            .setAdEventListener(adEventListener)
            .build()
        
        val adTagUri = Uri.parse(vastTagUrl)
        adsLoader.setAdViewProvider(...)
        adsLoader.requestAds(adTagUri)
    }
    
    "jwplayer" -> {
        val advertising = Advertising(AdSource.VAST, vastTagUrl)
        playerView.setup(PlaylistItem.Builder()
            .advertising(advertising)
            .build())
    }
}
```

#### For VAST XML:
```kotlin
val vastXmlBase64 = creative.vast?.xmlBase64
val vastXml = String(Base64.decode(vastXmlBase64, Base64.DEFAULT))

// Parse VAST XML manually or pass to player
// IMA SDK can accept VAST XML directly
```

### 3. Tracking Implementation

#### Video Event Tracking:
```kotlin
fun fireVideoEvent(eventKey: String) {
    creative.tracking.videoEvents
        ?.find { it.key == eventKey }
        ?.url?.let { url ->
            scope.launch(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.responseCode // Fire and forget
                    Log.d("VideoTracking", "Fired: $eventKey")
                } catch (e: Exception) {
                    Log.e("VideoTracking", "Error firing $eventKey: ${e.message}")
                }
            }
        }
}

// Events to track:
// - start (0%)
// - firstQuartile (25%)
// - midpoint (50%)
// - thirdQuartile (75%)
// - complete (100%)
// - pause, resume, skip, etc.
```

#### Progress Tracking:
```kotlin
player.addListener(object : Player.Listener {
    override fun onPositionDiscontinuity(...) {
        val position = player.currentPosition
        val duration = player.duration
        val progress = position.toFloat() / duration.toFloat()
        
        if (progress >= 0.25 && !fired25) {
            fireVideoEvent("firstQuartile")
            fired25 = true
        }
        if (progress >= 0.50 && !fired50) {
            fireVideoEvent("midpoint")
            fired50 = true
        }
        // etc...
    }
})
```

### 4. Content Key Mapping

Based on mock responses, key mappings:
- `videoAsset` → Main video URL (m3u8 or mp4)
- `posterImage` → Thumbnail/poster image
- `overlayAt` → Threshold to show overlay (if present)
- `endcard` → End card mode (from contents or metadata)
- `skipOffset` → When skip button appears (from creative.isSkippable())

For VAST:
- All tracking is inside VAST XML
- Player SDK handles it automatically
- For overlays/end cards, you parse companion ads from VAST

---

## Dependencies Needed

### build.gradle.kts additions:
```kotlin
dependencies {
    // ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.0")
    
    // Google IMA SDK
    implementation("androidx.media3:media3-exoplayer-ima:1.2.0")
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.31.0")
    
    // JW Player (if using)
    implementation("com.jwplayer:jwplayer-core:4.11.0")
}
```

---

## Implementation Priority

### Phase 1 (Critical):
1. ✅ Fix configuration refresh issue
2. ✅ Add video player selection to VideoOptionsSection
3. ✅ Implement ExoPlayer with videoAsset URL loading
4. ✅ Fire basic tracking events (start, complete)

### Phase 2 (Important):
5. ⏳ Handle VAST tag URL with IMA SDK
6. ⏳ Handle VAST XML Base64
7. ⏳ Implement all quartile tracking
8. ⏳ Add overlay at specific percentage

### Phase 3 (Nice to have):
9. ⏳ Implement JW Player option
10. ⏳ Native end cards
11. ⏳ VAST companion ads
12. ⏳ Skip functionality

---

## Current VideoPreviewScreen Issues

The current implementation is a **UI mockup**:
- Shows fake progress bar
- Simulates 30-second video
- Doesn't load real video URLs
- Tracking calls are placeholder

**It needs to be completely rewritten** to use actual video players.

---

## Recommended Approach

Start with **ExoPlayer + IMA** as it's the most common Android solution:

1. Replace simulated player UI with AndroidView containing ExoPlayer
2. Parse videoAsset from creative.contents
3. Load URL into ExoPlayer
4. Track progress and fire events
5. Then add VAST support
6. Finally add other player options

This is a significant refactor - the entire VideoPreviewScreen needs rebuilding.
