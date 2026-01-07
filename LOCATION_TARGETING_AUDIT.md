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

#### 1. Create DestinationTargetingInfo Data Class
- [ ] Create `DestinationTargetingInfo.kt` file in `sdk/.../model/request/` package
- [ ] Add package declaration `package com.admoai.sdk.model.request` to `DestinationTargetingInfo.kt`
- [ ] Add import `import kotlinx.serialization.Serializable` to `DestinationTargetingInfo.kt`
- [ ] Create `DestinationTargetingInfo` data class with fields: `latitude: Double`, `longitude: Double`, `minConfidence: Double`
- [ ] Add `@Serializable` annotation to `DestinationTargetingInfo` data class
- [ ] Add KDoc comments to `DestinationTargetingInfo.kt` explaining the purpose and parameters (similar to LocationTargetingInfo pattern)

#### 2. Update Targeting Model
- [ ] Add `destination` property to `Targeting.kt` data class constructor as `destination: List<DestinationTargetingInfo>? = null` (add after the `location` property line 14)
- [ ] Add `@property destination` KDoc tag to the class-level documentation block explaining destination targeting with minConfidence (following the same pattern as `@property location` on line 8)

#### 3. Add Builder Methods
- [ ] Add `addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double)` method to `DecisionRequestBuilder.kt` (after location targeting methods around line 54)
- [ ] Implement `addDestinationTarget()` method body with `= apply { }` wrapper pattern (mirroring lines 45-49 from location)
- [ ] Inside `addDestinationTarget()`: declare variable `val currentDestination = targeting.destination?.toMutableList() ?: mutableListOf()` (mirroring line 46 from location)
- [ ] Inside `addDestinationTarget()`: call `currentDestination.add(DestinationTargetingInfo(latitude, longitude, minConfidence))` (mirroring line 47 from location)
- [ ] Inside `addDestinationTarget()`: update targeting property `targeting = targeting.copy(destination = currentDestination)` (mirroring line 48 from location)
- [ ] Add `setDestinationTargets(destinations: List<DestinationTargetingInfo>)` method to `DecisionRequestBuilder.kt` (after addDestinationTarget)
- [ ] Implement `setDestinationTargets()` method body: `= apply { targeting = targeting.copy(destination = destinations) }` (mirroring lines 51-53 from location)

#### 4. Update Validation Logic
- [ ] Update validation in `DecisionRequestBuilder.build()` around line 87 - add `|| !it.destination.isNullOrEmpty()` to the finalTargeting takeIf condition

#### 5. Update Request Merging Logic
- [ ] Update merging logic in `Admoai.prepareFinalDecisionRequest()` around line 113 - add `|| requestTargeting?.destination != null` to the mergedTargeting condition
- [ ] Update merging logic in `Admoai.prepareFinalDecisionRequest()` around line 115-117 - add `destination = requestTargeting?.destination` to the Targeting constructor




### Sample App - Core

#### 1. Create DestinationItem Data Model
- [ ] Add DestinationItem data class in `UIModels.kt` after LocationItem (around line 29, following the existing pattern of LocationItem at lines 21-28)
- [ ] Add KDoc comment above `DestinationItem` data class in `UIModels.kt`: `/** * UI model for a destination in the targeting UI */` (following LocationItem pattern on lines 21-23)
- [ ] Create `DestinationItem` data class in `UIModels.kt` with fields: `id: String = java.util.UUID.randomUUID().toString()`, `latitude: Double`, `longitude: Double`, `minConfidence: Double` (following LocationItem pattern where id has default value)

#### 2. Add Imports to MainViewModel
- [ ] Add `DestinationTargetingInfo` import to `MainViewModel.kt` (import section alongside `LocationTargetingInfo`, `CustomTargetingInfo`)
- [ ] Add `DestinationItem` import to `MainViewModel.kt`
- [ ] Verify `java.util.UUID` import exists in `MainViewModel.kt` (may already be present for LocationItem)

