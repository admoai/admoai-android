# Video Ad Concepts – Single Source of Truth

This document centralizes the key concepts used by the Admoai Android Sample App. Keep this short, canonical, and authoritative. Other docs should link here instead of duplicating definitions.

---

## 1) Responsibility Rule
- The selected video player must fully apply the chosen delivery method and end‑card mode end‑to‑end.
  - VAST (tag or XML) → A VAST‑capable player must ingest VAST and fire tracking automatically (IMA).
  - JSON → Regardless of player, tracking is manual (no VAST SDK auto‑tracking).

---

## 2) Delivery Methods

| Delivery     | VAST object        | `videoAsset` present | Tracking location |
|--------------|--------------------|----------------------|-------------------|
| `json`       | `null`             | ✅ Yes               | JSON response     |
| `vast_tag`   | `{ tagUrl }`       | ❌ No                | VAST XML (remote) |
| `vast_xml`   | `{ xmlBase64 }`    | ❌ No                | VAST XML (decoded)|

Rules:
- `json` → `vast` must be `null` and `videoAsset` must be present.
- `vast_tag`/`vast_xml` → `videoAsset` must NOT be present; tracking comes from VAST XML.
- `posterImage` is always present but NOT displayed during video playback (see section 15).

Terminology:
- "VAST Tag" = URL endpoint that returns VAST XML.
- "VAST XML" = The XML document containing tracking, media files, companions, etc.

---

## 3) End‑Card Modes
- None: Video only.
- Native End‑Card: Publisher draws overlay using `companion*` keys from JSON, using `overlayAtPercentage` to determine when to show it.
- VAST Companion: From `<CompanionAds>` in VAST XML (applies to VAST deliveries).

Hybrid: VAST video + native end‑card overlay is allowed.

---

## 4) Content Keys

**⚠️ Naming Convention**: All content keys use **snake_case** (Oct 2025 update).

Canonical (non‑editable):
- `poster_image` – Always present.
- `video_asset` – Direct video URL (JSON only).
- `is_skippable` / `skip_offset` – Skip button configuration.

User‑defined (editable convention):
- `companion_headline`, `companion_cta`, `companion_destination_url` – Native end‑card content.
- `overlay_at_percentage` – When to show overlay (0.0–1.0).
- `show_close` – Close button visibility.

**Legacy**: Previous versions used camelCase (`posterImage`, `videoAsset`, etc.). Mock server changed to snake_case for consistency.

---

## 5) Player Capability Matrix

- **Media3 ExoPlayer + IMA**
  - VAST Tag: IMA auto-handles fetch/parse/tracking. Custom skip/companion overlays.
  - VAST XML: Manual parse (regex), IMA playback. Custom overlays.
  - JSON: Direct playback, manual SDK tracking. Custom overlays.
  - End-cards: Publisher-drawn Compose overlays (all modes).
  - Skip: Custom UI overlay (all modes). IMA native skip unavailable in Compose.

- **Media3 ExoPlayer**
  - All deliveries with full manual control.
  - VAST: HTTP fetch/Base64 decode → regex parse → manual HTTP tracking.
  - JSON: Direct playback, SDK tracking.
  - End-cards/Skip: Custom Compose overlays.

- **JW Player**
  - Commercial. Full VAST/IMA/OMID support (info only).

---

## 6) Tracking Responsibility

| Player | JSON | VAST Tag | VAST XML | Custom |
|--------|------|----------|----------|--------|
| **Media3 + IMA** | SDK | IMA Auto | HTTP | SDK |
| **Media3** | SDK | HTTP | HTTP | SDK |

**Quartiles**: 0%, 25%, 50%, 75%, 98% (not 100% to avoid race conditions).

**Event Names by Delivery**:
- **VAST (Tag/XML)**: camelCase per VAST standard → `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete`, `skip`
- **JSON**: snake_case per Admoai system → `start`, `first_quartile`, `midpoint`, `third_quartile`, `complete`, `skip`

