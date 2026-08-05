# Admoai Android SDK

The Admoai Android SDK is a lightweight wrapper around the Decision Engine API, enabling Android applications to request, render, and track native and video advertisements with advanced targeting capabilities.

## Features

- **Native Ads** – Multiple template types (wide, image+text, text-only, carousel)
- **Video Ads** – JSON, VAST Tag, and VAST XML delivery methods
- **Journey Ads** – Multi-stage, single-advertiser takeovers across a user session
- **Rich Targeting** – Geo, location, destination, and custom key-value targeting
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

---

## Quick Start

### 1. Initialize the SDK

```kotlin
val config = SDKConfig(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",         // Required for Journey Ads and the format filter
    enableLogging = true,              // Optional: for debugging
    networkRequestTimeoutMs = 30000L   // Optional: 30s timeout
)

Admoai.initialize(sdkConfig = config)
val sdk = Admoai.getInstance()
```

`initialize` takes no `Context`. There is also a shorthand overload, which is the most convenient way to set a
Journey session id up front:

```kotlin
Admoai.initialize(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",
    enableLogging = false,
    defaultLanguage = "en",
    sessionId = null                   // see Journey Ads
)
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
    .addGeoTarget(id = 2643743)  // London
    .addCustomTarget(key = "category", value = "news")
    .build()

// Request ads (returns Flow<DecisionResponse>, emitting once)
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
    .setUserConsent(gdpr = true)                 // or setUserConsent(Consent(gdpr = true))
    
    // Targeting
    .addGeoTarget(id = 2643743)
    .addLocationTarget(latitude = 37.7749, longitude = -122.4194)
    .addDestinationTarget(latitude = 37.7749, longitude = -122.4194, minConfidence = 0.7)
    .addCustomTarget(key = "category", value = "news")
    
    // Journey Ads (see the Journey Ads section)
    .setSessionId("3f1c-…")                      // overrides the sticky session id
    .setJourneyOpt(JourneyOpt.OPT_OUT)
    
    // Data collection
    .disableAppCollection()
    .disableDeviceCollection()
    
    .build()
```

Every setter has a matching clear: `clearGeoTargeting()`, `clearLocationTargeting()`,
`clearDestinationTargeting()`, `clearCustomTargeting()`, `clearTargeting()`, `clearPlacements()`,
`clearUser()`, `clearSessionId()`, `clearJourneyOpt()`, and `clearAll()`. Plural setters
(`setGeoTargets`, `setLocationTargets`, `setDestinationTargets`, `setCustomTargets`, `setPlacements`)
replace the whole list instead of appending.

`clearAll()` resets a builder for reuse: it drops placements, targeting and user, stops automatic
app and device collection, and clears `journeyOpt`. It deliberately **keeps** the sticky
`sessionId` — that is session-scoped state, not per-request state, so a journey survives a builder
reset. Call `clearSessionId()` to drop it explicitly. Re-enable collection with a fresh builder from
`createRequestBuilder()`.

> Matches the iOS and Flutter SDKs exactly, so the same call puts the same request on the wire on
> every platform.

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
│               ├── journey: JourneyInfo?       // Journey metadata (null on normal ads)
│               └── verificationScriptResources: List<VerificationScriptResource>?  // OM verification data
├── errors: List<Error>?
└── warnings: List<Warning>?
```

Every field is optional and decoded tolerantly: an unknown or missing value becomes `null` rather than
throwing, so a future server change can never break an older SDK build.

---

## Journey Ads

A **Journey Ad** is a single-advertiser takeover that unfolds across several screens of one user session.
Instead of an independent decision per placement, one advertiser holds the journey: the Decision Engine walks
the user through an ordered set of **stages**, remembers how far they got, and suppresses competing ads on the
placements it owns until the journey ends.

All of that logic — eligibility, which stage serves next, takeover protection, frequency capping, completion,
billing — belongs to the **engine**. The SDK's role is deliberately narrow:

| The SDK does | The SDK never does |
|---|---|
| Forwards the `sessionId` and `journeyOpt` you set | Generate, rotate, or persist a `sessionId` for you |
| Surfaces read-only journey metadata on the served creative | Decide which stage serves, or advance the journey |
| Fires exactly the tracking URLs the engine returned | Rebuild, rewrite, or auto-fire tracking URLs |

### What is new for you: one session, every call

This is the first Admoai feature where a **sequence** of requests behaves differently from a set of unrelated
ones. A journey can only progress if the engine can tell that two requests belong to the same session, and the
only thing that tells it that is your `sessionId`.

So the one new obligation is: **every decision request during a user's session must carry the same
`sessionId`.**

- Send a *different* value and the engine sees a new visitor — the journey restarts at stage 1.
- Send *none* and journeys never activate — you get normal ads. That is a safe, valid default.

### What Journey Ads require

`apiVersion = "2025-11-01"` or later. Without it the engine ignores the journey fields completely and serves
normal ads — silently, with no error.

```kotlin
Admoai.initialize(
    baseUrl = "https://api.admoai.com",
    apiVersion = "2025-11-01",
    sessionId = "3f1c-…"          // your own value; see the rules below
)
```

### The `sessionId` rules

| Rule | Why it matters |
|---|---|
| **You own the value** | The SDK never invents one. You decide what it is and when it changes. |
| **Identical for the whole session** | Progression depends on it. One differing request restarts the journey. |
| **Sticky by default** | Set it once; every builder from `createRequestBuilder()` is seeded with it. |
| **It is not a user id** | It identifies one *session*, not one *person*. A value that never changes means journeys can never restart; a value that changes per screen means they never progress. |
| **Max 256 bytes, trimmed** | Blank becomes absent. Longer values log a warning and are still sent. |
| **Treat it as PII** | Keep it out of your own logs, analytics, and crash reports. |

```kotlin
val sdk = Admoai.getInstance()

