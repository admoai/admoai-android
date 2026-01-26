# Admoai Android SDK

The Admoai Android SDK is a lightweight wrapper around the Decision Engine API, enabling Android applications to request, render, and track native and video advertisements with advanced targeting capabilities.

## Features

- **Native Ads** – Multiple template types (wide, image+text, text-only, carousel)
- **Video Ads** – JSON, VAST Tag, and VAST XML delivery methods
- **Rich Targeting** – Geo, location, and custom key-value targeting
- **Format Filter** – Request native-only, video-only, or any format
- **User Consent** – GDPR compliance with consent management
- **Event Tracking** – Impressions, clicks, video quartiles, and custom events
- **Jetpack Compose** – Native `rememberAdState` integration
- **Per-Request Control** – Override user/device data collection per request

## Requirements

- **Android API** 24+ (Android 7.0)
- **Kotlin** 1.8+
- **JDK** 17+

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.admoai:admoai-android:1.0.0")
}
```

Or in Groovy (`build.gradle`):

```groovy
dependencies {
    implementation 'com.admoai:admoai-android:1.0.0'
}
```

---

## Quick Start

### 1. Initialize the SDK

```kotlin
val config = SDKConfig(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",           // Optional: enables format filter (for Video Ads)
    enableLogging = true,                  // Optional: for debugging
    networkRequestTimeoutMs = 30000L       // Optional: 30s timeout
)

Admoai.initialize(sdkConfig = config)
val sdk = Admoai.getInstance()
```

### 2. Configure User Settings (Optional)

```kotlin
sdk.setUserConfig(
    UserConfig(
        id = "user_123",
        ip = "203.0.113.1",
        timezone = TimeZone.getDefault().id,
        consentData = Consent(gdpr = true)
    )
)

// Auto-populate device and app info
sdk.setDeviceConfig(DeviceConfig.systemDefault())
sdk.setAppConfig(AppConfig.systemDefault())
```

### 3. Build and Send a Request

```kotlin
val request = sdk.createRequestBuilder()
    .addPlacement(key = "home", format = PlacementFormat.NATIVE)
    .addPlacement(key = "promotions", format = PlacementFormat.VIDEO)
    .addGeoTarget(geoId = 2643743)  // London
    .addCustomTargeting(key = "category", value = "news")
    .build()

// Request ads (returns Flow)
sdk.requestAds(request).collect { response ->
    response.data?.forEach { adData ->
        adData.creatives?.forEach { creative ->
            // Render creative
        }
    }
}
```

### 4. Extract Content

```kotlin
creative.contents?.find { it.key == "headline" }?.value?.toString()
creative.contents?.find { it.key == "poster_image" }?.value?.toString()
creative.contents?.find { it.key == "video_asset" }?.value?.toString()
```

### 5. Track Events

```kotlin
// Impressions
sdk.fireImpression(creative.tracking)

// Clicks
sdk.fireClick(creative.tracking)

// Video quartiles 
sdk.fireVideoEvent(creative.tracking, "start")           // 0%
sdk.fireVideoEvent(creative.tracking, "first_quartile")  // 25%
sdk.fireVideoEvent(creative.tracking, "midpoint")        // 50%
sdk.fireVideoEvent(creative.tracking, "third_quartile")  // 75%
sdk.fireVideoEvent(creative.tracking, "complete")        // 98%
sdk.fireVideoEvent(creative.tracking, "skip")            // on skip

