# Video Player Implementation Roadmap

## ✅ Analysis Complete

### Content Keys Discovered:
- `videoAsset` - Direct video URL (HLS)
- `posterImage` - Thumbnail
- `isSkippable` / `skipOffset` - Skip functionality
- `companionHeadline` / `companionCta` / `companionDestinationUrl` - Native end cards
- `overlayAtPercentage` - When to show overlay (0.5 = 50%)
- `showClose` - Whether to show close button

### Delivery Methods:
1. **JSON + videoAsset** - Load video directly, fire tracking manually
2. **VAST Tag** - Load tag URL into IMA SDK
3. **VAST XML** - Decode Base64 → Load into IMA SDK

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
1. Check `delivery` field first
2. If `vast.tagUrl` or `vast.xmlBase64` exists → Use IMA players
3. If `videoAsset` exists → Use basic player
4. Parse overlay/end card keys regardless of delivery method

### Tracking Responsibility:
| Scenario | Impressions | Video Events | Custom Events |
|----------|-------------|--------------|---------------|
| JSON delivery | Manual | Manual | Manual |
| VAST Tag/XML | IMA auto | IMA auto | Manual |

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

**Current Status:** Analysis complete, ready to start Phase 1 implementation! 🎬
