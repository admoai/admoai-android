# Video Ad Concepts – Single Source of Truth

This document centralizes the key concepts used by the Admoai Android Sample App. Keep this short, canonical, and authoritative. Other docs should link here instead of duplicating definitions.

---

## 1) Responsibility Rule
- The selected video player must fully apply the chosen delivery method and end‑card mode end‑to‑end.
  - VAST (tag or XML) → A VAST‑capable player must ingest VAST and fire tracking automatically (IMA).
  - JSON → Regardless of player, tracking is manual (no VAST SDK auto‑tracking).

---

## 2) Delivery Methods

| Delivery     | VAST object        | `videoAsset` present | Tracking location |
|--------------|--------------------|----------------------|-------------------|
| `json`       | `null`             | ✅ Yes               | JSON response     |
| `vast_tag`   | `{ tagUrl }`       | ❌ No                | VAST XML (remote) |
| `vast_xml`   | `{ xmlBase64 }`    | ❌ No                | VAST XML (decoded)|

Rules:
- `json` → `vast` must be `null` and `videoAsset` must be present.
- `vast_tag`/`vast_xml` → `videoAsset` must NOT be present; tracking comes from VAST XML.
- `posterImage` is always present (all deliveries).

Terminology:
- "VAST Tag" = URL endpoint that returns VAST XML.
- "VAST XML" = The XML document containing tracking, media files, companions, etc.

---

## 3) End‑Card Modes
- None: Video only.
- Native End‑Card: Publisher draws overlay using `companion*` keys from JSON, using `overlayAtPercentage` to determine when to show it.
- VAST Companion: From `<CompanionAds>` in VAST XML (applies to VAST deliveries).

Hybrid: VAST video + native end‑card overlay is allowed.

---

## 4) Content Keys

Canonical (non‑editable):
- `posterImage` – Always present.
- `videoAsset` – Direct video URL (JSON only).
- `isSkippable` / `skipOffset` – Skip button configuration.

Skippable rules:
- If `isSkippable: true` then skip tracking must exist.
  - JSON: include `{ key: "skip", url: "..." }` in `tracking.videoEvents[]`.
  - VAST: include `<Tracking event="skip">` and `skipoffset` on `<Linear>`.

User‑defined (editable convention):
- `companionHeadline`, `companionCta`, `companionDestinationUrl` – Native end‑card content.
- `overlayAtPercentage` – When to show overlay (0.0–1.0).
- `showClose` – Close button visibility.

---

## 5) Player Capability Matrix

- **Media3 ExoPlayer + IMA**
  - VAST Tag: IMA auto-handles fetch/parse/tracking. Custom skip/companion overlays.
  - VAST XML: Manual parse (regex), IMA playback. Custom overlays.
  - JSON: Direct playback, manual SDK tracking. Custom overlays.
  - End-cards: Publisher-drawn Compose overlays (all modes).
  - Skip: Custom UI overlay (all modes). IMA native skip unavailable in Compose.

- **Media3 ExoPlayer**
  - All deliveries with full manual control.
  - VAST: HTTP fetch/Base64 decode → regex parse → manual HTTP tracking.
  - JSON: Direct playback, SDK tracking.
  - End-cards/Skip: Custom Compose overlays.

- **JW Player**
  - Commercial. Full VAST/IMA/OMID support (info only).

---

## 6) Tracking Responsibility

| Player | JSON | VAST Tag | VAST XML | Custom |
|--------|------|----------|----------|--------|
| **Media3 + IMA** | SDK | IMA Auto | SDK | SDK |
| **Media3** | SDK | HTTP | HTTP | SDK |

**Quartiles**: 0%, 25%, 50%, 75%, 98% (not 100% to avoid race conditions).
**Custom events** (overlay/CTA/close): Always manual.

---

## 7) Compliance & Validation
- For VAST delivered through IMA players, the default IMA UI shows "Ad" and "Learn more" badges. These are part of IMA’s compliance and generally cannot be disabled.
  - Validation check: If those badges do not appear on VAST playback via IMA, the integration is likely incorrect.
- HTTPS is required for VAST requests inside IMA (runs in WebView). HTTP is blocked by mixed‑content policy.

---

## 8) Practical Rules of Thumb
- Always check `delivery` first; it dictates parsing and tracking strategy.
- `posterImage` is universal.
- Do not mix manual tracking into an IMA‑handled VAST flow (except custom UI events).
- JSON + any player: play `videoAsset`, do manual tracking, optionally draw a native end‑card.
- VAST + IMA players: pass `adTagUrl` or `adsResponse` (pure IMA), let IMA handle tracking.

---

