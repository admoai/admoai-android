# AdMoai Kotlin SDK

AdMoai Kotlin SDK is a native advertising solution that enables seamless integration of ads into Android applications. The SDK provides a robust API for requesting and displaying various ad formats with advanced targeting capabilities.

## Features

- Native ad format support
- Rich targeting options (geo, location, custom)
- User consent management (GDPR)
- Flexible ad templates
- Companion ad support
- Carousel ad layouts
- Impression and click tracking
- Per-request data collection control
- Jetpack Compose integration

## Requirements

- Android API 24+
- Kotlin 1.8+

## Installation

The AdMoai Android SDK is available on Maven Central.

### Maven Central

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.admoai:admoai-android:1.0.0")
}
```

Or in your `build.gradle` file:

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url "https://maven.pkg.github.com/admoai/admoai-android"
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation 'com.admoai:admoai-android-sdk:1.0.0'
}
```

### Authentication

Create a GitHub Personal Access Token with `read:packages` permission and add it to your `gradle.properties` file:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Alternatively, set environment variables:
```bash
export USERNAME=YOUR_GITHUB_USERNAME
export TOKEN=YOUR_GITHUB_TOKEN
```

## Quick Start

1. Initialize the SDK:

```kotlin
// Initialize SDK with base URL and optional configurations
val config = SDKConfig(baseUrl = "https://example.api.admoai.com")
// Optionally configure additional settings
// val config = SDKConfig(
//     baseUrl = "https://example.api.admoai.com",
//     enableLogging = true,
//     defaultLanguage = "en-US"
// )

// Initialize the SDK
Admoai.initialize(sdkConfig = config)
val sdk = Admoai.getInstance()

// Configure user settings globally
sdk.setUserConfig(
    UserConfig(
        id = "user_123",
        ip = "203.0.113.1",
        timezone = TimeZone.getDefault().id,
        consentData = Consent(gdpr = true)
    )
)
```

2. Create and send an ad request:

```kotlin
// Using coroutines
suspend fun requestAds() {
    // Build request with placement
    val request = sdk.createRequestBuilder()
        .addPlacement(key = "home")
        .build()
    
    // Request ads
    val response = sdk.requestAds(request).first() // Using Flow.first() to get the first emitted value
}
```

You can also build the request with targeting and user settings:

```kotlin
val request = sdk.createRequestBuilder()
    .addPlacement(key = "home")
    
    // Override user settings for this request
    .setUserId("different_user")
    .setUserIp("203.0.113.2")
    .setUserTimezone("America/New_York")
    .setUserConsent(User.Consent(gdpr = false))
    
    // Add targeting
    .addGeoTargeting(2643743)  // London
    .addLocationTargeting(latitude = 37.7749, longitude = -122.4194)
    .addCustomTargeting(key = "category", value = "news")
    
    // Build request
    .build()
```

3. Handle the creative:

```kotlin
response.body.data?.firstOrNull()?.let { decision ->
    decision.creatives?.firstOrNull()?.let { creative ->
        // Access creative properties
        val headline = creative.contents?.find { it.key == "headline" }?.value?.toString()
        val imageUrl = creative.contents?.find { it.key == "coverImage" }?.value?.toString()
        
        // Track impression
        sdk.fireImpression(tracking = creative.tracking)
        
        // Handle click with tracking (Android example)
        creative.tracking.clicks?.find { it.key == "default" }?.url?.let { clickUrl ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))
            context.startActivity(intent)
        }
    }
}
```

4. Clean up on logout:

```kotlin
// Reset user configuration when user logs out
sdk.clearUserConfig()  // Resets to: id = null, ip = null, timezone = null, consent.gdpr = false
```

## Event Tracking

The SDK automatically handles event tracking through HTTP requests. Each creative contains tracking URLs for different events (impressions, clicks, custom events) that are called when triggered.

### Tracking Configuration

Each creative includes tracking configuration for different event types:

```kotlin
// Available tracking URLs in the creative
val impressionUrls = creative.tracking.impression
val clickUrls = creative.tracking.click
val customEventUrls = creative.tracking.custom

// Fire tracking events directly
sdk.fireImpression(creative.tracking)
sdk.fireClick(creative.tracking, key = "default") // or specify a different key
sdk.fireCustom(creative.tracking, eventName = "videoStart")
```

## Jetpack Compose Integration

The SDK provides native Jetpack Compose support through the `rememberAdState` composable, which manages ad requests and state in a declarative way:

```kotlin
@Composable
fun MyAdScreen() {
    // Create a decision request
    val decisionRequest = remember {
        DecisionRequest(
            placements = listOf(
                Placement(
                    key = "home",
                    count = 1
                )
            )
        )
    }
    
    // Use rememberAdState to manage ad loading
    val adState by rememberAdState(decisionRequest)
    
    when (adState) {
        is AdState.Loading -> {
            CircularProgressIndicator()
        }
        
        is AdState.Success -> {
            val adData = adState.response.data?.firstOrNull()
            if (adData != null) {
                // Display your ad content
                AdCard(
                    adData = adData,
                    onTrackImpression = { /* Handle impression */ },
                    onAdClick = { /* Handle click */ }
                )
            }
        }
        
        is AdState.Error -> {
            Text(
                text = "Failed to load ad: ${adState.exception.message}",
                color = MaterialTheme.colorScheme.error
            )
        }
        
        AdState.Idle -> {
            Text("Ready to load ads")
        }
    }
}
```

### Compose vs Traditional Approach

The `rememberAdState` composable provides several benefits over the traditional ViewModel approach:

- **Declarative**: State management follows Compose principles
- **Lifecycle-aware**: Automatically handles composition lifecycle
- **Less boilerplate**: No need for separate ViewModel setup
- **Reactive**: Built on Kotlin Flow for automatic UI updates

## Default Configuration Helpers

For ease of integration, the SDK provides helper methods for getting system default configurations:

```kotlin
// Get system default device configuration
val deviceConfig = DeviceConfig.systemDefault()

// Get system default app configuration
val appConfig = AppConfig.systemDefault()

// Apply these configurations
sdk.setDeviceConfig(deviceConfig)
sdk.setAppConfig(appConfig)
```

## API Reference

See the API documentation for detailed information on all classes and methods.

## License

Copyright 2025 AdMoai Inc. All rights reserved.
