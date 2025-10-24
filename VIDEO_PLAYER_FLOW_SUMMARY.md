# Video Player Flow Summary

## Overview

Video Ad Demo in Admoai Android Sample App. The video IS the ad (not pre-roll).

**Canonical reference**: See `VIDEO_CONCEPTS.md` for delivery methods, content keys, tracking rules, player capabilities.

---

## 1. Video Ad Demo Screen

**File**: `/sample/src/main/java/com/admoai/sample/ui/screens/VideoAdDemoScreen.kt`

**UI**:
- Title/description with bold "are independent of actual ad requests"
- Video Options: Delivery (JSON/VAST Tag/VAST XML), Companion (None/Custom UI/VAST Companion), Skippable toggle
- Video Player: Media3+IMA, Media3, JW Player (info only)
- "Launch Video Demo" button → navigates directly to video playback (no preview mode dialog)

**Helper texts** (italic, option name prefix):
- Delivery: "VAST Tag: URL to VAST XML..."
- Companion: "Custom UI: App-rendered..."  
- Skippable: "Skippable and Skip Offset: Mainly for video ads before content..."

**Logic**:
- `getLocalMockScenario()`: Maps selections → scenario string
- Fetches from `localhost:8080/endpoint?scenario={scenario}`
- All players support all deliveries (no disabling)

---

## 2. Video Preview Screen

**File**: `/sample/src/main/java/com/admoai/sample/ui/screens/VideoPreviewScreen.kt`

**Flow**:
1. Parse creative data (`parseVideoData`) - **Uses snake_case keys** (Oct 2025): `video_asset`, `poster_image`, `is_skippable`, `skip_offset`, `companion_headline`, `companion_cta`, `companion_destination_url`, `overlay_at_percentage`
2. Show "Implementation Details" card (context-aware per player/delivery/options)
3. Render player based on selection
4. Custom overlays for skip/companions (all scenarios)
5. Back arrow navigation (no reset button)

**Implementation Details Card**:
- **Media3+IMA + VAST Tag**: "IMA SDK auto-handles fetch/parse/tracking. Custom overlays. Can override manually."
- **Media3+IMA + VAST XML/JSON**: "Manual tracking. Custom overlays."
- **Media3 + Any**: "Full manual control. HTTP tracking for VAST, SDK for JSON."

**Player Selection**:
- `exoplayer` → `ExoPlayerImaVideoPlayer()` (Media3 + IMA)
- `vast_client` → `VastClientVideoPlayer()` (Media3 manual)
- `jwplayer` → Info text only

---

## 3. Media3 ExoPlayer + IMA

**Function**: `ExoPlayerImaVideoPlayer()`

**Architecture**:
- Media3: Playback engine
- IMA Extension: Ad logic (VAST Tag auto-tracking only)
- Uses `ImaAdsLoader` via `MediaSourceFactory`

**Delivery**:
- VAST Tag: `MediaItem.AdsConfiguration(adTagUrl)` → IMA auto-tracking
- VAST XML: Parse XML → store in `vastTrackingUrls` → HTTP GET tracking
- JSON: Direct play → SDK tracking

**Overlays**: Custom Compose UI (skip, companions)

**UI**: `controllerAutoShow = false` (controls hidden at start, tap to show)

---

## 4. Media3 ExoPlayer

**Function**: `VastClientVideoPlayer()`

**All deliveries**: Manual control
- VAST: HTTP fetch/Base64 decode → regex parse → HTTP tracking
- JSON: Direct play → SDK tracking

**Overlays**: Custom Compose UI

**UI**: `controllerAutoShow = false` (controls hidden at start, tap to show)

---

## 5. Tracking

See `VIDEO_CONCEPTS.md` section 6.

**Media3+IMA**:
- VAST Tag: IMA auto
- VAST XML: Direct HTTP GET
- JSON: Manual SDK

**Media3**:
- VAST: Manual HTTP GET
- JSON: Manual SDK

**All**: Custom events (overlay/CTA/close) always manual