#### 3. Add State Management
- [ ] Add `_destinationTargets` private state flow in `MainViewModel.kt` after `locationTargets` declaration (after line 95): `private val _destinationTargets = MutableStateFlow<List<DestinationItem>>(emptyList())` (mirroring line 94 from location)
- [ ] Add `destinationTargets` public state flow in `MainViewModel.kt` immediately after `_destinationTargets`: `val destinationTargets = _destinationTargets.asStateFlow()` (mirroring line 95 from location)

#### 4. Implement Destination Management Methods
- [ ] Add KDoc comment in `MainViewModel.kt`: `/** * Add destination target. */` (mirroring line 347-349 from location)
- [ ] Add `addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double)` method declaration in `MainViewModel.kt` (mirroring line 350 from location)
- [ ] Implement `addDestinationTarget()` method body: declare variable `val newDestination = DestinationItem(latitude = latitude, longitude = longitude, minConfidence = minConfidence)` (mirroring line 351 from location)
- [ ] In `addDestinationTarget()` method body: set `_destinationTargets.value = _destinationTargets.value + newDestination` (mirroring line 352 from location)
- [ ] Add KDoc comment in `MainViewModel.kt`: `/** * Update destination targets with a new list. */` (mirroring line 355-357 from location)
- [ ] Add `updateDestinationTargets(destinations: List<DestinationItem>)` method declaration in `MainViewModel.kt` (mirroring line 358 from location)
- [ ] Implement `updateDestinationTargets()` method body: set `_destinationTargets.value = destinations` (mirroring line 359 from location)
- [ ] Add KDoc comment in `MainViewModel.kt`: `/** * Add a random destination for demo purposes. */` (mirroring line 362-364 from location)
- [ ] Add `addRandomDestination()` method declaration in `MainViewModel.kt` (mirroring line 365 from location)
- [ ] Implement `addRandomDestination()` method body: add comment `// Generate random coordinates around the world` (mirroring line 366 from location)
- [ ] In `addRandomDestination()` method body: generate random latitude `val latitude = (Math.random() * 180.0) - 90.0` (mirroring line 367 from location)
- [ ] In `addRandomDestination()` method body: generate random longitude `val longitude = (Math.random() * 360.0) - 180.0` (mirroring line 368 from location)
- [ ] In `addRandomDestination()` method body: generate random minConfidence `val minConfidence = Math.random()` (new field, same pattern as latitude/longitude)
- [ ] In `addRandomDestination()` method body: call `addDestinationTarget(latitude, longitude, minConfidence)` (mirroring line 369 from location)
- [ ] Add KDoc comment in `MainViewModel.kt`: `/** * Clear destination targets. */` (mirroring line 372-374 from location)
- [ ] Add `clearDestinationTargets()` method declaration in `MainViewModel.kt` (mirroring line 375 from location)
- [ ] Implement `clearDestinationTargets()` method body: set `_destinationTargets.value = emptyList()` (mirroring line 376 from location)

#### 5. Integrate with Request Builder
- [ ] Add comment in `buildRequest()` method after custom targets (around line 511): `// Add destination targeting if any` (mirroring line 498 from location)
- [ ] Add destination targets to `buildRequest()` method after the comment: `if (_destinationTargets.value.isNotEmpty()) { val destinations = _destinationTargets.value.map { DestinationTargetingInfo(latitude = it.latitude, longitude = it.longitude, minConfidence = it.minConfidence) }; builder.setDestinationTargets(destinations) }` (mirroring lines 499-504 from location)
- [ ] Add comment in `updatePlacementPreviewJson()` method after custom targets (around line 554): `// Add destination targeting if any` (mirroring line 541 from location)
- [ ] Add destination targets to `updatePlacementPreviewJson()` method after the comment: `if (_destinationTargets.value.isNotEmpty()) { val destinations = _destinationTargets.value.map { DestinationTargetingInfo(it.latitude, it.longitude, it.minConfidence) }; builder.setDestinationTargets(destinations) }` (mirroring lines 541-546 from location)





### Sample App - UI Screen