// Custom events
sdk.fireCustomEvent(creative.tracking, "companionOpened")
```

### 6. Clean Up on Logout

```kotlin
sdk.clearUserConfig()
sdk.clearDeviceConfig()
sdk.clearAppConfig()
```

---

## Sample App

See the [Sample App](../sample/README.md) for complete integration examples demonstrating:

- Native ad templates
- Video playback with VAST and JSON
- Tracking implementation
- Compose integration

---

## Jetpack Compose Integration

The SDK provides native Compose support through `rememberAdState`:

```kotlin
@Composable
fun AdScreen() {
    val request = remember {
        DecisionRequest(
            placements = listOf(Placement(key = "home"))
        )
    }
    
    val adState by rememberAdState(request)
    
    when (adState) {
        AdState.Idle -> { /* Ready */ }
        AdState.Loading -> CircularProgressIndicator()
        is AdState.Success -> {
            val creative = adState.response.data?.firstOrNull()?.creatives?.firstOrNull()
            creative?.let { AdCard(it) }
        }
        is AdState.Error -> {
            Text("Error: ${adState.exception.message}")
        }
    }
}
```

### AdState

| State | Description |
|-------|-------------|
| `AdState.Idle` | Initial state, ready to load |
| `AdState.Loading` | Request in progress |
| `AdState.Success` | Response received, contains `response` |
| `AdState.Error` | Request failed, contains `exception` |

### Benefits

- **Declarative** – State management follows Compose principles
- **Lifecycle-aware** – Automatically handles composition lifecycle
- **Less boilerplate** – No separate ViewModel required
- **Reactive** – Built on Kotlin Flow

---

## Request Builder

The `DecisionRequestBuilder` provides a fluent API:

```kotlin
val request = sdk.createRequestBuilder()
    // Placements
    .addPlacement(key = "home")
    .addPlacement(key = "promotions", format = PlacementFormat.VIDEO)
    
    // User overrides (per-request)
    .setUserId("user_123")
    .setUserIp("203.0.113.1")
    .setUserTimezone("America/New_York")
    .setUserConsent(Consent(gdpr = true))
    
    // Targeting
    .addGeoTargeting(geoId = 2643743)
    .addLocationTargeting(latitude = 37.7749, longitude = -122.4194)
    .addCustomTargeting(key = "category", value = "news")
    
    // Data collection
    .disableAppCollection()
    .disableDeviceCollection()
    
    .build()
```

---

## Response Structure

```kotlin
DecisionResponse
├── success: Boolean
├── data: List<AdData>?
│   └── AdData
│       ├── placement: String
│       └── creatives: List<Creative>?
│           └── Creative
│               ├── id: String?
│               ├── contents: List<Content>     // Key-value pairs
│               ├── advertiser: Advertiser?
│               ├── template: TemplateInfo?     // {key, style}
│               ├── tracking: TrackingInfo      // Tracking URLs
│               ├── delivery: String?           // "json", "vast_tag", "vast_xml"
│               ├── vast: VastData?             // {tagUrl} or {xmlBase64}
│               └── verificationScriptResources: List<VerificationScriptResource>?  // OM verification data
├── errors: List<Error>?
└── warnings: List<Warning>?
```

---

## Event Tracking

The SDK fires tracking beacons via HTTP requests. All methods return `Flow<Unit>`.

### Available Methods

```kotlin
// Impressions (fired when ad is displayed)
sdk.fireImpression(trackingInfo, key = "default")

// Clicks (fired on user tap)
sdk.fireClick(trackingInfo, key = "default")

// Video events (JSON delivery only)
sdk.fireVideoEvent(trackingInfo, key = "start")

// Custom events
sdk.fireCustomEvent(trackingInfo, key = "companionOpened")
```

### Tracking Keys

Each tracking type supports multiple keys. Use `"default"` for standard events or specify custom keys defined in your campaign configuration.

---

## Video Ad Support

The SDK supports three video delivery methods:

| Delivery | Response Field | Tracking |
|----------|----------------|----------|
| **JSON** | `video_asset` content key | SDK methods (`fireVideoEvent`) |
| **VAST Tag** | `vast.tagUrl` | IMA SDK automatic or manual HTTP |
| **VAST XML** | `vast.xmlBase64` | Manual HTTP GET |

### Detecting Video Ads

```kotlin
// Check delivery method
val isVideo = creative.delivery == "json" || 
              creative.delivery == "vast_tag" || 
              creative.delivery == "vast_xml"

// Get video URL (JSON delivery)
val videoUrl = creative.contents?.find { it.key == "video_asset" }?.value?.toString()

