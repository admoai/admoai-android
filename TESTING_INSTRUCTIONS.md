# Testing Instructions

Video Demo is ready. Test different delivery/player/endcard combinations.

---

## Quick Reference

See `/admoai-android/VIDEO_CONCEPTS.md` for canonical definitions.

**Deliveries**: JSON (videoAsset), VAST Tag (tagUrl), VAST XML (xmlBase64)  
**End-cards**: None, Native (companion* keys), VAST Companion (<CompanionAds>)  
**Players**: Media3+IMA (VAST Tag auto, rest manual), Media3 (all manual), JW (info)

---

## 📋 Prerequisites

1. ✅ HTTPS mock server with SSL certificates
2. ✅ Android app trusts self-signed certificate
3. ✅ All VAST tag URLs updated to HTTPS
4. ✅ App rebuilt and ready to install

---

## Setup

**Mock Server** (HTTPS): `https://10.0.2.2:8080`  
```bash
cd /Users/matias-admoai/Documents/repos/mock-endpoints
CERT_FILE=cert.pem KEY_FILE=key.pem PORT=8080 go run main.go
```

**Install App**:
```bash
cd /Users/matias-admoai/Documents/repos/admoai-android
./gradlew :sample:installDebug
```

**Test Flows**:

**Video Ad Demo**:  
1. Open app → "Video Ad Demo"  
2. Select delivery/endcard/player  
3. "Launch Video Demo" → plays directly (no preview mode dialog)

**Placement Previews**:
1. Open app → "Placement Picker"
2. Select placement → preview screen shows realistic context
3. Standard: Refresh button fetches new ad
4. Free Minutes: Click prize box → fullscreen video → end-card

---

## Test Matrix

**Media3 + IMA**:
- JSON: Direct play, manual SDK tracking, custom overlays  
- VAST Tag: IMA auto-track, custom overlays  
- VAST XML: Manual parse, manual SDK tracking, custom overlays

**Media3**:
- All deliveries: Manual HTTP/SDK tracking, custom overlays

**Validation**:

**Video Ad Demo**:
- Poster shows before play  
- Skip button at offset (custom UI overlay)  
- End-card at `overlayAtPercentage` or completion  
- Quartiles: 0/25/50/75/98%  
- IMA "Ad" badge for VAST Tag (validates IMA integration)

**Free Minutes Placement**:
- Prize boxes clickable with badges
- NO refresh button in navigation bar
- Video plays immediately with back button + progress bar
- End-card shows on completion (image, text, X, CTA)
- Single click on back/X button closes (no multiple clicks needed)

## Response Validation

**JSON**: `vast: null`, `videoAsset` present, tracking populated  
**VAST Tag**: `vast.tagUrl` present, NO videoAsset, tracking empty  
**VAST XML**: `vast.xmlBase64` present, NO videoAsset, tracking empty  
**All**: `posterImage` always present

## Success Criteria

- Mock data fetches from `https://10.0.2.2:8080`  
- IMA loads VAST without errors  
- Poster → video plays → tracking fires  
- No CORS/mixed-content errors

## Related Docs

- `VIDEO_CONCEPTS.md` - Canonical definitions  
- `VIDEO_PLAYER_FLOW_SUMMARY.md` - UI/flow details  
- `VIDEO_IMPLEMENTATION_ROADMAP.md` - Implementation status
