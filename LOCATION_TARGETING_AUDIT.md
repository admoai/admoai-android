# Location Targeting - Feature Audit

Quick reference for implementing Destination Targeting by mirroring Location Targeting patterns.

---

## SDK Core Files

| File | Path | Key Elements |
|------|------|--------------|
| **LocationTargetingInfo.kt** | `sdk/.../model/request/` | Data class: `latitude: Double, longitude: Double` |
| **Targeting.kt** | `sdk/.../model/request/` | Property: `location: List<LocationTargetingInfo>?` (line 14) |
| **DecisionRequestBuilder.kt** | `sdk/.../model/request/` | Methods: `addLocationTarget()` (45-49), `setLocationTargets()` (51-53), validation (87-89) |
| **Admoai.kt** | `sdk/.../` | Merging logic in `prepareFinalDecisionRequest()` (108-118) |
| **DecisionRequest.kt** | `sdk/.../model/request/` | Contains `targeting: Targeting?` property (19-29) |

---

## Sample App Files

| File | Path | Key Elements |
|------|------|--------------|
| **UIModels.kt** | `sample/.../ui/model/` | `LocationItem(id, latitude, longitude)` (21-28) |
| **MainViewModel.kt** | `sample/.../ui/` | State: `_locationTargets` (94), Methods: `addLocationTarget()` (350), `updateLocationTargets()` (358), `addRandomLocation()` (365), `clearLocationTargets()` (375), Builder integration (498-504) |
| **LocationTargetingScreen.kt** | `sample/.../ui/screens/` | Full screen UI with list, add/remove, empty state (45-200) |
| **TargetingSection.kt** | `sample/.../ui/components/` | Summary row with count display (67-86) |
| **DecisionRequestScreen.kt** | `sample/.../ui/screens/` | Parameter: `onLocationTargetingClick` (51, 130) |
| **MainActivity.kt** | `sample/.../` | Route: `LOCATION_TARGETING` (103), Navigation (167-212) |

---

## Tests

| File | Test Cases |
|------|-----------|
| **DecisionRequestBuilderTest.kt** | `add location target` (164-174), `set location targets` (177-186) |
| **TargetingTest.kt** | Integration test with location data (36-69) |

---

## Documentation

| File | References |
|------|-----------|
| **sdk/README.md** | Example with `.addLocationTarget()` (line 81, 210) |
| **sample/README.md** | Navigation flow (60), Features table (102) |
| **CHANGELOG.md** | Initial release mention (line 40) |

---

## Implementation Checklist for Destination Targeting

### SDK Core
- [ ] Create `DestinationTargetingInfo.kt` file in `sdk/.../model/request/` package
- [ ] Add package declaration `package com.admoai.sdk.model.request` to `DestinationTargetingInfo.kt`
- [ ] Add import `import kotlinx.serialization.Serializable` to `DestinationTargetingInfo.kt`
- [ ] Create `DestinationTargetingInfo` data class with fields: `latitude: Double`, `longitude: Double`, `minConfidence: Double`
- [ ] Add `@Serializable` annotation to `DestinationTargetingInfo` data class
- [ ] Add KDoc comments to `DestinationTargetingInfo.kt` explaining the purpose and parameters (similar to LocationTargetingInfo pattern)
- [ ] Consider minConfidence validation (should be between 0.0 and 1.0) in data class or builder
- [ ] Add `destination` property to `Targeting.kt` as `destination: List<DestinationTargetingInfo>? = null`
- [ ] Update KDoc in `Targeting.kt` to document the destination property
- [ ] Add `addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double)` to `DecisionRequestBuilder.kt`
- [ ] Add `setDestinationTargets(destinations: List<DestinationTargetingInfo>)` to `DecisionRequestBuilder.kt`
- [ ] Implement proper list handling in `addDestinationTarget()` (mutableList pattern like location - create mutableList from targeting.destination, add new item, update targeting with copy)
- [ ] Update validation in `DecisionRequestBuilder.build()` around line 87 - add `|| !it.destination.isNullOrEmpty()` to the finalTargeting takeIf condition
- [ ] Update merging logic in `Admoai.prepareFinalDecisionRequest()` around line 113 - add `|| requestTargeting?.destination != null` to the mergedTargeting condition
- [ ] Update merging logic in `Admoai.prepareFinalDecisionRequest()` around line 115-117 - add `destination = requestTargeting?.destination` to the Targeting constructor