// Get VAST tag URL
val vastTagUrl = creative.vast?.tagUrl

// Get VAST XML (Base64 encoded)
val vastXmlBase64 = creative.vast?.xmlBase64
```

### Video Tracking Events

**Important**: Always fire the **impression** event first when the ad is displayed, then fire video-specific events as playback progresses.

| Event | When to Fire | Key |
|-------|--------------|-----|
| **Impression** | Ad displayed (before playback) | `default` |
| Start | Video begins playing (0%) | `start` |
| First Quartile | 25% progress | `first_quartile` |
| Midpoint | 50% progress | `midpoint` |
| Third Quartile | 75% progress | `third_quartile` |
| Complete | Video ends (98%) | `complete` |
| Skip | User skips | `skip` |

**Manual tracking** works with any delivery method:

```kotlin
// 1. Fire impression first (when ad is displayed)
sdk.fireImpression(creative.tracking)

// 2. Fire video events as playback progresses
sdk.fireVideoEvent(creative.tracking, "start")
sdk.fireVideoEvent(creative.tracking, "first_quartile")
sdk.fireVideoEvent(creative.tracking, "midpoint")
sdk.fireVideoEvent(creative.tracking, "third_quartile")
sdk.fireVideoEvent(creative.tracking, "complete")
sdk.fireVideoEvent(creative.tracking, "skip")  // if user skips
```

- **JSON delivery**: Tracking URLs are in the response—easiest to use with SDK methods
- **VAST Tag/XML**: Requires fetching the tag URL or decoding Base64 XML to extract tracking URLs, then firing HTTP GET beacons manually

> **Note**: Admoai is OM-compatible and passes verification metadata through VAST `<AdVerifications>` tags. See the [Open Measurement Integration](#open-measurement-integration) section below for implementation guidance. 

---

## Open Measurement Integration

Admoai is **OM-compatible** and passes Open Measurement verification metadata through VAST `<AdVerifications>` tags. This section explains how publishers can implement Open Measurement viewability and verification measurement in their apps.

### Roles and Responsibilities

**What Admoai does:**
- Acts as a strict ad server / decision engine
- Includes `<AdVerifications>` tags in VAST responses
- Provides verification metadata via SDK helper methods
- Documents OM integration patterns

**What Admoai does NOT do:**
- Ship an OM SDK or namespaced OM build
- Act as the "OM integration partner" in the trust chain
- Provide IAB OM certification

**What you (the Publisher) must do:**
- Own the OM integration in your app
- Obtain and use your own IAB namespace
- Integrate the IAB OM SDK or OM-compatible video player
- Manage OM session lifecycle (create, start, track events, finish)

> **Important**: Admoai stays out of the OM trust chain. Your app is the OM integration partner and uses your own IAB namespace for all measurements.

---

### Do I Need My Own IAB Namespace?

**Short answer:** No namespace = verification still works, but the SDK owns OM. Namespace = you own OM.

**Detailed explanation:**

You do **not** need your own IAB OM namespace if you use an OM-certified SDK like Google IMA (Path B). In that case, verification vendors (IAS, DoubleVerify, Moat, etc.) will still receive all required measurement data, but the OM integration partner will be the SDK provider (e.g., Google), not your app.

Creating your own IAB OM namespace is **only required** if you want to implement Open Measurement directly (e.g., using ExoPlayer as shown in Path A) and retain full control and ownership of the OM session lifecycle. This gives you complete flexibility over the video player UI and behavior.

**In summary:**
- **Path A (Native OM SDK)**: Requires your own IAB namespace → You own the OM integration
- **Path B (IMA Extension)**: No namespace needed → Google owns the OM integration
- **Path C (JW Player)**: No namespace needed → JW Player owns the OM integration

> If you choose Path A and want full control, proceed to Step 1 below. If you choose Path B or C, skip to their respective implementation sections.

---

### Step 1: Get Your IAB Namespace (Path A Only)

If you're implementing Path A (Native OM SDK), you need to obtain your own namespaced OM SDK from IAB Tech Lab:

1. **Visit the IAB Tech Lab website**: Go to [https://iabtechlab.com/standards/open-measurement-sdk/](https://iabtechlab.com/standards/open-measurement-sdk/)
2. **Click "Download OM SDK"**: This will take you to the compliance portal
3. **Sign in or register**: Create an account if you don't have one already
4. **Navigate to "Open Measurement SDK" section**: Find the SDK download area in your account dashboard
5. **Add a namespace**: Create a unique namespace identifier for your organization (e.g., `com.yourcompany-omid`)
   - Use a simple, recognizable name that represents your organization
   - This namespace identifies you as the OM integration partner
6. **Click "Build Android"**: Generate the Android SDK with your namespace
7. **Download from Android tab**: Download the `.aar` file (e.g., `omsdk-android-1.6.1-YourNamespace.aar`)
8. **Place in your project**: Add the `.aar` file to your app's `libs/` folder

> **Critical**: Your namespace will follow you throughout the OM trust chain. All verification vendors (IAS, DoubleVerify, Moat, etc.) will see your namespace as the OM integration partner, not Admoai.

---

### Step 2: Choose Your Implementation Path

Admoai is OM-compatible and works with any OM integration approach. We recommend the Native OM SDK for maximum flexibility, but you have multiple options:

| Approach | Pros | Cons | Best For |
|----------|------|------|----------|
| **Path A: Native OM SDK** (Recommended) | Full control, better UX, custom UI | More engineering effort | Publishers wanting complete control over video UX |
| **Path B: ExoPlayer + IMA Extension** | OM handled automatically, less code | Less control, IMA watermarks | Publishers prioritizing speed over customization |
| **Path C: JW Player** | Commercial support, OM built-in | License cost, vendor lock-in | Publishers wanting commercial-grade video player with support |

---

### Path A: Native OM SDK Integration (Recommended for Best UX)

Use this approach for full control over video playback and custom UI.

#### 1. Add the IAB OM SDK to your project

After downloading the namespaced OM SDK `.aar` from IAB:

```gradle
// In your app/build.gradle.kts
dependencies {
    implementation(files("libs/omsdk-android-1.4.x-YourNamespace.aar"))
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
}
```

#### 2. Extract verification resources from Admoai

```kotlin
import com.admoai.sdk.utils.getVerificationResources
import com.admoai.sdk.utils.hasOMVerification
import com.iab.omid.library.yournamespace.* // Your IAB namespace

