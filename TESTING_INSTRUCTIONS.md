# 🚀 Testing Instructions - HTTPS Mock Server + IMA SDK

## ✅ Setup Complete!

All configurations are done. Follow these steps to test the video ad player with IMA SDK.

---

## 📚 Quick Reference - Content Keys & Delivery Methods

### Delivery Methods:
| Delivery | VAST Object | videoAsset in Contents? | Tracking Location |
|----------|-------------|-------------------------|-------------------|
| `"json"` | `null` | ✅ YES | In JSON response |
| `"vast_tag"` | `{ tagUrl }` | ❌ NO | In XML from tagUrl |
| `"vast_xml"` | `{ xmlBase64 }` | ❌ NO | In decoded XML |

### Canonical Content Keys (Non-Editable):
- **`posterImage`** - Always present for all delivery methods
- **`videoAsset`** - Only in JSON delivery
- **`isSkippable`** / **`skipOffset`** - Skip button configuration

### User-Defined Content Keys (Standard Convention):
- **`companionHeadline`** / **`companionCta`** / **`companionDestinationUrl`** - Native end-card content
- **`overlayAtPercentage`** - When to show overlay (0.0-1.0)
- **`showClose`** - Close button visibility

### End-Card Modes:
1. **None** - Video only
2. **Native End-Card** - Publisher draws overlay using `companion*` keys
3. **VAST Companion** - XML contains `<CompanionAds>`

### Player Capabilities:
- **Media3 ExoPlayer + IMA**: Media3 ExoPlayer for playback, IMA extension for ad logic (VAST Tag, JSON)
- **Google IMA SDK**: Pure IMA SDK - Full control over VAST Tag & VAST XML 
- **Basic Player**: JSON delivery only (no VAST support, manual tracking)

---

##  Player Architecture

