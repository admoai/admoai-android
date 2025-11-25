# Admoai Android SDK – Sample App

A comprehensive request-builder and preview tool demonstrating how to integrate the Admoai SDK to construct, send, and inspect `POST /decision` requests, and render the returned native and video creatives in realistic placement contexts.

## Overview

The sample app serves as both a **learning resource** and a **testing tool** for SDK integrators. It demonstrates:

- Building ad requests with placements, targeting, and user configuration
- Previewing ads in realistic host-app contexts (home screen, search, menu, etc.)
- Inspecting raw API responses and tracking URLs
- Native ad rendering with multiple template types
- Video ad playback with VAST and JSON delivery methods
- Jetpack Compose integration patterns

## Requirements

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** API level 24+ (Android 7.0)
- **Kotlin** 1.8+
- **JDK** 17+

## Quick Start

### Build & Run

```bash
# Clone and navigate to project
git clone https://github.com/admoai/admoai-android.git
cd admoai-android

# Build the sample app
./gradlew :sample:assembleDebug

# Install on connected device/emulator
./gradlew :sample:installDebug
```

Or in Android Studio:
1. Open the project
2. Select the `sample` run configuration
3. Click **Run** (Shift + F10)

---

## App Structure

The sample app consists of three principal parts:

1. **Decision Request Screen** – Main configuration interface for building ad requests
2. **Placement Previews** – Realistic host-app contexts showing rendered ads
3. **Response Inspector** – Tabbed interface for examining API responses and tracking

### Navigation Flow

```
MainActivity
├── Decision Request Screen (main configuration)
│   ├── Placement Picker
│   ├── Targeting Pickers (Geo, Location, Custom)
│   ├── User Settings
│   ├── HTTP Request Preview
│   └── Compose Integration Demo
├── Placement Preview Screens (9 placements)
│   └── Response Details Inspector
└── Video Ad Demo (sandbox)
    └── Video Preview Screen
```

---

## Decision Request Screen

The main screen assembles all parameters for the `POST /decision` request. Content is organized in logical sections:

### Placement

| Field | Description |
|-------|-------------|
| **Key** | Single-select picker with predefined placement keys |
| **Format Filter** | Optional filter: Any / Native / Video (requires API version `2025-11-01+`) |

**Available Placements**:

| Placement | Supports | Badge |
|-----------|----------|-------|
| Home | Native | – |
| Search | Native | – |
| Menu | Native | – |
| Promotions | Native + Video | 🎬 Video |
| Waiting | Native + Video | 🎬 Video |
| Vehicle Selection | Native | – |
| Ride Summary | Native + Video | 🎬 Video |
| Free Minutes | Video only | 🎬 Video |
| Invalid Placement | Error demo | – |

### Targeting

| Option | Description |
|--------|-------------|
| **Geo Targeting** | Multi-select list of cities |
| **Location Targeting** | Add lat/long coordinates manually or randomly |
| **Custom Targeting** | Key-value pairs (string values in demo) |

### User

| Field | Description |
|-------|-------------|
| **User ID** | Text field for user identification |
| **User IP** | Pre-filled example IP (for geo-targeting) |
| **Timezone** | Picker with Olson timezone strings |
| **Consent (GDPR)** | Toggle for GDPR compliance flag |

### App & Device (Read-only)

Auto-populated fields showing name, version, build, identifier, model, OS, and timezone.

### Data Collection

- **Collect App Data** – Toggle (default: on)
- **Collect Device Data** – Toggle (default: on)

### Actions

- **View HTTP Request** – Preview the formatted JSON request with headers
- **Request and Preview** – Send the request and navigate to placement preview

---

## Placement Previews

After sending a request, the app displays the ad in a **realistic host-app context** demonstrating how the creative would appear in production.

| Placement | Context | Ad Position |
|-----------|---------|-------------|
| **Home** | Grey home screen with navigation | Card pinned near top center |
| **Search** | Search results list | Embedded mid-feed |
| **Menu** | Settings menu | Sponsored banner at bottom |
| **Promotions** | Promotional carousel | Swipeable tiles (native or video) |
| **Waiting** | Map with driver search | Bottom-sheet carousel overlay |
| **Vehicle Selection** | Vehicle picker | Top banner |
| **Ride Summary** | Trip summary | Bottom banner (native or video) |
| **Free Minutes** | Rewarded video | Full-screen portrait |
| **Invalid Placement** | Error state | No ad rendered |

### Navigation Chrome

All preview screens include:
- **Back arrow** (upper left)
- **Title + subtitle** (placement name / key)
- **Response Details** icon (document icon, upper right)
- **Refresh** icon (circular arrow, upper right) – fetches new ad

> **Note**: Free Minutes does not have a refresh button; users tap prize boxes to request ads.

### Video Rendering

- **Free Minutes**: Full-screen portrait, occupies entire app surface
- **All other placements**: Inline video embedded in the ad card (no modal)

---

## Response Details Inspector

Tap the document icon to open a tabbed inspector with five segments:

| Tab | Content |
|-----|---------|
| **Contents** | All assets (images, headline, body, CTA, colors) with key/type/value |
| **Info** | Advertiser card, template key/style, metadata (adId, creativeId, etc.) |
| **Tracking** | Grouped impression/click URLs with manual verification buttons |
| **Validation** | Schema validation issues (if any) |
| **JSON** | Raw prettified API response |

---

## Video Ad Demo (Sandbox)

A dedicated sandbox for experimenting with video ad configurations:

### Configuration Options

| Option | Values |
|--------|--------|
| **Delivery Method** | JSON, VAST Tag, VAST XML |
| **End-Card Mode** | None, Native (custom UI), VAST Companion |
| **Skippable** | On / Off |
| **Player** | Media3 ExoPlayer + IMA SDK, Media3 ExoPlayer |