## 9) Placement Selection

**File**: `/sample/.../PlacementPickerScreen.kt`

**Flow**:
1. List of available placements with preview navigation
2. Each placement has a preview screen showing realistic app context
3. Standard placements: Show ad response directly
4. Special placement (freeMinutes): Multi-step interactive flow

**Standard Preview Navigation**:
- Back button (left)
- Response Details button (right)
- Refresh button (right) - fetches new ad

---

## 10) Free Minutes Placement (Special Case)

**File**: `/sample/.../previews/FreeMinutesPreviewScreen.kt`

**UI Differences**:
- NO refresh button (user clicks prize boxes instead)
- Response Details saves last played video response
- Three clickable prize boxes with notification badges

**Two-Step Flow**:
1. Prize selection screen: User taps any box to request ad
2. Fullscreen video player: Plays hardcoded video (dev mode)

**Video Player**:
- Back button + message (top-left, immediate): "Advertiser will give you free minutes for watching this video"
- Progress bar (bottom): Orange 4dp bar, 64dp from bottom
- Both elements hide when video completes

**End-Card** (shows on video completion):
- Background: Full-screen image from S3
- Helper text + X button (top, full-width): "¡The advertiser just granted **free minutes** for watching this Video!" (bold text)
- CTA button (bottom-center): "Explore more Advertiser deals" → opens `http://example.partner.com/`

**Future**: Video URL and end-card content will come from ad response (last clicked box).

---

## 11) Native Ad Template Mapping Rules

**Critical for AI**: This section defines how ad responses map to UI components across all placements.

### 11.1) Template Structure

Every creative has:
```kotlin
creative.template.key    // e.g., "carousel3Slides", "imageWithText", "wideImageOnly"
creative.template.style  // e.g., "default", "imageLeft", "imageRight"
creative.contents[]      // Array of Content objects with {key, type, value}
```

**Mapper Location**: `/sample/.../mapper/AdTemplateMapper.kt`

---

### 11.2) Content Key Extraction

**Helper Methods**:
```kotlin
AdTemplateMapper.getContentValue(creative, "headline")      // Returns String?
AdContent.extractTextContent(creative, "headline")         // Returns String?
AdContent.extractUrlContent(creative, "posterImage")       // Returns String?
```

**⚠️ CRITICAL - Case Sensitivity**:
- Content keys are **case-sensitive**
- API may return `URLSlide1` (uppercase) vs `urlSlide1` (lowercase)
- Always check actual API response for correct casing
- Example fix: Changed `urlSlide1` → `URLSlide1` to match API

---

### 11.3) Placement-to-Template Mappings

**Routing Logic** (`AdCard.kt`):

| Placement | Template Key | Style | Component | Click-Through |
|-----------|-------------|-------|-----------|---------------|
| **home** | `wideWithCompanion` | `imageLeft`, `wideImageOnly` | `HorizontalAdCard` | ✅ Enabled |
| **search** | `imageWithText` | `imageLeft`, `imageRight` | `SearchAdCard` | ❌ Disabled |
| **menu** | `textOnly` | (none) | `MenuAdCard` | ❌ Disabled |
| **promotions** | `carousel3Slides` | `default` | `PromotionsCarouselCard` | ✅ **CTA URLs** |
| **waiting** | `carousel3Slides` | `default` | `PromotionsCarouselCard` | ✅ **CTA URLs** |
| **vehicleSelection** | `imageWithText` | `imageLeft`, `imageRight` | `SearchAdCard` | ✅ Enabled |
| **vehicleSelection** | `wideImageOnly` | `default` | `HorizontalAdCard` | ✅ Enabled |
| **rideSummary** | `standard` | (none) | `HorizontalAdCard` | ✅ Enabled |
| **freeMinutes** | (special) | (custom) | Custom flow | ✅ Enabled |

---

### 11.4) Content Keys by Template

#### **`wideWithCompanion`** (home)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text",
  "ctaText": "text",
  "clickThroughURL": "url"
}
```

#### **`imageWithText`** (search, vehicleSelection)
```json
{
  "squareImage": "url",
  "headline": "text"
}
```
**Note**: SearchAdCard expects `squareImage`, not `posterImage`

#### **`textOnly`** (menu)
```json
{
  "headline": "text",
  "description": "text"
}
```

#### **`carousel3Slides`** (promotions, waiting)
```json
{
  "imageSlide1": "url",
  "headlineSlide1": "text",
  "ctaSlide1": "text",
  "URLSlide1": "url",   // ⚠️ Note: Capital URL, not lowercase
  "imageSlide2": "url",
  "headlineSlide2": "text",
  "ctaSlide2": "text",
  "URLSlide2": "url",
  "imageSlide3": "url",
  "headlineSlide3": "text",
  "ctaSlide3": "text",
  "URLSlide3": "url"
}
```
**Click Behavior**: Clicking slide opens `URLSlideN` in browser

#### **`wideImageOnly`** (vehicleSelection)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text"
}
```
**Note**: Uses `posterImage` (not `squareImage`), rendered by `HorizontalAdCard`

