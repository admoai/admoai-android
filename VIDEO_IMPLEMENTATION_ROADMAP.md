# Video Player Implementation Roadmap

## ✅ Analysis Complete

See: `/admoai-android/VIDEO_CONCEPTS.md` for canonical keys, delivery methods, end‑card modes, and tracking rules.

### Canonical Content Keys (Non-Editable by Users):
- **`posterImage`** - Thumbnail image URL (ALWAYS present, all delivery methods)
- **`videoAsset`** - Direct video URL (HLS/MP4) - **JSON delivery ONLY**
- **`isSkippable`** - Boolean, enables skip button
- **`skipOffset`** - Time string "00:00:05", when skip appears

### User-Defined Content Keys (Standard Convention):
- **`companion*`** prefix - Native end-card elements:
  - `companionHeadline` - Headline text
  - `companionCta` - CTA button label
  - `companionDestinationUrl` - Click destination URL
- **`overlayAtPercentage`** - Float (0.0-1.0), when overlay shows (0.5 = 50%)
- **`showClose`** - Integer/Boolean, close button visibility

### Delivery Methods & Critical Rules:
1. **`delivery: "json"`**
   - `vast` must be `null`
   - `videoAsset` must be included
   - `tracking.impressions` and `tracking.videoEvents` included in JSON
   - Manual tracking required

2. **`delivery: "vast_tag"`**
   - `vast.tagUrl` present (URL endpoint returning VAST XML)
   - `videoAsset` must NOT be included
   - `tracking.impressions` and `tracking.videoEvents` empty (handled in XML)
   - IMA SDK handles tracking automatically

3. **`delivery: "vast_xml"`**
   - `vast.xmlBase64` present (Base64-encoded VAST XML)
   - `videoAsset` must NOT be included
   - `tracking.impressions` and `tracking.videoEvents` empty (handled in XML)
   - Decode XML → Pass to IMA SDK or parse manually

### End-Card Modes:
1. **None** - Video only, no overlays
2. **Native** - Publisher draws overlay using `companion*` keys
3. **VAST Companion** - XML contains `<CompanionAds>` with size options (requires explicit template-level configuration)

**Note**: None/Native modes are for demo purposes. In production, end-cards are determined by template configuration and how the publisher interprets template fields. Only VAST Companion requires explicit template-level configuration.

### Skippable Videos:
- Skip button appears as a badge bubble in the top-right corner when `isSkippable: true`
- Button only appears once skip offset is reached (parsed from `skipOffset` in ad response, defaults to 5s)
- Skip offset supports both "00:00:05" time format and plain numbers like "5"
- Clicking skip stops video playback, sets completion state, and fires skip tracking event
- JSON delivery: uses SDK's `fireVideoEvent(creative, "skip")`
- VAST delivery: fires skip tracking URLs via HTTP GET

---

## 🎯 Implementation Plan


---

## ⚠️ Important Considerations

### VAST vs Non-VAST:
- **VAST Tag/XML**: IMA SDK handles ALL tracking automatically
- **JSON/videoAsset**: Must manually fire all tracking events
- **Hybrid (VAST + Native End Card)**: IMA handles video, we handle overlay/end card

### Content Key Priority:
1. **ALWAYS check `delivery` field first** - this determines parsing strategy
2. If `delivery: "json"` → Expect `videoAsset` in contents, parse tracking from JSON
3. If `delivery: "vast_tag"` or `"vast_xml"` → NO `videoAsset`, use IMA players, tracking in XML
4. **`posterImage` is ALWAYS present** regardless of delivery method
5. Parse `companion*` keys regardless of delivery method (for native overlays)
6. For VAST Companion mode, parse `<CompanionAds>` from XML instead of JSON keys

### Tracking Responsibility:
| Scenario | Impressions | Video Events (start, quartiles, complete) | Skip Event | Custom Events (overlay, CTA, close) |
|----------|-------------|-------------------------------------------|------------|-------------------------------------|
| JSON delivery | Manual (SDK) | Manual (SDK) | Manual (SDK) | Manual (SDK) |
| VAST Tag/XML | IMA Auto | IMA Auto | IMA Auto | Manual (SDK) |

**Key Rule for Skippable Videos:**
- When `isSkippable: true`, skip tracking MUST be included:
  - JSON: Add `{ "key": "skip", "url": "..." }` to `tracking.videoEvents[]`
  - VAST: Add `<Tracking event="skip">` to XML + `skipoffset` attribute on `<Linear>`
