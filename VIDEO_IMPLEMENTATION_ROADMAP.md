# Video Implementation Status

## ✅ Implementation Complete

Video Demo ready. See `VIDEO_CONCEPTS.md` for canonical reference.

## Current Features

**Video Ad Demo**:
- **Deliveries**: JSON, VAST Tag, VAST XML  
- **Players**: Media3+IMA (VAST Tag auto, rest manual), Media3 (all manual), JW (info)  
- **End-cards**: None, Native (companion* keys), VAST Companion (<CompanionAds>)  
- **Skip**: Custom overlay UI (all modes)  
- **Tracking**: Quartiles 0/25/50/75/98%, custom events (overlay/CTA/close)

**Decision Request Builder - Video Integration** (✅ NEW - Oct 24, 2025):
- **Video Format Support**: Decision Request Builder now supports video format requests
- **Local Testing**: Video requests route to `http://10.0.2.2:8080/v1/decision` (⚠️ DEVELOPMENT ONLY)
- **Custom Header**: Includes `X-Decision-Version: 2025-11-01` for video requests
- **Video Player**: Full VAST Tag playback in placement previews using Media3 ExoPlayer (Player 2 approach)
- **Manual Tracking**: HTTP GET beacons for VAST deliveries, SDK methods for JSON
- **Supported Placements**: Promotions, Waiting, Vehicle Selection, Ride Summary
- **⚠️ Development Workarounds** (to be removed later):
  - Hardcoded placement key to `"vasttag_none"` for localhost testing
  - Modified `getAdDataForPlacement()` to return first placement for video format
  - Conditional routing in `loadAds()` checks for format="video"

**Placement Previews** (✅ All Implemented):
- **home**: `wideWithCompanion` template, click-through enabled
- **search**: `imageWithText` template (imageLeft/imageRight), no click-through
- **menu**: `textOnly` template, no click-through
- **promotions**: `carousel3Slides` template, CTA URLs open browser
- **waiting**: `carousel3Slides` template, CTA URLs open browser
- **vehicleSelection**: Supports both `imageWithText` and `wideImageOnly` templates
- **rideSummary**: `standard` template, click-through enabled
- **freeMinutes**: Custom two-step flow (special case)

**Template Mapping** (✅ Complete):
- Comprehensive mapping rules documented in `VIDEO_CONCEPTS.md` section 11
- Support for 6 template types: `wideWithCompanion`, `imageWithText`, `textOnly`, `carousel3Slides`, `wideImageOnly`, `standard`
- Case-sensitive content key extraction
- Click-through logic for 5 placements

**Free Minutes** (✅ Special Case):
- Prize boxes with notification badges
- Fullscreen video player (ExoPlayer/Media3)
- Back button + progress bar during playback
- End-card on completion (image, text, CTA)
- Hardcoded for dev (🔄 future: from ad response)

**Recent Fixes** (Oct 2025):
- ✅ **Content Key Naming**: Changed to snake_case (`video_asset`, `poster_image`, `is_skippable`, `skip_offset`, `companion_*`, `overlay_at_percentage`)
- ✅ **Tracking Event Naming**: VAST uses camelCase (`firstQuartile`, `thirdQuartile`), JSON uses snake_case (`first_quartile`, `third_quartile`)
- ✅ **Player 1 VAST XML Tracking**: Fixed to fire tracking URLs via HTTP GET (stored in `vastTrackingUrls` map) instead of SDK method
- ✅ **Player UI Controls**: Added `controllerAutoShow = false` to Player 1 & 3 (controls hidden at start, tap to show)
- ✅ **Logging Standards**: Professional logs with structured tags `[MANUAL]`, `[AUTOMATIC]`, `[URL]`, `[HTTP]`
- ✅ Removed theme toggle circles overlaying nav buttons
- ✅ Fixed Vehicle Selection padding (82dp → 120dp)
- ✅ Fixed `wideImageOnly` template rendering (now uses `HorizontalAdCard`)
- ✅ Fixed carousel CTA clicks (case fix: `urlSlide1` → `URLSlide1`)
- ✅ Fixed Card click handling (use `onClick` parameter)
- ✅ Increased API timeout (10s → 30s)

**UI**: Material Design 3, direct navigation (no preview dialog), context-aware implementation details, italic helper texts

## Implementation Notes

See `VIDEO_CONCEPTS.md` for:
- Content keys (posterImage, videoAsset, companion*, isSkippable, etc.)
- Delivery methods (JSON/VAST Tag/VAST XML rules)
- Tracking responsibility matrix
- Player capabilities

**Key Files**:

**Video Ad Demo**:
- `/sample/.../VideoAdDemoScreen.kt` - Options UI + launch
- `/sample/.../VideoPreviewScreen.kt` - Players + playback
- `/sample/.../VideoOptionsSection.kt` - Selectors

**Placement Previews**:
- `/sample/.../PlacementPickerScreen.kt` - Placement list
- `/sample/.../previews/HomePreviewScreen.kt` - Home placement
- `/sample/.../previews/SearchPreviewScreen.kt` - Search placement
- `/sample/.../previews/MenuPreviewScreen.kt` - Menu placement
- `/sample/.../previews/PromotionsPreviewScreen.kt` - Promotions placement
- `/sample/.../previews/WaitingPreviewScreen.kt` - Waiting placement
- `/sample/.../previews/VehicleSelectionPreviewScreen.kt` - Vehicle Selection
- `/sample/.../previews/RideSummaryPreviewScreen.kt` - Ride Summary
- `/sample/.../previews/FreeMinutesPreviewScreen.kt` - Free Minutes (special)