### Sample App - Core
- [ ] Create `DestinationItem` data class in `UIModels.kt` with fields: `id: String`, `latitude: Double`, `longitude: Double`, `minConfidence: Double`
- [ ] Ensure `id` field in `DestinationItem` has default value: `id: String = java.util.UUID.randomUUID().toString()` (following LocationItem pattern)
- [ ] Add `DestinationTargetingInfo` import to `MainViewModel.kt` (import section alongside `LocationTargetingInfo`, `CustomTargetingInfo`)
- [ ] Add `DestinationItem` import to `MainViewModel.kt`
- [ ] Verify `java.util.UUID` import exists in `MainViewModel.kt` (may already be present for LocationItem)
- [ ] Add `_destinationTargets` state flow in `MainViewModel.kt`: `MutableStateFlow<List<DestinationItem>>(emptyList())`
- [ ] Add `destinationTargets` public state flow in `MainViewModel.kt`: `StateFlow<List<DestinationItem>>` using `.asStateFlow()` pattern
- [ ] Add `addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double)` method in `MainViewModel.kt`
- [ ] Implement ID generation in `addDestinationTarget()` using `java.util.UUID.randomUUID().toString()` pattern (like LocationItem)
- [ ] Add `updateDestinationTargets(destinations: List<DestinationItem>)` method in `MainViewModel.kt`
- [ ] Add `addRandomDestination()` method in `MainViewModel.kt` (generate random lat/lon in range -90 to 90 for lat, -180 to 180 for lon)
- [ ] Generate random minConfidence in `addRandomDestination()` in range 0.0 to 1.0 using `Math.random()`
- [ ] Add `clearDestinationTargets()` method in `MainViewModel.kt`
- [ ] Add destination targets to `buildRequest()` method after custom targets (around line 509-513) with code: `if (_destinationTargets.value.isNotEmpty()) { val destinations = _destinationTargets.value.map { DestinationTargetingInfo(latitude = it.latitude, longitude = it.longitude, minConfidence = it.minConfidence) }; builder.setDestinationTargets(destinations) }`
- [ ] Add destination targets to `updatePlacementPreviewJson()` method after custom targets (around line 547-553) with same mapping pattern as buildRequest
- [ ] Add destination targets to `loadAds()` method by ensuring `buildRequest()` includes them (already covered by buildRequest implementation)