#### 1. Create DestinationTargetingScreen.kt - Main Screen Structure
- [ ] Create `DestinationTargetingScreen.kt` file in `sample/.../ui/screens/` (full path: `sample/src/main/java/com/admoai/sample/ui/screens/DestinationTargetingScreen.kt`)
- [ ] Add package declaration to `DestinationTargetingScreen.kt`: `package com.admoai.sample.ui.screens` (mirroring line 1 from LocationTargetingScreen)
- [ ] Add specific layout imports to `DestinationTargetingScreen.kt`: `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.Column`, `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.layout.padding`, `androidx.compose.foundation.layout.width` (mirroring lines 3-9 from LocationTargetingScreen)
- [ ] Add specific lazy imports to `DestinationTargetingScreen.kt`: `androidx.compose.foundation.lazy.LazyColumn`, `androidx.compose.foundation.lazy.items`, `androidx.compose.foundation.lazy.rememberLazyListState` (mirroring lines 10-12 from LocationTargetingScreen)
- [ ] Add specific icon imports to `DestinationTargetingScreen.kt`: `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.automirrored.filled.ArrowBack`, `androidx.compose.material.icons.filled.Add`, `androidx.compose.material.icons.filled.Delete`, `androidx.compose.material.icons.filled.LocationOn` (mirroring lines 14-18 from LocationTargetingScreen)
- [ ] Add specific Material3 imports to `DestinationTargetingScreen.kt`: `androidx.compose.material3.Button`, `androidx.compose.material3.Card`, `androidx.compose.material3.ExperimentalMaterial3Api`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.Scaffold`, `androidx.compose.material3.Text`, `androidx.compose.material3.TextButton`, `androidx.compose.material3.TopAppBar` (mirroring lines 19-27 from LocationTargetingScreen)
- [ ] Add specific runtime imports to `DestinationTargetingScreen.kt`: `androidx.compose.runtime.Composable`, `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue` (mirroring lines 28-30 from LocationTargetingScreen)
- [ ] Add specific UI imports to `DestinationTargetingScreen.kt`: `androidx.compose.ui.Alignment`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.text.style.TextOverflow`, `androidx.compose.ui.unit.dp` (mirroring lines 31-34 from LocationTargetingScreen)
- [ ] Add MainViewModel and DestinationItem imports to `DestinationTargetingScreen.kt`: `com.admoai.sample.ui.MainViewModel`, `com.admoai.sample.ui.model.DestinationItem` (mirroring lines 35-36 from LocationTargetingScreen)
- [ ] Add kotlin.math import to `DestinationTargetingScreen.kt`: `kotlin.math.roundToInt` (mirroring line 37 from LocationTargetingScreen)
- [ ] Add KDoc comment above `DestinationTargetingScreen` composable in `DestinationTargetingScreen.kt`: `/** * Screen for adding and managing destination targets */` (mirroring lines 40-42 from LocationTargetingScreen)
- [ ] Add `@OptIn(ExperimentalMaterial3Api::class)` annotation to `DestinationTargetingScreen` composable function (mirroring line 43 from LocationTargetingScreen)
- [ ] Add `@Composable` annotation to `DestinationTargetingScreen` composable function (mirroring line 44 from LocationTargetingScreen)
- [ ] Declare `DestinationTargetingScreen` function with proper signature: `fun DestinationTargetingScreen()` (mirroring line 45 from LocationTargetingScreen)
- [ ] Accept `viewModel: MainViewModel` parameter in `DestinationTargetingScreen` composable (mirroring line 46 from LocationTargetingScreen)
- [ ] Accept `onNavigateBack: () -> Unit` parameter in `DestinationTargetingScreen` composable (mirroring line 47 from LocationTargetingScreen)
- [ ] Collect `destinationTargets` state in `DestinationTargetingScreen`: `val destinationTargets by viewModel.destinationTargets.collectAsState()` (mirroring line 49 from LocationTargetingScreen)