#### **`standard`** (rideSummary)
```json
{
  "posterImage": "url",
  "headline": "text",
  "description": "text",
  "ctaText": "text",
  "clickThroughURL": "url"
}
```

---

### 11.5) Click-Through Rules

**Thumb Rule**: Any ad with `cta*` + `URL` or `clickThroughURL` → clicking opens browser

**Implementation**:
1. Check `AdTemplateMapper.supportsClickthrough(placementKey)`
2. If true, extract URL from content
3. Use `Intent(ACTION_VIEW)` to open in browser

**Supported Placements**:
- home, vehicleSelection, rideSummary: Full ad card clickable
- promotions, waiting: Individual carousel slides clickable
- freeMinutes: Custom CTA button

**Disabled Placements**:
- search, menu: Display only, no click-through

---

### 11.6) Component Selection Logic

**Priority Order** (in `AdCard.kt`):
```kotlin
when {
    templateKey == "wideWithCompanion" || placementKey == "home" → HorizontalAdCard
    placementKey == "search" → SearchAdCard
    placementKey == "menu" || isTextOnlyTemplate → MenuAdCard
    placementKey == "promotions" || placementKey == "waiting" || isCarouselTemplate → PromotionsCarouselCard
    templateKey == "wideImageOnly" → HorizontalAdCard  // Special case
    placementKey == "rideSummary" || isStandardTemplate → HorizontalAdCard
    else → EmptyAdCard (fallback)
}
```

---

### 11.7) Critical Implementation Notes

**⚠️ Common Pitfalls**:

1. **Case Sensitivity**
   - Always verify content key casing in API response
   - Example: `URLSlide1` not `urlSlide1`

2. **Content Key Mismatches**
   - `SearchAdCard` uses `squareImage`
   - `HorizontalAdCard` uses `posterImage`
   - Don't use wrong card for template

3. **Click Handlers**
   - Card `onClick` parameter > `.clickable()` modifier
   - Use `Card(onClick = ...)` for reliable clicks

4. **Template vs Placement**
   - Check both `template.key` AND `placementKey`
   - Some placements override template routing

5. **URL Extraction**
   - Carousel: `URLSlide1/2/3` (uppercase URL)
   - Standard: `clickThroughURL` (camelCase)
   - Always use `AdContent.extractUrlContent()`

---

### 11.8) Adding New Mappings

**Steps to add new template**:

1. **Add Template Constants** (`AdTemplateMapper.kt`):
   ```kotlin
   object TemplateType {
       const val NEW_TEMPLATE = "newTemplate"
   }
   ```

2. **Add Helper Method**:
   ```kotlin
   fun isNewTemplate(adData: AdData): Boolean {
       return getTemplateKey(adData) == TemplateType.NEW_TEMPLATE
   }
   ```

3. **Create Component** (if needed):
   - `/sample/.../components/NewTemplateCard.kt`
   - Extract content keys
   - Handle click-through if needed

4. **Update Routing** (`AdCard.kt`):
   ```kotlin
   placementKey == "newPlacement" || isNewTemplate(adData) -> {
       NewTemplateCard(...)
   }
   ```

5. **Update Documentation**:
   - Add to table in section 11.3
   - Document content keys in section 11.4
   - Update click-through rules if applicable

---

## 12) SDK Configuration

**File**: `/sdk/.../config/SDKConfig.kt`

**Timeout Settings**:
```kotlin
val networkRequestTimeoutMs: Long = 30000L  // 30 seconds
val networkConnectTimeoutMs: Long = 30000L  // 30 seconds  
val networkSocketTimeoutMs: Long = 30000L   // 30 seconds
```

**Rationale**: Increased from 10s to 30s to handle slow mock server responses.

**When to Adjust**:
- Production: Can reduce to 15-20s
- Dev/Mock: Keep at 30s
- Look for `HttpRequestTimeoutException` in logs

---

## 13) See Also
- `TESTING_INSTRUCTIONS.md` – How to test and validate.
- `VIDEO_PLAYER_FLOW_SUMMARY.md` – UI/flow and player responsibilities.
- `VIDEO_IMPLEMENTATION_ROADMAP.md` – Actionable implementation plan.