// Get creative from Admoai SDK
val creative = response.data?.firstOrNull()?.creatives?.firstOrNull()

// Check if OM verification is available
if (creative?.hasOMVerification() == true) {
    val verificationResources = creative.getVerificationResources()
    // Proceed with OM session creation
}
```

#### 3. Create and start OM session

```kotlin
import com.iab.omid.library.yournamespace.adsession.*
import com.iab.omid.library.yournamespace.ScriptInjector
import android.webkit.WebView

class VideoAdPlayer {
    
    private var omAdSession: AdSession? = null
    private var omAdEvents: AdEvents? = null
    private var omMediaEvents: MediaEvents? = null
    
    fun setupOMSession(creative: Creative, videoView: View) {
        if (!creative.hasOMVerification()) return
        
        // 1. Activate OM SDK (once per app lifecycle)
        Omid.activate(context)
        
        // 2. Create Partner (your company info)
        val partner = Partner.createPartner(
            "YourCompany",      // Your company name
            "1.0.0"             // Your app version
        )
        
        // 3. Extract verification scripts from Admoai
        val verificationResources = creative.getVerificationResources() ?: return
        val verificationScripts = verificationResources.map { resource ->
            // Create VerificationScriptResource for each vendor
            VerificationScriptResource.createVerificationScriptResourceWithParameters(
                resource.vendorKey,
                URL(resource.scriptUrl),
                resource.verificationParameters ?: ""
            )
        }
        
        // 4. Create AdSessionContext
        val adSessionContext = AdSessionContext.createNativeAdSessionContext(
            partner,
            ScriptInjector.injectScriptContentIntoHtml(
                Omid.getJsServiceContent(context),
                "<html><head></head><body></body></html>"
            ),
            verificationScripts,
            null,   // contentUrl (optional)
            null    // customReferenceData (optional)
        )
        
        // 5. Create AdSessionConfiguration
        val config = AdSessionConfiguration.createAdSessionConfiguration(
            CreativeType.VIDEO,
            ImpressionType.BEGIN_TO_RENDER,
            Owner.NATIVE,          // You own OM events
            Owner.NONE,            // No external video events owner
            false                  // Not isolated
        )
        
        // 6. Create AdSession
        omAdSession = AdSession.createAdSession(config, adSessionContext)
        
        // 7. Register video view
        omAdSession?.registerAdView(videoView)
        
        // 8. Create event trackers
        omAdEvents = AdEvents.createAdEvents(omAdSession)
        omMediaEvents = MediaEvents.createMediaEvents(omAdSession)
        
        // 9. Start session
        omAdSession?.start()
        
        // 10. Fire loaded event
        omAdEvents?.loaded(
            VastProperties.createVastPropertiesForNonSkippableMedia(
                isAutoPlay = true,
                Position.STANDALONE
            )
        )
    }
    