**Tracking Methods**:
- **VAST XML**: Direct HTTP GET to URLs parsed from XML (Player 1 stores in `vastTrackingUrls` map)
- **VAST Tag**: IMA SDK fires automatically
- **JSON**: SDK's `fireVideoEvent()` method
- **Custom events** (overlay/CTA/close): Always manual via SDK

---

## 7) Compliance & Validation

**IMA Watermarks & Clickthrough**:
- VAST Tag playback via IMA shows "Ad" and "Learn more" badges (compliance requirement).
- Tapping "Learn more" opens clickthrough URL in browser.
- **Android 11+ Requirement**: App must declare package visibility in `AndroidManifest.xml` to detect browsers.
- Validation: If badges don't appear or clicks fail, check manifest `<queries>` block.

**IMA Skip Button**:
- Requires minimum 8-second ad duration to display.
- VAST Tag: IMA native skip works correctly, fires `SKIPPED` event automatically.
- Custom overlays (companions) can coexist with IMA native skip.

**HTTPS Requirement**:
- VAST requests via IMA require HTTPS (runs in WebView).
- HTTP blocked by mixed-content policy.

---

## 8) Practical Rules of Thumb
- Always check `delivery` first; it dictates parsing and tracking strategy.
- `poster_image` is universal (snake_case).
- Do not mix manual tracking into an IMA‑handled VAST flow (except custom UI events).
- JSON + any player: play `video_asset`, do manual tracking, optionally draw a native end‑card.
- VAST + IMA players: pass `adTagUrl` or `adsResponse` (pure IMA), let IMA handle tracking.

**Logging Standards** (Oct 2025): All logs are professional, emoji-free, structured with tags like `[MANUAL]`, `[AUTOMATIC]`, `[URL]`, `[Response]`. No repetitive logs in tracking loops.

---

## 9) Placement Selection

**File**: `/sample/.../PlacementPickerScreen.kt`

**Flow**:
1. List of available placements with preview navigation
2. Each placement has a preview screen showing realistic app context
3. Standard placements: Show ad response directly
4. Special placement (freeMinutes): Multi-step interactive flow

**Standard Preview Navigation**:
- Back button (left)
- Response Details button (right)
- Refresh button (right) - fetches new ad

---

## 10) Free Minutes Placement (Special Case)

**File**: `/sample/.../previews/FreeMinutesPreviewScreen.kt`

**UI Differences**:
- NO refresh button (user clicks prize boxes instead)
- Response Details saves last played video response
- Three clickable prize boxes with notification badges

**Two-Step Flow**:
1. Prize selection screen: User taps any box to request ad
2. Fullscreen video player: Plays hardcoded video (dev mode)

**Video Player**:
- Back button + message (top-left, immediate): "Advertiser will give you free minutes for watching this video"
- Progress bar (bottom): Orange 4dp bar, 64dp from bottom
- Both elements hide when video completes

**End-Card** (shows on video completion):
- Background: Full-screen image from S3
- Helper text + X button (top, full-width): "¡The advertiser just granted **free minutes** for watching this Video!" (bold text)
- CTA button (bottom-center): "Explore more Advertiser deals" → opens `http://example.partner.com/`

**Future**: Video URL and end-card content will come from ad response (last clicked box).

---

## 11) Native Ad Template Mapping Rules

**Critical for AI**: This section defines how ad responses map to UI components across all placements.

### 11.1) Template Structure

Every creative has:
```kotlin
creative.template.key    // e.g., "carousel3Slides", "imageWithText", "wideImageOnly"
creative.template.style  // e.g., "default", "imageLeft", "imageRight"
creative.contents[]      // Array of Content objects with {key, type, value}
```

**Mapper Location**: `/sample/.../mapper/AdTemplateMapper.kt`

---

### 11.2) Content Key Extraction

