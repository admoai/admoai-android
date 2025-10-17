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
1. Parse creative data (`parseVideoData`)
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
- VAST Tag: `MediaItem.AdsConfiguration(adTagUrl)` → IMA auto
- VAST XML: Regex parse → manual SDK tracking
- JSON: Direct play → manual SDK tracking

**Overlays**: Custom Compose UI (skip, companions) - all modes

---

## 4. Media3 ExoPlayer

**Function**: `VastClientVideoPlayer()`

**All deliveries**: Manual control
- VAST: HTTP fetch/Base64 decode → regex parse → HTTP tracking
- JSON: Direct play → SDK tracking

**Overlays**: Custom Compose UI

---

## 5. Tracking

See `VIDEO_CONCEPTS.md` section 6.

**Media3+IMA**:
- VAST Tag: IMA auto (impression, quartiles)
- Other: Manual SDK

**Media3**:
- VAST: Manual HTTP GET
- JSON: Manual SDK

**All**: Custom events (overlay/CTA/close) always manual

**Quartiles**: 0%, 25%, 50%, 75%, 98%

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

## 8. Free Minutes Preview

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

## 10. Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions
- `TESTING_INSTRUCTIONS.md` - Setup & test matrix
- `VIDEO_IMPLEMENTATION_ROADMAP.md` - Implementation status
