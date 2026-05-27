# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0](https://github.com/admoai/admoai-android/admoai/admoai-android/compare/v1.3.0...v1.4.0) (2026-05-27)


### Added

* **builder:** add clear* methods to DecisionRequestBuilder ([#41](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/41)) ([24fbbc5](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/24fbbc5d52b7f30b29b59ae18877eb7dd79e1017))
* **builder:** add targeting deduplication and minConfidence validation ([#46](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/46)) ([0422443](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/04224432488f54ffd9be2fe2cd142d7662a05033))
* **config:** complete systemDefault() for DeviceConfig and AppConfig ([#47](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/47)) ([e882f32](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/e882f32f3f961a7edff01a7bc5df8d88f6c377ba))
* **network:** send Accept-Language header on tracking requests ([#45](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/45)) ([b9f8d85](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/b9f8d8572e1b7f17bed3ad10b656616c2a5a915c))
* **network:** send User-Agent: AdMoaiSDK/{version} on all requests ([#40](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/40)) ([2ec1fa5](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/2ec1fa51372203176fa576149e251ab39e41fd14))
* **response:** add getContent/hasContents/isType helpers to List&lt;Content&gt; ([#43](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/43)) ([8841e7b](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/8841e7b2c6fecad8e8dea9e3d6f10724eb101953))
* **response:** add TrackingType enum and safe URL helpers to TrackingInfo ([#42](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/42)) ([747353d](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/747353d1c85ee0869fa26d487934b91ac19e1f1d))
* **sdk:** make tracking fire-and-forget and expose public fireTracking(url) ([#44](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/44)) ([8e890b7](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/8e890b7eb3d68af79a7f7389642ac1a3a45b34e0))


### Fixed

* **response:** add UNKNOWN fallback to MetadataPriority — prevent SerializationException on future server values ([#54](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/54)) ([d0aacd7](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/d0aacd775f229f188682c1afb85cc1541c10061f))
* **response:** make Advertiser.id nullable (String? instead of String = "") ([#39](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/39)) ([655a6c4](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/655a6c49b4997ccce83bb8e8f5d6d6796312e9e5))
* send X-Decision-Version header on tracking requests ([#27](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/27)) ([ac6fa85](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/ac6fa8573172480eb4b9617ddb8c70095dc8798b)), closes [#26](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/26)
* **test:** resolve nullable metadata access in Ktor3IntegrationTest ([#48](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/48)) ([c04cdb2](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/c04cdb222c01408c61873d474ff0930d9c8cf248))


### Changed

* bump version to 1.4.0 ([#55](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/55)) ([dadd88e](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/dadd88e842d1ab2d0a6eb66435ea3892cb6e4380))
* release 1.1.2 ([#24](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/24)) ([164aea7](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/164aea7129c86e622d65ce92f44e4f57363e8d32))

## [1.3.0](https://github.com/admoai/admoai-android/admoai/admoai-android/compare/v1.2.0...v1.3.0) (2026-02-17)


### Added

* om integration ([#16](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/16)) ([4b1557b](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/4b1557b57580747904ccaab958a9045f0b8fddcc))
* **sdk:** upgrade Ktor from 2.x to 3.x for compatibility with modern Android apps ([#18](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/18)) ([e75806a](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/e75806a596c0c8c9180d5ae90817544aef3b0842))

## [1.2.0](https://github.com/admoai/admoai-android/admoai/admoai-android/compare/v1.1.1...v1.2.0) (2026-01-08)


### Added

* **android-sdk:** Verification models and helper functions added. Re… ([#8](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/8)) ([23d73e7](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/23d73e7546ad3114b637a899500f2ccbbd1c707c))
* destination targeting ([#13](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/13)) ([97e7c3f](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/97e7c3f5791b7b77a1049812ebcd7d49eb8d5194))


### Changed

* update version metadata 1.1.1 ([#11](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/11)) ([6bb6cae](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/6bb6caef81bf4be8e185e6739c7d9d8fcc0053e1))

## [1.1.1](https://github.com/admoai/admoai-android/admoai/admoai-android/compare/v1.1.0...v1.1.1) (2025-12-12)


### Fixed

* handle missing metadata in creative objects ([#9](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/9)) ([6ec9893](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/6ec98937e6859ac629c4b2f253c94d3da40938d7))

## [1.1.0](https://github.com/admoai/admoai-android/admoai/admoai-android/compare/v1.0.0...v1.1.0) (2025-11-27)


### Added

* add contributing guidelines and release configuration ([#1](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/1)) ([ab0fc86](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/ab0fc86d0841398c9819dfdbb1187b5ba03fe968))
* **sdk:** add video ad support with VAST Tag, VAST XML, and JSON delivery ([#2](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/2)) ([d1f5945](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/d1f5945f756fedcb9a553f2839629156c9901801))


### Changed

* Refactor release-please configuration and update GitHub Actions workflow ([#6](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/6)) ([528dc07](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/528dc072ad9e7224858006dd1f7e8b58689c412d))
* Update release-please configuration to use generic file types for extra files ([#3](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/3)) ([977056d](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/977056d5cf79f0b9602ae554b3700022b90b42ac))
* Update release-please workflow to include config and manifest file parameters ([#4](https://github.com/admoai/admoai-android/admoai/admoai-android/issues/4)) ([9e9f86b](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/9e9f86b425e127394e72ab4870dba376a5659528))


### Documentation

* Update README for Maven Central availability ([1d6b1bd](https://github.com/admoai/admoai-android/admoai/admoai-android/commit/1d6b1bd26dad65909b5fe8b005cfedeb2b830d29))

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