sdk.setSessionId("3f1c-…")   // applies to every builder created afterwards
sdk.getSessionId()           // read the current sticky value
sdk.clearSessionId()         // stop sending it (journeys stop activating)
```

Per-request override — rarely needed, and it wins over the sticky value:

```kotlin
val request = sdk.createRequestBuilder()
    .addPlacement(key = "home")
    .setSessionId("a-different-session")
    .build()
```

**When to rotate it.** Start a new value when a genuinely new session begins — a fresh app launch, a login or
logout, or the end of a business activity such as a completed trip. Do **not** rotate per screen or per
request: that is the single most common way to break journey progression.

### Opt-in and opt-out

There are three states, and the difference between *omitted* and *opt-out* is the one to internalise:

| `journeyOpt` | On the wire | Behaviour |
|---|---|---|
| not set (default) | field absent | Journeys may serve, and an active journey continues. Correct for normal traffic. |
| `JourneyOpt.OPT_IN` | `"in"` | Explicitly asks for a journey. Same permissive effect as omitting. |
| `JourneyOpt.OPT_OUT` | `"out"` | Suppresses journeys for this request **and closes any active journey** for the session. Normal ads serve instead. |

> **Common mistake:** assuming that *not* sending `journeyOpt` means "no journey". Omitting is **permissive**.
> `OPT_OUT` is the only way to keep a session out of journeys.

```kotlin
val request = sdk.createRequestBuilder()
    .addPlacement(key = "home")
    .setJourneyOpt(JourneyOpt.OPT_OUT)   // or OPT_IN; omit for default behaviour
    .build()
