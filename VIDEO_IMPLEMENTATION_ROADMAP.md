# Video Player Implementation Roadmap

## ✅ Analysis Complete

### Canonical Content Keys (Non-Editable by Users):
- **`posterImage`** - Thumbnail image URL (ALWAYS present, all delivery methods)
- **`videoAsset`** - Direct video URL (HLS/MP4) - **JSON delivery ONLY**
- **`isSkippable`** - Boolean, enables skip button
- **`skipOffset`** - Time string "00:00:05", when skip appears

### User-Defined Content Keys (Standard Convention):
- **`companion*`** prefix - Native end-card elements:
  - `companionHeadline` - Headline text
  - `companionCta` - CTA button label
  - `companionDestinationUrl` - Click destination URL
- **`overlayAtPercentage`** - Float (0.0-1.0), when overlay shows (0.5 = 50%)
- **`showClose`** - Integer/Boolean, close button visibility

### Delivery Methods & Critical Rules:
1. **`delivery: "json"`**
   - `vast` must be `null`
   - `videoAsset` must be included
   - `tracking.impressions` and `tracking.videoEvents` included in JSON
   - Manual tracking required

2. **`delivery: "vast_tag"`**
   - `vast.tagUrl` present (URL endpoint returning VAST XML)
   - `videoAsset` must NOT be included
   - `tracking.impressions` and `tracking.videoEvents` empty (handled in XML)
   - IMA SDK handles tracking automatically

3. **`delivery: "vast_xml"`**
   - `vast.xmlBase64` present (Base64-encoded VAST XML)
   - `videoAsset` must NOT be included
   - `tracking.impressions` and `tracking.videoEvents` empty (handled in XML)
   - Decode XML → Pass to IMA SDK or parse manually

### End-Card Modes:
1. **None** - Video only, no overlays
2. **Native End-Card** - Publisher draws overlay using `companion*` keys
3. **VAST Companion** - XML contains `<CompanionAds>` with size options

---

## 🎯 Implementation Plan

### Phase 1: Add Player Selection UI ✅
**Status:** Ready to implement
**Time:** 30 min
**Tasks:**
- [ ] Add `videoPlayer` StateFlow to MainViewModel
- [ ] Add player selection FilterChips to VideoOptionsSection
- [ ] Options: Basic, ExoPlayer+IMA ⭐, Google IMA, JW Player

---

### Phase 2: Implement Basic Player (Non-VAST)
**Status:** Current simulated player → Make functional
**Time:** 2-3 hours
**Tasks:**
- [ ] Parse `videoAsset` URL from creative.contents
- [ ] Add ExoPlayer dependency
- [ ] Create AndroidView with ExoPlayer
- [ ] Load HLS video URL
- [ ] Implement progress tracking (0%, 25%, 50%, 75%, 100%)
- [ ] Fire tracking.videoEvents HTTP calls
- [ ] Parse `isSkippable` / `skipOffset` from contents
- [ ] Show skip button at correct time
- [ ] Fire "skip" tracking event
- [ ] Parse native end card data (`companionHeadline`, `companionCta`, `companionDestinationUrl`)
- [ ] Show end card overlay after completion
- [ ] Handle end card click tracking

**Content Keys Used:**
- videoAsset ✅
- posterImage ✅
- isSkippable ✅
- skipOffset ✅
- companionHeadline ✅
- companionCta ✅
- companionDestinationUrl ✅

---

### Phase 3: Implement ExoPlayer + IMA (VAST-Friendly) ⭐
**Status:** Recommended implementation
**Time:** 4-5 hours
**Tasks:**
- [ ] Add Media3 ExoPlayer + IMA dependencies
- [ ] Create ImaAdsLoader
- [ ] Handle `vast.tagUrl` - Pass to IMA SDK
- [ ] Handle `vast.xmlBase64` - Decode → Pass to IMA
- [ ] IMA handles all VAST tracking automatically
- [ ] Parse `overlayAtPercentage` from contents
- [ ] Show custom overlay at percentage
- [ ] Handle native end card (if present) after VAST complete
- [ ] Fire custom tracking (closeBtn, cta clicks)
- [ ] Handle VAST companion ads (if vast_companion mode)

**Content Keys Used:**
- vast.tagUrl ✅
- vast.xmlBase64 ✅
- posterImage ✅
- overlayAtPercentage ✅
- showClose ✅
- companionHeadline ✅ (for native end card)
- companionCta ✅
- companionDestinationUrl ✅

**Key Insight:** 
- VAST handles videoEvents tracking internally
- We only fire custom tracking (overlay, end card interactions)
- Can mix VAST video + native end card

---

