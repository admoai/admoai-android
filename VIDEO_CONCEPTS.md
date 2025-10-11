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

## 5) Player Capability Matrix (Current)

- **Media3 ExoPlayer + IMA**
  - Delivery: VAST Tag (IMA auto), VAST XML (manual), JSON (manual).
  - VAST Tag: IMA SDK handles fetching, parsing, tracking automatically.
  - VAST XML: Manual parsing (regex), video playback, SDK tracking (Media3 wrapper doesn't expose `adsResponse`).
  - JSON: Direct video playback, manual SDK tracking.
  - End-cards: Always manual (publisher-drawn overlays).
  - Skippable: Custom overlay UI only. IMA's built-in skip button is unavailable due to Compose/Media3 API architecture limitations (no access to IMA's internal UI components in Compose environment).
  - Custom events: Always manual (overlay/CTA/close).

- **Media3 ExoPlayer**
  - Delivery: All (JSON, VAST Tag, VAST XML) - full manual control.
  - VAST Tag: Fetches XML via HTTP, parses `<MediaFile>` with regex, fires tracking URLs manually.
  - VAST XML: Decodes Base64, parses `<MediaFile>`, fires tracking URLs manually.
  - JSON: Direct playback, manual SDK tracking.
  - Tracking: HTTP GET for VAST tracking URLs, SDK methods for JSON.

- **JW Player** (Optional)
  - Commercial SDK with full VAST/IMA/OMID support out-of-the-box.

---

## 6) Tracking Responsibility

| Player | JSON | VAST Tag | VAST XML | Custom Events |
|--------|------|----------|----------|---------------|
| **Media3 ExoPlayer + IMA** | Manual (SDK) | IMA Auto | Manual (SDK) | Manual (SDK) |
| **Media3 ExoPlayer** | Manual (SDK) | Manual (HTTP GET) | Manual (HTTP GET) | Manual (SDK) |

**Quartile Thresholds**: start (0%), firstQuartile (25%), midpoint (50%), thirdQuartile (75%), complete (98%).

Notes:
- Complete event fires at 98% (not 100%) to avoid race conditions with player state transitions.
- Custom UI events (overlay/CTA/close) are always publisher‑fired, never automatic.

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

## 9) See Also
- `TESTING_INSTRUCTIONS.md` – How to test and validate.
- `VIDEO_PLAYER_FLOW_SUMMARY.md` – UI/flow and player responsibilities.
- `VIDEO_IMPLEMENTATION_ROADMAP.md` – Actionable implementation plan.