#### 2. Build Screen Layout - Scaffold and TopBar
- [ ] Implement Scaffold structure with topBar parameter and content lambda using paddingValues parameter (mirroring line 51 from LocationTargetingScreen)
- [ ] Add TopAppBar in topBar lambda with title, navigationIcon, and actions parameters (mirroring line 52 from LocationTargetingScreen)
- [ ] Add title to TopAppBar using Text composable: `title = { Text("Destination Targeting") }` (mirroring line 53 from LocationTargetingScreen)
- [ ] Add navigationIcon to TopAppBar using IconButton and Icons.AutoMirrored.Filled.ArrowBack calling onNavigateBack (mirroring lines 54-58 from LocationTargetingScreen)
- [ ] Add contentDescription to navigation icon: `contentDescription = "Back"` (mirroring line 56 from LocationTargetingScreen)
- [ ] Add actions block to TopAppBar for clear all button (mirroring line 60 from LocationTargetingScreen)
- [ ] Add inline comment in actions: `// Clear all destinations` (mirroring line 61 from LocationTargetingScreen)
- [ ] Add clear all button in TopAppBar actions with conditional: `if (destinationTargets.isNotEmpty())` (mirroring line 62 from LocationTargetingScreen)
- [ ] Implement clear button as IconButton with Delete icon calling `viewModel.clearDestinationTargets()` (mirroring lines 63-68 from LocationTargetingScreen)
- [ ] Add contentDescription to clear button icon: `contentDescription = "Clear All Destinations"` (mirroring line 66 from LocationTargetingScreen)

#### 3. Build Main Content Area
- [ ] Open Scaffold content lambda with `{ paddingValues ->` parameter (mirroring line 72 from LocationTargetingScreen)
- [ ] Use Column with Modifier.fillMaxSize().padding(paddingValues) for main content (mirroring lines 73-76 from LocationTargetingScreen)
- [ ] Add inline comment above description text: `// Description text` (mirroring line 77 from LocationTargetingScreen)
- [ ] Add Text with description: `text = "Add latitude, longitude, and minimum confidence threshold to target predicted destinations. Confidence values range from 0.0 to 1.0. You can add multiple destinations. For demo purposes, you can generate random coordinates."` (mirroring lines 78-82 from LocationTargetingScreen structure)
- [ ] Apply text style: `style = MaterialTheme.typography.bodySmall` (mirroring line 79 from LocationTargetingScreen)
- [ ] Apply text color: `color = MaterialTheme.colorScheme.onSurfaceVariant` (mirroring line 80 from LocationTargetingScreen)
- [ ] Apply text padding: `modifier = Modifier.padding(16.dp)` (mirroring line 81 from LocationTargetingScreen)
- [ ] Add inline comment above button: `// Add random destination button` (mirroring line 84 from LocationTargetingScreen)
- [ ] Add "Add Random Destination" Button with Add icon (mirroring lines 85-99 from LocationTargetingScreen)
- [ ] Apply button modifiers: `modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).align(Alignment.CenterHorizontally)` (mirroring lines 87-89 from LocationTargetingScreen)
- [ ] Add icon to button: `Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))` (mirroring lines 91-95 from LocationTargetingScreen)
- [ ] Add button text: `Text("Add Random Destination")` (mirroring line 96 from LocationTargetingScreen)
- [ ] Wire up button onClick to call `viewModel.addRandomDestination()` (mirroring line 86 from LocationTargetingScreen)

#### 4. Build Empty State
- [ ] Add conditional check for empty destinations: `if (destinationTargets.isEmpty())` (mirroring line 101 from LocationTargetingScreen)
- [ ] Add inline comment above empty state Box: `// Show empty state` (mirroring line 102 from LocationTargetingScreen)
- [ ] Implement empty state UI when no destinations are added using Box (mirroring lines 103-106 from LocationTargetingScreen)
- [ ] Apply Box modifiers: `modifier = Modifier.fillMaxWidth().weight(1f)` (mirroring line 104 from LocationTargetingScreen)
- [ ] Apply Box contentAlignment: `contentAlignment = Alignment.Center` (mirroring line 105 from LocationTargetingScreen)
- [ ] Display "No destinations added yet" text in empty state (mirroring lines 108-112 from LocationTargetingScreen)
- [ ] Apply text style: `style = MaterialTheme.typography.bodyLarge` (mirroring line 109 from LocationTargetingScreen)
- [ ] Apply text color: `color = MaterialTheme.colorScheme.onSurfaceVariant` (mirroring line 110 from LocationTargetingScreen)