### Flow

1. Select configuration options
2. Tap **Launch Video Demo**
3. Watch the video with selected behavior
4. Scroll to view Request/Response tabs
5. Review implementation details card

Helper text throughout the sandbox explains what's happening in both UX and code.

---

## Supported Ad Formats

### Native Ads

| Template | Used By | Component |
|----------|---------|-----------|
| `wideWithCompanion` | Home | `HorizontalAdCard` |
| `imageWithText` | Search, Vehicle Selection | `SearchAdCard` |
| `textOnly` | Menu | `MenuAdCard` |
| `carousel3Slides` | Promotions, Waiting | `PromotionsCarouselCard` |
| `wideImageOnly` | Vehicle Selection | `HorizontalAdCard` |
| `standard` | Ride Summary | `HorizontalAdCard` |

### Video Ads

| Delivery | Description | Tracking |
|----------|-------------|----------|
| **JSON** | Direct video URL in response | SDK methods (`fireVideoEvent`) |
| **VAST Tag** | URL returning VAST XML | IMA SDK automatic |
| **VAST XML** | Base64-encoded VAST in response | Manual HTTP GET |

---

## Code Examples

### SDK Initialization

```kotlin
val config = SDKConfig(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",
    enableLogging = true
)
Admoai.initialize(sdkConfig = config)
```

### Building a Request

```kotlin
val request = Admoai.getInstance().createRequestBuilder()
    .addPlacement(key = "home", format = PlacementFormat.NATIVE)
    .addGeoTargeting(geoId = 2643743)
    .setUserId("user_123")
    .setUserConsent(Consent(gdpr = true))
    .build()
```

### Jetpack Compose Integration

```kotlin
@Composable
fun AdScreen() {
    val adState by rememberAdState(decisionRequest)
    
    when (adState) {
        AdState.Loading -> CircularProgressIndicator()
        is AdState.Success -> AdCard(adState.response)
        is AdState.Error -> ErrorMessage(adState.exception)
        AdState.Idle -> { /* Ready */ }
    }
}
```

### Tracking Events

```kotlin
// Native ads
sdk.fireImpression(creative.tracking)
sdk.fireClick(creative.tracking)

// Video ads (JSON delivery)
sdk.fireVideoEvent(creative.tracking, "start")
sdk.fireVideoEvent(creative.tracking, "first_quartile")
sdk.fireVideoEvent(creative.tracking, "complete")
```

---

## Project Structure

```
sample/src/main/java/com/admoai/sample/
├── MainActivity.kt              # Navigation + routes
├── config/
│   └── AppConfig.kt             # API URL, version
└── ui/
    ├── MainViewModel.kt         # State management + SDK interaction
    ├── components/
    │   ├── AdCard.kt            # Template routing
    │   ├── HorizontalAdCard.kt  # Wide card component
    │   ├── SearchAdCard.kt      # Image+text component
    │   ├── MenuAdCard.kt        # Text-only component
    │   ├── PromotionsCarouselCard.kt
    │   ├── VideoAdCard.kt       # Video + companion
    │   ├── VideoPlayerForPlacement.kt
    │   └── VideoOptionsSection.kt
    ├── mapper/
    │   └── AdTemplateMapper.kt  # Template detection
    └── screens/
        ├── DecisionRequestScreen.kt
        ├── VideoAdDemoScreen.kt
        ├── VideoPreviewScreen.kt
        ├── PlacementPickerScreen.kt
        ├── ComposeIntegrationScreen.kt
        ├── ResponseDetailsScreen.kt
        └── previews/
            ├── HomePreviewScreen.kt
            ├── SearchPreviewScreen.kt
            ├── MenuPreviewScreen.kt
            ├── PromotionsPreviewScreen.kt
            ├── WaitingPreviewScreen.kt
            ├── VehicleSelectionPreviewScreen.kt
            ├── RideSummaryPreviewScreen.kt
            └── FreeMinutesPreviewScreen.kt
```

---

## Customization

### Custom API Endpoint

Update `config/AppConfig.kt`:

```kotlin
object AppConfig {
    const val API_BASE_URL = "https://your-api-endpoint.com"
    const val API_VERSION = "2025-11-01"
}
```

### Custom Ad Layouts

Extend or replace ad card components in `ui/components/`:

```kotlin
@Composable
fun CustomAdCard(
    adData: AdData,
    onTrackImpression: () -> Unit,
    onTrackClick: () -> Unit
) {
    // Your custom layout
}
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build errors | Run `./gradlew clean build` |
| Emulator network issues | Verify internet connectivity; use `10.0.2.2` for localhost |
| VAST clickthrough fails | Add `<queries>` block to AndroidManifest.xml for browser intents |
| Video timeout | Increase SDK timeout to 30000ms |
| Content key not found | Verify exact case in Response Details → JSON tab |

### Logcat Filters

```bash
adb logcat -s MainViewModel:D Tracking:D VideoAdCard:D IMA:D
```

---

## Design Notes

The UI intentionally mimics **iOS conventions** while running on Android:

- SF Symbols-like iconography
- iOS-style spacing and typography
- iPhone safe-area behavior
- Primary actions in blue, destructive actions in red
- Light/dark mode support

This cross-platform consistency helps mobility-industry developers who ship on both iOS and Android.

---

## Related Documentation

- **[SDK Documentation](../sdk/README.md)** – Core SDK integration guide
- **[Complete Reference](../ADMOAI_ANDROID_COMPLETE_REFERENCE.md)** – Full technical documentation

---

## Support

- **Issues**: [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- **Email**: support@admoai.com

---

**Built with Jetpack Compose, Media3 ExoPlayer, and the Admoai SDK**