### Sample App - UI Screen
- [ ] Create `DestinationTargetingScreen.kt` file in `sample/.../ui/screens/` (full path: `sample/src/main/java/com/admoai/sample/ui/screens/DestinationTargetingScreen.kt`)
- [ ] Add required imports to `DestinationTargetingScreen.kt`: `androidx.compose.foundation.layout.*`, `androidx.compose.foundation.lazy.*`, `androidx.compose.material3.*`, `androidx.compose.runtime.*`, `androidx.compose.ui.*`, `com.admoai.sample.ui.MainViewModel`, `com.admoai.sample.ui.model.DestinationItem`, `kotlin.math.roundToInt`
- [ ] Add required icon imports to `DestinationTargetingScreen.kt`: `Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Default.Add`, `Icons.Default.Delete`, and appropriate destination icon (e.g., `Icons.Default.Place`, `Icons.Outlined.Flight`)
- [ ] Add `@OptIn(ExperimentalMaterial3Api::class)` annotation to `DestinationTargetingScreen` composable function
- [ ] Accept `viewModel: MainViewModel` parameter in `DestinationTargetingScreen` composable
- [ ] Accept `onNavigateBack: () -> Unit` parameter in `DestinationTargetingScreen` composable
- [ ] Collect `destinationTargets` state in `DestinationTargetingScreen`: `destinationTargets by viewModel.destinationTargets.collectAsState()`
- [ ] Implement Scaffold structure with topBar, content using paddingValues parameter
- [ ] Add TopAppBar with navigationIcon using IconButton and Icons.AutoMirrored.Filled.ArrowBack calling onNavigateBack
- [ ] Update screen title to "Destination Targeting" in TopAppBar
- [ ] Add clear all button in TopAppBar actions when destinations list is not empty (IconButton with Delete icon calling `viewModel.clearDestinationTargets()`)
- [ ] Add descriptive text explaining destination targeting with minConfidence field (style: MaterialTheme.typography.bodySmall, color: MaterialTheme.colorScheme.onSurfaceVariant, padding: 16.dp)
- [ ] Use Column with Modifier.fillMaxSize().padding(paddingValues) for main content
- [ ] Add "Add Random Destination" Button with Add icon, centered with padding(horizontal = 16.dp, vertical = 8.dp)
- [ ] Wire up button onClick to call `viewModel.addRandomDestination()`
- [ ] Implement empty state UI when no destinations are added (Box with fillMaxWidth().weight(1f), contentAlignment = Alignment.Center)
- [ ] Display "No destinations added yet" text in empty state (style: MaterialTheme.typography.bodyLarge, color: MaterialTheme.colorScheme.onSurfaceVariant)
- [ ] Implement LazyColumn with `rememberLazyListState()` stored in a `state` variable
- [ ] Apply modifiers to LazyColumn: Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)
- [ ] Apply verticalArrangement to LazyColumn: `verticalArrangement = Arrangement.spacedBy(8.dp)` for proper item spacing between cards
- [ ] Use items(destinationTargets) { destinationItem -> ... } to iterate through destination list
- [ ] Create `DestinationTargetItem` composable function in `DestinationTargetingScreen.kt` with parameters: `destination: DestinationItem`, `onRemove: () -> Unit`
- [ ] Apply Card modifier to destination items: Modifier.fillMaxWidth().padding(vertical = 4.dp)
- [ ] Use Row inside Card with Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
- [ ] Add destination Icon with tint = MaterialTheme.colorScheme.primary and Modifier.padding(end = 16.dp)
- [ ] Add Column with Modifier.weight(1f) to display destination details
- [ ] Display latitude as "Lat: ${(destination.latitude * 1000).roundToInt() / 1000.0}" (rounds to 3 decimal places)
- [ ] Display longitude as "Lng: ${(destination.longitude * 1000).roundToInt() / 1000.0}" (rounds to 3 decimal places)
- [ ] Display minConfidence as "Confidence: ${(destination.minConfidence * 1000).roundToInt() / 1000.0}" (rounds to 3 decimal places)
- [ ] Use Text with style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis for each field
- [ ] Add TextButton for remove action with Modifier.width(100.dp)
- [ ] Add Delete icon and "Remove" text to TextButton with icon padding(end = 4.dp)
- [ ] Implement onRemove by calling `viewModel.updateDestinationTargets()` with filtered list using ID comparison: `viewModel.updateDestinationTargets(destinationTargets.filter { it.id != destination.id })`
- [ ] Import `DestinationTargetingScreen` in `MainActivity.kt`
- [ ] Add `DESTINATION_TARGETING` route constant to `Routes` object in `MainActivity.kt` (e.g., `const val DESTINATION_TARGETING = "destination_targeting"`)
- [ ] Add `onDestinationTargetingClick: () -> Unit = {}` parameter to `DecisionRequestScreen` composable function signature in `DecisionRequestScreen.kt` (alongside existing `onLocationTargetingClick`, `onGeoTargetingClick`, etc.)
- [ ] Add `onDestinationTargetingClick` handler in `MainActivity.kt` navigation lambda: `{ navController.navigate(Routes.DESTINATION_TARGETING) }`
- [ ] Pass `onDestinationTargetingClick` from `MainActivity.kt` to `DecisionRequestScreen` composable in NavHost route (around line 142-184)
- [ ] Add `composable(Routes.DESTINATION_TARGETING)` route in NavHost with DestinationTargetingScreen composable
- [ ] Pass `viewModel` and `onNavigateBack = { navController.navigateUp() }` to DestinationTargetingScreen in route
- [ ] Pass `onDestinationTargetingClick` to `TargetingSection` in `DecisionRequestScreen.kt` (around line 127-132)
- [ ] Add `onDestinationTargetingClick: () -> Unit = {}` parameter to `TargetingSection.kt` signature
- [ ] Collect `destinationTargets` state in `TargetingSection.kt`: `val destinationTargets by viewModel.destinationTargets.collectAsState()`
- [ ] Add destination icon import to `TargetingSection.kt` (e.g., `Icons.Outlined.Flight` or appropriate destination icon)
- [ ] Create destination targeting row in `TargetingSection.kt` using `SectionRow` composable
- [ ] Display destination count in TargetingSection row: `if (destinationTargets.isEmpty()) "None" else "${destinationTargets.size} destinations"`
- [ ] Add navigation arrow icon to destination targeting row (Icons.AutoMirrored.Filled.KeyboardArrowRight)
- [ ] Wire up `onClick = onDestinationTargetingClick` in destination targeting row