**Helper Methods**:
```kotlin
AdTemplateMapper.getContentValue(creative, "headline")      // Returns String?
AdContent.extractTextContent(creative, "headline")         // Returns String?
AdContent.extractUrlContent(creative, "posterImage")       // Returns String?
```

**⚠️ CRITICAL - Case Sensitivity**:
- Content keys are **case-sensitive**
- API may return `URLSlide1` (uppercase) vs `urlSlide1` (lowercase)
- Always check actual API response for correct casing
- Example fix: Changed `urlSlide1` → `URLSlide1` to match API

---

### 11.3) Placement-to-Template Mappings

**Routing Logic** (`AdCard.kt`):

| Placement | Template Key | Style | Component | Click-Through |
|-----------|-------------|-------|-----------|---------------|
| **home** | `wideWithCompanion` | `imageLeft`, `wideImageOnly` | `HorizontalAdCard` | ✅ Enabled |
| **search** | `imageWithText` | `imageLeft`, `imageRight` | `SearchAdCard` | ❌ Disabled |
| **menu** | `textOnly` | (none) | `MenuAdCard` | ❌ Disabled |
| **promotions** | `carousel3Slides` | `default` | `PromotionsCarouselCard` | ✅ **CTA URLs** |
| **waiting** | `carousel3Slides` | `default` | `PromotionsCarouselCard` | ✅ **CTA URLs** |
| **vehicleSelection** | `imageWithText` | `imageLeft`, `imageRight` | `SearchAdCard` | ✅ Enabled |
| **vehicleSelection** | `wideImageOnly` | `default` | `HorizontalAdCard` | ✅ Enabled |
| **rideSummary** | `standard` | (none) | `HorizontalAdCard` | ✅ Enabled |
| **freeMinutes** | (special) | (custom) | Custom flow | ✅ Enabled |

---

### 11.4) Content Keys by Template

#### **`wideWithCompanion`** (home)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text",
  "ctaText": "text",
  "clickThroughURL": "url"
}
```

#### **`imageWithText`** (search, vehicleSelection)
```json
{
  "squareImage": "url",
  "headline": "text"
}
```
**Note**: SearchAdCard expects `squareImage`, not `posterImage`

#### **`textOnly`** (menu)
```json
{
  "headline": "text",
  "description": "text"
}
```

#### **`carousel3Slides`** (promotions, waiting)
```json
{
  "imageSlide1": "url",
  "headlineSlide1": "text",
  "ctaSlide1": "text",
  "URLSlide1": "url",   // ⚠️ Note: Capital URL, not lowercase
  "imageSlide2": "url",
  "headlineSlide2": "text",
  "ctaSlide2": "text",
  "URLSlide2": "url",
  "imageSlide3": "url",
  "headlineSlide3": "text",
  "ctaSlide3": "text",
  "URLSlide3": "url"
}
```
**Click Behavior**: Clicking slide opens `URLSlideN` in browser

#### **`wideImageOnly`** (vehicleSelection)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text"
}
```
**Note**: Uses `posterImage` (not `squareImage`), rendered by `HorizontalAdCard`