    fun onVideoStarted() {
        omMediaEvents?.start(duration = 30.0f, videoPlayerVolume = 1.0f)
        omAdEvents?.impressionOccurred()
    }
    
    fun onVideoProgress(currentTime: Float) {
        // Track quartiles
        val progress = currentTime / videoDuration
        when {
            progress >= 0.25 && !firstQuartileFired -> {
                omMediaEvents?.firstQuartile()
                firstQuartileFired = true
            }
            progress >= 0.5 && !midpointFired -> {
                omMediaEvents?.midpoint()
                midpointFired = true
            }
            progress >= 0.75 && !thirdQuartileFired -> {
                omMediaEvents?.thirdQuartile()
                thirdQuartileFired = true
            }
        }
    }
    
    fun onVideoCompleted() {
        omMediaEvents?.complete()
        omAdSession?.finish()
    }
    
    fun onVideoSkipped() {
        omMediaEvents?.skipped()
        omAdSession?.finish()
    }
    
    fun cleanup() {
        omAdSession?.finish()
        omAdSession = null
        omAdEvents = null
        omMediaEvents = null
    }
}
```

#### 4. Integrate with ExoPlayer

```kotlin
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoAdActivity : AppCompatActivity() {
    
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var videoAdPlayer: VideoAdPlayer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        playerView.player = player
        
        // Get creative from Admoai SDK
        val creative = getCreativeFromAdmoai()
        
        // Setup OM session
        videoAdPlayer = VideoAdPlayer()
        videoAdPlayer.setupOMSession(creative, playerView.videoSurfaceView!!)
        
        // Setup video URL (VAST or JSON delivery)
        val videoUrl = when {
            creative.isVastTagDelivery() -> {
                // Fetch and parse VAST XML to get MediaFile URL
                fetchVastAndExtractMediaUrl(creative.vast?.tagUrl)
            }
            creative.isVastXmlDelivery() -> {
                // Decode Base64 VAST XML and extract MediaFile URL
                parseVastXmlAndExtractMediaUrl(creative.vast?.xmlBase64)
            }
            else -> {
                // JSON delivery: direct video URL
                creative.contents?.find { it.key == "video_asset" }?.value?.toString()
            }
        }
        
        // Load video
        val mediaItem = MediaItem.fromUri(videoUrl!!)
        player.setMediaItem(mediaItem)
        player.prepare()
        
        // Listen to playback events
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (player.isPlaying) {
                            videoAdPlayer.onVideoStarted()
                        }
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressTracking()
                }
            }
        })
        
        player.play()
    }
    
    private fun startProgressTracking() {
        // Update OM session with video progress
        handler.postDelayed(object : Runnable {
            override fun run() {
                val currentTime = player.currentPosition / 1000f
                videoAdPlayer.onVideoProgress(currentTime)
                handler.postDelayed(this, 250) // Check every 250ms
            }
        }, 250)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        videoAdPlayer.cleanup()
        player.release()
    }
}
```

---

### Path B: ExoPlayer + IMA Extension (Convenience Path)

Use this approach if you want OM handled automatically with less code, at the cost of less UI control.

#### 1. Add dependencies

```gradle
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-exoplayer-ima:1.2.0")
}
```

#### 2. Setup IMA with OM support

```kotlin
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings

