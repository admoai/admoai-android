# AdMoai Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8+-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-green)](https://developer.android.com/jetpack/compose)

AdMoai Android SDK provides a comprehensive native advertising solution for Android applications. Built with modern Android development practices, it offers seamless integration of ads with advanced targeting capabilities and full Jetpack Compose support.

## Features

- **Native Ad Formats** - Seamlessly integrated native advertising
- **Advanced Targeting** - Geo, location, and custom targeting options
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

## Quick Start

```kotlin
// 1. Initialize the SDK
val config = SDKConfig(baseUrl = "https://api.admoai.com")
Admoai.initialize(this, config)

// 2. Create an ad request
val request = DecisionRequestBuilder()
    .addPlacement("home_feed", count = 1)
    .build()

// 3. Request ads
Admoai.getInstance().requestAds(request) { response ->
    // Handle ad response
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

We welcome contributions! Please see our contributing guidelines and code of conduct.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Documentation**: [SDK README](./sdk/README.md)
- **Issues**: [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- **Email**: support@admoai.com

---

**Built with ❤️ by the Admoai Team**