**Quartiles**: 0%, 25%, 50%, 75%, 98%

**Event Names by Delivery**:
- **VAST**: camelCase (`firstQuartile`, `thirdQuartile`)
- **JSON**: snake_case (`first_quartile`, `third_quartile`)

---

## 6. File Structure

```
/sample/src/main/java/com/admoai/sample/ui/
├── screens/
│   ├── VideoAdDemoScreen.kt         # Video options UI + launch
│   ├── VideoPreviewScreen.kt        # Video players + playback
│   ├── PlacementPickerScreen.kt     # Placement list
│   └── previews/
│       └── FreeMinutesPreviewScreen.kt  # Free Minutes special case
├── components/
│   ├── VideoOptionsSection.kt       # Delivery/companion/player selectors
│   └── PreviewNavigationBar.kt      # Navigation (conditional refresh)
└── MainViewModel.kt                 # State + tracking
```

---

## 7. Placement Selection

**File**: `/sample/.../PlacementPickerScreen.kt`

**UI**: List of placements (promotions, rideSummary, waiting, freeMinutes, etc.) with badges indicating video-friendly placements.

**Preview Navigation** (standard):
- Back button (left)
- Response Details button (right)
- Refresh button (right) - fetches new ad

**Exception**: freeMinutes has NO refresh button (user clicks prize boxes instead).

---

## 8. Placement Preview Screens

**All preview screens** follow similar pattern:
1. Extend from `/sample/.../previews/*PreviewScreen.kt`
2. Use `PreviewNavigationBar` for consistent top bar
3. Call `AdCard` with appropriate `placementKey`
4. Handle `onAdClick` and `onTrackEvent` callbacks

### 8.1) Common Issues & Fixes

**Issue**: Theme toggle circles overlaying navigation buttons  
**Fix**: Removed circles from Home, VehicleSelection, Waiting screens  
**Affected Files**: `HomePreviewScreen.kt`, `VehicleSelectionPreviewScreen.kt`, `WaitingPreviewScreen.kt`

**Issue**: Vehicle Selection ads too high, nav bar overlapping content  
**Fix**: Increased top padding from 82dp → 120dp  
**File**: `VehicleSelectionPreviewScreen.kt`

**Issue**: `wideImageOnly` template with `default` style not rendering  
**Root Cause**: Used `SearchAdCard` (expects `squareImage`) instead of `HorizontalAdCard` (expects `posterImage`)  
**Fix**: Changed to use `HorizontalAdCard` for `wideImageOnly` template  
**File**: `VehicleSelectionPreviewScreen.kt`

**Issue**: Carousel CTA clicks not opening browser (promotions, waiting)  
**Root Cause**: Case mismatch - code looked for `urlSlide1`, API returns `URLSlide1`  
**Fix**: Changed content key from `urlSlide1` → `URLSlide1` (uppercase URL)  
**File**: `PromotionsCarouselCard.kt`

**Issue**: Card clicks not registering  
**Root Cause**: Using `.clickable()` modifier instead of Card's `onClick` parameter  
**Fix**: Use `Card(onClick = ...)` for reliable click handling  
**File**: `PromotionsCarouselCard.kt`

### 8.2) Free Minutes Preview

**File**: `/sample/.../previews/FreeMinutesPreviewScreen.kt`

See `VIDEO_CONCEPTS.md` section 10 for full details.

**Components**:
- `/sample/.../components/PreviewNavigationBar.kt` - Supports conditional refresh button via `showRefreshButton` parameter
- Fullscreen video player with ExoPlayer (Media3)
- End-card overlay with AsyncImage (Coil)

**Flow**:
1. Prize boxes screen → tap box → fullscreen video
2. Video plays with back button + progress bar
3. On completion → end-card with X button + CTA

**Key Implementation Details**:
- Video URL: Hardcoded (dev mode) - future: from ad response
- End-card: Hardcoded image/CTA - future: from ad response
- Response Details: Will save last played video response
- No refresh button: Clicking prize boxes replaces refresh functionality

---

