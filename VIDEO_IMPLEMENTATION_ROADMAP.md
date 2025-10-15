# Video Implementation Status

## ✅ Implementation Complete

Video Demo ready. See `VIDEO_CONCEPTS.md` for canonical reference.

## Current Features

**Deliveries**: JSON, VAST Tag, VAST XML  
**Players**: Media3+IMA (VAST Tag auto, rest manual), Media3 (all manual), JW (info)  
**End-cards**: None, Native (companion* keys), VAST Companion (<CompanionAds>)  
**Skip**: Custom overlay UI (all modes)  
**Tracking**: Quartiles 0/25/50/75/98%, custom events (overlay/CTA/close)

**UI**: Material Design 3, direct navigation (no preview dialog), context-aware implementation details, italic helper texts

## Implementation Notes

See `VIDEO_CONCEPTS.md` for:
- Content keys (posterImage, videoAsset, companion*, isSkippable, etc.)
- Delivery methods (JSON/VAST Tag/VAST XML rules)
- Tracking responsibility matrix
- Player capabilities

**Files**:
- `/sample/.../VideoAdDemoScreen.kt` - Options UI + launch
- `/sample/.../VideoPreviewScreen.kt` - Players + playback
- `/sample/.../VideoOptionsSection.kt` - Selectors
- `/sample/.../MainViewModel.kt` - State + tracking

**Mock Server**: `https://10.0.2.2:8080` (HTTPS with self-signed cert)

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions
- `TESTING_INSTRUCTIONS.md` - Setup & tests
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details