#### **`standard`** (rideSummary)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text",
  "ctaText": "text",
  "clickThroughURL": "url"
}
```

---

### 11.5) Click-Through Rules

**Thumb Rule**: Any ad with `cta*` + `URL` or `clickThroughURL` → clicking opens browser

**Implementation**:
1. Check `AdTemplateMapper.supportsClickthrough(placementKey)`
2. If true, extract URL from content
3. Use `Intent(ACTION_VIEW)` to open in browser

**Supported Placements**:
- home, vehicleSelection, rideSummary: Full ad card clickable
- promotions, waiting: Individual carousel slides clickable
- freeMinutes: Custom CTA button

**Disabled Placements**:
- search, menu: Display only, no click-through

---

### 11.6) Component Selection Logic

**Priority Order** (in `AdCard.kt`):
```kotlin
when {
    templateKey == "wideWithCompanion" || placementKey == "home" → HorizontalAdCard
    placementKey == "search" → SearchAdCard
    placementKey == "menu" || isTextOnlyTemplate → MenuAdCard
    placementKey == "promotions" || placementKey == "waiting" || isCarouselTemplate → PromotionsCarouselCard
    templateKey == "wideImageOnly" → HorizontalAdCard  // Special case
    placementKey == "rideSummary" || isStandardTemplate → HorizontalAdCard
    else → EmptyAdCard (fallback)
}
```

---

### 11.7) Critical Implementation Notes

**⚠️ Common Pitfalls**:

1. **Case Sensitivity**
   - Always verify content key casing in API response
   - Example: `URLSlide1` not `urlSlide1`

2. **Content Key Mismatches**
   - `SearchAdCard` uses `squareImage`
   - `HorizontalAdCard` uses `posterImage`
   - Don't use wrong card for template

3. **Click Handlers**
   - Card `onClick` parameter > `.clickable()` modifier
   - Use `Card(onClick = ...)` for reliable clicks

4. **Template vs Placement**
   - Check both `template.key` AND `placementKey`
   - Some placements override template routing

5. **URL Extraction**
   - Carousel: `URLSlide1/2/3` (uppercase URL)
   - Standard: `clickThroughURL` (camelCase)
   - Always use `AdContent.extractUrlContent()`

---

### 11.8) Adding New Mappings

**Steps to add new template**:

1. **Add Template Constants** (`AdTemplateMapper.kt`):
   ```kotlin
   object TemplateType {
       const val NEW_TEMPLATE = "newTemplate"
   }
   ```

2. **Add Helper Method**:
   ```kotlin
   fun isNewTemplate(adData: AdData): Boolean {
       return getTemplateKey(adData) == TemplateType.NEW_TEMPLATE
   }
   ```

3. **Create Component** (if needed):
   - `/sample/.../components/NewTemplateCard.kt`
   - Extract content keys
   - Handle click-through if needed

4. **Update Routing** (`AdCard.kt`):
   ```kotlin
   placementKey == "newPlacement" || isNewTemplate(adData) -> {
       NewTemplateCard(...)
   }
   ```

5. **Update Documentation**:
   - Add to table in section 11.3
   - Document content keys in section 11.4
   - Update click-through rules if applicable

---

## 12) SDK Configuration

**File**: `/sdk/.../config/SDKConfig.kt`

**Timeout Settings**:
```kotlin
val networkRequestTimeoutMs: Long = 30000L  // 30 seconds
val networkConnectTimeoutMs: Long = 30000L  // 30 seconds  
val networkSocketTimeoutMs: Long = 30000L   // 30 seconds
```

**Rationale**: Increased from 10s to 30s to handle slow mock server responses.

**When to Adjust**:
- Production: Can reduce to 15-20s
- Dev/Mock: Keep at 30s
- Look for `HttpRequestTimeoutException` in logs

---

## 13) Decision Request Builder Video Integration

**Added**: October 24, 2025

### Overview

The Decision Request Builder now supports video format requests. When format is set to "video", the app routes requests to a local development server and renders video ads in placement preview screens.

### Key Points

1. **Video Format Selection**:
   - User enables "Use Format Filter" toggle
   - Selects "Video" from format dropdown
   - Supported placements: Promotions, Waiting, Vehicle Selection, Ride Summary

2. **Development-Only Routing** (⚠️ TEMPORARY):
   - Video requests route to `http://10.0.2.2:8080/v1/decision`
   - Includes custom header: `X-Decision-Version: 2025-11-01`
   - ⚠️ Hardcoded placement key to `"vasttag_none"` for testing
   - ⚠️ Modified placement matching to return first placement for video format
   - **Must be cleaned up before production** (see `VIDEO_IMPLEMENTATION_ROADMAP.md` section for checklist)

3. **Video Player Component**:
   - New component: `VideoPlayerForPlacement.kt`
   - Uses Media3 ExoPlayer with manual tracking (Player 2 approach)
   - Supports all delivery methods: JSON, VAST Tag, VAST XML
   - Full feature parity: skip buttons, end-cards, quartile tracking

