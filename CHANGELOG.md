# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-06-24

### Added
- Initial release of AdMoai Android SDK
- Ad request and decision API with `DecisionRequestBuilder`
- Support for targeting (location, demographics, interests, custom attributes)
- Real-time ad tracking (impressions, clicks, custom events)
- Flow-based async API with coroutines support
- Compose UI integration helpers
- Configuration management (user, app, and SDK configs)
- Error handling with structured exception types
- Network optimization with request/response caching
- Sample application demonstrating SDK integration
- Comprehensive BDD test suite
- Production-ready documentation and README

### Technical Details
- **Minimum Android API**: 24 (Android 7.0)
- **Target Android API**: 35 (Android 15)
- **Kotlin Version**: 1.8+
- **Gradle Version**: 8.9
- **Architecture**: Clean Architecture with MVVM pattern
- **Dependencies**: AndroidX, Compose, Kotlin Coroutines, Serialization
- **Testing**: JUnit 5, MockK, Robolectric, BDD test patterns

### API Overview
- `Admoai.initialize(sdkConfig)` - SDK initialization
- `Admoai.createRequestBuilder()` - Create ad request builders
- `Admoai.requestAds(request)` - Request ads with targeting
- `Admoai.fireImpression/fireClick/fireCustomEvent` - Event tracking
- `SDKConfig`, `UserConfig`, `AppConfig` - Configuration classes
- Extension functions for content access and tracking URL retrieval

[1.0.0]: https://github.com/admoai/admoai-android/releases/tag/v1.0.0
