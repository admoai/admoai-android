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
- **Basic Player**: JSON delivery only (no VAST support)
- **ExoPlayer + IMA**: All delivery methods (recommended)
- **Google IMA SDK**: VAST only
- **JW Player**: Full VAST support

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
   - **Placement**: Any (e.g., "Home")
   - **Delivery Method**: **VAST Tag** ⭐
   - **End-Card Type**: **None** (for simplest test)
   - **Video Player**: **ExoPlayer + IMA** ⭐

4. **Click "Launch Video Demo"**

---

### Step 4: What to Look For

#### ✅ **Success Indicators (in Logcat):**

**Filter:** `com.admoai.sample`

**Expected logs:**
```
ExoPlayerIMA: Loading VAST tag URL: https://10.0.2.2:8080/endpoint?scenario=tagurl_vasttag_none
VideoAdDemo: Fetched mock data for scenario: vasttag_none (1305 chars)
```

**IMA SDK should load ads (no errors):**
```
IMA: Ad event: LOADED
IMA: Ad event: STARTED
IMA: Tracking impression
IMA: Tracking start
IMA: Tracking firstQuartile
IMA: Tracking midpoint
IMA: Tracking thirdQuartile
IMA: Tracking complete
```

#### ❌ **Errors to Watch For:**

**If you see this - FIXED!**
```
❌ Mixed Content: ... requested an insecure XMLHttpRequest endpoint 'http://...'
❌ Access to XMLHttpRequest ... blocked by CORS policy
```

**These should NOT appear anymore!**

---

### Step 5: Test Matrix

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
- ✅ Poster image displays before playback

#### Test 5: VAST Tag + Native End-Card + ExoPlayer + IMA
- ✅ `delivery: "vast_tag"`
- ✅ `companion*` keys present in contents
- ✅ Video plays via IMA (automatic tracking)
- ✅ Custom overlay appears at `overlayAtPercentage`
- ✅ Publisher-drawn UI over IMA player
- ✅ CTA and close button tracked via custom events

#### Test 6: VAST Tag + VAST Companion + ExoPlayer + IMA
- ✅ `delivery: "vast_tag"`
- ✅ NO `companion*` keys in contents
- ✅ VAST XML contains `<CompanionAds>` with multiple size options
- ✅ IMA loads video + companion creatives
- ✅ Companion ad displays (player/publisher selects best-fit size)
- ✅ Companion click tracking fires

#### Test 7: VAST XML + Skippable + ExoPlayer + IMA
- ✅ `delivery: "vast_xml"`, `vast.xmlBase64` present
- ✅ Decode Base64 → Parse VAST XML
- ✅ XML contains `skipoffset="00:00:05"` on `<Linear>`
- ✅ XML contains `<Tracking event="skip">`
- ✅ IMA shows skip button at 5 seconds
- ✅ Skip tracking fires automatically

#### Test 8: VAST XML + Native End-Card + ExoPlayer + IMA
- ✅ `delivery: "vast_xml"`
- ✅ Hybrid approach: Video from XML + overlay from JSON `companion*` keys
- ✅ IMA handles video playback
- ✅ Publisher overlays custom end-card
- ✅ Mixed tracking: IMA auto + manual custom events

---

## 🔍 Troubleshooting

### Issue: Certificate Not Trusted

**Symptom:** `javax.net.ssl.SSLHandshakeException`

**Solution:**
1. Verify certificate exists: `/sample/src/main/res/raw/mock_server_cert.der`
2. Check Network Security Config references it
3. Clean and rebuild app

### Issue: Server Not Responding

**Check if server is running:**
```bash
lsof -i :8080
```

**Restart server:**
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
kill $(lsof -ti :8080)
./start-https.sh
```

### Issue: "Ad" Indicator Shows

**This is normal** - IMA SDK displays "Ad" label. See documentation for how to hide it if needed.

---

## 📊 Expected Results

### Before HTTPS Fix:
- ❌ CORS errors
- ❌ Mixed content errors
- ❌ IMA SDK cannot load ads
- ❌ No tracking events

### After HTTPS Fix:
- ✅ No CORS errors
- ✅ No mixed content errors  
- ✅ IMA SDK loads VAST XML
- ✅ Video plays with ads
- ✅ Tracking events fire automatically

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

## 🔮 Future Testing Scenarios

### Scenario 1: IMA SDK Watermark Customization Testing

**Purpose:** Verify that ExoPlayer + IMA allows full UI control vs Pure Google IMA SDK

**Test A: Pure IMA SDK (Default Watermarks)**
1. Select player: **Google IMA SDK**
2. Select delivery: **VAST Tag** or **VAST XML**
3. Launch video
4. **Expected Results:**
   - ✅ IMA's default "Ad" watermark appears
   - ✅ IMA's default "Learn More" button appears
   - ❌ Cannot customize watermark styling
   - ❌ Cannot customize button text/appearance

**Test B: ExoPlayer + IMA (Custom Overlays)**
1. Select player: **ExoPlayer + IMA**
2. Enable feature flag: **Custom IMA Overlays** (when implemented)
3. Select delivery: **VAST Tag** or **VAST XML**
4. Launch video
5. **Expected Results:**
   - ✅ IMA's default UI is hidden via `AdsRenderingSettings`
   - ✅ Custom "Ad" badge appears (publisher-styled)
   - ✅ Custom "Learn More" / CTA button appears (publisher-styled)
   - ✅ Clicks on custom button fire `adsManager.click()`
   - ✅ VAST tracking events still fire automatically
   - ✅ OMID compliance maintained

**Validation Points:**
```
Logcat Filter: com.admoai.sample