```

Opting out **ends** the active journey rather than pausing it. If the same session later opts in, the engine
starts a *new* journey with a new `journeyInstanceId` — the old one never resumes. The same is true after a
journey completes: completed journeys are terminal.

### Reading journey metadata

Read-only extensions on `Creative`, all in `com.admoai.sdk.utils`. On a normal ad they return `null` / `false`.

| Accessor | Returns |
|---|---|
| `isJourneyAd()` | `true` when this creative is part of a journey |
| `journeyInstanceId()` | Id of this journey run — stable across its stages |
| `journeyDefinitionKey()` | Key of the journey definition being served |
| `journeyStageKey()` / `journeyStageId()` | The stage this serve belongs to |
| `journeyStageNodeId()` | The specific node (placement + template) that served |
| `journeyDealId()` | The journey deal — constant for the whole journey |
| `journeySessionId()` | The session id the engine matched, echoed back |
| `journeyOptStatus()` | The opt state the engine applied (`JourneyOpt?`) |
| `journeyPricingModel()` / `journeyFallbackBillingMode()` | Commercial metadata, informational |
| `isJourneyCompletion()` | `true` when this serve completed the journey (see below) |
| `hasCompletionUrl()` | `true` when a completion beacon is present and must be fired |

These are for logging, debugging, and your own analytics. **Do not** drive rendering decisions off stage keys
or node ids — the engine owns progression, and hard-coding its shape will break when the journey is edited.

`creative.metadata?.impId` carries the **render-level attribution key**, minted per served creative
and present on every journey serve (`null` on normal ads). Use it to reconcile a specific render
against reporting. It is not a substitute for the tracking token — the encrypted `e=` token stays
authoritative server-side — and the SDK derives nothing from it. `metadata` also carries
`skipOffsetSeconds` and `endCardMode` for video creatives.

### Completion

A journey ends in one of two mutually exclusive ways, chosen per campaign by the engine. You do not pick, but
you must handle both:

**1. `custom_event` — you fire a beacon.** The creative carries a completion beacon and completion is only
recorded when you fire it. This is what bills the campaign, so a missed fire is lost revenue.

```kotlin
if (creative.hasCompletionUrl()) {
    // Fire once, when the action the campaign is paying for actually happens
    // (ride booked, order placed, …) — not on render.
    sdk.fireCompletion(creative.tracking, key = "journey_complete")
}
```

`fireCompletion` is a safe no-op when there is no completion beacon, so calling it unconditionally will not
double-count — but it also will not tell you anything, which is why `hasCompletionUrl()` exists.

**2. `final_stage` — the engine records it.** Completion is marked server-side at decision time and there is
**no** beacon to fire. Detect it if you want to react in your UI:

```kotlin
if (creative.isJourneyCompletion()) {
    // Final stage of the journey served. Fire only the normal impression.
}
```

Fire the normal impression in both cases. The completion beacon is *additional*, never a replacement.

### No-ad is a valid, expected outcome

Because a takeover holds its placements for one advertiser, a journey-owned placement may return **no ad**
rather than a competing brand. This is correct behaviour, not an error and not a fill failure.

```kotlin
val adData = response.data?.firstOrNull()
if (adData == null || adData.isNoAd()) {
    // Render nothing, collapse the slot. Do NOT substitute your own or another network's ad,
    // and do NOT retry in a loop — the placement is intentionally held.
}
```

Treat `creatives` being `[]`, `null`, or absent identically; `isNoAd()` / `hasCreative()` already do.

### Worked example: a ride-hailing session

One session id, three screens, one journey. Note that nothing about the journey is steered by the app — it
simply keeps sending the same session id.

```kotlin
// ── App start: one session id for this whole ride ─────────────────────────────
class RideSession(private val sdk: Admoai = Admoai.getInstance()) {

    fun begin() {
        // Your own value. A UUID per app launch or per trip is a good default.
        sdk.setSessionId(UUID.randomUUID().toString())
    }

    fun end() {
        // The trip is over: the next ride must be a new journey.
        sdk.clearSessionId()
    }
}

// ── Screen 1: choosing a vehicle ─────────────────────────────────────────────
suspend fun loadVehicleScreenAd(sdk: Admoai): Creative? {
    val request = sdk.createRequestBuilder()
        .addPlacement(key = "vehicleSelection")   // sessionId is already seeded
        .build()

    val response = sdk.requestAds(request).first()
    val adData = response.data?.firstOrNull() ?: return null
    if (adData.isNoAd()) return null              // takeover may hold this slot

    val creative = adData.creatives?.firstOrNull() ?: return null
    sdk.fireImpression(creative.tracking)         // always, journey or not
    return creative
}

// ── Screen 2 and 3: same session, later stages ───────────────────────────────
// Identical code with key = "journey" and key = "rideSummary". The engine advances
// the stage because the session id matches; the app does not track stages at all.

// ── When the ride is booked: the action the campaign pays for ─────────────────
fun onRideBooked(sdk: Admoai, creative: Creative) {
    if (creative.hasCompletionUrl()) {
        sdk.fireCompletion(creative.tracking, key = "journey_complete")
    }
}
```

### Worked example: honouring a personalisation toggle

A user who turns off personalised advertising should still see normal ads — so opt out of journeys rather than
stopping requests.

```kotlin
val builder = sdk.createRequestBuilder().addPlacement(key = "home")

if (!userAllowsPersonalisedAds) {
    builder.setJourneyOpt(JourneyOpt.OPT_OUT)   // ends any active journey; normal ads still serve
}

