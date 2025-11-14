package com.admoai.sample.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.admoai.sdk.Admoai
import com.admoai.sdk.config.AppConfig
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.config.UserConfig
import io.ktor.client.engine.okhttp.OkHttp
import com.admoai.sdk.model.request.Consent
import com.admoai.sdk.model.request.CustomTargetingInfo
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.LocationTargetingInfo
import com.admoai.sdk.model.request.Placement
import com.admoai.sdk.model.request.PlacementFormat
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sample.ui.model.CustomTargetItem
import com.admoai.sample.ui.model.GeoTargetItem
import com.admoai.sample.ui.model.LocationItem
import com.admoai.sample.ui.model.PlacementItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Main ViewModel for the AdMoai sample application.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MOCK_BASE_URL = "http://10.0.2.2:8080"
        private const val TAG = "MainViewModel"
    }

    // Placement selection
    private val _placementKey = MutableStateFlow("home")
    val placementKey = _placementKey.asStateFlow()

    // Available placements
    private val _placements = MutableStateFlow(listOf(
        PlacementItem(id = "home", name = "Home", key = "home", title = "Home", icon = "house"),
        PlacementItem(id = "search", name = "Search", key = "search", title = "Search", icon = "magnifyingglass"),
        PlacementItem(id = "menu", name = "Menu", key = "menu", title = "Menu", icon = "list.bullet"),
        PlacementItem(id = "promotions", name = "Promotions", key = "promotions", title = "Promotions", icon = "tag"),
        PlacementItem(id = "vehicleSelection", name = "Vehicle Selection", key = "vehicleSelection", title = "Vehicle Selection", icon = "car"),
        PlacementItem(id = "rideSummary", name = "Ride Summary", key = "rideSummary", title = "Ride Summary", icon = "doc.text"),
        PlacementItem(id = "waiting", name = "Waiting", key = "waiting", title = "Waiting", icon = "clock"),
        PlacementItem(id = "freeMinutes", name = "Free Minutes", key = "freeMinutes", title = "Free Minutes", icon = "cardgiftcard"),
        PlacementItem(id = "invalidPlacement", name = "Invalid Placement", key = "invalidPlacement", title = "Invalid Placement", icon = "exclamationmark.triangle")
    ))
    val placements = _placements.asStateFlow()

    private val _userId = MutableStateFlow("user_123")
    val userId = _userId.asStateFlow()

    private val _userIp = MutableStateFlow("203.0.113.1")
    val userIp = _userIp.asStateFlow()

    private val _userTimezone = MutableStateFlow(java.util.TimeZone.getDefault().id)
    val userTimezone = _userTimezone.asStateFlow()

    private val _gdprConsent = MutableStateFlow(true)
    val gdprConsent = _gdprConsent.asStateFlow()

    private val _collectAppData = MutableStateFlow(true)
    val collectAppData = _collectAppData.asStateFlow()

    private val _collectDeviceData = MutableStateFlow(true)
    val collectDeviceData = _collectDeviceData.asStateFlow()

    private val _geoTargets = MutableStateFlow<List<GeoTargetItem>>(emptyList())
    val geoTargets = _geoTargets.asStateFlow()

    private val _locationTargets = MutableStateFlow<List<LocationItem>>(emptyList())
    val locationTargets = _locationTargets.asStateFlow()

    private val _customTargets = MutableStateFlow<List<CustomTargetItem>>(emptyList())
    val customTargets = _customTargets.asStateFlow()

    // Video ad options
    private val _formatFilterEnabled = MutableStateFlow(false)
    val formatFilterEnabled = _formatFilterEnabled.asStateFlow()
    
    private val _selectedFormat = MutableStateFlow<String?>(null) // null="Any", "native", "video"
    val selectedFormat = _selectedFormat.asStateFlow()
    
    private val _videoDelivery = MutableStateFlow("vast_tag") // "vast_tag", "vast_xml", "json"
    val videoDelivery = _videoDelivery.asStateFlow()
    
    private val _videoEndCard = MutableStateFlow("none") // "none", "native_endcard", "vast_companion"
    val videoEndCard = _videoEndCard.asStateFlow()
    
    private val _videoPlayer = MutableStateFlow("") // "exoplayer", "vast_client", "jwplayer"
    val videoPlayer = _videoPlayer.asStateFlow()
    
    private val _overlayAtPercent = MutableStateFlow(0.5f) // 0.0 to 1.0
    val overlayAtPercent = _overlayAtPercent.asStateFlow()
    
    private val _isSkippable = MutableStateFlow(false)
    val isSkippable = _isSkippable.asStateFlow()
    
    private val _skipOffset = MutableStateFlow("5") // seconds as string
    val skipOffset = _skipOffset.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _requestJson = MutableStateFlow("")
    val requestJson = _requestJson.asStateFlow()

    private val _response = MutableStateFlow<DecisionResponse?>(null)
    val response = _response.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    // Decision response
    private val _decisionResponse = MutableStateFlow<DecisionResponse?>(null)
    val decisionResponse = _decisionResponse.asStateFlow()
    
    // Formatted response for display
    private val _formattedResponse = MutableStateFlow("")
    val formattedResponse = _formattedResponse.asStateFlow()
    
    // Video demo scenario tracking
    private val _videoDemoScenario = MutableStateFlow<String?>(null)
    val videoDemoScenario = _videoDemoScenario.asStateFlow()

    // Mock placements available in the demo
    val availablePlacements = listOf(
        PlacementItem(id = "home", name = "Home", key = "home", title = "Home", icon = "house"),
        PlacementItem(id = "search", name = "Search", key = "search", title = "Search", icon = "magnifyingglass"),
        PlacementItem(id = "menu", name = "Menu", key = "menu", title = "Menu", icon = "list.bullet"),
        PlacementItem(id = "promotions", name = "Promotions", key = "promotions", title = "Promotions", icon = "tag"),
        PlacementItem(id = "vehicleSelection", name = "Vehicle Selection", key = "vehicleSelection", title = "Vehicle Selection", icon = "car"),
        PlacementItem(id = "rideSummary", name = "Ride Summary", key = "rideSummary", title = "Ride Summary", icon = "doc.text"),
        PlacementItem(id = "waiting", name = "Waiting", key = "waiting", title = "Waiting", icon = "clock"),
        PlacementItem(id = "freeMinutes", name = "Free Minutes", key = "freeMinutes", title = "Free Minutes", icon = "cardgiftcard"),
        PlacementItem(id = "invalidPlacement", name = "Invalid Placement", key = "invalidPlacement", title = "Invalid Placement", icon = "exclamationmark.triangle")
    )

    // Cities for geo targeting with their numeric IDs
    val availableCities = listOf(
        GeoTargetItem("2643743", "London"),
        GeoTargetItem("353059", "Miami"),
        GeoTargetItem("5128581", "New York"),
        GeoTargetItem("2988507", "Paris"),
        GeoTargetItem("3169070", "Rome"),
        GeoTargetItem("3871336", "Santiago")
    )

    // App info - read only
    val appName: String
    val appVersion: String
    val appIdentifier: String
    val appBuild: String
    val appLanguage: String?

    // Device info - read only
    val deviceId: String = UUID.randomUUID().toString()
    val deviceModel: String = Build.MODEL
    val deviceManufacturer: String = Build.MANUFACTURER
    val deviceOs: String = "Android"
    val deviceOsVersion: String = Build.VERSION.RELEASE
    val deviceTimezone: String = java.util.TimeZone.getDefault().id
    val deviceLanguage: String = java.util.Locale.getDefault().language

    // SDK instance
    private val sdk: Admoai

    // Ad tracking functionality
    private val _showingCreativeDetail = MutableStateFlow(false)
    val showingCreativeDetail = _showingCreativeDetail.asStateFlow()
    
    // Selected ad data for detailed view
    private val _selectedAdData = MutableStateFlow<AdData?>(null)
    val selectedAdData = _selectedAdData.asStateFlow()
    
    /**
     * Track an ad event (impression, click, custom).
     */
    fun trackAdEvent(eventType: String, trackingUrl: String) {
        viewModelScope.launch {
            try {
                // Log for debugging
                println("Tracking $eventType event: $trackingUrl")
                
                // Call the appropriate SDK method based on event type
                when (eventType.lowercase()) {
                    "impression" -> {
                        // Find the creative with this tracking URL and fire impression
                        _response.value?.data?.flatMap { it.creatives.orEmpty() }?.forEach { creative ->
                            if (creative.tracking.impressions != null) {
                                for (impression in creative.tracking.impressions) {
                                    if (impression.url == trackingUrl) {
                                        sdk.fireImpression(creative.tracking, impression.key)
                                    }
                                }
                            }
                        }
                    }
                    "click" -> {
                        // Find the creative with this tracking URL and fire click
                        _response.value?.data?.flatMap { it.creatives.orEmpty() }?.forEach { creative ->
                            if (creative.tracking.clicks != null) {
                                for (click in creative.tracking.clicks) {
                                    if (click.url == trackingUrl) {
                                        sdk.fireClick(creative.tracking, click.key)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // Custom events would go here
                        println("Custom event type: $eventType not implemented")
                    }
                }
            } catch (e: Exception) {
                println("Error tracking $eventType: ${e.message}")
            }
        }
    }
    
    /**
     * Show the creative detail screen with selected ad data.
     */
    fun showCreativeDetail(adData: AdData?) {
        _selectedAdData.value = adData
        _showingCreativeDetail.value = true
    }
    
    /**
     * Hide the creative detail screen.
     */
    fun hideCreativeDetail() {
        _showingCreativeDetail.value = false
    }

    init {
        // Initialize app info
        val context = getApplication<Application>()
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }

        appName = context.applicationInfo.loadLabel(packageManager).toString()
        appVersion = packageInfo.versionName ?: "unknown"
        appBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        appIdentifier = context.packageName
        appLanguage = java.util.Locale.getDefault().language

        // Initialize the SDK with OkHttp engine to avoid TLS issues
        val config = SDKConfig(
            baseUrl = MOCK_BASE_URL,
            apiVersion = "2025-11-01",
            enableLogging = true,
            networkClientEngine = OkHttp.create()
        )
        sdk = Admoai.getInstance()
        Admoai.initialize(config)
        
        // Set user config
        sdk.setUserConfig(newUserConfig = UserConfig(id = _userId.value))
        
        // Set app config for data collection
        val appConfig = AppConfig(
            appName = appName,
            appVersion = appVersion,
            packageName = appIdentifier,
            buildNumber = appBuild,
            language = appLanguage
        )
        sdk.setAppConfig(appConfig)
        
        // Set device config for data collection
        val deviceConfig = DeviceConfig(
            model = android.os.Build.MODEL,
            osName = "Android",
            osVersion = android.os.Build.VERSION.RELEASE,
            deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ),
            timezone = java.util.TimeZone.getDefault().id,
            manufacturer = deviceManufacturer,
            language = deviceLanguage
        )
        sdk.setDeviceConfig(deviceConfig)
        
        // Initialize mock geo targeting items
        _geoTargets.value = availableCities
    }

    /**
     * Update the selected placement key.
     */
    fun setPlacementKey(key: String) {
        _placementKey.value = key
    }

    /**
     * Update user ID.
     */
    fun setUserId(id: String) {
        _userId.value = id
        sdk.setUserConfig(newUserConfig = UserConfig(id = id))
    }

    /**
     * Update user IP.
     */
    fun setUserIp(ip: String) {
        _userIp.value = ip
    }

    /**
     * Update user timezone.
     */
    fun setUserTimezone(timezone: String) {
        _userTimezone.value = timezone
    }

    /**
     * Toggle GDPR consent.
     */
    fun setGdprConsent(enabled: Boolean) {
        _gdprConsent.value = enabled
    }

    /**
     * Toggle app data collection.
     */
    fun setCollectAppData(enabled: Boolean) {
        _collectAppData.value = enabled
        // Auto-refresh request preview when toggle changes
        updateRequestJsonPreview()
    }

    /**
     * Toggle device data collection.
     */
    fun setCollectDeviceData(enabled: Boolean) {
        _collectDeviceData.value = enabled
        // Auto-refresh request preview when toggle changes
        updateRequestJsonPreview()
    }

    /**
     * Update geo targeting list.
     */
    fun updateGeoTargets(cities: List<GeoTargetItem>) {
        _geoTargets.value = cities
    }

    /**
     * Add location target.
     */
    fun addLocationTarget(latitude: Double, longitude: Double) {
        val newLocation = LocationItem(latitude = latitude, longitude = longitude)
        _locationTargets.value = _locationTargets.value + newLocation
    }
    
    /**
     * Update location targets with a new list.
     */
    fun updateLocationTargets(locations: List<LocationItem>) {
        _locationTargets.value = locations
    }

    /**
     * Add a random location for demo purposes.
     */
    fun addRandomLocation() {
        // Generate random coordinates around the world
        val latitude = (Math.random() * 180.0) - 90.0
        val longitude = (Math.random() * 360.0) - 180.0
        addLocationTarget(latitude, longitude)
    }

    /**
     * Clear location targets.
     */
    fun clearLocationTargets() {
        _locationTargets.value = emptyList()
    }

    /**
     * Add custom targeting key-value pair.
     */
    fun addCustomTarget(key: String, value: String) {
        val newCustomTarget = CustomTargetItem(key = key, value = value)
        _customTargets.value = _customTargets.value + newCustomTarget
    }

    /**
     * Clear custom targeting.
     */
    fun clearCustomTargets() {
        _customTargets.value = emptyList()
    }
    
    /**
     * Update custom targets with a new list.
     */
    fun updateCustomTargets(targets: List<CustomTargetItem>) {
        _customTargets.value = targets
    }

    /**
     * Toggle format filter on/off.
     */
    fun setFormatFilterEnabled(enabled: Boolean) {
        _formatFilterEnabled.value = enabled
        if (enabled) {
            // Set default to "native" when filter is enabled
            if (_selectedFormat.value == null) {
                _selectedFormat.value = "native"
            }
        } else {
            // Reset format when disabled
            _selectedFormat.value = null
        }
    }

    /**
     * Set the selected format (null for "Any", "native", or "video").
     */
    fun setSelectedFormat(format: String?) {
        _selectedFormat.value = format
    }

    /**
     * Set video delivery method.
     */
    fun setVideoDelivery(delivery: String) {
        _videoDelivery.value = delivery
    }

    /**
     * Set video end-card mode.
     */
    fun setVideoEndCard(endCard: String) {
        _videoEndCard.value = endCard
    }
    
    /**
     * Set video player type.
     */
    fun setVideoPlayer(player: String) {
        _videoPlayer.value = player
    }

    /**
     * Set overlay threshold percentage (0.0 to 1.0).
     */
    fun setOverlayAtPercent(percent: Float) {
        _overlayAtPercent.value = percent.coerceIn(0f, 1f)
    }

    /**
     * Toggle skippable on/off.
     */
    fun setSkippable(skippable: Boolean) {
        _isSkippable.value = skippable
    }

    /**
     * Set skip offset in seconds.
     */
    fun setSkipOffset(offset: String) {
        _skipOffset.value = offset
    }

    /**
     * Build the request object based on current selections.
     */
    fun buildRequest(): DecisionRequest {
        // Determine placement format based on filter
        // When filter is enabled, always include format (never null)
        val placementFormat = when {
            !_formatFilterEnabled.value -> null // Filter disabled = no format restriction
            _selectedFormat.value == "native" -> PlacementFormat.NATIVE
            _selectedFormat.value == "video" -> PlacementFormat.VIDEO
            else -> PlacementFormat.NATIVE // Default to native if somehow null when enabled
        }
        
        // Create request builder with placement including format
        val builder = sdk.createRequestBuilder()
            .addPlacement(Placement(
                key = _placementKey.value,
                format = placementFormat
            ))
            .setUserIp(_userIp.value)
            .setUserId(_userId.value)
            .setUserTimezone(_userTimezone.value)
            .setUserConsent(Consent(gdpr = _gdprConsent.value))

        // Add geo targeting if selected
        val selectedGeos = _geoTargets.value.filter { it.isSelected }
        if (selectedGeos.isNotEmpty()) {
            // Use the actual numeric geo IDs
            val geoIds = selectedGeos.map { it.id.toInt() }
            builder.setGeoTargets(geoIds)
        }

        // Add location targeting if any
        if (_locationTargets.value.isNotEmpty()) {
            val locations = _locationTargets.value.map { 
                LocationTargetingInfo(latitude = it.latitude, longitude = it.longitude)
            }
            builder.setLocationTargets(locations)
        }

        // Add custom targeting if any (format is now part of Placement, not custom targeting)
        // Note: Video-specific options (delivery, endcard, overlayAt, isSkippable, skipOffset) 
        // are NOT sent to the server - they only control UI behavior in the sample app
        if (_customTargets.value.isNotEmpty()) {
            val customTargetList = _customTargets.value.map { 
                CustomTargetingInfo(it.key, JsonPrimitive(it.value))
            }
            builder.setCustomTargets(customTargetList)
        }

        // Disable app/device collection only when toggles are OFF
        if (!_collectAppData.value) {
            builder.disableAppCollection()
        }

        if (!_collectDeviceData.value) {
            builder.disableDeviceCollection()
        }

        return builder.build()
    }
    
    @OptIn(ExperimentalSerializationApi::class)
    fun updatePlacementPreviewJson(placementKey: String) {
        viewModelScope.launch {
            try {
                // Create a builder with minimal info to avoid property reference issues
                val builder = sdk.createRequestBuilder()
                
                // Add the placement with only the key (simplest approach)
                builder.addPlacement(placementKey)
                
                // Add targeting if available
                if (_geoTargets.value.any { it.isSelected }) {
                    val selectedGeos = _geoTargets.value.filter { it.isSelected }
                    val geoIds = selectedGeos.map { it.id.toInt() }
                    builder.setGeoTargets(geoIds)
                }
                
                if (_locationTargets.value.isNotEmpty()) {
                    val locations = _locationTargets.value.map { 
                        LocationTargetingInfo(it.latitude, it.longitude) 
                    }
                    builder.setLocationTargets(locations)
                }
                
                if (_customTargets.value.isNotEmpty()) {
                    val customTargeting = _customTargets.value.map { item ->
                        CustomTargetingInfo(item.key, JsonPrimitive(item.value))
                    }
                    builder.setCustomTargets(customTargeting)
                }
                
                // Build initial request and set collection flags
                val initialRequest = builder.build()
                    .copy(
                        collectAppData = _collectAppData.value,
                        collectDeviceData = _collectDeviceData.value
                    )
                
                // Process through SDK to add app/device as top-level fields
                val finalRequest = sdk.prepareFinalDecisionRequest(initialRequest)
                
                val prettyJson = Json { 
                    prettyPrint = true 
                    encodeDefaults = false
                    explicitNulls = false
                }
                _requestJson.value = prettyJson.encodeToString(finalRequest)
            } catch (e: Exception) {
                _requestJson.value = "Error generating placement preview: ${e.message}"
            }
        }
    }

    /**
     * Load ads from the server.
     * For video format, makes a direct request to localhost:8080 with custom header.
     */
    fun loadAds() {
        _isLoading.value = true
        _response.value = null
        _decisionResponse.value = null
        _formattedResponse.value = ""
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Use SDK for all requests (both native and video)
                val request = buildRequest()
                sdk.requestAds(request).collect { response ->
                    _response.value = response
                    _decisionResponse.value = response
                    _formattedResponse.value = formatResponseToJson(response)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown error occurred"
                _isLoading.value = false
            }
        }
    }

    /**
     * Load ads using a specific DecisionRequest (for Compose integration demo).
     */
    fun loadAds(request: DecisionRequest) {
        _isLoading.value = true
        _response.value = null
        _decisionResponse.value = null
        _formattedResponse.value = ""
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                sdk.requestAds(request).collect { response ->
                    _response.value = response
                    _decisionResponse.value = response
                    _formattedResponse.value = formatResponseToJson(response)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown error occurred"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update the request JSON preview for UI display.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun updateRequestJsonPreview() {
        viewModelScope.launch {
            try {
                // First build the initial request
                val initialRequest = buildRequest()
                // Then pass it through the SDK's prepareFinalDecisionRequest to get the complete request
                // with app and device objects added as top-level fields
                val finalRequest = sdk.prepareFinalDecisionRequest(initialRequest)
                
                val prettyJson = Json { 
                    prettyPrint = true 
                    encodeDefaults = false
                    explicitNulls = false
                }
                _requestJson.value = prettyJson.encodeToString(finalRequest)
            } catch (e: Exception) {
                _requestJson.value = "Error generating request preview: ${e.message}"
            }
        }
    }
    
    /**
     * Get the complete HTTP request representation.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun getHttpRequest(): String {
        return try {
            val initialRequest = buildRequest()
            // Use the SDK's prepareFinalDecisionRequest to get the complete request
            // with app and device objects added as top-level fields
            val finalRequest = sdk.prepareFinalDecisionRequest(initialRequest)
            
            val prettyJson = Json { 
                prettyPrint = true
                encodeDefaults = false
                explicitNulls = false
            }
            val requestBody = prettyJson.encodeToString(finalRequest)
            
            // Use same host and endpoint for all requests (video and native)
            val targetHost = MOCK_BASE_URL.removePrefix("http://")
            val endpoint = "/v1/decision"
            
            // Format as an HTTP request with headers
            val sb = StringBuilder()
            sb.append("POST $endpoint HTTP/1.1\n")
            sb.append("Host: $targetHost\n")
            sb.append("Content-Type: application/json\n")
            sb.append("User-Agent: AdmoaiExample/Android\n")
            
            // Add X-Decision-Version header for all requests (as configured in SDK)
            sb.append("X-Decision-Version: 2025-11-01\n")
            
            sb.append("\n")
            sb.append(requestBody)
            
            sb.toString()
        } catch (e: Exception) {
            "Error generating HTTP request: ${e.message}"
        }
    }
    
    /**
     * Format response to pretty JSON for display.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun formatResponseToJson(response: DecisionResponse?): String {
        if (response == null) return ""
        return try {
            val prettyJson = Json { 
                prettyPrint = true 
                encodeDefaults = false
                explicitNulls = false
            }
            prettyJson.encodeToString(response)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error formatting response", e)
            "Error formatting response: ${e.message}"
        }
    }

    /**
     * Reload ads using the same parameters.
     */
    fun refreshAds() {
        loadAds()
    }

    /**
     * Fire impression tracking.
     */
    fun fireImpression(creative: Creative, key: String = "default") {
        val tracking = creative.tracking ?: return
        sdk.fireImpression(tracking, key)
    }

    /**
     * Fire click tracking.
     */
    fun fireClick(creative: Creative, key: String = "default") {
        val tracking = creative.tracking ?: return
        sdk.fireClick(tracking, key)
    }

    /**
     * Fire custom tracking event.
     */
    fun fireCustomEvent(creative: Creative, key: String) {
        val tracking = creative.tracking ?: return
        sdk.fireCustomEvent(tracking, key)
    }

    /**
     * Fire video event tracking (start, firstQuartile, midpoint, thirdQuartile, complete, skip).
     */
    fun fireVideoEvent(creative: Creative, key: String) {
        val tracking = creative.tracking ?: return
        sdk.fireVideoEvent(tracking, key)
    }

    /**
     * Get first creative from the response.
     */
    fun getFirstCreative(): Creative? {
        return _response.value?.data?.firstOrNull()?.creatives?.firstOrNull()
    }

    /**
     * Clear the current response.
     */
    fun clearResponse() {
        _response.value = null
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Get a placement by its key.
     */
    fun getPlacementByKey(placementKey: String): PlacementItem? {
        return availablePlacements.find { it.key == placementKey }
    }
    
    /**
     * Get ad data for a specific placement from the current response.
     * Returns null if creatives are null or empty.
     */
    fun getAdDataForPlacement(placementKey: String): AdData? {
        val response = _decisionResponse.value ?: return null
        val data = response.data
        
        // Match by placement key (works for both video and native formats)
        val adData = data?.find { adData ->
            adData.placement == placementKey
        }
        
        // Return null if creatives is null or empty
        if (adData != null && adData.creatives.isEmpty()) {
            android.util.Log.d(TAG, "No creatives found for placement $placementKey, returning null")
            return null
        }
        
        return adData
    }
    
    /**
     * Check if a creative is a video creative.
     * A creative is considered video if it has VIDEO content type or has a delivery method set.
     */
    fun isVideoCreative(creative: Creative): Boolean {
        // Check if any content is of type VIDEO
        val hasVideoContent = creative.contents.any { it.type == ContentType.VIDEO }
        
        // Check if delivery method is set (indicates video ad)
        val hasDelivery = creative.delivery != null
        
        return hasVideoContent || hasDelivery
    }
    
    /**
     * Check if ad data contains video creatives.
     */
    fun hasVideoCreative(adData: AdData?): Boolean {
        if (adData == null) return false
        return adData.creatives.any { isVideoCreative(it) }
    }
    
    /**
     * Set a demo response from the Video Ad Demo feature.
     * This temporarily replaces the current response for demo purposes.
     */
    fun setDemoResponse(demoResponse: DecisionResponse, scenario: String? = null) {
        _response.value = demoResponse
        _decisionResponse.value = demoResponse
        _formattedResponse.value = formatResponseToJson(demoResponse)
        _videoDemoScenario.value = scenario
    }
    
    /**
     * Get the video demo HTTP request for display.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun getVideoDemoHttpRequest(): String {
        val scenario = _videoDemoScenario.value ?: return "No scenario data available"
        
        return try {
            val requestBody = """
{
  "placements": [
    {
      "key": "$scenario",
      "format": "video"
    }
  ]
}
            """.trimIndent()
            
            // Format as an HTTP request with headers
            val sb = StringBuilder()
            sb.append("POST /v1/decision HTTP/1.1\n")
            sb.append("Host: 10.0.2.2:8080\n")
            sb.append("Content-Type: application/json\n")
            sb.append("Accept-Language: en\n")
            sb.append("X-Decision-Version: 2025-11-01\n")
            sb.append("\n")
            sb.append(requestBody)
            
            sb.toString()
        } catch (e: Exception) {
            "Error generating video demo HTTP request: ${e.message}"
        }
    }
}