### Tests
- [ ] Add `add destination target` test to `DecisionRequestBuilderTest.kt` at path `sdk/src/test/kotlin/com/admoai/sdk/model/request/DecisionRequestBuilderTest.kt` (verify single destination added correctly with latitude, longitude, and minConfidence)
- [ ] Add `set destination targets` test to `DecisionRequestBuilderTest.kt` (verify list replacement works correctly)
- [ ] Add `build includes targeting if only destination is set` test to `DecisionRequestBuilderTest.kt` (verify targeting object is created when ONLY destination is populated and geo/location are null - this validates the condition `|| !it.destination.isNullOrEmpty()` works correctly)
- [ ] Add `addDestinationTarget multiple times` test to `DecisionRequestBuilderTest.kt` (verify multiple destination targets can be added and are accumulated in the list)
- [ ] Add integration test with destination data to `TargetingTest.kt` at path `sdk/src/test/kotlin/com/admoai/sdk/TargetingTest.kt` (full request-response cycle with destination targeting)
- [ ] Verify minConfidence values are properly serialized and included in the request in integration test

### Documentation
- [ ] Update `sdk/README.md` - add example showing destination targeting with minConfidence field near existing `.addLocationTarget()` examples (around line 81 and 210)
- [ ] **IMPORTANT**: Use real minConfidence values in examples like `minConfidence = 0.8` or `minConfidence = 0.95` to clearly demonstrate the 0.0-1.0 range (NOT placeholder values)
- [ ] Add complete method documentation to `sdk/README.md` showing both methods:
  - `.addDestinationTarget(latitude = 37.7749, longitude = -122.4194, minConfidence = 0.8)` - adds single destination
  - `.setDestinationTargets(destinations)` - replaces entire destination list
- [ ] Add explanation of minConfidence parameter: "minConfidence specifies the minimum confidence level (0.0-1.0) required for the destination prediction"
- [ ] Update `sample/README.md` - add destination targeting to navigation flow diagram section (around line 60) showing: Decision Request Screen → Destination Targeting Screen
- [ ] Update `sample/README.md` - add destination targeting row to features table (around line 102) with clear description: "Destination Targeting - Target predicted destinations with confidence thresholds (0.0-1.0)"
- [ ] Add entry to `CHANGELOG.md` in appropriate version section with feature description: "Added destination targeting with minimum confidence parameter"