val response = sdk.requestAds(builder.build()).first()
```

### Journey ads with video

Journey creatives support the same three delivery modes as normal ads (`json`, `vast_tag`, `vast_xml`) — see
[Video Ad Support](#video-ad-support). One rule bears repeating because it causes double-billing:

> For `vast_tag` and `vast_xml`, impression, quartile, and click beacons live **inside the VAST document** and
> belong to your player. Do not also fire `creative.tracking` for those events. The SDK never auto-fires
> anything, so this is entirely under your control.

### Common mistakes

| Mistake | Consequence | Do this instead |
|---|---|---|
| A new `sessionId` per request or per screen | Journey restarts constantly; stages never advance | One value per user session |
| Reusing a user id as the session id forever | Journeys never restart for returning users | Rotate when a new session begins |
| Omitting `journeyOpt` to mean "no journeys" | Journeys still serve — omitting is permissive | Send `OPT_OUT` explicitly |
| Substituting your own ad on a journey no-ad | Breaks the takeover the advertiser paid for | Collapse the slot |
| Skipping `fireCompletion` on a `custom_event` campaign | Completion never records — lost revenue | Fire it when the paid action happens |
| Firing `creative.tracking` for VAST journey ads | Double-counted impressions | Let the player own VAST beacons |
| Branching UI on `journeyStageKey()` | Breaks whenever the campaign is edited | Treat metadata as read-only telemetry |

### Verifying your integration

Two checks catch nearly every integration bug:

1. **Log `journeySessionId()` and `journeyInstanceId()` across a full session.** The instance id must stay
   constant while the user moves through screens. If it changes, your session id is changing.
2. **Confirm the stage advances.** `journeyStageKey()` should move forward across successive screens and never
   repeat. A repeated stage, or a sudden no-ad on every placement, means the engine is seeing a new session.

---

## Event Tracking

The SDK fires tracking beacons as HTTP GETs. Every `fire*` method is **fire-and-forget**: it returns `Unit`,
dispatches on the SDK's own scope, and never throws into your call site. A failed beacon is logged (when
logging is enabled) rather than surfaced, so do not build retry logic around a return value.

The SDK fires **only** what you ask it to — nothing is ever fired automatically.

### Available Methods

```kotlin
// Impressions (fired when the ad is displayed)
sdk.fireImpression(trackingInfo, key = "default")

// Clicks (fired on user tap)
sdk.fireClick(trackingInfo, key = "default")

// Video events (JSON delivery only)
sdk.fireVideoEvent(trackingInfo, key = "start")

// Custom events
sdk.fireCustomEvent(trackingInfo, key = "companionOpened")

// Journey completion — custom_event campaigns only, see Journey Ads
sdk.fireCompletion(trackingInfo, key = "journey_complete")

// Any URL the engine handed you, fired verbatim
sdk.fireTracking(url)
```

### Tracking Keys

Each tracking type supports multiple keys. Use `"default"` for standard events, or a custom key defined in your
campaign configuration. A key that is not present in the list is a no-op with a warning, never a crash.

Tracking URLs are **opaque**: identity is carried inside an encrypted token. Fire them exactly as received —
never parse, rebuild, or append to them.

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

Helpers in `com.admoai.sdk.utils` read the same data more safely:

```kotlin
creative.isJsonDelivery()      // delivery == "json"
creative.isVastTagDelivery()   // delivery == "vast_tag"
creative.isVastXmlDelivery()   // delivery == "vast_xml"

creative.getVastTagUrl()       // optional mediaType / mediaDelivery filters
creative.getVastXmlBase64()

creative.isSkippable()         // Boolean
creative.getSkipOffset()       // String?, e.g. "5"
```

`isSkippable()` and `getSkipOffset()` read the engine-owned `creative.metadata` first
(`metadata?.isSkippable`, `metadata?.skipOffsetSeconds`), then fall back to the creative's content
fields — accepting either `is_skippable`/`skip_offset` or the camelCase spellings, because the
template field names are author-controlled. For a typed value read
`creative.metadata?.skipOffsetSeconds` (`Int?`) directly; `getSkipOffset()` returns a `String?` for
backwards compatibility.

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

> **Tip**: For VAST-based ads you may optionally integrate a third-party VAST SDK (e.g. Google IMA) for
> automatic tracking and Open Measurement viewability. That is outside the scope of this SDK, but it is
> demonstrated in the [Sample App](../sample/README.md).

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

> [!WARNING]
> **OM Certification Notice**: The Admoai SDK provides Open Measurement verification data as received from the
> ad server, but **the SDK itself is not OM certified**. Publishers must ensure their implementation with
> third-party verification providers (such as IAS or DoubleVerify) complies with Open Measurement standards and
> requirements. Admoai acts as a strict ad server only; publishers are responsible for the proper
> implementation of their OM integration.

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
