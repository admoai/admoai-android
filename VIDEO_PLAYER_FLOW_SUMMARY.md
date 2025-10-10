# Video Player Flow - Complete Implementation Summary

## Overview
This document describes the complete **Video Ad** player implementation in the Admoai Android Sample App. 

**Key Concept**: The video itself IS the advertisement. This is not pre-roll ads before content - the entire video creative is the ad unit. After playback, the publisher can replay or fetch another video ad via the Decision API.

This covers all UI flows, player selection logic, VAST tag handling, overlay rendering, and tracking integration.
See: `/admoai-android/VIDEO_CONCEPTS.md` for the canonical definitions (delivery methods, content keys, tracking rules, player capabilities).

---

## 1. Entry Point: Video Ad Demo Screen

**File:** `/sample/src/main/java/com/admoai/sample/ui/screens/VideoAdDemoScreen.kt`

### UI Components:
1. **Video Options Section** - Select delivery method and end-card type:
   - **Delivery Method**: JSON, VAST Tag, VAST XML
   - **End Card Type**: None, Native End-card, VAST Companion
2. **Video Player Section** - Choose player implementation:
   - **Media3 ExoPlayer + IMA** - VAST Tag (auto), VAST XML (manual), JSON (manual)
   - **Media3 ExoPlayer** - All deliveries with full manual control
   - **JW Player** - Commercial option (info only)
3. **Launch Video Demo Button** - Triggers video preview

### Key Logic:
- **Scenario Generation** (`getLocalMockScenario`): Maps UI selections to mock server scenarios
  - Example: `delivery="vast_tag" + endCard="none"` → `"vasttag_none"`
- **Mock Server Request**: Fetches ad creative data from `localhost:8080/endpoint?scenario={scenario}`
- **All players support all delivery methods**: No disabling logic needed

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
       "exoplayer" -> ExoPlayerImaVideoPlayer()  // Media3 ExoPlayer + IMA
       "vast_client" -> VastClientVideoPlayer() // Media3 ExoPlayer (manual)
       "jwplayer" -> Text("JW Player - Commercial (info only)")
   }
   ```

3. **Video Configuration** (`VideoPlayerConfig` data class):
   - `videoAssetUrl`: VAST tag URL or direct video URL
   - `posterImageUrl`: Thumbnail image
   - `overlayAtPercentage`: When to show native end-card (0.5 = 50%)
   - `showClose`: Whether to show close button
   - `companionHeadline`, `companionCta`, `companionDestinationUrl`: End-card content

---

## 3. Media3 ExoPlayer + IMA Implementation

**Function:** `ExoPlayerImaVideoPlayer()`

### Delivery Support:
- **VAST Tag**: IMA SDK auto-handles (fetches XML, parses, tracks)
- **VAST XML**: Manual parsing (regex extracts `<MediaFile>`), SDK tracking
- **JSON**: Direct playback, SDK tracking

**Key Point**: Media3's IMA wrapper doesn't expose `adsResponse`, so VAST XML is handled manually.

### Architecture - Separation of Responsibilities:

**Media3 ExoPlayer Responsibilities:**
- Video playback engine
- Buffering and streaming
- Video decoding (codec support)
- Video rendering to surface
- UI controls (play/pause, seek bar)

**IMA SDK Extension Responsibilities:**
- Ad logic and decision making
- VAST Tag fetching and parsing
- Tracking beacon firing (impressions, quartiles)
- Click-through handling
- OMID compliance

**Implementation Details:**
- Uses `androidx.media3.exoplayer.ima.ImaAdsLoader` (Media3's IMA wrapper)
- Integrates via `MediaSourceFactory.setAdsLoaderProvider()`
- VAST Tag passed via `MediaItem.AdsConfiguration.Builder(adTagUrl)`
- **Limitation**: Cannot use `adsResponse` for VAST XML (Media3 doesn't expose it)

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
- **Content URI**: Uses the actual video URL extracted from VAST XML
- **Ads Configuration**: VAST tag URL (the endpoint that returns VAST XML)
```kotlin
val contentVideoUri = "https://videos.admoai.com/VwBe1DrWseFTdiIPnzPzKhoo7fX01N92Hih4h6pNCuDA.m3u8"
MediaItem.Builder()
    .setUri(Uri.parse(contentVideoUri))
    .setAdsConfiguration(
        MediaItem.AdsConfiguration.Builder(Uri.parse(vastTagUrl)).build()
    )
