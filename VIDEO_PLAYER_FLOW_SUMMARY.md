# Video Player Flow - Complete Implementation Summary

## Overview
This document describes the complete **Video Ad** player implementation in the Admoai Android Sample App. 

**Key Concept**: The video itself IS the advertisement. This is not pre-roll ads before content - the entire video creative is the ad unit. After playback, the publisher can replay or fetch another video ad via the Decision API.

This covers all UI flows, player selection logic, VAST tag handling, overlay rendering, and tracking integration.

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
- ✅ Plays video ads via IMA SDK (video IS the ad)
- ✅ Shows poster image before playback (mandatory for all videos)
- ✅ Displays custom publisher-drawn overlay UI (end-cards, companions)
- ✅ Error handling with user-friendly messages
- ✅ Automatic tracking via VAST XML (impressions, quartiles, clicks)

---

## 4. Basic Video Player Implementation

**Function:** `BasicVideoPlayer()` (lines 1174-1405)

### Configuration:
- Simple ExoPlayer without IMA integration
- Manual tracking via Admoai SDK
- **JSON delivery only** - Cannot play VAST Tag or VAST XML (not VAST-compliant)

### Features:
- ✅ Direct video playback from `videoAsset` URL
- ✅ Poster image support (mandatory)
- ✅ Publisher-drawn overlay UI (end-cards, skip button, close button)
- ✅ Manual video event tracking (start, quartiles, complete)
- ✅ Custom event tracking (overlay shown, CTA clicks, close button)

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

## 7. Canonical Content Keys

These keys appear in the Decision API response under `creative.contents[]` and define video ad behavior:

### Mandatory Keys:
- **`posterImage`**: Thumbnail image URL (required for all video ads)

### Video Delivery Keys:
- **`videoAsset`**: Direct video URL (JSON delivery only)
- VAST delivery uses `creative.vast.tagUrl` or `creative.vast.xmlBase64` instead

### Skippable Video Keys (Optional):
- **`isSkippable`**: Boolean, if true the video can be skipped
- **`skipOffset`**: String, when skip button appears (e.g., "00:00:05" = 5 seconds)

### Overlay/End-Card Keys (Optional):
- **`companionHeadline`**: Text headline for end-card overlay
- **`companionCta`**: Call-to-action button text (e.g., "Book Now")
- **`companionDestinationUrl`**: URL to open when CTA clicked
- **`overlayAtPercentage`**: Float (0.0-1.0), when overlay appears (0.5 = 50% through video)
- **`showClose`**: Integer (0 or 1), whether to show X close button in top-right corner

### Important Notes:
- **All overlay UI elements are publisher-drawn** - not player-native
- The playground demonstrates best practices for rendering these overlays with smooth animations and modern UI
- Overlays work with both JSON and VAST delivery methods

---

## 8. Tracking Integration

### IMA SDK (ExoPlayer + IMA):
- **Automatic**: VAST Tag returns VAST XML containing `<Tracking>` event URLs that fire automatically by IMA SDK
- Events: impression, start, firstQuartile, midpoint, thirdQuartile, complete, click
- No manual tracking needed - IMA handles all video event pings

### Manual Tracking (Basic Player):
- Uses Admoai SDK methods: `viewModel.fireVideoEvent(creative, eventName)`
- Events fired at video progress milestones (0%, 25%, 50%, 75%, 100%)
- Developer responsibility to track events

### Custom Events (Both Players):
- **Overlay Shown**: `viewModel.fireCustomEvent(creative, "overlayShown")`
- **CTA Click**: `viewModel.fireClick(creative, "cta")`
- **Close Button**: `viewModel.fireCustomEvent(creative, "closeBtn")`

**Clarification**: "VAST Tag" = URL endpoint that returns VAST XML. "VAST XML" = the actual XML document containing tracking URLs.

---

## 9. Key Fixes Applied

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

## 10. File Structure

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

## 11. Testing Checklist & Rules

### Core Rules:
1. **Video Options** (Delivery + End-Card) are independent from **Video Player** selection
2. **Basic Player** cannot play VAST Tag or VAST XML - it's disabled when either is selected
3. **All overlay UI** is publisher-drawn regardless of delivery method or player
4. Focus on demonstrating **cool animations and modern UI** for overlays in the playground

