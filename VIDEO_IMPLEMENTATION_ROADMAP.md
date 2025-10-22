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

**Placement Previews** (✅ All Implemented):
- **home**: `wideWithCompanion` template, click-through enabled
- **search**: `imageWithText` template (imageLeft/imageRight), no click-through
- **menu**: `textOnly` template, no click-through
- **promotions**: `carousel3Slides` template, CTA URLs open browser
- **waiting**: `carousel3Slides` template, CTA URLs open browser
- **vehicleSelection**: Supports both `imageWithText` and `wideImageOnly` templates
- **rideSummary**: `standard` template, click-through enabled
- **freeMinutes**: Custom two-step flow (special case)

**Template Mapping** (✅ Complete):
- Comprehensive mapping rules documented in `VIDEO_CONCEPTS.md` section 11
- Support for 6 template types: `wideWithCompanion`, `imageWithText`, `textOnly`, `carousel3Slides`, `wideImageOnly`, `standard`
- Case-sensitive content key extraction
- Click-through logic for 5 placements

**Free Minutes** (✅ Special Case):
- Prize boxes with notification badges
- Fullscreen video player (ExoPlayer/Media3)
- Back button + progress bar during playback
- End-card on completion (image, text, CTA)
- Hardcoded for dev (🔄 future: from ad response)

**Recent Fixes** (Oct 2025):
- ✅ **Content Key Naming**: Changed from camelCase to snake_case (`video_asset`, `poster_image`, `is_skippable`, `skip_offset`, `companion_headline`, `companion_cta`, `companion_destination_url`, `overlay_at_percentage`)
- ✅ **Tracking Event Naming**: Changed to snake_case (`start`,`first_quartile`, `midpoint`,`third_quartile`,`complete`)
- ✅ **Skip Button Tracking Bug**: Fixed phantom quartile events after skip by setting all tracking flags before seeking (all 3 players)
- ✅ **Logging Standards**: Removed all emojis, made logs professional with structured tags `[MANUAL]`, `[AUTOMATIC]`, `[URL]`, `[Response]`
- ✅ **Repetitive Logs**: Fixed skip button logging every frame, now logs only on state change
- ✅ Removed theme toggle circles overlaying nav buttons (home, vehicleSelection, waiting)
- ✅ Fixed Vehicle Selection padding (82dp → 120dp)
- ✅ Fixed `wideImageOnly` template rendering (now uses `HorizontalAdCard`)
- ✅ Fixed carousel CTA clicks (case fix: `urlSlide1` → `URLSlide1`)
- ✅ Fixed Card click handling (use `onClick` parameter, not `.clickable()` modifier)
- ✅ Increased API timeout (10s → 30s for slow mock server)

**UI**: Material Design 3, direct navigation (no preview dialog), context-aware implementation details, italic helper texts

## Implementation Notes

See `VIDEO_CONCEPTS.md` for:
- Content keys (posterImage, videoAsset, companion*, isSkippable, etc.)
- Delivery methods (JSON/VAST Tag/VAST XML rules)
- Tracking responsibility matrix
- Player capabilities

**Key Files**:

**Video Ad Demo**:
- `/sample/.../VideoAdDemoScreen.kt` - Options UI + launch
- `/sample/.../VideoPreviewScreen.kt` - Players + playback
- `/sample/.../VideoOptionsSection.kt` - Selectors

**Placement Previews**:
- `/sample/.../PlacementPickerScreen.kt` - Placement list
- `/sample/.../previews/HomePreviewScreen.kt` - Home placement
- `/sample/.../previews/SearchPreviewScreen.kt` - Search placement
- `/sample/.../previews/MenuPreviewScreen.kt` - Menu placement
- `/sample/.../previews/PromotionsPreviewScreen.kt` - Promotions placement
- `/sample/.../previews/WaitingPreviewScreen.kt` - Waiting placement
- `/sample/.../previews/VehicleSelectionPreviewScreen.kt` - Vehicle Selection
- `/sample/.../previews/RideSummaryPreviewScreen.kt` - Ride Summary
- `/sample/.../previews/FreeMinutesPreviewScreen.kt` - Free Minutes (special)

**Components**:
- `/sample/.../components/PreviewNavigationBar.kt` - Navigation (conditional refresh)
- `/sample/.../components/AdCard.kt` - Template routing logic
- `/sample/.../components/HorizontalAdCard.kt` - Wide cards (home, rideSummary, wideImageOnly)
- `/sample/.../components/SearchAdCard.kt` - Image+text cards (search, vehicleSelection)
- `/sample/.../components/MenuAdCard.kt` - Text-only cards (menu)
- `/sample/.../components/PromotionsCarouselCard.kt` - Carousel (promotions, waiting)

**Mapping & Helpers**:
- `/sample/.../mapper/AdTemplateMapper.kt` - Template detection & helpers
- `/sample/.../model/AdContent.kt` - Content extraction utilities

**SDK Configuration**:
- `/sdk/.../config/SDKConfig.kt` - Timeout settings (30s)

**Shared**:
- `/sample/.../MainViewModel.kt` - State + tracking

**Mock Server**: `https://10.0.2.2:8080` (HTTPS with self-signed cert)

## Next Steps (Future)

**Free Minutes Integration**:
1. Replace hardcoded video URL with ad response content
2. Replace hardcoded end-card with ad response content
3. Save last played video response to Response Details
4. Add template mapping rules for Free Minutes ad format

**Template Expansion**:
- Additional templates can follow pattern in `VIDEO_CONCEPTS.md` section 11.8
- Add new constants to `AdTemplateMapper.kt`
- Create component if needed
- Update routing in `AdCard.kt`
- Document in section 11

---

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions (★ see section 11 for mapping rules)
- `TESTING_INSTRUCTIONS.md` - Setup & tests (includes debugging guide)
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details (see section 8 for common fixes)