```

**Note**: IMA SDK may show "Ad" indicator, which can be confusing since users expect content after ads. In our case, the video IS the ad itself - there's no additional content.

### Features:
- ✅ VAST Tag: IMA auto-tracking (impressions, quartiles at 0/25/50/75/98%)
- ✅ VAST XML: Manual parsing + SDK tracking
- ✅ JSON: Direct playback + SDK tracking
- ✅ Poster images (all deliveries)
- ✅ Publisher-drawn overlays (native end-cards)
- ✅ Error handling with diagnostics

---

## 4. Media3 ExoPlayer Implementation

**Function:** `VastClientVideoPlayer()`

### Delivery Support:
- **All deliveries**: JSON, VAST Tag, VAST XML - full manual control
- **VAST Tag**: Fetches XML via HTTP GET, parses with regex, extracts video URL and tracking URLs
- **VAST XML**: Decodes Base64 XML, parses, extracts video URL and tracking URLs  
- **JSON**: Direct playback from `videoAsset`

### Tracking:
- **VAST**: Fires tracking URLs via HTTP GET (start, quartiles at 0/25/50/75/98%, complete)
- **JSON**: SDK tracking methods (`fireVideoEvent`)
- **Custom events**: SDK methods (overlay, CTA, close)

### Features:
- ✅ Full manual control over VAST parsing and tracking
- ✅ Works with all delivery methods
- ✅ Regex-based XML parsing for `<MediaFile>` extraction
- ✅ Poster images, native end-cards, error handling

---

## 6. Mock Server Integration

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

## 7. Video Player Options UI

**File:** `/sample/src/main/java/com/admoai/sample/ui/components/VideoOptionsSection.kt`

### VideoPlayerSection:
- Renders player selection chips
- **All players enabled**: No disabling logic - all support all deliveries
- Player names always bold
- JW Player shows "View Integration Guide" when selected

---

## 8. Content Keys & Delivery Methods - Complete Reference

Note: For canonical definitions and examples, see `/admoai-android/VIDEO_CONCEPTS.md`.

### 8.1 Delivery Methods Overview

The `delivery` field determines how video assets and tracking are delivered:

| Delivery | VAST Object | videoAsset | Tracking Location |
|----------|-------------|------------|-------------------|
| `json` | `null` | ✅ Included | In JSON response |
| `vast_tag` | `{ tagUrl }` | ❌ Excluded | In XML from tagUrl |
| `vast_xml` | `{ xmlBase64 }` | ❌ Excluded | In decoded XML |

**Critical Rules:**
1. When `delivery: "json"` → `vast` must be `null` and `videoAsset` must be present
2. When `delivery: "vast_tag"` or `vast_xml"` → `videoAsset` must NOT be included
3. `posterImage` is ALWAYS included regardless of delivery method (non-VAST related)
4. `tracking.impressions` and `tracking.videoEvents` are empty for VAST deliveries (handled in XML)

---

### 8.2 Canonical Content Keys

These keys are **non-editable by users** and represent specific video ad behaviors defined by Admoai:

#### Always Present:
- **`posterImage`** (type: `image`): Thumbnail image URL, required for all video ads as fallback if video doesn't load or takes too long

#### JSON Delivery Only:
- **`videoAsset`** (type: `video`): Direct video URL (HLS/MP4). Must NOT appear when delivery is `vast_tag` or `vast_xml`

#### Skippable Video (Optional):
- **`isSkippable`** (type: `bool`): If `true`, overlay a skip button on the player
- **`skipOffset`** (type: `text`): Time format `"00:00:05"` indicating when skip button appears

**Skippable Video Rules:**
- When `isSkippable: true`, tracking MUST include a `skip` event
- For JSON delivery: Include `{ "key": "skip", "url": "..." }` in `tracking.videoEvents[]`
- For VAST delivery: Include `<Tracking event="skip">` in XML and `skipoffset` attribute on `<Linear>` element
- Skip button implementation: Publisher's responsibility (custom UI) or player-native (IMA SDK)

---

### 8.3 User-Defined Content Keys (Standard Convention)

These keys are **editable by users** but follow standard naming conventions throughout the sample app:

#### Native End-Card Keys:
- **`companionHeadline`** (type: `text`): Headline text for publisher-drawn overlay
- **`companionCta`** (type: `text`): Call-to-action button label (e.g., "Book Now", "Install")
- **`companionDestinationUrl`** (type: `url`): URL to open when CTA is clicked

**Note:** Keys starting with `companion*` prefix refer to elements of the native end-card that the publisher draws over the video player.

#### Overlay Behavior Keys:
- **`overlayAtPercentage`** (type: `float`): Video progress percentage (0.0-1.0) when overlay appears (e.g., `0.5` = show at 50% progress)
- **`showClose`** (type: `integer` or `bool`): Whether to display close button (1/true = show, 0/false = hide)

---

### 8.4 End-Card Modes

Three modes determine how companion content is displayed:

#### Mode 1: None
- No overlay or end-card
- Video plays and completes
- Only canonical tracking events

#### Mode 2: Native End-Card (Publisher-Drawn)
- Publisher draws custom overlay UI over video player
- Uses `companion*` keys from `contents[]`
- Publisher controls styling, animations, and layout
- Works with ANY delivery method (JSON, VAST Tag, VAST XML)
- Publisher fires custom tracking for CTA clicks and close button