#### 5. Build Destinations List
- [ ] Add else block for when destinations exist: `} else {` (mirroring line 113 from LocationTargetingScreen)
- [ ] Add inline comment above LazyColumn: `// List of destination targets` (mirroring line 114 from LocationTargetingScreen)
- [ ] Implement LazyColumn with `state = rememberLazyListState()` parameter (mirroring line 115 from LocationTargetingScreen)
- [ ] Apply modifiers to LazyColumn: `modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)` (mirroring lines 116-119 from LocationTargetingScreen)
- [ ] Use `items(destinationTargets) { destinationItem -> }` to iterate through destination list (mirroring line 121 from LocationTargetingScreen)
- [ ] Call DestinationTargetItem composable inside items block (mirroring line 122 from LocationTargetingScreen)
- [ ] Pass destination parameter: `destination = destinationItem` (mirroring line 123 from LocationTargetingScreen)
- [ ] Pass onRemove lambda: `onRemove = { viewModel.updateDestinationTargets(destinationTargets.filter { it != destinationItem }) }` (mirroring lines 124-126 from LocationTargetingScreen - NOTE: uses object equality `it != destinationItem`, NOT ID comparison)

#### 6. Create DestinationTargetItem Composable - List Item UI
- [ ] Add `@Composable` annotation to `DestinationTargetItem` composable function (mirroring line 133 from LocationTargetingScreen)
- [ ] Create `DestinationTargetItem` composable function in `DestinationTargetingScreen.kt` with proper signature: `fun DestinationTargetItem()` (mirroring line 134 from LocationTargetingScreen)
- [ ] Accept `destination: DestinationItem` parameter in function signature (mirroring line 135 from LocationTargetingScreen)
- [ ] Accept `onRemove: () -> Unit` parameter in function signature (mirroring line 136 from LocationTargetingScreen)
- [ ] Apply Card modifier to destination items: `modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)` (mirroring lines 138-140 from LocationTargetingScreen)
- [ ] Use Row inside Card with proper configuration (mirroring line 142 from LocationTargetingScreen)
- [ ] Apply Row modifiers: `modifier = Modifier.fillMaxWidth().padding(16.dp)` (mirroring lines 143-144 from LocationTargetingScreen)
- [ ] Apply Row verticalAlignment: `verticalAlignment = Alignment.CenterVertically` (mirroring line 145 from LocationTargetingScreen)
- [ ] Apply Row horizontalArrangement: `horizontalArrangement = Arrangement.SpaceBetween` (mirroring line 146 from LocationTargetingScreen)
- [ ] Add inline comment above icon: `// Icon` (mirroring line 148 from LocationTargetingScreen)
- [ ] Add destination Icon using `Icons.Default.LocationOn` (mirroring line 149 from LocationTargetingScreen)
- [ ] Apply icon contentDescription: `contentDescription = null` (mirroring line 150 from LocationTargetingScreen)
- [ ] Apply icon tint: `tint = MaterialTheme.colorScheme.primary` (mirroring line 151 from LocationTargetingScreen)
- [ ] Apply icon modifier: `modifier = Modifier.padding(end = 16.dp)` (mirroring line 152 from LocationTargetingScreen)
- [ ] Add inline comment above Column: `// Destination info` (mirroring line 155 from LocationTargetingScreen)
- [ ] Add Column with Modifier.weight(1f) to display destination details (mirroring lines 156-158 from LocationTargetingScreen)
- [ ] Display latitude as "Lat: ${(destination.latitude * 1000).roundToInt() / 1000.0}" (mirroring lines 159-164 from LocationTargetingScreen)
- [ ] Apply latitude Text style: `style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis` (mirroring lines 161-163 from LocationTargetingScreen)
- [ ] Display longitude as "Lng: ${(destination.longitude * 1000).roundToInt() / 1000.0}" (mirroring lines 166-171 from LocationTargetingScreen)
- [ ] Apply longitude Text style: `style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis` (mirroring lines 168-170 from LocationTargetingScreen)
- [ ] Display minConfidence as "Confidence: ${(destination.minConfidence * 1000).roundToInt() / 1000.0}" (following same pattern as latitude/longitude from lines 159-164, treating minConfidence like latitude)
- [ ] Apply minConfidence Text style: `style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis` (following same pattern as latitude/longitude from lines 161-163)
- [ ] Add inline comment above TextButton: `// Remove button` (mirroring line 174 from LocationTargetingScreen)
- [ ] Add TextButton for remove action with Modifier.width(100.dp) (mirroring lines 175-177 from LocationTargetingScreen)
- [ ] Add Delete icon to TextButton: `Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.padding(end = 4.dp))` (mirroring lines 179-183 from LocationTargetingScreen)
- [ ] Add "Remove" text to TextButton: `Text("Remove")` (mirroring line 184 from LocationTargetingScreen)
- [ ] Wire onClick to call onRemove lambda (mirroring line 176 from LocationTargetingScreen)