### Phase 4: Implement Pure Google IMA SDK
**Status:** VAST-only implementation
**Time:** 3-4 hours
**Tasks:**
- [ ] Add IMA SDK dependency (without ExoPlayer)
- [ ] Create ImaAdsLoader with VideoAdPlayer interface
- [ ] Implement custom video surface
- [ ] Load VAST tag URL
- [ ] Let IMA handle all playback + tracking
- [ ] Only works with VAST (no JSON/videoAsset support)

**Use Case:** Users who want pure IMA without ExoPlayer

---

### Phase 5: Implement JW Player
**Status:** Commercial player
**Time:** 4-5 hours
**Tasks:**
- [ ] Add JW Player SDK dependency
- [ ] Create JWPlayerView
- [ ] Handle videoAsset URLs
- [ ] Handle VAST tag URLs (via Advertising plugin)
- [ ] Configure IMA plugin for VAST
- [ ] Custom overlay implementation
- [ ] Native end card implementation

**Note:** Requires JW Player license for production use

---

## 🏗️ Architecture Design

### VideoPreviewScreen Structure:
```
VideoPreviewScreen
├── when (selectedPlayer)
│   ├── "basic" → BasicVideoPlayer
│   ├── "exoplayer" → ExoPlayerWithIMA
│   ├── "ima" → PureIMAPlayer
│   └── "jwplayer" → JWPlayerComponent
└── Common UI Elements
    ├── Overlay (at overlayAtPercentage)
    ├── Skip Button (if isSkippable)
    ├── Close Button (if showClose)
    └── End Card (native or VAST companion)
```

### Tracking Manager:
```kotlin
object VideoTrackingManager {
    fun fireEvent(url: String)
    fun fireImpression(urls: List<String>)
    fun fireQuartile(eventKey: String, tracking: TrackingInfo)
    fun fireCustom(eventKey: String, tracking: TrackingInfo)
}
```

---

## 📦 Dependencies Needed

### build.gradle.kts:
```kotlin
dependencies {
    // Phase 2: Basic Player
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    
    // Phase 3: ExoPlayer + IMA (Recommended)
    implementation("androidx.media3:media3-exoplayer-ima:1.2.1")
    
    // Phase 4: Pure IMA SDK
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.31.0")
    
    // Phase 5: JW Player (Commercial)
    implementation("com.jwplayer:jwplayer-core:4.11.0")
    implementation("com.jwplayer:jwplayer-ima:4.11.0")
}
```

---

## ⚠️ Important Considerations

### VAST vs Non-VAST:
- **VAST Tag/XML**: IMA SDK handles ALL tracking automatically
- **JSON/videoAsset**: Must manually fire all tracking events
- **Hybrid (VAST + Native End Card)**: IMA handles video, we handle overlay/end card

### Content Key Priority:
1. **ALWAYS check `delivery` field first** - this determines parsing strategy
2. If `delivery: "json"` → Expect `videoAsset` in contents, parse tracking from JSON
3. If `delivery: "vast_tag"` or `"vast_xml"` → NO `videoAsset`, use IMA players, tracking in XML
4. **`posterImage` is ALWAYS present** regardless of delivery method
5. Parse `companion*` keys regardless of delivery method (for native overlays)
6. For VAST Companion mode, parse `<CompanionAds>` from XML instead of JSON keys

### Tracking Responsibility:
| Scenario | Impressions | Video Events (start, quartiles, complete) | Skip Event | Custom Events (overlay, CTA, close) |
|----------|-------------|-------------------------------------------|------------|-------------------------------------|
| JSON delivery | Manual (SDK) | Manual (SDK) | Manual (SDK) | Manual (SDK) |
| VAST Tag/XML | IMA Auto | IMA Auto | IMA Auto | Manual (SDK) |

**Key Rule for Skippable Videos:**
- When `isSkippable: true`, skip tracking MUST be included:
  - JSON: Add `{ "key": "skip", "url": "..." }` to `tracking.videoEvents[]`
  - VAST: Add `<Tracking event="skip">` to XML + `skipoffset` attribute on `<Linear>`

---

## 🚀 Execution Order

### Today (Session 1): ✅ DONE
- [x] Analyze all mock scenarios
- [x] Map content keys to behaviors
- [x] Document implementation plan

### Next (Session 2):
- [ ] Add player selection UI
- [ ] Implement Basic Player (Phase 2)
- [ ] Test with JSON scenarios

### Later (Session 3):
- [ ] Implement ExoPlayer + IMA (Phase 3)
- [ ] Test with VAST scenarios
- [ ] Handle overlays and end cards