class VideoAdActivity : AppCompatActivity() {
    
    private lateinit var player: ExoPlayer
    private lateinit var adsLoader: ImaAdsLoader
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get creative from Admoai SDK
        val creative = getCreativeFromAdmoai()
        
        // Setup IMA with OM enabled
        val imaSdkSettings = ImaSdkSettings().apply {
            enableOmid = true  // Enable OM in IMA
        }
        
        adsLoader = ImaAdsLoader.Builder(this)
            .setImaSdkSettings(imaSdkSettings)
            .build()
        
        // Setup ExoPlayer with IMA
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                exoPlayer.setAdsLoader(adsLoader)
            }
        
        val playerView: PlayerView = findViewById(R.id.player_view)
        playerView.player = player
        
        // Get VAST tag URL from Admoai creative
        val vastTagUrl = creative.vast?.tagUrl
        
        // Create ad tag data source
        val adTagUri = Uri.parse(vastTagUrl)
        val adTagDataSpec = DataSpec(adTagUri)
        
        // Load VAST ad
        adsLoader.setAdTagDataSpec(adTagDataSpec)
        
        // Prepare player
        player.prepare()
        player.play()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        adsLoader.release()
        player.release()
    }
}
```

> **Note on IMA**: Google IMA automatically handles OM session creation when `enableOmid = true` and VAST includes `<AdVerifications>` tags. However, you get less control over UI (IMA shows watermarks, "Learn More" buttons, and default skip buttons). For custom video UX, use Path A.

---

### Accessing Admoai's Verification Metadata

Regardless of which path you choose, Admoai provides helper methods to access OM verification data:

```kotlin
import com.admoai.sdk.utils.hasOMVerification
import com.admoai.sdk.utils.getVerificationResources

