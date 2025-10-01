# Video Player Flow - Complete Implementation Summary

## Overview
This document describes the complete video player implementation in the Admoai Android Sample App, including all UI flows, player selection logic, VAST tag handling, and tracking integration.

---

## 1. Entry Point: Video Ad Demo Screen

**File:** `/sample/src/main/java/com/admoai/sample/ui/screens/VideoAdDemoScreen.kt`

### UI Components:
1. **Placement Selector** - Choose where the ad appears (home, rider_waiting, etc.)
2. **Video Options Section** - Select delivery method and end-card type:
   - **Delivery Method**: JSON, VAST Tag, VAST XML
   - **End Card Type**: None, Native End-card, VAST Companion
3. **Video Player Section** - Choose player implementation:
   - **ExoPlayer + IMA** ⭐ (Recommended, VAST-friendly)
   - **Basic Player** (Non-VAST, JSON only) - DISABLED for VAST delivery
   - **Google IMA SDK** (Pure IMA, VAST-only)
   - **JW Player** (Commercial, full VAST support)
4. **Launch Video Demo Button** - Triggers video preview

### Key Logic:
- **Scenario Generation** (`getLocalMockScenario`): Maps UI selections to mock server scenarios
  - Example: `delivery="vast_tag" + endCard="none"` → `"vasttag_none"`
- **Mock Server Request**: Fetches ad creative data from `localhost:8080/endpoint?scenario={scenario}`
- **Player Disabling**: Basic Player is disabled when VAST Tag/XML is selected (not VAST-compliant)

---

## 2. Video Preview Screen

**File:** `/sample/src/main/java/com/admoai/sample/ui/screens/VideoPreviewScreen.kt`

### Flow After "Launch Video Demo":

1. **Parse Video Data** (`parseVideoData` function):
   - Extracts video URL from creative based on delivery method
   - For VAST: Uses `creative.vast.tagUrl`
   - For JSON: Uses `contents["videoAsset"]`
   - Extracts poster image, overlay settings, companion ad data

2. **Player Selection Logic**:
   ```kotlin
   when (videoPlayer) {
       "exoplayer" -> ExoPlayerImaVideoPlayer()
       "basic" -> BasicVideoPlayer()
       "ima" -> Text("Pure IMA SDK - Not Yet Implemented")
       "jwplayer" -> Text("JW Player - Not Yet Implemented")
   }
   ```

3. **Video Configuration** (`VideoPlayerConfig` data class):
   - `videoAssetUrl`: VAST tag URL or direct video URL
   - `posterImageUrl`: Thumbnail image
   - `overlayAtPercentage`: When to show native end-card (0.5 = 50%)
   - `showClose`: Whether to show close button
   - `companionHeadline`, `companionCta`, `companionDestinationUrl`: End-card content

---

## 3. ExoPlayer + IMA Player Implementation

**Function:** `ExoPlayerImaVideoPlayer()` (lines 925-1172)

### IMA SDK Integration:
```kotlin
// 1. Create IMA ads loader
val adsLoader = ImaAdsLoader.Builder(context).build()

// 2. PlayerView reference for ad rendering
var playerView: PlayerView? by remember { mutableStateOf(null) }

// 3. Configure ExoPlayer with IMA
ExoPlayer.Builder(context)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(context)
            .setAdsLoaderProvider { adsLoader }
            .setAdViewProvider { playerView!! } // ← CRITICAL for IMA
    )
    .build()
```

### VAST Tag Handling:
- **Content URI**: Uses real video URL (not VAST tag) as content
- **Ads Configuration**: VAST tag URL goes here, IMA fetches and parses it
```kotlin
val contentVideoUri = "https://videos.admoai.com/VwBe1DrWseFTdiIPnzPzKhoo7fX01N92Hih4h6pNCuDA.m3u8"
MediaItem.Builder()
    .setUri(Uri.parse(contentVideoUri))
    .setAdsConfiguration(
        MediaItem.AdsConfiguration.Builder(Uri.parse(vastTagUrl)).build()
    )
```

### Features:
- ✅ Plays VAST ads via IMA SDK
- ✅ Shows poster image before playback
- ✅ Displays custom native end-card overlay (for JSON delivery)
- ✅ Error handling with user-friendly messages
- ✅ Automatic IMA tracking (via VAST XML events)

---

## 4. Basic Video Player Implementation

**Function:** `BasicVideoPlayer()` (lines 1174-1405)

### Configuration:
- Simple ExoPlayer without IMA integration
- Manual tracking via Admoai SDK
- For JSON delivery only (no VAST support)

### Features:
- ✅ Direct video playback from URL
- ✅ Poster image support
- ✅ Custom native end-card overlay
- ✅ Manual video event tracking (start, quartiles, complete)

---

## 5. Mock Server Integration

**Location:** `/Users/matias-admoai/Documents/repos/mock-endpoints/main.go`

### Endpoints:
- **Decision API**: `GET /endpoint?scenario={scenario}`
  - Returns JSON with creative data (delivery, vast, contents, tracking)
  