**Components**:
- `/sample/.../components/PreviewNavigationBar.kt` - Navigation (conditional refresh)
- `/sample/.../components/AdCard.kt` - Template routing logic
- `/sample/.../components/HorizontalAdCard.kt` - Wide cards (home, rideSummary, wideImageOnly)
- `/sample/.../components/SearchAdCard.kt` - Image+text cards (search, vehicleSelection)
- `/sample/.../components/MenuAdCard.kt` - Text-only cards (menu)
- `/sample/.../components/PromotionsCarouselCard.kt` - Carousel (promotions, waiting)
- `/sample/.../components/VideoPlayerForPlacement.kt` - ✅ **NEW**: Video player for Decision Request Builder placements

**Mapping & Helpers**:
- `/sample/.../mapper/AdTemplateMapper.kt` - Template detection & helpers
- `/sample/.../model/AdContent.kt` - Content extraction utilities

**SDK Configuration**:
- `/sdk/.../config/SDKConfig.kt` - Timeout settings (30s)

**Shared**:
- `/sample/.../MainViewModel.kt` - State + tracking

**Mock Server**: 
- **Video Demo**: `https://10.0.2.2:8080` (HTTPS with self-signed cert)
- **Decision Request Builder**: `http://10.0.2.2:8080` (HTTP, ⚠️ development only)

## Decision Request Builder Video Integration - Technical Details

**Added in October 24, 2025**

### Files Modified

1. **`MainViewModel.kt`**:
   - Added `loadVideoAdsFromLocalhost()` function for direct HTTP calls
   - Modified `loadAds()` to conditionally route video format requests
   - ⚠️ **TEMPORARY**: Hardcoded placement key to `"vasttag_none"` in request body (line 670-677)
   - ⚠️ **TEMPORARY**: Modified `getAdDataForPlacement()` to return first placement for video format (line 951-954)
   - Updated `getHttpRequest()` to show `X-Decision-Version: 2025-11-01` header in preview
   - **Production Cleanup Required**: Remove hardcoded placement override, restore normal placement matching

2. **`VideoPlayerForPlacement.kt`** (NEW FILE):
   - Full VAST Tag/XML playback with Media3 ExoPlayer
   - Manual tracking via HTTP GET for VAST deliveries (camelCase events: `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete`, `skip`)
   - SDK tracking methods for JSON deliveries (snake_case events: `start`, `first_quartile`, etc.)
   - VAST XML parsing with `parseVastXml()` helper function
   - Skip button support with countdown
   - Native end-card overlay support
   - Loading/error states

3. **Placement Preview Screens** (Updated):
   - `PromotionsPreviewScreen.kt` - Added video detection and rendering
   - `WaitingPreviewScreen.kt` - Added video detection and rendering  
   - `VehicleSelectionPreviewScreen.kt` - Added video detection and rendering
   - `RideSummaryPreviewScreen.kt` - Added video detection and rendering
   - Each uses `viewModel.isVideoCreative()` to detect video ads
   - Renders `VideoPlayerForPlacement` for videos, falls back to native cards

### How It Works

```kotlin
// 1. User selects format="video" in Decision Request Builder
// 2. MainViewModel.loadAds() detects video format
if (_selectedFormat.value == "video") {
    loadVideoAdsFromLocalhost()  // ⚠️ Dev only: Routes to localhost:8080
} else {
    // Normal SDK flow to mock.api.admoai.com
}

// 3. Video request includes custom header
connection.setRequestProperty("X-Decision-Version", "2025-11-01")

// 4. Placement key temporarily overridden (⚠️ TO BE REMOVED)
val modifiedRequest = sdk.prepareFinalDecisionRequest(request).copy(
    placements = listOf(
        Placement(key = "vasttag_none", format = PlacementFormat.VIDEO)
    )
)

// 5. Response parsed and stored
_decisionResponse.value = response

// 6. Placement screen gets ad data
val adData = viewModel.getAdDataForPlacement(placementKey)
// ⚠️ For video, returns first placement regardless of key mismatch

// 7. Placement screen renders video if detected
if (viewModel.isVideoCreative(creative)) {
    VideoPlayerForPlacement(creative, viewModel)  // VAST Tag playback
}
```

### Production Cleanup Checklist

When moving to production with `mock.api.admoai.com`:

- [ ] **Remove hardcoded placement override** in `MainViewModel.kt` (lines 668-677)
  - Use actual selected placement key instead of `"vasttag_none"`
  
- [ ] **Restore normal placement matching** in `getAdDataForPlacement()` (lines 951-954)
  - Remove the special case that returns first placement for video
  - Use standard key matching for all requests
  
- [ ] **Update endpoint** in `loadVideoAdsFromLocalhost()` (line 652)
  - Change from `http://10.0.2.2:8080/v1/decision` 
  - To `https://mock.api.admoai.com/decisions` or production endpoint
  
- [ ] **Consider conditional routing**:
  - Option A: Always use standard SDK flow (remove `loadVideoAdsFromLocalhost()`)
  - Option B: Keep separate flow if production video endpoint differs
  
- [ ] **Update `getHttpRequest()` preview**:
  - Ensure correct host/endpoint shown in Request Preview tab

## Next Steps (Future)

**Free Minutes Integration**:
1. Replace hardcoded video URL with ad response content
2. Replace hardcoded end-card with ad response content
3. Save last played video response to Response Details
4. Add template mapping rules for Free Minutes ad format

**Template Expansion**:
- Additional templates can follow pattern in `VIDEO_CONCEPTS.md` section 11.8
- Add new constants to `AdTemplateMapper.kt`
- Create component if needed
- Update routing in `AdCard.kt`
- Document in section 11

---

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions (★ see section 11 for mapping rules)
- `TESTING_INSTRUCTIONS.md` - Setup & tests (includes debugging guide)
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details (see section 8 for common fixes)