## 9. UI/UX Details

**Material Design 3**: 16dp screen padding, consistent sections

**Sections**: "VIDEO OPTIONS", "VIDEO PLAYER" with proper alignment

**Helper texts**: Italic, option name prefix, concise professional language

**Custom overlays**:
- Skip button: Badge bubble top-right
- Companions: Card-based with elevation, smooth animations
- CTA: Clickable with ripple effect

**No removed**: Preview mode dialog, placement/advertiser/delivery header, "Video Ad Configuration" section, reset button

---

## 10. Template Mapping Quick Reference

See `VIDEO_CONCEPTS.md` section 11 for complete mapping rules.

**Key Files**:
- `/sample/.../mapper/AdTemplateMapper.kt` - Template detection & helpers
- `/sample/.../components/AdCard.kt` - Routing logic
- `/sample/.../model/AdContent.kt` - Content extraction helpers

**Component Map**:
```
HorizontalAdCard    → home, vehicleSelection (wideImageOnly), rideSummary
SearchAdCard        → search, vehicleSelection (imageWithText)
MenuAdCard          → menu
PromotionsCarouselCard → promotions, waiting
```

**Critical Content Keys**:
- **snake_case** (Oct 2025): `poster_image`, `video_asset`, `is_skippable`, `skip_offset`, `companion_headline`, `companion_cta`, `companion_destination_url`, `overlay_at_percentage`
- `poster_image` (HorizontalAdCard) vs `squareImage` (SearchAdCard)
- `URLSlide1/2/3` (uppercase) for carousel URLs
- `clickThroughURL` for standard ad URLs

**Click-Through**: Enabled for home, vehicleSelection, rideSummary, promotions (CTA), waiting (CTA), freeMinutes

---

## 11. Decision Request Builder Video Integration

**Added**: October 24, 2025

### Overview

Video functionality integrated into Decision Request Builder. Users can now select "Video" format and see video ads playing in placement preview screens.

**File**: `/sample/src/main/java/com/admoai/sample/ui/MainViewModel.kt`
**Component**: `/sample/src/main/java/com/admoai/sample/ui/components/VideoPlayerForPlacement.kt`

### Flow

1. **Decision Request Screen**:
   - User enables "Use Format Filter" toggle
   - Selects "Video" from format dropdown
   - Selects video-eligible placement (Promotions, Waiting, Vehicle Selection, Ride Summary)
   - Taps "Request and Preview"

2. **Request Routing** (⚠️ DEVELOPMENT ONLY):
   ```kotlin
   // MainViewModel.loadAds() - Line 623
   if (_selectedFormat.value == "video") {
       loadVideoAdsFromLocalhost()  // Routes to localhost:8080
   } else {
       // Normal SDK flow to mock.api.admoai.com
   }
   ```

3. **Video Request Details**:
   - **Endpoint**: `http://10.0.2.2:8080/v1/decision` (⚠️ Dev only, HTTP not HTTPS)
   - **Header**: `X-Decision-Version: 2025-11-01`
   - **⚠️ Hardcoded**: Placement key overridden to `"vasttag_none"` (line 670-677)
   - **⚠️ Modified**: `getAdDataForPlacement()` returns first placement for video format (line 951-954)

4. **Response Handling**:
   - Parses decision-engine response
   - Handles `"creatives": null` case (replaces with empty array)
   - Logs full response for debugging
   - Stores in `_decisionResponse` state

5. **Placement Preview Rendering**:
   ```kotlin
   // Each placement screen checks:
   val isVideoAd = adData?.creatives?.firstOrNull()?.let { creative ->
       viewModel.isVideoCreative(creative)  // Checks delivery != null or contentType == VIDEO
   } ?: false
   
   if (isVideoAd && adData != null) {
       VideoPlayerForPlacement(creative, viewModel)  // VAST playback
   } else {
       AdCard(adData, ...)  // Native ad rendering
   }
   ```