### Media3 ExoPlayer + IMA
**Separation of Responsibilities:**
- **Media3 ExoPlayer**: Video playback, buffering, decoding, rendering, UI
- **IMA SDK Extension**: Ad logic, VAST tag parsing, tracking beacon firing
- **Implementation**: Uses `androidx.media3.exoplayer.ima.ImaAdsLoader` (Media3's IMA wrapper)
- **Supports**: VAST Tag (adTagUrl), JSON (direct video)
- **Limitation**: Cannot use `adsResponse` for VAST XML (Media3 doesn't expose it)

### Google IMA SDK
**Pure IMA SDK Architecture:**
- **Supports (when refactored)**: VAST Tag (adTagUrl), VAST XML (adsResponse), all delivery methods

### Basic Player
**Simple ExoPlayer:**
- No IMA integration
- Direct video playback from `videoAsset` URL
- Manual tracking via Admoai SDK
- **Supports**: JSON delivery only

---

## 📋 Prerequisites

1. ✅ HTTPS mock server with SSL certificates
2. ✅ Android app trusts self-signed certificate
3. ✅ All VAST tag URLs updated to HTTPS
4. ✅ App rebuilt and ready to install

---

## 🎯 Step-by-Step Testing

### Step 1: Start HTTPS Mock Server

The mock server is **currently running** at `https://10.0.2.2:8080` (HTTPS enabled).

**To verify it's running:**
```bash
curl -k https://localhost:8080/health
```

**Expected response:**
```json
{"status": "healthy", "service": "mock-endpoints"}
```

**If you need to restart it:**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
kill $(lsof -ti :8080)  # Kill old server
CERT_FILE=cert.pem KEY_FILE=key.pem PORT=8080 go run main.go
```

---

### Step 2: Install Android App

```bash
cd /Users/matias-admoai/Documents/repos/admoai-android
./gradlew :sample:installDebug
```

Or use Android Studio's "Run" button.

---

### Step 3: Test Video Ad Demo

1. **Open the Admoai Sample App** on the emulator

2. **Navigate to "Video Ad Demo"** (bottom navigation)

3. **Select Test Configuration:**
   - **Delivery Method**: **VAST Tag** ⭐
   - **End-Card Type**: **None** (for simplest test)
   - **Video Player**: **Media3 ExoPlayer + IMA** ⭐

4. **Click "Launch Video Demo"**

---

### Step 4: Test Matrix

Test these configurations to verify full functionality across delivery methods and end-card modes:

#### Test 1: JSON + None + Basic Player
- ✅ `delivery: "json"`, `vast: null`
- ✅ `videoAsset` present in contents
- ✅ Direct video playback
- ✅ Manual tracking (impression, video events)
- ✅ Poster image displays before playback
- ✅ No IMA SDK involved

#### Test 2: JSON + Native End-Card + Basic Player
- ✅ `delivery: "json"`
- ✅ `videoAsset`, `companionHeadline`, `companionCta`, `companionDestinationUrl` present
- ✅ Custom overlay appears at `overlayAtPercentage` (e.g., 50%)
- ✅ CTA button clickable
- ✅ Manual tracking for video events AND custom tracking for CTA click

#### Test 3: JSON + Skippable + Basic Player
- ✅ `delivery: "json"`
- ✅ `isSkippable: true`, `skipOffset: "00:00:05"` in contents
- ✅ Skip button appears at 5 seconds
- ✅ Skip tracking event fires when clicked
- ✅ Video skips to end or closes

#### Test 4: VAST Tag + None + ExoPlayer + IMA
- ✅ `delivery: "vast_tag"`, `vast.tagUrl` present
- ✅ NO `videoAsset` in contents
- ✅ `tracking.impressions` and `tracking.videoEvents` empty
- ✅ IMA SDK loads VAST XML from tagUrl
- ✅ Video plays with automatic tracking
- ✅ IMA "Ad" and "Learn more" badges appear (non-removable) → confirms IMA/VAST path
- ✅ Poster image displays before playback

#### Test 5: VAST Tag + Native End-Card + ExoPlayer + IMA
- ✅ `delivery: "vast_tag"`
- ✅ `companion*` keys present in contents
- ✅ Video plays via IMA (automatic tracking)
- ✅ IMA "Ad" and "Learn more" badges appear (non-removable) → confirms IMA/VAST path
- ✅ Custom overlay appears at `overlayAtPercentage`
- ✅ Publisher-drawn UI over IMA player
- ✅ CTA and close button tracked via custom events

#### Test 6: VAST Tag + VAST Companion + ExoPlayer + IMA
- ✅ `delivery: "vast_tag"`
- ✅ NO `companion*` keys in contents
- ✅ VAST XML contains `<CompanionAds>` with multiple size options
- ✅ IMA loads video + companion creatives
- ✅ IMA "Ad" and "Learn more" badges appear (non-removable) → confirms IMA/VAST path
- ✅ Companion ad displays (player/publisher selects best-fit size)
- ✅ Companion click tracking fires

> Note: `delivery: "vast_xml"` requires the **Pure Google IMA SDK** (adsResponse) which is not yet implemented. VAST XML testing is tracked in the Future section below.

---


### Content Keys Validation (What to Check in Responses):

#### For JSON Delivery:
```json
{
  "delivery": "json",
  "vast": null,  // ✅ Must be null
  "contents": [
    { "key": "videoAsset", ... },     // ✅ Must be present
    { "key": "posterImage", ... }     // ✅ Always present
  ],
  "tracking": {
    "impressions": [...],             // ✅ Must have URLs
    "videoEvents": [...]              // ✅ Must have URLs
  }
}
```

#### For VAST Tag Delivery:
```json
{
  "delivery": "vast_tag",
  "vast": { "tagUrl": "..." },        // ✅ Must have tagUrl
  "contents": [
    // ❌ NO videoAsset key
    { "key": "posterImage", ... }     // ✅ Always present
  ],
  "tracking": {
    "impressions": [],                // ✅ Must be empty
    "videoEvents": []                 // ✅ Must be empty
  }
}
```

#### For VAST XML Delivery:
```json
{
  "delivery": "vast_xml",
  "vast": { "xmlBase64": "..." },     // ✅ Must have xmlBase64
  "contents": [
    // ❌ NO videoAsset key
    { "key": "posterImage", ... }     // ✅ Always present
  ],
  "tracking": {
    "impressions": [],                // ✅ Must be empty
    "videoEvents": []                 // ✅ Must be empty
  }
}
```

---

## 📄 Related Documentation

- **Video Concepts (Canonical)**: `/admoai-android/VIDEO_CONCEPTS.md`
- **Full Flow Documentation**: `/admoai-android/VIDEO_PLAYER_FLOW_SUMMARY.md`
- **Network Security Config**: `/sample/src/main/res/xml/network_security_config.xml`
- **Mock Server Code**: `/mock-endpoints/main.go`
- **SSL Certificates**: `/mock-endpoints/cert.pem`, `/mock-endpoints/key.pem`

---

## 🎉 Success Criteria

You'll know everything works when:

1. ✅ App fetches mock data from `https://10.0.2.2:8080`
2. ✅ IMA SDK loads VAST XML without errors
3. ✅ Video plays with poster image
4. ✅ Tracking events appear in logcat (impression, quartiles, complete)
5. ✅ No CORS or mixed content errors in logs

---

## 🔮 Future

- **VAST XML via Pure Google IMA SDK (adsResponse)**
  - Status: Not yet implemented; will enable native VAST XML ingestion.
  - Tests will mirror VAST Tag behavior with automatic tracking and companion handling.

- **Side‑by‑Side UI Showcase (Compliance)**
  - Compare IMA default badges vs publisher overlays for JSON flows.
  - For VAST via IMA, "Ad" and "Learn more" badges must appear and cannot be removed; use them as a validation signal of correct IMA/VAST integration.

References: `/admoai-android/VIDEO_CONCEPTS.md`, `/admoai-android/VIDEO_IMPLEMENTATION_ROADMAP.md`.

---

**Good luck testing! 🚀**
