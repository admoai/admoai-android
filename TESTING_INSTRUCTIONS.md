# Testing Instructions

Video Demo is ready. Test different delivery/player/endcard combinations.

---

## Quick Reference

See `/admoai-android/VIDEO_CONCEPTS.md` for canonical definitions.

**Deliveries**: JSON (videoAsset), VAST Tag (tagUrl), VAST XML (xmlBase64)  
**End-cards**: None, Native (companion* keys), VAST Companion (<CompanionAds>)  
**Players**: Media3+IMA (VAST Tag auto, rest manual), Media3 (all manual), JW (info)

---

## 📋 Prerequisites

1. ✅ HTTPS mock server with SSL certificates
2. ✅ Android app trusts self-signed certificate
3. ✅ All VAST tag URLs updated to HTTPS
4. ✅ App rebuilt and ready to install

---

## Setup

**Mock Server** (HTTPS): `https://10.0.2.2:8080`  
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
CERT_FILE=cert.pem KEY_FILE=key.pem PORT=8080 go run main.go
```

**Install App**:
```bash
cd /Users/matias-admoai/Documents/repos/admoai-android
./gradlew :sample:installDebug
```

**Test Flows**:

**Video Ad Demo**:  
1. Open app → "Video Ad Demo"  
2. Select delivery/endcard/player  
3. "Launch Video Demo" → plays directly (no preview mode dialog)

**Decision Request Builder - Video Integration** (✅ NEW - Oct 24, 2025):
1. Open app → "Decision Request" 
2. **Enable** "Use Format Filter" toggle
3. **Select** "Video" format
4. **Select** a video-eligible placement:
   - Promotions
   - Waiting  
   - Vehicle Selection
   - Ride Summary
5. **Tap** "Request and Preview"
6. ⚠️ **Requires**: Local mock server running on `http://localhost:8080`
7. **Result**: Video ad plays in placement context with VAST Tag delivery
8. **Note**: Currently hardcoded to request `"vasttag_none"` placement key (development only)

**Placement Previews**:
1. Open app → "Placement Picker"
2. Select placement → preview screen shows realistic context
3. Standard: Refresh button fetches new ad
4. Free Minutes: Click prize box → fullscreen video → end-card

---

## Test Matrix

**Media3 + IMA**:
- JSON: Direct play, SDK tracking, custom overlays  
- VAST Tag: IMA auto-tracking, custom overlays  
- VAST XML: Parse XML, HTTP GET tracking, custom overlays

**Media3**:
- All deliveries: Manual HTTP/SDK tracking, custom overlays

**UI Controls**: Both players start with controls hidden (tap video to show)

**Validation**:

**Video Ad Demo**:
- ✅ Black screen during loading (poster NOT shown - Nov 2025 change)
- ✅ Skip button at offset (custom UI overlay)  
- ✅ End-card at `overlay_at_percentage` or completion  
- ✅ Quartiles: 0/25/50/75/98%  
- ✅ IMA "Ad" and "Learn more" badges for VAST Tag (validates IMA integration)
- ✅ VAST clickthrough: Tap "Learn more" → browser opens (requires manifest `<queries>` block)

**Placement Previews**:
- **All Placements**: Back + Response Details buttons visible and clickable
- **Standard Placements**: Refresh button visible (home, search, menu, promotions, etc.)
- **Free Minutes Only**: NO refresh button (use prize boxes instead)
- **No-Ads Behavior** (Nov 2025): When format filter yields no ads, ad card completely hidden (not greyed out)

**Template Rendering**:
- **imageWithText**: Check if `image_left` or `image_right` style applied correctly
- **wideImageOnly**: Should use `HorizontalAdCard`, not `SearchAdCard`
- **carousel3Slides**: Auto-advances every 3 seconds, manual swipe works

**Click-Through**:
- **home, vehicleSelection, rideSummary**: Full card clickable → opens browser
- **promotions, waiting**: Individual slides clickable → opens browser with `URLSlideN`
- **search, menu**: No click-through (display only)
- **freeMinutes**: CTA button opens browser