6. **Video Playback**:
   - **VAST Tag**: Fetches XML from `tagUrl`, parses, extracts video URL and tracking
   - **VAST XML**: Decodes Base64 `xmlBase64`, parses same as VAST Tag
   - **JSON**: Direct playback with `video_asset` URL
   - **Player**: Media3 ExoPlayer with manual tracking (Player 2 approach)

### VideoPlayerForPlacement Component

**Capabilities**:
- ✅ VAST Tag fetching and parsing
- ✅ VAST XML Base64 decoding and parsing  
- ✅ JSON direct playback
- ✅ Manual tracking (HTTP GET for VAST, SDK for JSON)
- ✅ Skip button with countdown (uses VAST skipoffset if available)
- ✅ Native end-card overlays (`companionHeadline`, `companionCta`, `companionDestinationUrl`)
- ✅ Loading/error states
- ✅ Proper cleanup on dispose

**Tracking Events**:
- **VAST** (camelCase): `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete`, `skip`
- **JSON** (snake_case): `start`, `first_quartile`, `midpoint`, `third_quartile`, `complete`, `skip`

**Content Keys** (from creative.contents):
- `video_asset` - Video URL (JSON delivery only)
- `poster_image` - Poster/thumbnail (all deliveries)
- `is_skippable` - Skip configuration
- `skip_offset` - Skip offset in seconds
- `companionHeadline` - End-card headline (camelCase)
- `companionCta` - End-card CTA text (camelCase)
- `companionDestinationUrl` - End-card destination (camelCase)
- `overlayAtPercentage` - When to show overlay 0.0-1.0 (camelCase)

### Modified Placement Screens

All video-eligible placement screens updated:
- `/sample/.../previews/PromotionsPreviewScreen.kt`
- `/sample/.../previews/WaitingPreviewScreen.kt`
- `/sample/.../previews/VehicleSelectionPreviewScreen.kt`
- `/sample/.../previews/RideSummaryPreviewScreen.kt`

Each screen:
1. Checks if creative is video using `viewModel.isVideoCreative()`
2. Renders `VideoPlayerForPlacement` if video
3. Falls back to native ad card if not video
4. Maintains existing animations and layout

### Production Cleanup Required

⚠️ **Before production deployment**, remove development workarounds:

1. **Remove hardcoded placement key** (`MainViewModel.kt` lines 668-677):
   ```kotlin
   // REMOVE THIS:
   val modifiedRequest = sdk.prepareFinalDecisionRequest(request).copy(
       placements = listOf(Placement(key = "vasttag_none", format = PlacementFormat.VIDEO))
   )
   
   // USE THIS:
   val finalRequest = sdk.prepareFinalDecisionRequest(request)
   ```

2. **Restore normal placement matching** (`MainViewModel.kt` lines 951-954):
   ```kotlin
   // REMOVE special case for video format
   if (_selectedFormat.value == "video" && data != null && data.isNotEmpty()) {
       return data.first()  // ← REMOVE THIS
   }
   
   // Keep only:
   return data?.find { it.placement == placementKey }
   ```

3. **Update endpoint** or **remove conditional routing**:
   - Option A: Use standard SDK flow for all requests (remove `loadVideoAdsFromLocalhost()`)
   - Option B: Update endpoint from `http://10.0.2.2:8080` to production URL

4. **Update `getHttpRequest()` preview**:
   - Ensure correct production host/endpoint shown in Request Preview tab

### Testing

See `TESTING_INSTRUCTIONS.md` for complete test flow.

**Quick Test**:
```bash
# 1. Start local mock server
cd /path/to/mock-endpoints
PORT=8080 go run main.go

# 2. Run app
./gradlew :sample:installDebug

# 3. In app:
# - Open "Decision Request"
# - Enable "Use Format Filter"
# - Select "Video" format
# - Select "Promotions" placement
# - Tap "Request and Preview"
# - Result: Video plays with VAST Tag delivery
```

---

## 12. Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions
- `TESTING_INSTRUCTIONS.md` - Setup & test matrix
- `VIDEO_IMPLEMENTATION_ROADMAP.md` - Implementation status