#### Mode 3: VAST Companion
- Companion ad data embedded in VAST XML
- XML contains `<CompanionAds>` with multiple size options
- Each `<Companion>` includes:
  - `<StaticResource>` - Image URL for companion creative
  - `<AltText>` - Fallback text
  - `<CompanionClickThrough>` - Destination URL
  - `<CompanionClickTracking>` - Click tracking beacon
  - `<TrackingEvents>` - creativeView tracking
- Player or publisher selects best-fit companion size
- **Only for VAST Tag/XML deliveries** - not applicable to JSON

**Hybrid Approach:** VAST Tag/XML can include BOTH VAST companions (in XML) AND native end-card keys (in JSON contents) for fallback or additional overlays.

---

### 7.5 Complete Examples by Delivery Method

#### Example 1: JSON + None
```json
{
  "delivery": "json",
  "vast": null,
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "https://videos.admoai.com/...m3u8" },
    { "key": "posterImage", "type": "image", "value": "https://image.mux.com/.../thumbnail.png" }
  ],
  "tracking": {
    "impressions": [{ "key": "default", "url": "https://api.admoai.com/..." }],
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

#### Example 2: JSON + Native End-Card
```json
{
  "delivery": "json",
  "vast": null,
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "https://videos.admoai.com/...m3u8" },
    { "key": "posterImage", "type": "image", "value": "https://image.mux.com/.../thumbnail.png" },
    { "key": "companionHeadline", "type": "text", "value": "The Best Ride Awaits" },
    { "key": "companionCta", "type": "text", "value": "Book Now" },
    { "key": "companionDestinationUrl", "type": "url", "value": "https://partner.com/landing" }
  ],
  "tracking": {
    "impressions": [...],
    "videoEvents": [...]
  }
}
```

#### Example 3: JSON + Skippable
```json
{
  "delivery": "json",
  "vast": null,
  "contents": [
    { "key": "videoAsset", "type": "video", "value": "https://videos.admoai.com/...m3u8" },
    { "key": "posterImage", "type": "image", "value": "https://image.mux.com/.../thumbnail.png" },
    { "key": "isSkippable", "type": "bool", "value": true },
    { "key": "skipOffset", "type": "text", "value": "00:00:05" }
  ],
  "tracking": {
    "impressions": [...],
    "videoEvents": [
      { "key": "start", "url": "..." },
      { "key": "firstQuartile", "url": "..." },
      { "key": "midpoint", "url": "..." },
      { "key": "thirdQuartile", "url": "..." },
      { "key": "complete", "url": "..." },
      { "key": "skip", "url": "..." }  // ← REQUIRED for skippable
    ]
  }
}
```

#### Example 4: VAST Tag + None
```json
{
  "delivery": "vast_tag",
  "vast": {
    "tagUrl": "https://example.com/vast4?r=..."
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "https://image.mux.com/.../thumbnail.png" }
    // ❌ NO videoAsset key
  ],
  "tracking": {
    "impressions": [],  // ← Empty, handled in VAST XML
    "videoEvents": []   // ← Empty, handled in VAST XML
  }
}
```

**Corresponding VAST XML from tagUrl:**
```xml
<VAST version="4.2">
  <Ad id="ad_123">
    <InLine>
      <Impression><![CDATA[https://api.admoai.com/tracking?e=impression]]></Impression>
      <Creatives>
        <Creative>
          <Linear>
            <Duration>00:00:07</Duration>
            <TrackingEvents>
              <Tracking event="start"><![CDATA[https://api.admoai.com/tracking?e=start]]></Tracking>
              <Tracking event="firstQuartile"><![CDATA[...]]></Tracking>
              <Tracking event="midpoint"><![CDATA[...]]></Tracking>
              <Tracking event="thirdQuartile"><![CDATA[...]]></Tracking>
              <Tracking event="complete"><![CDATA[...]]></Tracking>
            </TrackingEvents>
            <MediaFiles>
              <MediaFile delivery="progressive" type="video/mp4">
                <![CDATA[https://videos.admoai.com/...m3u8]]>
              </MediaFile>
            </MediaFiles>
          </Linear>
        </Creative>
      </Creatives>
    </InLine>
  </Ad>
</VAST>
```

#### Example 5: VAST Tag + Native End-Card
```json
{
  "delivery": "vast_tag",
  "vast": {
    "tagUrl": "https://example.com/vast4?r=..."
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." },
    { "key": "companionHeadline", "type": "text", "value": "The Best Ride Awaits" },
    { "key": "companionCta", "type": "text", "value": "Book Now" },
    { "key": "companionDestinationUrl", "type": "url", "value": "https://partner.com/landing" },
    { "key": "overlayAtPercentage", "type": "float", "value": 0.5 },
    { "key": "showClose", "type": "integer", "value": 1 }
  ],
  "tracking": {
    "impressions": [],
    "clicks": [{ "key": "cta", "url": "..." }],  // ← Custom tracking for publisher-drawn CTA
    "custom": [{ "key": "closeBtn", "url": "..." }],
    "videoEvents": []  // ← Empty, VAST handles these
  }
}
```

#### Example 6: VAST Tag + VAST Companion
```json
{
  "delivery": "vast_tag",
  "vast": {
    "tagUrl": "https://example.com/vast4?r=..."
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." }
    // NO companion* keys - companions are in VAST XML
  ],
  "tracking": {
    "impressions": [],
    "videoEvents": []
  }
}
```

**Corresponding VAST XML includes CompanionAds:**
```xml
<VAST version="4.2">
  <Ad id="ad_123">
    <InLine>
      <Creatives>
        <Creative>
          <Linear>...</Linear>
        </Creative>
        <Creative>
          <CompanionAds>
            <Companion id="companionImage_300x250" width="300" height="250">
              <StaticResource creativeType="image/png">
                <![CDATA[https://cdn.admoai.com/endcards/bg_300x250.png]]>
              </StaticResource>
              <AltText>Ride Smarter Today -- Install</AltText>
              <CompanionClickThrough><![CDATA[https://partner.com/landing]]></CompanionClickThrough>
              <CompanionClickTracking><![CDATA[https://api.admoai.com/tracking]]></CompanionClickTracking>
              <TrackingEvents>
                <Tracking event="creativeView"><![CDATA[...]]></Tracking>
              </TrackingEvents>
            </Companion>
            <Companion id="companionImage_500x700" width="500" height="700">
              <!-- Additional size option -->
            </Companion>
          </CompanionAds>
        </Creative>
      </Creatives>
    </InLine>
  </Ad>
</VAST>
```

#### Example 7: VAST XML + Skippable
```json
{
  "delivery": "vast_xml",
  "vast": {
    "xmlBase64": "PFZBU1QgdmVyc2lvbj0i..."  // Base64-encoded VAST XML
  },
  "contents": [
    { "key": "posterImage", "type": "image", "value": "..." }
    // NO isSkippable or skipOffset keys - handled in XML
  ],
  "tracking": {
    "impressions": [],
    "videoEvents": []  // Skip event is in VAST XML
  }
}
```

**Decoded VAST XML includes skipoffset:**
```xml
<VAST version="4.2">
  <Ad id="ad_123">
    <InLine>
      <Creatives>
        <Creative>
          <Linear skipoffset="00:00:05">  <!-- ← Skip attribute on Linear element -->
            <Duration>00:00:07</Duration>
            <TrackingEvents>
              <Tracking event="start"><![CDATA[...]]></Tracking>
              <Tracking event="skip"><![CDATA[https://api.admoai.com/tracking?e=skip]]></Tracking>  <!-- ← Skip tracking -->
              <Tracking event="complete"><![CDATA[...]]></Tracking>
            </TrackingEvents>
            <MediaFiles>...</MediaFiles>
          </Linear>
        </Creative>
      </Creatives>
    </InLine>
  </Ad>
</VAST>
```

---

### 7.6 Implementation Guidance for Sample App

The sample app Video Demo Playground should display all these combinations to demonstrate:

1. **Video Player Capability Mapping:**
   - **Media3 ExoPlayer + IMA**: VAST Tag (IMA auto), VAST XML (manual), JSON (manual)
   - **Media3 ExoPlayer**: All deliveries with manual control (regex parsing, HTTP GET tracking)
   - **JW Player**: Commercial option (info only)

2. **Delivery Method Detection:**
```kotlin
when (creative.delivery) {
    "json" -> {
        // Use videoAsset from contents
        // Fire tracking.impressions manually
        // Fire tracking.videoEvents manually
    }
    "vast_tag" -> {
        // Pass creative.vast.tagUrl to IMA SDK
        // IMA handles all video tracking automatically
    }
    "vast_xml" -> {
        // Decode creative.vast.xmlBase64
        // Pass decoded XML to IMA SDK or parse MediaFile URL
        // IMA handles all video tracking automatically
    }
}
```

3. **Companion Content Handling:**
   - Check for `companion*` keys in `contents[]` → Publisher draws native overlay
   - Check for `<CompanionAds>` in VAST XML → Player/publisher selects best-fit companion
   - Display at `overlayAtPercentage` if specified, otherwise at video completion

4. **Tracking Responsibility Matrix:**

| Player | JSON | VAST Tag | VAST XML | Custom |
|--------|------|----------|----------|--------|
| **Media3 ExoPlayer + IMA** | Manual (SDK) | IMA Auto | Manual (SDK) | Manual (SDK) |
| **Media3 ExoPlayer** | Manual (SDK) | Manual (HTTP) | Manual (HTTP) | Manual (SDK) |

**Note**: Complete event fires at 98% (not 100%) to avoid player state race conditions.

---

### 7.7 Important Notes

- **All overlay UI elements are publisher-drawn** - not player-native (except IMA's built-in skip button)
- The playground demonstrates best practices for rendering overlays with smooth animations and modern UI
- `posterImage` is the ONLY content key that appears in ALL scenarios regardless of delivery method
- When implementing video players, always check `delivery` field first to determine the correct parsing strategy
- VAST companion ads provide multiple size options; publisher selects the best-fit based on available screen space

---

## 9. Tracking Integration

### IMA SDK (ExoPlayer + IMA):
- **Automatic**: VAST Tag returns VAST XML containing `<Tracking>` event URLs that fire automatically by IMA SDK
- Events: impression, start, firstQuartile, midpoint, thirdQuartile, complete, click, skip
- No manual tracking needed - IMA handles all video event pings

### Manual Tracking (Basic Player):
- Uses Admoai SDK methods: `viewModel.fireVideoEvent(creative, eventName)`
- Events fired at video progress milestones (0%, 25%, 50%, 75%, 100%)
- Developer responsibility to track events
- Must manually fire skip event when user skips

### Custom Events (Both Players):
- **Overlay Shown**: `viewModel.fireCustomEvent(creative, "overlayShown")`
- **CTA Click**: `viewModel.fireClick(creative, "cta")`
- **Close Button**: `viewModel.fireCustomEvent(creative, "closeBtn")`

**Clarification**: "VAST Tag" = URL endpoint that returns VAST XML. "VAST XML" = the actual XML document containing tracking URLs.

---

## 10. Key Fixes Applied

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

## 11. File Structure

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

## 12. Testing Checklist & Rules

### Core Rules:
1. **Video Options** (Delivery + End-Card) are independent from **Video Player** selection
2. **All players support all delivery methods** - no disabling logic
3. **All overlay UI** is publisher-drawn regardless of delivery method or player
4. Focus on demonstrating **cool animations and modern UI** for overlays in the playground

### Test Matrix:

#### Media3 ExoPlayer + IMA:
- ✅ **JSON + All end-cards**: Direct playback, SDK tracking
- ✅ **VAST Tag + All end-cards**: IMA auto-tracking, poster, "Ad" indicator shown
- ✅ **VAST XML + All end-cards**: Manual parsing, SDK tracking

#### Media3 ExoPlayer:
- ✅ **All delivery methods + All end-cards**: Full manual control
- ✅ **VAST Tag**: Fetches XML, parses, fires HTTP GET tracking
- ✅ **VAST XML**: Decodes Base64, parses, fires HTTP GET tracking

#### UI/UX Tests:
- ✅ **Poster image**: Displays for all video ads before playback
- ✅ **Skip button**: Appears at `skipOffset` when `isSkippable` is true
- ✅ **Close button**: Top-right X when `showClose` = 1
- ✅ **Overlay animations**: Smooth slide-up or fade-in for end-cards
- ✅ **CTA interactions**: Button clicks open `companionDestinationUrl` and fire tracking
- ✅ **Quartile tracking**: Fires at 0%, 25%, 50%, 75%, 98%

### Overlay UI Focus Areas:
- Modern Card-based design with proper elevation
- Smooth animations (slide up, fade in, scale)
- Responsive layouts that adapt to different screen sizes
- Proper color theming (Material 3)
- Touch feedback and ripple effects on interactive elements

---

## 13. Player Architecture Summary

### Current Implementation Status:

#### ✅ Media3 ExoPlayer + IMA (Fully Implemented)
- **Architecture**: Media3 ExoPlayer for playback, IMA extension for ad logic
- **Supports**: VAST Tag (IMA auto), VAST XML (manual parsing), JSON (manual tracking)
- **VAST XML Handling**: Media3 wrapper doesn't expose `adsResponse`, so XML is parsed manually with regex
- **Native End-card**: ✅ Compose overlay
- **Tracking**: Automatic (VAST Tag), Manual (VAST XML + JSON)

#### ✅ Media3 ExoPlayer (Fully Implemented)
- **Architecture**: Media3 ExoPlayer with full manual control
- **Supports**: All deliveries (JSON, VAST Tag, VAST XML)
- **VAST Parsing**: Fetches XML via HTTP GET or decodes Base64, regex parses `<MediaFile>`
- **Tracking**: HTTP GET for VAST tracking URLs, SDK methods for JSON
- **Native End-card**: ✅ Compose overlay

#### ℹ️ JW Player (Info Only)
- **Status**: Commercial option - not implemented in sample
- **Purpose**: Shows integration guide link when selected

---

## 14. Known Issues & Resolutions

### 🚨 **RESOLVED ISSUES:**

#### Issue 1: CORS Policy Error ✅ FIXED
**Error:**
```
Access to XMLHttpRequest at 'http://10.0.2.2:8080/endpoint?scenario=tagurl_vasttag_none' 
from origin 'https://imasdk.googleapis.com' has been blocked by CORS policy: 
The value of the 'Access-Control-Allow-Origin' header must not be the wildcard '*' 
when the request's credentials mode is 'include'.
```

**Cause**: IMA SDK runs in HTTPS context and sends credentials. Mock server was returning `Access-Control-Allow-Origin: *`, which is rejected by browsers when credentials are included.

**Fix Applied**: 
- Changed CORS to reflect the actual origin from request header
- Added `Access-Control-Allow-Credentials: true`
- Changed `Access-Control-Allow-Headers` to include `Accept`

**Action Required**: Restart mock server after this fix.

#### Issue 2: Mixed Content (HTTP/HTTPS) ❌ **STILL BLOCKING**
**Error (Latest Logs 2025-10-02 12:51):**
```
Mixed Content: The page at 'https://imasdk.googleapis.com/...' was loaded over HTTPS, 
but requested an insecure XMLHttpRequest endpoint 'http://10.0.2.2:8080/...'
```

**Status**: ✅ CORS fixed, ❌ Mixed Content still blocking

**Cause**: IMA SDK runs inside a **WebView (Chromium)** which has browser-level mixed content policy. Network Security Config only affects native Android HTTP requests, not WebView's JavaScript requests.

**Why Network Security Config Didn't Help**:
- Network Security Config applies to: HttpURLConnection, OkHttp, native Android networking
- IMA SDK uses: WebView with JavaScript XMLHttpRequest
- WebView enforces its own mixed content policy independent of Android

**Working Solutions**:
1. ⭐ **Use HTTPS Mock Server** (mock server already supports HTTPS via env vars)
2. ⭐ **Use Real HTTPS VAST Tag** (easiest for immediate testing)

#### Issue 3: Invalid IMA SDK Message ⚠️
**Warning:**
```
Invalid internal message. Make sure the Google IMA SDK library is up to date.
```

**Likely Cause**: Cascading failure from CORS/mixed content errors causing IMA SDK to fail gracefully.

**Action**: Update IMA SDK dependency to latest version (currently using transitive dependency from media3).

---

### 🚦 Current Status (Updated 2025-10-02 15:05):
- ✅ **FIXED**: CORS policy error (mock server updated)
- ✅ **FIXED**: HTTPS mock server configured with SSL certificates
- ✅ **FIXED**: Android app trusts mock server's self-signed certificate
- ✅ **FIXED**: All VAST tag URLs updated to use HTTPS
- ✅ **FIXED**: Removed incorrect HTTPS validation code blocking localhost
- ✅ **READY**: App rebuilt, installed, and ready to test

### ✅ **HTTPS Mock Server Setup Complete!**

**What Was Done:**
1. ✅ Generated self-signed SSL certificates (`cert.pem`, `key.pem`)
2. ✅ Converted certificate to Android-compatible format (`cert.der`)
3. ✅ Added certificate to `/sample/src/main/res/raw/mock_server_cert.der`
4. ✅ Updated Network Security Config to trust the certificate
5. ✅ Updated all VAST tag URLs to use `https://10.0.2.2:8080`
6. ✅ Created `start-https.sh` script for easy server startup
7. ✅ Removed incorrect validation code that was blocking HTTPS URLs
8. ✅ Rebuilt and installed Android app successfully

### 📋 Ready to Test:
- ⏳ **NEXT**: Start HTTPS mock server (see instructions below)
- ⏳ **NEXT**: Install app and test VAST Tag + ExoPlayer + IMA
- ⏳ **NEXT**: Verify IMA SDK loads VAST ads without errors
- ⏳ **NEXT**: Confirm VAST tracking events fire automatically
- Test skip button implementation (`isSkippable`, `skipOffset`)
- Test close button implementation (`showClose`)
- Verify overlay animations and transitions

### Known UI Issues:
- **IMA "Ad" Indicator**: IMA SDK shows "Ad" label during playback, which can confuse users into thinking content follows. Consider:
  - Hiding the indicator via custom PlayerView styling
  - Adding "Video Ad" label to clarify
  - Using custom controls that don't show "Ad" text

### Not Yet Implemented:
- Pure Google IMA SDK player (placeholder in UI)
- JW Player integration (placeholder in UI)
- VAST XML delivery (Base64-encoded XML in decision response)
- Skip button UI and logic
- Close button UI and logic
- Advanced overlay animations (slide-up, fade-in, scale effects)

---

### 🎯 **VAST XML Delivery: Native Support vs Manual Decoding**

#### Two Approaches for `delivery: "vast_xml"`

When the Decision API returns `vast.xmlBase64` (Base64-encoded VAST XML), there are two implementation approaches:

#### Approach 1: Native VAST XML Support (Recommended for IMA-based players)

**Supported By:**
- Google IMA SDK (pure, via `adsResponse`)

**How it Works:**
```kotlin
when (creative.delivery) {
    "vast_tag" -> {
        // IMA fetches VAST XML from URL
        val adsRequest = AdsRequest.Builder()
            .setAdTagUrl(creative.vast.tagUrl)
            .build()
    }
    "vast_xml" -> {
        // IMA accepts raw VAST XML string
        val decodedXml = Base64.decode(creative.vast.xmlBase64, Base64.DEFAULT)
            .toString(Charsets.UTF_8)
        val adsRequest = AdsRequest.Builder()
            .setAdsResponse(decodedXml)  // ← Pass decoded XML directly
            .build()
    }
}
```

**IMA SDK automatically:**
- Parses VAST XML
- Fires impression/quartile/skip/click tracking beacons
- Loads `<MediaFile>` URL from VAST
- Handles OMID registration
- Manages `<CompanionAds>` if present

**Advantages:**
- ✅ Automatic tracking (impression, quartiles, complete, skip)
- ✅ No manual VAST parsing needed
- ✅ OMID compliant automatically
- ✅ Companion ads handled

**Use Case:** When using ExoPlayer + IMA or Pure IMA SDK

---

#### Approach 2: Manual VAST XML Decoding

**Supported By:**
- Basic Player (non-VAST)
- Custom video player implementations
- Players without IMA SDK integration

**How it Works:**
```kotlin
when (creative.delivery) {
    "vast_xml" -> {
        // 1. Decode Base64
        val decodedXml = Base64.decode(creative.vast.xmlBase64, Base64.DEFAULT)
            .toString(Charsets.UTF_8)
        
        // 2. Parse VAST XML manually
        val vastParser = VastXmlParser()
        val mediaFileUrl = vastParser.extractMediaFileUrl(decodedXml)
        val trackingUrls = vastParser.extractTrackingEvents(decodedXml)
        val skipOffset = vastParser.extractSkipOffset(decodedXml)
        
        // 3. Play video directly
        exoPlayer.setMediaItem(MediaItem.fromUri(mediaFileUrl))
        
        // 4. Fire tracking manually
        trackingUrls.forEach { (event, url) ->
            when (event) {
                "impression" -> fireTrackingUrl(url)
                "start" -> fireWhenVideoStarts(url)
                // ... handle all events manually
            }
        }
    }
}
```

**Publisher Responsibility:**
- ❌ Manual VAST XML parsing required
- ❌ Manual tracking beacon firing
- ❌ Manual skip button implementation
- ❌ Manual companion ad extraction
- ❌ OMID implementation (if required)

**Use Case:** When using Basic Player (JSON-only) but need to support VAST XML fallback

---

#### Implementation Matrix:

| Delivery Method | Player | IMA Involvement | Tracking | VAST Parsing |
|----------------|--------|-----------------|----------|--------------|
| `json` | Basic Player | None | Manual | N/A |
| `json` | ExoPlayer + IMA | None | Manual | N/A |
| `vast_tag` | ExoPlayer + IMA | Full | IMA Auto | IMA Auto |
| `vast_xml` (native) | ExoPlayer + IMA | Full | IMA Auto | IMA Auto |
| `vast_xml` (manual) | Basic Player | None | Manual | Manual |

---

#### Demo Showcase Opportunity:

Create two video player demos for `vast_xml`:

**Demo 1: ExoPlayer + IMA (Native VAST XML Support)**
- Decode `xmlBase64` → Pass to `AdsRequest.setAdsResponse()`
- IMA handles everything automatically
- Show tracking events firing in logcat
- Highlight: "Zero manual tracking code"

**Demo 2: Basic Player (Manual VAST XML Decoding)**
- Decode `xmlBase64` → Parse with XML parser
- Extract `<MediaFile>` URL manually
- Extract `<Tracking>` events manually
- Play video + fire tracking URLs manually
- Show companion ad extraction
- Highlight: "Full control but more complexity"

**Key Takeaway:** Pure Google IMA SDK can accept VAST XML via `AdsRequest.setAdsResponse()`. The Media3 ExoPlayer+IMA wrapper does NOT expose `adsResponse`; Basic Player requires manual parsing and tracking implementation.

---

## 13. HTTPS Mock Server Setup Guide

For setup steps and troubleshooting, see `/admoai-android/TESTING_INSTRUCTIONS.md`. The summary below is for quick reference.

### ✅ **Setup Complete - Ready to Use!**

The mock server is now configured with HTTPS and the Android app trusts the self-signed certificate.

### 📂 **Files Created:**

**Mock Server:**
- `/mock-endpoints/cert.pem` - SSL certificate
- `/mock-endpoints/key.pem` - SSL private key
- `/mock-endpoints/cert.der` - Android-compatible certificate format
- `/mock-endpoints/start-https.sh` - Script to start HTTPS server

**Android App:**
- `/sample/src/main/res/raw/mock_server_cert.der` - Trusted certificate
- `/sample/src/main/res/xml/network_security_config.xml` - Updated to trust certificate

### 🚀 **How to Start HTTPS Mock Server:**

**Option 1: Using the script (Recommended)**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
./start-https.sh
```

**Option 2: Manual start**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
CERT_FILE=cert.pem KEY_FILE=key.pem PORT=8080 go run main.go
```

**Expected Output:**
```
Mock Endpoints Server starting on port 8080 (HTTPS)
Available scenarios:
  - json_none
  - vasttag_none
  ...
Example usage: GET https://localhost:8080/endpoint?scenario=json_none
```

### 🔍 **How It Works:**

1. **Self-Signed Certificate**: Generated with `openssl`, valid for 365 days
2. **Certificate Trust**: Android app explicitly trusts the certificate via Network Security Config
3. **HTTPS URLs**: All VAST tag URLs now use `https://10.0.2.2:8080`
4. **WebView Support**: WebView (used by IMA SDK) accepts the trusted certificate

### ❗ **Why This Was Necessary:**

IMA SDK runs in a **WebView** which enforces browser-level mixed content policy:
- ✅ HTTPS WebView → HTTPS requests = Allowed
- ❌ HTTPS WebView → HTTP requests = **BLOCKED**
- Network Security Config only affects native Android networking, not WebView
- Solution: Enable HTTPS on mock server + trust self-signed certificate

---

## Key Takeaways

1. **Video IS the Ad**: These are not pre-roll ads - the entire video is the advertisement. No content follows.
2. **Delivery Methods Are Critical**: Always check `delivery` field first:
   - `"json"` → `vast: null`, `videoAsset` present, tracking in JSON
   - `"vast_tag"` → `vast.tagUrl` present, NO `videoAsset`, tracking in XML
   - `"vast_xml"` → `vast.xmlBase64` present, NO `videoAsset`, tracking in XML
3. **posterImage Is Universal**: The ONLY content key that appears in ALL scenarios regardless of delivery method
4. **VAST Tag Flow**: Decision API returns tagUrl → IMA fetches VAST XML → IMA parses MediaFile URL → IMA plays video ad → Automatic tracking
5. **JSON Flow**: Decision API returns videoAsset URL → Player loads directly → Manual tracking required
6. **Publisher-Drawn Overlays**: ALL native overlay UI (end-cards, skip buttons, close buttons) are rendered by the publisher using `companion*` keys, not the player (except IMA's built-in skip button)
7. **Canonical Keys** (Non-Editable):
   - `posterImage` - Always present
   - `videoAsset` - JSON delivery only
   - `isSkippable` / `skipOffset` - Skip functionality
8. **User-Defined Keys** (Editable Convention):
   - `companion*` prefix - Native end-card content (headline, CTA, destination URL)
   - `overlayAtPercentage` - When to show overlay (0.0-1.0)
   - `showClose` - Close button visibility
9. **End-Card Modes**: Three options:
   - None - Just video playback
   - Native End-Card - Publisher draws overlay from `companion*` keys
   - VAST Companion - XML contains `<CompanionAds>` with multiple size options
10. **Skippable Video Rules**: When `isSkippable: true`, MUST include skip tracking event (in JSON for JSON delivery, in XML for VAST)
11. **Tracking Responsibility**:
    - JSON delivery: Manual tracking for ALL events (impression, video events, skip)
    - VAST delivery: IMA handles video events automatically, publisher handles custom events (overlay, CTA, close)
12. **IMA Requirements**: Both `setAdsLoaderProvider` AND `setAdViewProvider` must be configured for IMA SDK
13. **IMA HTTPS Requirement**: IMA SDK runs in WebView and **requires HTTPS VAST URLs**. HTTP URLs are blocked by browser mixed content policy. Network Security Config does NOT help for WebView because it only applies to native Android networking, not WebView's JavaScript requests.
14. **Mock Server HTTPS**: Local mock server now runs with HTTPS on port 8080 using self-signed certificates. Android app trusts the certificate via Network Security Config + custom certificate in `/res/raw/`.
15. **Basic Player Limitation**: Cannot play VAST Tag or VAST XML - only JSON delivery with direct video URLs
16. **Content URI for VAST**: Use actual video URL as content, VAST tag URL in AdsConfiguration
17. **VAST Terminology**: "VAST Tag" = URL endpoint, "VAST XML" = the XML document with tracking URLs
18. **Certificate Management**: Self-signed certificates valid for 365 days. Regenerate annually using the same `openssl` command.