### Future (Session 4+):
- [ ] Implement Pure IMA (Phase 4)
- [ ] Implement JW Player (Phase 5)
- [ ] Polish UI/UX
- [ ] Comprehensive testing

---

## 📝 Testing Checklist

### JSON Delivery Tests:
- [ ] json_none - Basic video playback
- [ ] json_none_skippable - Skip functionality
- [ ] json_native-end-card - End card display

### VAST Delivery Tests:
- [ ] vasttag_none - Basic VAST playback
- [ ] vasttag_native-end-card - VAST + native end card
- [ ] vasttag_native-end-card_skippable - VAST + skip
- [ ] vasttag_vast-companion - VAST companion ads
- [ ] vastxml_vast-companion - VAST XML parsing

---

## 🔮 Future Enhancements

### Enhancement 1: IMA SDK Watermark Customization

**Goal:** Showcase that ExoPlayer + IMA allows full UI control vs Pure Google IMA SDK

**Pure IMA SDK Limitations:**
- ❌ Cannot customize "Ad" watermark
- ❌ Cannot customize "Learn More" button
- ❌ Fixed branding
- ❌ Limited UI control

**ExoPlayer + IMA Advantages:**
- ✅ Hide IMA's default UI via `AdsRenderingSettings`
- ✅ Draw custom "Ad" badge (required for compliance)
- ✅ Draw custom "Learn More" / CTA button
- ✅ Full control over visual chrome
- ✅ Maintain VAST/OMID compliance

**Implementation Tasks:**
- [ ] Add `setAdsRenderingSettingsProvider` to ExoPlayer + IMA player
- [ ] Configure `uiElements = EnumSet.noneOf(AdUiElement::class.java)` to hide IMA UI
- [ ] Create custom overlay views (Ad badge, Learn More button)
- [ ] Wire custom button clicks to `adsLoader.adsManager?.click()`
- [ ] Create side-by-side demo in Video Playground:
  - Left: Pure IMA SDK (default watermarks)
  - Right: ExoPlayer + IMA (custom branded overlays)

**Reference:** See Section 12 in VIDEO_PLAYER_FLOW_SUMMARY.md for detailed implementation steps

---

### Enhancement 2: VAST XML Native Support vs Manual Decoding

**Goal:** Demonstrate two approaches for handling `delivery: "vast_xml"`

**Approach 1: Native VAST XML Support (IMA SDK)**
- ✅ IMA accepts decoded XML via `AdsRequest.setAdsResponse(decodedXml)`
- ✅ Automatic VAST parsing
- ✅ Automatic tracking
- ✅ OMID compliant
- ✅ Zero manual tracking code

**Approach 2: Manual VAST XML Decoding (Basic Player)**
- ❌ Requires custom XML parser
- ❌ Manual `<MediaFile>` extraction
- ❌ Manual tracking beacon firing
- ❌ Manual companion ad extraction
- ✅ Full control over implementation

**Implementation Tasks:**

**For ExoPlayer + IMA (Native Support):**
- [ ] Decode `creative.vast.xmlBase64` from Base64
- [ ] Pass decoded XML to `AdsRequest.Builder().setAdsResponse(decodedXml)`
- [ ] Let IMA handle all tracking automatically
- [ ] Show "Zero manual tracking" badge in demo

**For Basic Player (Manual Decoding):**
- [ ] Create `VastXmlParser` utility class
- [ ] Implement `extractMediaFileUrl()` method
- [ ] Implement `extractTrackingEvents()` method
- [ ] Implement `extractSkipOffset()` method
- [ ] Implement `extractCompanionAds()` method
- [ ] Fire tracking URLs manually at correct video progress points
- [ ] Show "Full control but complex" badge in demo

**Implementation Matrix:**

| Delivery | Player | IMA | Tracking | VAST Parsing | Complexity |
|----------|--------|-----|----------|--------------|------------|
| `json` | Basic | No | Manual | N/A | Low |
| `json` | ExoPlayer+IMA | No | Manual | N/A | Low |
| `vast_tag` | ExoPlayer+IMA | Yes | Auto | Auto | Very Low |
| `vast_xml` (native) | ExoPlayer+IMA | Yes | Auto | Auto | Very Low |
| `vast_xml` (manual) | Basic | No | Manual | Manual | High |

**Demo Showcase:**
- [ ] Create "VAST XML - Native Support" demo option
- [ ] Create "VAST XML - Manual Decoding" demo option
- [ ] Show code comparison in UI
- [ ] Highlight tracking event logs for both approaches

**Reference:** See Section 12 in VIDEO_PLAYER_FLOW_SUMMARY.md for detailed implementation examples

---

**Current Status:** Analysis complete, ready to start Phase 1 implementation! 🎬
