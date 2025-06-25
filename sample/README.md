# AdMoai Android SDK - Sample App

This sample application demonstrates the integration and usage of the AdMoai Android SDK, showcasing both traditional ViewModel approaches and modern Jetpack Compose integration.

## Getting Started

### Prerequisites

- **Android Studio** Arctic Fox (2020.3.1) or newer
- **Android SDK** with API level 24 or higher
- **Kotlin** plugin enabled

### Running the Sample App

1. **Open the project** in Android Studio:
   ```bash
   # From the root directory
   cd admoai-android
   # Open in Android Studio or run:
   ./gradlew :sample:installDebug
   ```

2. **Build and run** the sample app:
   - Select the `sample` module as your run configuration
   - Choose your target device (emulator or physical device)
   - Click **Run** or use `Shift + F10`

3. **Alternative: Command Line**
   ```bash
   ./gradlew :sample:assembleDebug
   ./gradlew :sample:installDebug  # If device is connected
   ```

## What's Included

The sample app demonstrates:

### **Main Screen**
- SDK initialization and configuration
- User and device targeting setup
- Placement configuration

### **Decision Request Screen**
- Ad request configuration
- Custom targeting parameters
- Geo and location targeting
- Real-time request/response display

### **Jetpack Compose Integration** *(NEW in v1.0.0)*
- **Traditional ViewModel approach** with StateFlow
- **Modern Compose approach** with `rememberAdState`
- Side-by-side comparison of both integration methods
- Declarative ad state management

### **Response Display**
- JSON formatted ad responses
- Creative details and metadata
- Tracking URL information

## Key Features Demonstrated

### SDK Initialization
```kotlin
val config = SDKConfig(
    baseUrl = "https://mock.api.admoai.com", // Mock endpoint for demo
    collectAppData = true,
    collectDeviceData = true
)
Admoai.initialize(application, config)
```

### Traditional Integration (ViewModel)
```kotlin
class MainViewModel : AndroidViewModel(application) {
    fun loadAds() {
        Admoai.getInstance().requestAds(request) { response ->
            // Handle response
        }
    }
}
```

### Compose Integration (NEW)
```kotlin
@Composable
fun ComposeAdDemo() {
    val adState by rememberAdState(decisionRequest)
    
    when (adState) {
        is AdState.Success -> { /* Show ads */ }
        is AdState.Loading -> { /* Show loading */ }
        is AdState.Error -> { /* Show error */ }
        AdState.Idle -> { /* Ready state */ }
    }
}
```

## UI Components

The sample includes reusable components:

- **`ConfigurationScreen`** - SDK setup and user configuration
- **`DecisionRequestScreen`** - Ad request builder interface  
- **`ComposeIntegrationScreen`** - Compose vs ViewModel comparison
- **`AdCard`** - Reusable ad display component
- **`PlacementPicker`** - Interactive placement selector

## Testing Different Scenarios

### Mock API Responses
The sample uses mock endpoints to demonstrate various scenarios:
- Successful ad responses
- Error handling
- Different ad formats
- Tracking events

### Targeting Options
Test different targeting configurations:
- **Geo Targeting**: Country, region, city-based targeting
- **Location Targeting**: GPS coordinate-based targeting  
- **Custom Targeting**: Key-value pair targeting
- **User Targeting**: Demographics and preferences

### Consent Management
Explore GDPR compliance features:
- User consent toggles
- Data collection preferences
- Privacy-aware ad requests

## Exploring the Code

### Key Files

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Navigation setup and app entry point |
| `MainViewModel.kt` | Traditional ViewModel integration example |
| `ComposeIntegrationScreen.kt` | Compose integration demonstration |
| `ConfigurationScreen.kt` | SDK configuration interface |
| `DecisionRequestScreen.kt` | Ad request builder |

### Navigation Structure
```
MainActivity
├── Configuration Screen (SDK setup)
├── Decision Request Screen (ad requests)
│   └── Compose Integration Demo ← NEW!
└── Response Display (results)
```

## Customization

### Adding Your Own API Endpoint

Replace the mock endpoint in `MainViewModel.kt`:

```kotlin
companion object {
    private const val MOCK_BASE_URL = "https://your-api-endpoint.com"
}
```

### Custom Ad Layouts

The `AdCard` component can be customized for your specific creative formats:

```kotlin
@Composable
fun CustomAdCard(adData: AdData) {
    // Your custom ad layout
}
```

## Troubleshooting

### Common Issues

1. **Build Errors**
   ```bash
   ./gradlew clean build
   ```

2. **Emulator Issues**
   - Ensure emulator has API level 24+
   - Check internet connectivity for mock API calls

3. **Compose Preview Issues**
   - Ensure Compose preview is enabled in Android Studio
   - Check that preview dependencies are up to date

### Getting Help

- Check the [main SDK documentation](../sdk/README.md)
- Review [GitHub Issues](https://github.com/admoai/admoai-android/issues)
- Contact support at support@admoai.com

## Next Steps

After exploring the sample app:

1. **Integrate the SDK** into your own app using the [SDK README](../sdk/README.md)
2. **Customize** the ad layouts for your use case
3. **Configure** your production API endpoints
4. **Test** with real ad inventory

---

**Happy coding!**
