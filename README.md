# AdMoai Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8+-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-green)](https://developer.android.com/jetpack/compose)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)

AdMoai Android SDK provides a comprehensive native advertising solution for Android applications. Built with modern Android development practices, it offers seamless integration of ads with advanced targeting capabilities and full Jetpack Compose support.

## Features

- **Native Ad Formats** - Seamlessly integrated native advertising
- **Journey Ads** - Multi-stage, single-advertiser takeovers across one user activity
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
        apiVersion = "2025-11-01"   // gates Journey Ads, video, POI targeting, mid-flight changes, Open Measurement
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

## Journey Ads

Journey Ads are single-brand, multi-stage experiences that follow a user across one activity, sold
as a single deal. All Journey logic — eligibility, stage progression, takeover protection,
completion and billing — is owned by the Decision Engine; the SDK forwards your `sessionId` and
`journeyOpt`, exposes read-only journey metadata, and fires only the beacons you ask it to.

**Journey Ads put a new obligation on your app: the same `sessionId` on every request of a user's
activity.** Get it wrong and journeys silently never progress — no error, no warning.

> 📖 **The full integration guide lives in [`sdk/README.md` → Journey Ads](./sdk/README.md#journey-ads).**
> It is the single source of truth and covers the commercial model, what a session actually is, the
> `sessionId` contract, opt-in/opt-out, completion, no-ad handling, idle expiry, frequency capping,
> a pre-launch checklist, a glossary and an FAQ. This page deliberately does not summarise it —
> a partial copy would drift out of date and mislead.


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
