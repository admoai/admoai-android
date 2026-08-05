# AdMoai Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8+-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-green)](https://developer.android.com/jetpack/compose)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)

AdMoai Android SDK provides a comprehensive native advertising solution for Android applications. Built with modern Android development practices, it offers seamless integration of ads with advanced targeting capabilities and full Jetpack Compose support.

## Features

- **Native Ad Formats** - Seamlessly integrated native advertising
- **Journey Takeover Ads** - Multi-stage, single-advertiser takeovers across a session
- **Advanced Targeting** - Geo, location, destination, and custom targeting options
- **Jetpack Compose** - First-class Compose support with `rememberAdState`
- **GDPR Compliance** - Built-in user consent management
- **Event Tracking** - Comprehensive impression and click tracking
- **Flexible Templates** - Customizable ad layouts and formats
- **Kotlin-First** - Built for modern Android development

## Requirements

- **Android API 24+** (Android 7.0)
- **Kotlin 1.8+**
- **Jetpack Compose** (optional, for Compose integration)

## Installation

The AdMoai Android SDK is available on Maven Central for easy integration.

### Maven Central

Add the dependency to your app's `build.gradle.kts`:

<!-- x-release-please-start-version -->
```kotlin
dependencies {
    implementation("com.admoai:admoai-android:1.5.0")
}
```
<!-- x-release-please-end-version -->

Or in Groovy (`build.gradle`):

<!-- x-release-please-start-version -->
```groovy
dependencies {
    implementation 'com.admoai:admoai-android:1.5.0'
}
```
<!-- x-release-please-end-version -->

## Quick Start

```kotlin
// 1. Initialize the SDK (no Context required)
Admoai.initialize(
    SDKConfig(
        baseUrl = "https://api.admoai.com",
        apiVersion = "2025-11-01"   // required for Journey Ads and the format filter
    )
)
val sdk = Admoai.getInstance()

// 2. Build an ad request
val request = sdk.createRequestBuilder()
    .addPlacement(key = "home_feed", count = 1)
    .build()

// 3. Request ads — requestAds returns a Flow<DecisionResponse> that emits once
sdk.requestAds(request).collect { response ->
    val creative = response.data?.firstOrNull()?.creatives?.firstOrNull() ?: return@collect
    sdk.fireImpression(creative.tracking)
}
```

### Jetpack Compose Integration

```kotlin
@Composable
fun MyAdScreen() {
    val adState by rememberAdState(decisionRequest)
    
    when (adState) {
        is AdState.Success -> {
            // Display your ads
        }
        is AdState.Loading -> {
            CircularProgressIndicator()
        }
        // Handle other states...
    }
}
```

## Journey Takeover Ads

Journey Takeover Ads are single-brand, multi-stage experiences that follow a user across trip
stages. All Journey logic (eligibility, stage progression, takeover protection, completion,
billing) is owned by the decision engine — the SDK only forwards session context, surfaces
read-only metadata, and fires the engine's tracking URLs verbatim.

> **This is the first Admoai feature where a _sequence_ of calls matters.** A journey only advances
> when every request in a user's session carries the **same `sessionId`**. Full rules, worked
> examples, and the common mistakes are in the
> **[Journey Ads guide](./sdk/README.md#journey-ads)** — read it before integrating.

**Requires** `apiVersion = "2025-11-01"` (minimum Journey-capable engine version). Without it,
Journey is ignored by the engine and normal ads are served, silently.

```kotlin
// 1. Configure with the Journey-capable API version and a stable, publisher-provided session id.
Admoai.initialize(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",
    sessionId = "your-stable-session-id"
)

// The sessionId is sticky and seeded into every request builder. Rotate it explicitly per your
// own rules (the SDK never generates or changes it). It is PII — keep it out of your own logs.
Admoai.getInstance().setSessionId("new-session-id")

// 2. Optionally opt a session in/out of Journey at serve time.
val request = Admoai.getInstance().createRequestBuilder()
    .addPlacement("home_feed")
    .setJourneyOpt(JourneyOpt.OPT_IN) // or OPT_OUT
    .build()

// 3. Read the read-only Journey metadata off the served creative.
if (creative.isJourneyAd()) {
    val dealId = creative.journeyDealId()
    val stageKey = creative.journeyStageKey()
    val optStatus = creative.journeyOptStatus()
}
```

### Completion

Completion has two mutually-exclusive, engine-decided modes:

- **`custom_event`** — the creative carries a completion beacon, and completion is recorded **only**
  when you fire it. Fire it once, when the action the campaign pays for actually happens (not on
  render). The key is defined by the campaign:
  ```kotlin
  if (creative.hasCompletionUrl()) {
      Admoai.getInstance().fireCompletion(creative.tracking, key = "journey_complete")
  }
  ```
  `fireCompletion` is a no-op when there is no completion beacon, so it is safe to call on any ad.
- **`final_stage`** — completion is recorded server-side at decision time; there is no URL to fire.
  Check `creative.isJourneyCompletion()`. Fire only the normal impression.

### No-ad handling

Single-brand takeover may return no ad rather than a competing brand. Treat `creatives` `[]`,
`null`, and absent uniformly via `adData.isNoAd()` / `adData.hasCreative()`. Do not substitute a
local ad.

### Tracking notes

- Tracking GETs are sent with the `X-Tracking-Version` header (from `apiVersion`).
- For VAST delivery (`vast_tag` / `vast_xml`), impression/click beacons live inside the VAST
  payload — do not also fire `creative.tracking` for VAST, or you will double-count.

## Documentation

- **[SDK Documentation](./sdk/README.md)** - Complete API reference and integration guide
- **[Sample App Guide](./sample/README.md)** - How to run and explore the demo application

## Project Structure

```
admoai-android/
├── README.md ← You are here
├── sdk/
│   └── README.md ← SDK-specific setup & API
└── sample/
    └── README.md ← How to run the demo app
```

## Development

### Building the Project

```bash
# Clone the repository
git clone https://github.com/admoai/admoai-android.git
cd admoai-android

# Build the SDK and sample app
./gradlew clean build

# Run tests
./gradlew test
```

### Running the Sample App

See the [Sample App README](./sample/README.md) for detailed instructions on running and exploring the demo application.

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on:

- How to submit Pull Requests
- Commit message conventions (Conventional Commits)
- Code style and testing requirements
- Development workflow

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Documentation**: [SDK README](./sdk/README.md)
- **Issues**: [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- **Email**: support@admoai.com

---

**Built with ❤️ by the Admoai Team**
