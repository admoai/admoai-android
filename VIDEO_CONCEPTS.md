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

## 9) See Also
- `TESTING_INSTRUCTIONS.md` – How to test and validate.
- `VIDEO_PLAYER_FLOW_SUMMARY.md` – UI/flow and player responsibilities.
- `VIDEO_IMPLEMENTATION_ROADMAP.md` – Actionable implementation plan.