// Check if creative has OM verification
if (creative.hasOMVerification()) {
    val resources = creative.getVerificationResources()
    
    resources?.forEach { resource ->
        println("Vendor: ${resource.vendorKey}")           // e.g., "company.com-omid"
        println("Script URL: ${resource.scriptUrl}")       // e.g., "https://verification.ias.com/..."
        println("Parameters: ${resource.verificationParameters}")  // e.g., "anId=123&advId=789"
    }
}
```

#### VerificationScriptResource Properties

| Property | Type | Description |
|----------|------|-------------|
| `vendorKey` | String | Vendor identifier (e.g., "ias", "doubleverify", "moat") |
| `scriptUrl` | String | URL to verification JavaScript that OM SDK will load |
| `verificationParameters` | String? | Query parameters for verification session |

---

### VAST `<AdVerifications>` Handling

When you use VAST Tag or VAST XML delivery, Admoai includes `<AdVerifications>` in the VAST response:

```xml
<VAST version="4.2">
  <Ad>
    <InLine>
      <AdVerifications>
        <Verification vendor="company.com-omid">
          <JavaScriptResource apiFramework="omid" browserOptional="true">
            <![CDATA[https://verification.ias.com/omid_verification.js]]>
          </JavaScriptResource>
          <VerificationParameters>
            <![CDATA[anId=123&advId=789&creativeId=456]]>
          </VerificationParameters>
        </Verification>
      </AdVerifications>
      <!-- Linear creative, tracking, media files, etc. -->
    </InLine>
  </Ad>
</VAST>
```

- **Path A (Native OM SDK)**: Parse VAST yourself, extract `<AdVerifications>`, map to OM SDK `VerificationScriptResource` objects
- **Path B (IMA Extension)**: IMA automatically parses `<AdVerifications>` and creates OM sessions

---

### Testing Your OM Integration

**Use OM SDK validation**: The IAB OM SDK includes validation modes to verify your integration

---

### Summary

- **Admoai is OM-compatible**: We pass verification metadata via VAST `<AdVerifications>` and SDK helpers
- **Publishers own OM integration**: Publisher's app is the OM integration partner with your own IAB namespace
- **Two paths available**: Native OM SDK (full control) or ExoPlayer + IMA (convenience)
- **Admoai stays out of the trust chain**: We're a strict ad server; you're responsible for OM implementation

---

## Event Tracking

The SDK fires tracking beacons via HTTP requests. All methods return `Flow<Unit>`.

### Available Methods

```kotlin
// Impressions (fired when ad is displayed)
sdk.fireImpression(trackingInfo, key = "default")

// Clicks (fired on user tap)
sdk.fireClick(trackingInfo, key = "default")

// Video events (JSON delivery only)
sdk.fireVideoEvent(trackingInfo, key = "start")

// Custom events
sdk.fireCustomEvent(trackingInfo, key = "companionOpened")
```

### Tracking Keys

Each tracking type supports multiple keys. Use `"default"` for standard events or specify custom keys defined in your campaign configuration.

---

## Video Ad Support

The SDK supports three video delivery methods:

| Delivery | Response Field | Tracking |
|----------|----------------|----------|
| **JSON** | `video_asset` content key | SDK methods (`fireVideoEvent`) |
| **VAST Tag** | `vast.tagUrl` | IMA SDK automatic or manual HTTP |
| **VAST XML** | `vast.xmlBase64` | Manual HTTP GET |

### Detecting Video Ads

```kotlin
// Check delivery method
val isVideo = creative.delivery == "json" || 
              creative.delivery == "vast_tag" || 
              creative.delivery == "vast_xml"

// Get video URL (JSON delivery)
val videoUrl = creative.contents?.find { it.key == "video_asset" }?.value?.toString()

// Get VAST tag URL
val vastTagUrl = creative.vast?.tagUrl

// Get VAST XML (Base64 encoded)
val vastXmlBase64 = creative.vast?.xmlBase64
```

### Video Tracking Events

**Important**: Always fire the **impression** event first when the ad is displayed, then fire video-specific events as playback progresses.

| Event | When to Fire | Key |
|-------|--------------|-----|
| **Impression** | Ad displayed (before playback) | `default` |
| Start | Video begins playing (0%) | `start` |
| First Quartile | 25% progress | `first_quartile` |
| Midpoint | 50% progress | `midpoint` |
| Third Quartile | 75% progress | `third_quartile` |
| Complete | Video ends (98%) | `complete` |
| Skip | User skips | `skip` |

**Manual tracking** works with any delivery method:

```kotlin
// 1. Fire impression first (when ad is displayed)
sdk.fireImpression(creative.tracking)

// 2. Fire video events as playback progresses
sdk.fireVideoEvent(creative.tracking, "start")
sdk.fireVideoEvent(creative.tracking, "first_quartile")
sdk.fireVideoEvent(creative.tracking, "midpoint")
sdk.fireVideoEvent(creative.tracking, "third_quartile")
sdk.fireVideoEvent(creative.tracking, "complete")
sdk.fireVideoEvent(creative.tracking, "skip")  // if user skips
```

- **JSON delivery**: Tracking URLs are in the response—easiest to use with SDK methods
- **VAST Tag/XML**: Requires fetching the tag URL or decoding Base64 XML to extract tracking URLs, then firing HTTP GET beacons manually

> **Tip**: For VAST-based ads, you may optionally integrate a third-party VAST SDK (e.g., Google IMA) for automatic tracking and Open Measurement (OM) viewability. This is outside the scope of the current Admoai SDK but demonstrated in the [Sample App](../sample/README.md).

---

## Open Measurement Integration

The Admoai SDK provides support for Open Measurement (OM) verification data, allowing publishers to integrate with third-party viewability and verification measurement providers, such as Integral Ad Science (IAS), DoubleVerify, Moat, and others.

### Accessing Verification Resources

Each creative may include Open Measurement verification script resources that contain the necessary data for third-party verification:

```kotlin
val creative = decision.data?.firstOrNull()?.creatives?.firstOrNull()

// Check if the creative has OM verification data
if (creative?.hasOMVerification() == true) {
    // Get the verification resources
    val verificationResources = creative.getVerificationResources()
    
    verificationResources?.forEach { resource ->
        println("Vendor: ${resource.vendorKey}")
        println("Script URL: ${resource.scriptUrl}")
        println("Parameters: ${resource.verificationParameters}")
        
        // Use these values with your third-party verification SDK
        // Example: IAS or DoubleVerify integration
    }
}
```

### Verification Script Resource Properties

Each `VerificationScriptResource` contains:

- **vendorKey**: The identifier for the verification vendor (e.g., "ias", "doubleverify")
- **scriptUrl**: The URL to the verification script that needs to be loaded
- **verificationParameters**: Additional parameters required for verification setup

### Integration Example

Here's a complete example of how to extract and use OM data:

```kotlin
fun setupOMVerification(creative: Creative) {
    if (!creative.hasOMVerification()) return
    
    val resources = creative.getVerificationResources() ?: return
    
    resources.forEach { resource ->
        // Extract OM data
        val vendorKey = resource.vendorKey
        val scriptUrl = resource.scriptUrl
        val parameters = resource.verificationParameters
        
        // Integrate with your chosen verification SDK
        // Example pseudocode:
        // when (vendorKey) {
        //     "ias" -> {
        //         IASSDK.setupVerification(scriptUrl, parameters)
        //     }
        //     "doubleverify" -> {
        //         DoubleVerifySDK.setupVerification(scriptUrl, parameters)
        //     }
        // }
    }
}
```

### Important Notice

> [!WARNING] 
> **OM Certification Notice**: The Admoai SDK provides Open Measurement verification data as received from the ad server, but **the SDK itself is not OM certified**. Publishers must ensure that their implementation with third-party verification providers (such as IAS or DoubleVerify) complies with Open Measurement standards and requirements. Admoai acts as a strict ad server only; publishers are responsible for the proper implementation of their OM integration.

---

## Default Configuration Helpers

Auto-populate device and app information:

```kotlin
// Device info (model, OS, manufacturer, etc.)
sdk.setDeviceConfig(DeviceConfig.systemDefault())

// App info (name, version, identifier, etc.)
sdk.setAppConfig(AppConfig.systemDefault())
```

---

## Configuration Reference

### SDKConfig

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `baseUrl` | String | Required | Decision Engine API endpoint |
| `apiVersion` | String? | `null` | API version (e.g., `"2025-11-01"` for format filter) |
| `enableLogging` | Boolean | `false` | Enable debug logging |
| `defaultLanguage` | String? | `null` | Default language for requests |
| `networkRequestTimeoutMs` | Long | `10000` | HTTP request timeout (ms) |
| `networkConnectTimeoutMs` | Long | `10000` | Connection timeout (ms) |
| `networkSocketTimeoutMs` | Long | `10000` | Socket timeout (ms) |

### PlacementFormat

| Value | Description |
|-------|-------------|
| `PlacementFormat.NATIVE` | Request native ads only |
| `PlacementFormat.VIDEO` | Request video ads only |
| `null` | Request any format (default, recommended) |

> **Note**: Format filter requires `apiVersion = "2025-11-01"` or later.

---

## Thread Safety

The SDK is designed for concurrent use:

- Singleton pattern with thread-safe initialization
- Configuration changes are mutex-protected
- All network calls are non-blocking (Kotlin Flow)

---

## Proguard / R8

If using code shrinking, add these rules:

```proguard
-keep class com.admoai.sdk.** { *; }
-keepclassmembers class com.admoai.sdk.model.** { *; }
```

---

## Support

- **Email**: support@admoai.com

---

## License

Copyright 2025 Admoai Inc. All rights reserved.
