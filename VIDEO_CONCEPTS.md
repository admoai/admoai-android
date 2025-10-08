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
- Native End‑Card: Publisher draws overlay using `companion*` keys from JSON.
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

- Basic Player (Simple ExoPlayer)
  - Delivery: JSON only.
  - Tracking: Manual for impressions, quartiles, skip, and custom events.

- Media3 ExoPlayer + IMA (Wrapper)
  - Delivery: VAST Tag, JSON.
  - VAST XML via `adsResponse`: Not supported by Media3 wrapper.
  - Tracking: IMA auto (for VAST), manual for JSON; publisher handles custom events (overlay/CTA/close).

- Google IMA SDK (Pure, Target)
  - Delivery: VAST Tag via `adTagUrl` AND VAST XML via `adsResponse`.
  - Requires implementing `ImaSdkFactory` flow and `VideoAdPlayer` integration.
  - Tracking: IMA auto (VAST); JSON still manual if supported by your player.

- JW Player (Optional)
  - Commercial SDK with IMA/VAST plugin support.

---

## 6) Tracking Responsibility

| Scenario       | Impression | Video Events (start/quartiles/complete) | Skip | Custom (overlay/CTA/close) |
|----------------|------------|-----------------------------------------|------|----------------------------|
| JSON           | Manual     | Manual                                  | Manual | Manual                   |
| VAST Tag/XML   | IMA Auto   | IMA Auto                                | IMA Auto | Manual                  |

Notes:
- Custom UI events are always publisher‑fired.
- For JSON, ALL video events must be fired manually.

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