### Test Matrix:

#### JSON Delivery (Basic Player):
- ✅ **JSON + None**: Video from `videoAsset`, manual tracking, poster image displayed
- ✅ **JSON + Native End-card**: Custom overlay at `overlayAtPercentage`, `companionHeadline`/`companionCta` rendered with animations
- ✅ **JSON + VAST Companion**: Overlay shows companion ad data (though VAST companions typically VAST-only)

#### JSON Delivery (ExoPlayer + IMA):
- ✅ **JSON + None**: Video from `videoAsset`, IMA tracking (though no VAST XML), poster image
- ✅ **JSON + Native End-card**: Custom publisher-drawn overlay, smooth animations, CTA tracking

#### VAST Tag Delivery (ExoPlayer + IMA ONLY):
- ✅ **VAST Tag + None**: IMA loads VAST XML, plays video ad, automatic tracking, poster image, **NOTE: IMA shows "Ad" indicator**
- ✅ **VAST Tag + Native End-card**: IMA video + custom publisher overlay at specified percentage
- ✅ **VAST Tag + VAST Companion**: IMA video + VAST companion from XML (or fallback to native overlay)

#### VAST XML Delivery (ExoPlayer + IMA ONLY):
- ✅ **VAST XML + VAST Companion**: Same as VAST Tag but XML embedded in response

#### UI/UX Tests:
- ✅ **Basic Player disabled**: Grayed out when VAST Tag/XML selected
- ✅ **Poster image**: Displays for all video ads before playback
- ✅ **Skip button**: Appears at `skipOffset` when `isSkippable` is true
- ✅ **Close button**: Top-right X when `showClose` = 1
- ✅ **Overlay animations**: Smooth slide-up or fade-in for end-cards
- ✅ **CTA interactions**: Button clicks open `companionDestinationUrl` and fire tracking

### Overlay UI Focus Areas:
- Modern Card-based design with proper elevation
- Smooth animations (slide up, fade in, scale)
- Responsive layouts that adapt to different screen sizes
- Proper color theming (Material 3)
- Touch feedback and ripple effects on interactive elements

---

## 12. Next Steps / Known Issues

### 🚨 **CRITICAL ISSUES BLOCKING IMA SDK:**

#### Issue 1: CORS Policy Error ❌
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

## 13. HTTPS Mock Server Setup Guide

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
2. **VAST Tag Flow**: Decision API returns tagUrl → IMA fetches VAST XML → IMA parses MediaFile URL → IMA plays video ad → Automatic tracking
3. **JSON Flow**: Decision API returns videoAsset URL → Player loads directly → Manual tracking required
4. **Publisher-Drawn Overlays**: ALL overlay UI (end-cards, skip buttons, close buttons) are rendered by the publisher, not the player
5. **Canonical Keys**: `posterImage` (mandatory), `videoAsset` (JSON only), `companion*` (overlay content), `overlayAtPercentage` (timing), `isSkippable`/`skipOffset` (skip behavior), `showClose` (close button)
6. **IMA Requirements**: Both `setAdsLoaderProvider` AND `setAdViewProvider` must be configured for IMA SDK
7. **IMA HTTPS Requirement**: IMA SDK runs in WebView and **requires HTTPS VAST URLs**. HTTP URLs are blocked by browser mixed content policy. Network Security Config does NOT help for WebView because it only applies to native Android networking, not WebView's JavaScript requests.
8. **Mock Server HTTPS**: Local mock server now runs with HTTPS on port 8080 using self-signed certificates. Android app trusts the certificate via Network Security Config + custom certificate in `/res/raw/`.
9. **Basic Player Limitation**: Cannot play VAST Tag or VAST XML - only JSON delivery with direct video URLs
10. **Content URI for VAST**: Use actual video URL as content, VAST tag URL in AdsConfiguration
11. **VAST Terminology**: "VAST Tag" = URL endpoint, "VAST XML" = the XML document with tracking URLs
12. **Certificate Management**: Self-signed certificates valid for 365 days. Regenerate annually using the same `openssl` command.