#### 7. Wire Navigation - MainActivity Routes
- [ ] Import `DestinationTargetingScreen` in `MainActivity.kt` (add to imports section at top of file, alongside LocationTargetingScreen import)
- [ ] Add `DESTINATION_TARGETING` route constant to `Routes` object in `MainActivity.kt`: `const val DESTINATION_TARGETING = "destination_targeting"` (mirroring line 103 from MainActivity for LOCATION_TARGETING)
- [ ] Add `composable(Routes.DESTINATION_TARGETING)` route in NavHost with DestinationTargetingScreen composable (mirroring lines 202-209 from MainActivity for LOCATION_TARGETING)
- [ ] Add opening brace for composable block: `composable(Routes.DESTINATION_TARGETING) {` (mirroring line 202 from MainActivity)
- [ ] Call DestinationTargetingScreen inside composable block (mirroring line 203 from MainActivity)
- [ ] Pass `viewModel` parameter: `viewModel = viewModel,` (mirroring line 204 from MainActivity)
- [ ] Pass `onNavigateBack` parameter: `onNavigateBack = { navController.popBackStack() }` (mirroring lines 205-207 from MainActivity)
- [ ] Close composable block with closing brace: `}` (mirroring line 209 from MainActivity)
- [ ] Add `onDestinationTargetingClick` handler in DecisionRequestScreen composable call navigation lambda: `onDestinationTargetingClick = { navController.navigate(Routes.DESTINATION_TARGETING) },` (mirroring lines 167-169 from MainActivity for onLocationTargetingClick)
- [ ] Pass `onDestinationTargetingClick` from `MainActivity.kt` to `DecisionRequestScreen` composable in NavHost route (around line 142-184, after onLocationTargetingClick parameter)

#### 8. Connect DecisionRequestScreen - Callback Parameter
- [ ] Add `onDestinationTargetingClick: () -> Unit = {}` parameter to `DecisionRequestScreen` composable function signature in `DecisionRequestScreen.kt` after `onLocationTargetingClick` parameter (mirroring line 51 pattern from DecisionRequestScreen where onLocationTargetingClick is defined)
- [ ] Pass `onDestinationTargetingClick` to `TargetingSection` in `DecisionRequestScreen.kt` around line 127-132 after `onLocationTargetingClick` parameter (mirroring line 130 from DecisionRequestScreen)