4. **Content Key Naming**:
   - System-generated: `video_asset`, `poster_image`, `is_skippable`, `skip_offset` (snake_case)
   - User-generated: `companionHeadline`, `companionCta`, `companionDestinationUrl`, `overlayAtPercentage` (camelCase)
   - Consistent with Video Demo section naming conventions

5. **Tracking**:
   - VAST deliveries: HTTP GET beacons with camelCase event names (`start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete`, `skip`)
   - JSON delivery: SDK methods with snake_case event names (`start`, `first_quartile`, `midpoint`, `third_quartile`, `complete`, `skip`)

### Modified Files

- `MainViewModel.kt` - Added `loadVideoAdsFromLocalhost()`, modified `loadAds()` and `getAdDataForPlacement()`
- `VideoPlayerForPlacement.kt` - **NEW** component for video playback in placements
- `PromotionsPreviewScreen.kt` - Added video detection and rendering
- `WaitingPreviewScreen.kt` - Added video detection and rendering
- `VehicleSelectionPreviewScreen.kt` - Added video detection and rendering
- `RideSummaryPreviewScreen.kt` - Added video detection and rendering

### Production Cleanup Checklist

Before deploying to production:
- [ ] Remove hardcoded `"vasttag_none"` placement override
- [ ] Restore normal placement key matching in `getAdDataForPlacement()`
- [ ] Update endpoint or remove conditional routing
- [ ] Verify `X-Decision-Version` header handling in production

See `VIDEO_IMPLEMENTATION_ROADMAP.md` section "Production Cleanup Checklist" for detailed instructions.

### Documentation

For detailed implementation, flow diagrams, and testing:
- Technical details: `VIDEO_IMPLEMENTATION_ROADMAP.md` section "Decision Request Builder Video Integration"
- Flow and component specs: `VIDEO_PLAYER_FLOW_SUMMARY.md` section 11
- Testing instructions: `TESTING_INSTRUCTIONS.md` - Decision Request Builder test flow

---

## 14) UX Improvements (Nov 2025)

### Poster Image Handling
**Decision**: Do NOT display `poster_image` before video playback starts.

**Rationale**: Poster images are often low quality and create poor UX.

**Implementation**: Show black screen during video loading instead of poster overlay.

**Files Modified**:
- `VideoAdCard.kt` - Removed poster extraction and overlay logic
- `VideoPlayerForPlacement.kt` - Removed poster extraction and overlay logic
- `FreeMinutesPreviewScreen.kt` - Removed poster overlay

**Note**: Poster images still present in API response but not rendered.

---

### No-Ads Behavior
**Decision**: When no ads match selected format, hide ad card completely.

**Rationale**: Better emulates real publisher behavior (no ad = no UI).

**Implementation**: Wrap all ad card rendering in `if (adData != null)` checks.

**Files Modified**: All placement preview screens (Home, Menu, Search, Promotions, Waiting, VehicleSelection, RideSummary).

**Before**: Greyed-out placeholder card shown when no ads available.

**After**: Ad card completely hidden, only placement UI visible.

---

### VAST Clickthrough Requirements
**Android 11+ Package Visibility**: App must declare browser intents in `AndroidManifest.xml`.

**Required Manifest Entry**:
```xml
<queries>
    <package android:name="com.android.chrome" />
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="http" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
</queries>
```

**Without this**: IMA SDK cannot detect browsers, clickthrough fails with `AppsFilter: BLOCKED` error.

**Implementation**: `/sample/src/main/AndroidManifest.xml`

---

## 15) See Also
- `TESTING_INSTRUCTIONS.md` – How to test and validate.
- `VIDEO_PLAYER_FLOW_SUMMARY.md` – UI/flow and player responsibilities.
- `VIDEO_IMPLEMENTATION_ROADMAP.md` – Actionable implementation plan.