✅ Check: "Setting AdsRenderingSettings with uiElements = NONE"
✅ Check: "IMA: Ad event: LOADED"
✅ Check: "IMA: Ad event: STARTED"  
✅ Check: "Custom overlay shown"
✅ Check: "IMA: Tracking impression"
✅ Check: "Custom CTA clicked, forwarding to adsManager.click()"
✅ Check: "IMA: Tracking click"
```

**Side-by-Side Demo:**
- Compare default IMA watermarks vs custom branded overlays
- Document UI control differences in README

---

### Scenario 2: VAST XML Native Support vs Manual Decoding

**Purpose:** Verify two approaches for handling `delivery: "vast_xml"`

**Test A: ExoPlayer + IMA (Native VAST XML Support)**
1. Select player: **ExoPlayer + IMA**
2. Select delivery: **VAST XML**
3. Select end-card: Any option
4. Launch video
5. **Expected Results:**
   - ✅ App decodes `vast.xmlBase64` from Base64
   - ✅ Pass decoded XML to `AdsRequest.setAdsResponse(decodedXml)`
   - ✅ IMA parses VAST XML automatically
   - ✅ IMA fires all tracking events automatically
   - ✅ Video plays from `<MediaFile>` URL
   - ✅ Companion ads handled automatically (if present)
   - ⭐ **Zero manual tracking code**

**Validation Points:**
```
Logcat Filter: com.admoai.sample

✅ Check: "Decoding VAST XML from Base64"
✅ Check: "Decoded XML length: XXX characters"
✅ Check: "Setting AdsRequest with XML response"
✅ Check: "IMA: Parsing VAST XML"
✅ Check: "IMA: Ad event: LOADED"
✅ Check: "IMA: Tracking impression"
✅ Check: "IMA: Tracking start"
✅ Check: "IMA: Tracking firstQuartile"
✅ Check: "IMA: Tracking midpoint"
✅ Check: "IMA: Tracking thirdQuartile"
✅ Check: "IMA: Tracking complete"
❌ Should NOT see: Manual tracking URL firing
```

**Test B: Basic Player (Manual VAST XML Decoding)**
1. Select player: **Basic Player**
2. Select delivery: **VAST XML**
3. Enable feature flag: **Manual VAST Parsing** (when implemented)
4. Launch video
5. **Expected Results:**
   - ✅ App decodes `vast.xmlBase64` from Base64
   - ✅ Custom `VastXmlParser` extracts `<MediaFile>` URL
   - ✅ Custom parser extracts all `<Tracking>` event URLs
   - ✅ Custom parser extracts `skipoffset` (if present)
   - ✅ Custom parser extracts `<CompanionAds>` (if present)
   - ✅ Video plays from extracted MediaFile URL
   - ✅ Publisher manually fires tracking at video progress milestones
   - ⚠️ **High complexity, full control**

**Validation Points:**
```
Logcat Filter: com.admoai.sample

✅ Check: "Decoding VAST XML from Base64"
✅ Check: "VastXmlParser: Extracting MediaFile URL"
✅ Check: "VastXmlParser: Found MediaFile: https://..."
✅ Check: "VastXmlParser: Extracting tracking events"
✅ Check: "VastXmlParser: Found impression tracking: https://..."
✅ Check: "VastXmlParser: Found start tracking: https://..."
✅ Check: "VastXmlParser: Found firstQuartile tracking: https://..."
✅ Check: "Playing video from extracted URL"
✅ Check: "Video progress: 0% - Firing impression tracking"
✅ Check: "Video progress: 0% - Firing start tracking"
✅ Check: "Video progress: 25% - Firing firstQuartile tracking"
✅ Check: "Video progress: 50% - Firing midpoint tracking"
✅ Check: "Video progress: 75% - Firing thirdQuartile tracking"
✅ Check: "Video progress: 100% - Firing complete tracking"
```

**Comparison Matrix:**

| Aspect | ExoPlayer + IMA (Native) | Basic Player (Manual) |
|--------|--------------------------|----------------------|
| Base64 Decoding | ✅ Manual | ✅ Manual |
| VAST XML Parsing | ✅ IMA Auto | ❌ Custom Parser |
| MediaFile Extraction | ✅ IMA Auto | ❌ Custom Code |
| Tracking Beacon Firing | ✅ IMA Auto | ❌ Manual Firing |
| Companion Ad Handling | ✅ IMA Auto | ❌ Custom Parsing |
| Skip Button | ✅ IMA Auto | ❌ Manual UI |
| OMID Compliance | ✅ IMA Auto | ❌ Manual Implementation |
| Code Complexity | ⭐ Very Low | ⚠️ High |
| Publisher Control | 🟡 Limited | ✅ Full Control |

**Demo Showcase Ideas:**
- Show code side-by-side for both approaches
- Highlight tracking event logs
- Display complexity badges ("Zero tracking code" vs "Full control")
- Add toggle to switch between native/manual parsing
- Show VAST XML structure in debug panel

---

## 📚 Related Documentation for Future Features

- **IMA Watermark Customization**: See Section 12 in VIDEO_PLAYER_FLOW_SUMMARY.md
- **VAST XML Approaches**: See Section 12 in VIDEO_PLAYER_FLOW_SUMMARY.md
- **Implementation Tasks**: See "Future Enhancements" in VIDEO_IMPLEMENTATION_ROADMAP.md

---

**Good luck testing! 🚀**