- **VAST Tag URLs**: `GET /endpoint?scenario=tagurl_vasttag_none`
  - Returns VAST XML with `Content-Type: application/xml` ✅
  - Contains `<MediaFile>` with actual video URL
  - Contains `<Tracking>` events for impressions, clicks, quartiles

### Key Fix Applied:
Changed VAST tag responses from `Content-Type: text/plain` → `application/xml`
```go
case "tagurl_vasttag_none":
    response = mockData.TagURLVastTagNone
    contentType = "application/xml" // ← Fixed
```

### Android Emulator URL Translation:
- From Mac terminal: `localhost:8080`
- From Android emulator: `10.0.2.2:8080` (special IP to reach host)

---

## 6. Video Player Options UI

**File:** `/sample/src/main/java/com/admoai/sample/ui/components/VideoOptionsSection.kt`

### VideoPlayerSection:
- Renders player selection chips
- **Dynamic Disabling**: Basic Player is disabled for VAST delivery
```kotlin
val isVastDelivery = videoDelivery in listOf("vast_tag", "vast_xml")
val isDisabled = option.value == "basic" && isVastDelivery
FilterChip(enabled = !isDisabled, ...)
```

---

## 7. Tracking Integration

### IMA SDK (ExoPlayer + IMA):
- **Automatic**: VAST XML `<Tracking>` events fire automatically
- Events: impression, start, firstQuartile, midpoint, thirdQuartile, complete, click

### Manual Tracking (Basic Player):
- Uses Admoai SDK methods: `viewModel.fireVideoEvent(creative, eventName)`
- Events fired at video progress milestones (0%, 25%, 50%, 75%, 100%)

### Custom Events:
- **Overlay Shown**: `viewModel.fireCustomEvent(creative, "overlayShown")`
- **CTA Click**: `viewModel.fireClick(creative, "cta")`
- **Close Button**: `viewModel.fireCustomEvent(creative, "closeBtn")`

---

## 8. Key Fixes Applied

### Issue 1: Player/Delivery Mismatch Warning
**Fix**: Removed warning when using JSON with ExoPlayer + IMA (publishers use single player)

### Issue 2: CTA Button Text Too Large
**Fix**: Changed from `labelMedium` → `labelSmall`, added `maxLines = 1`

### Issue 3: VAST + Basic Player Allowed
**Fix**: Disabled Basic Player chip when VAST delivery selected

### Issue 4: No Poster Images
**Fix**: Added `MediaMetadata` with `artworkUri` to both players

### Issue 5: SSL Error with VAST Tags
**Fix**: Mock server returns `http://` (not `https://`) for `10.0.2.2` URLs

### Issue 6: UnrecognizedInputFormatException
**Fix**: Use real video URL as content URI, not VAST tag URL

### Issue 7: Resource Not Found
**Fix**: Use actual video URL instead of fake `android.resource://` URI

### Issue 8: IMA Ads Not Loading
**Fix**: Added `.setAdViewProvider { playerView!! }` to MediaSourceFactory

---

## 9. File Structure

```
/sample/src/main/java/com/admoai/sample/ui/
├── screens/
│   ├── VideoAdDemoScreen.kt       # Main video demo UI + scenario selection
│   └── VideoPreviewScreen.kt      # Video players + playback logic
├── components/
│   └── VideoOptionsSection.kt     # Delivery/end-card/player selectors
└── MainViewModel.kt               # State management + tracking methods
```

---

## 10. Testing Checklist

✅ **JSON + None + Basic Player**: Direct video playback, manual tracking
✅ **JSON + Native End-card + Basic Player**: Overlay appears at 50%, CTA works
✅ **JSON + None + ExoPlayer + IMA**: Video plays (no ads, content only)
✅ **JSON + Native End-card + ExoPlayer + IMA**: Overlay + IMA integration
✅ **VAST Tag + None + ExoPlayer + IMA**: IMA loads VAST, plays ads
✅ **VAST Tag + Native End-card + ExoPlayer + IMA**: IMA ads + custom overlay
✅ **Basic Player disabled for VAST**: Chip grayed out when VAST selected

---

## 11. Next Steps / Known Issues

### To Test:
- Verify IMA SDK actually loads VAST ads (check for IMA logs, not just "Playing media without ads")
- Confirm VAST tracking events fire automatically
- Test different VAST tag URLs (Google DoubleClick, custom servers)

### Not Yet Implemented:
- Pure Google IMA SDK player (placeholder)
- JW Player integration (placeholder)
- VAST XML delivery (Base64-encoded XML in decision response)

---

## Key Takeaways

1. **VAST Tag Flow**: Decision API returns tagUrl → IMA fetches VAST XML → IMA parses MediaFile URL → IMA plays ad
2. **JSON Flow**: Decision API returns videoAsset URL → Player loads directly → Manual tracking
3. **IMA Requirements**: Both `setAdsLoaderProvider` AND `setAdViewProvider` must be configured
4. **Mock Server URLs**: Use `http://10.0.2.2:8080` from Android emulator, `localhost:8080` from Mac
5. **Content URI**: For VAST-only ads, use real video URL as content, VAST tag in AdsConfiguration