#### 9. Update TargetingSection - Summary Row
- [ ] Add `onDestinationTargetingClick: () -> Unit = {}` parameter to `TargetingSection` composable function signature in `TargetingSection.kt` after `onLocationTargetingClick` parameter (mirroring line 28 pattern from TargetingSection where onLocationTargetingClick is defined)
- [ ] Collect `destinationTargets` state in `TargetingSection.kt`: `val destinationTargets by viewModel.destinationTargets.collectAsState()` (add after line 32 where locationTargets is collected, mirroring line 32 from TargetingSection)
- [ ] Note: Use `Icons.Outlined.LocationOn` for destination icon - this import already exists at line 7 of TargetingSection.kt (same icon as location targeting, no new import needed)
- [ ] Add inline comment above destination targeting row: `// Destination Targeting row` (mirroring line 67 pattern from TargetingSection where location targeting row comment is)
- [ ] Create destination targeting row in `TargetingSection.kt` using `SectionRow` composable after location targeting row (mirroring lines 68-86 from TargetingSection)
- [ ] Add icon parameter to SectionRow: `icon = Icons.Outlined.LocationOn` (mirroring line 69 from TargetingSection)
- [ ] Add label parameter to SectionRow: `label = "Destination Targeting"` (mirroring line 70 from TargetingSection)
- [ ] Add value parameter to SectionRow with Row composable containing Text and Icon (mirroring lines 71-84 from TargetingSection)
- [ ] Add value lambda: `value = { Row(verticalAlignment = Alignment.CenterVertically) { ... } }` (mirroring line 71-72 from TargetingSection)
- [ ] Add Text inside Row: `Text(text = if (destinationTargets.isEmpty()) "None" else "${destinationTargets.size} destinations", color = MaterialTheme.colorScheme.onSurfaceVariant)` (mirroring lines 73-75 from TargetingSection)
- [ ] Add navigation arrow Icon inside Row: `Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)` (mirroring lines 76-80 from TargetingSection)
- [ ] Add onClick parameter to SectionRow: `onClick = onDestinationTargetingClick` (mirroring line 86 from TargetingSection)






### Tests
- [ ] Add `add destination target` test to `DecisionRequestBuilderTest.kt` at path `sdk/src/test/kotlin/com/admoai/sdk/model/request/DecisionRequestBuilderTest.kt` (verify single destination added correctly with latitude, longitude, and minConfidence)
- [ ] Add `set destination targets` test to `DecisionRequestBuilderTest.kt` (verify list replacement works correctly)
- [ ] Add `build includes targeting if only destination is set` test to `DecisionRequestBuilderTest.kt` (verify targeting object is created when ONLY destination is populated and geo/location are null - this validates the condition `|| !it.destination.isNullOrEmpty()` works correctly)
- [ ] Add `addDestinationTarget multiple times` test to `DecisionRequestBuilderTest.kt` (verify multiple destination targets can be added and are accumulated in the list)
- [ ] Add integration test with destination data to `TargetingTest.kt` at path `sdk/src/test/kotlin/com/admoai/sdk/TargetingTest.kt` (full request-response cycle with destination targeting)
- [ ] Verify minConfidence values are properly serialized and included in the request in integration test

### Documentation
- [ ] Update `sdk/README.md` - add example showing destination targeting with minConfidence field near existing `.addLocationTarget()` examples (around line 81 and 210)
- [ ] Use real minConfidence values in examples like `minConfidence = 0.8` or `minConfidence = 0.95` to clearly demonstrate the 0.0-1.0 range
- [ ] Add complete method documentation to `sdk/README.md` showing both methods:
  - `.addDestinationTarget(latitude = 37.7749, longitude = -122.4194, minConfidence = 0.8)` - adds single destination
  - `.setDestinationTargets(destinations)` - replaces entire destination list
- [ ] Add explanation of minConfidence parameter: "minConfidence specifies the minimum confidence level (0.0-1.0) required for the destination prediction"
- [ ] Update `sample/README.md` - add destination targeting to navigation flow diagram section (around line 60) showing: Decision Request Screen → Destination Targeting Screen
- [ ] Update `sample/README.md` - add destination targeting row to features table (around line 102) with clear description: "Destination Targeting - Target predicted destinations with confidence thresholds (0.0-1.0)"
- [ ] Add entry to `CHANGELOG.md` in appropriate version section with feature description: "Added destination targeting with minimum confidence parameter"