**Free Minutes Placement**:
- Prize boxes clickable with badges
- NO refresh button in navigation bar
- Video plays immediately with back button + progress bar
- End-card shows on completion (image, text, X, CTA)
- Single click on back/X button closes (no multiple clicks needed)

**Vehicle Selection**:
- Ad cards should not overlap with nav bar (120dp top padding)
- Both `image_with_text` and `wide_image_only` templates render correctly
- No theme toggle circles overlaying buttons

## Response Validation

**⚠️ Content Keys**: All use **snake_case** (Oct 2025): `video_asset`, `poster_image`, `is_skippable`, `skip_offset`, `companion_headline`, `companion_cta`, `companion_destination_url`, `overlay_at_percentage`.

**JSON**: `vast: null`, `video_asset` present, tracking populated  
**VAST Tag**: `vast.tagUrl` present, NO video_asset, tracking empty  
**VAST XML**: `vast.xmlBase64` present, NO video_asset, tracking empty  
**All**: `poster_image` always present

**Tracking Event Naming by Delivery**:
- **VAST (Tag/XML)**: camelCase → `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete`, `skip`
- **JSON**: snake_case → `start`, `first_quartile`, `midpoint`, `third_quartile`, `complete`, `skip`

## Success Criteria

**Network**:
- Mock data fetches from `https://10.0.2.2:8080`
- No timeout errors (30s timeout configured)
- Look for `HttpRequestTimeoutException` if requests fail

**Video Ads**:
- IMA loads VAST without errors  
- Poster → video plays → tracking fires  
- No CORS/mixed-content errors

**Native Ads**:
- Template matches content (no broken/empty cards)
- Images load successfully
- Click-through opens browser when enabled
- No navigation button overlay issues

**Logging**:
- **Standards** (Oct 2025): Professional, structured tags: `[MANUAL]`, `[AUTOMATIC]`, `[URL]`, `[HTTP]`
- **VAST XML Tracking**: Look for `[MANUAL] Firing VAST 'firstQuartile' tracking` with HTTP GET
- **JSON Tracking**: Look for `[MANUAL] Firing 'first_quartile' event` with SDK method
- **Carousel**: Check `"URLSlide1"` extraction (uppercase)
- **Click handlers**: Verify `"onSlideClick called with URL: ..."`
- **UI Controls**: Controls should be hidden at video start, appear on tap

## Common Issues & Debugging

**Issue**: Carousel not opening browser  
**Check**: Logcat for `"URLSlideN"` extraction (must be uppercase)  
**Fix**: Verify API response content key casing

**Issue**: Template not rendering  
**Check**: `AdTemplateMapper` routing in `AdCard.kt`  
**Fix**: Ensure correct component for template key

**Issue**: Content key not found  
**Check**: API response in Response Details screen  
**Fix**: Verify exact key name (case-sensitive)

**Issue**: Timeout errors  
**Check**: Network timeout in `SDKConfig.kt` (should be 30000ms)  
**Fix**: Increase timeout or fix mock server

**Issue**: VAST XML tracking not firing  
**Check**: Verify tracking URLs stored in `vastTrackingUrls` map, look for HTTP GET logs  
**Fix**: Ensure Player 1 fires via HTTP GET, not SDK method (see `VIDEO_CONCEPTS.md` section 6)

**Issue**: Video controls showing at start  
**Check**: Controls should be hidden when video starts  
**Fix**: Verify `controllerAutoShow = false` set on PlayerView

**Issue**: Card clicks not working  
**Check**: Using `Card(onClick = ...)` not `.clickable()` modifier  
**Fix**: Replace `.clickable()` with Card's onClick parameter

**Issue**: VAST clickthrough not working ("Learn more" tap does nothing)  
**Check**: Logcat for `AppsFilter: BLOCKED` error  
**Fix**: Add `<queries>` block to `AndroidManifest.xml` (see `VIDEO_CONCEPTS.md` section 14)

**Issue**: Poster image showing before video (old behavior)  
**Check**: Should show black screen during loading  
**Fix**: Verify poster overlay removed from video components (Nov 2025 change)

---

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions (see section 11 for mapping rules)  
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details (see section 8 for fixes)  
- `VIDEO_IMPLEMENTATION_ROADMAP.md` - Implementation status
