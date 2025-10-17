# Video Implementation Status

## ✅ Implementation Complete

Video Demo ready. See `VIDEO_CONCEPTS.md` for canonical reference.

## Current Features

**Video Ad Demo**:
- **Deliveries**: JSON, VAST Tag, VAST XML  
- **Players**: Media3+IMA (VAST Tag auto, rest manual), Media3 (all manual), JW (info)  
- **End-cards**: None, Native (companion* keys), VAST Companion (<CompanionAds>)  
- **Skip**: Custom overlay UI (all modes)  
- **Tracking**: Quartiles 0/25/50/75/98%, custom events (overlay/CTA/close)

**Placement Previews**:
- List of placements with realistic app context previews
- Standard navigation: Back, Response Details, Refresh
- Special case (freeMinutes): Two-step flow, no refresh button
- Video-friendly placements marked with badges

**Free Minutes** (Special):
- Prize boxes with notification badges
- Fullscreen video player (ExoPlayer/Media3)
- Back button + progress bar during playback
- End-card on completion (image, text, CTA)
- Hardcoded for dev (future: from ad response)

**UI**: Material Design 3, direct navigation (no preview dialog), context-aware implementation details, italic helper texts

## Implementation Notes

See `VIDEO_CONCEPTS.md` for:
- Content keys (posterImage, videoAsset, companion*, isSkippable, etc.)
- Delivery methods (JSON/VAST Tag/VAST XML rules)
- Tracking responsibility matrix
- Player capabilities

**Files**:

Video Ad Demo:
- `/sample/.../VideoAdDemoScreen.kt` - Options UI + launch
- `/sample/.../VideoPreviewScreen.kt` - Players + playback
- `/sample/.../VideoOptionsSection.kt` - Selectors

Placement Previews:
- `/sample/.../PlacementPickerScreen.kt` - Placement list
- `/sample/.../previews/FreeMinutesPreviewScreen.kt` - Free Minutes special case
- `/sample/.../components/PreviewNavigationBar.kt` - Navigation (conditional refresh)

Shared:
- `/sample/.../MainViewModel.kt` - State + tracking

**Mock Server**: `https://10.0.2.2:8080` (HTTPS with self-signed cert)

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions
- `TESTING_INSTRUCTIONS.md` - Setup & tests
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details
