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
    .addGeoTargeting(geoId = 2643743)  // London
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
│               └── vast: VastData?             // {tagUrl} or {xmlBase64}
├── errors: List<Error>?
└── warnings: List<Warning>?
```

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

## Sample App

See the [Sample App](../sample/README.md) for complete integration examples demonstrating:

- Native ad templates
- Video playback with VAST and JSON
- Tracking implementation
- Compose integration

---

## Support

- **Issues**: [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- **Email**: support@admoai.com

---

## License

Copyright 2025 Admoai Inc. All rights reserved.
