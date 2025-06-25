package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.config.UserConfig
import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.AppConfig
import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.model.request.App
import com.admoai.sdk.model.request.CustomTargetingInfo
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.Device
import com.admoai.sdk.model.request.Targeting
import com.admoai.sdk.model.request.User
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.network.AdMoaiApiService
import com.admoai.sdk.network.AdMoaiApiServiceImpl
import com.admoai.sdk.network.AdmoaiHttpRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import androidx.annotation.VisibleForTesting // For @VisibleForTesting
import java.io.Closeable // Added import

/**
 * Main class for interacting with the AdMoai SDK.
 */
class Admoai private constructor() {

    private var sdkConfig: SDKConfig? = null
    internal var apiService: AdMoaiApiService? = null
        internal set(value) {
            // Close the old apiService if it's Closeable and a new one is being set
            (field as? Closeable)?.let {
                try {
                    it.close()
                    log("Old ApiService closed successfully.", LogLevel.DEBUG)
                } catch (e: Exception) {
                    log("Error closing old ApiService: ${e.message}", LogLevel.ERROR, e)
                }
            }
            field = value
        }

    private val configurationMutex = Mutex()

    // Configuration properties
    private var userConfig: UserConfig? = null
    private var deviceConfig: DeviceConfig? = null
    private var appConfig: AppConfig? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log("Coroutine uncaught exception: ${throwable.localizedMessage}", LogLevel.ERROR, throwable)
    }

    internal val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    private fun applyConfiguration(newConfig: SDKConfig) {
        this.sdkConfig = newConfig
        // The assignment to this.apiService will trigger the custom setter, closing the old one.
        this.apiService = AdMoaiApiServiceImpl(newConfig, newConfig.networkClientEngine)
        log("AdMoai SDK configured. Logging: ${newConfig.enableLogging}, BaseURL: ${newConfig.baseUrl}, Language: ${newConfig.defaultLanguage ?: "Not set"}")
    }

    suspend fun configure(newConfig: SDKConfig) {
        configurationMutex.withLock {
            if (!isInitialized()) {
                throw AdMoaiConfigurationException("AdMoai SDK has not been initialized. Call Admoai.initialize() first.")
            }
            
            // If critical parts of config like baseUrl change, we need a new ApiService.
            // The current logic in applyConfiguration handles creating a new service.
            applyConfiguration(newConfig)
        }
    }

    fun setUserConfig(newUserConfig: UserConfig?) {
        this.userConfig = newUserConfig
        log("UserConfig updated.")
    }

    fun clearUserConfig() {
        this.userConfig = null
        log("UserConfig cleared.")
    }

    fun getUserConfig(): UserConfig? = this.userConfig

    fun setDeviceConfig(newDeviceConfig: DeviceConfig?) {
        this.deviceConfig = newDeviceConfig
        log("DeviceConfig updated.")
    }

    fun clearDeviceConfig() {
        this.deviceConfig = null
        log("DeviceConfig cleared.")
    }

    fun getDeviceConfig(): DeviceConfig? = this.deviceConfig

    fun setAppConfig(newAppConfig: AppConfig?) {
        this.appConfig = newAppConfig
        log("AppConfig updated.")
    }

    fun clearAppConfig() {
        this.appConfig = null
        log("AppConfig cleared.")
    }

    fun getAppConfig(): AppConfig? = this.appConfig

    fun createRequestBuilder(): DecisionRequestBuilder = DecisionRequestBuilder()

    /**
     * Merges the initial request with global settings.
     * 
     * @param initialDecisionRequest The initial decision request
     * @return A new DecisionRequest with merged data
     */
    fun prepareFinalDecisionRequest(initialDecisionRequest: DecisionRequest): DecisionRequest {
        val requestUser = initialDecisionRequest.user
        val globalUser = this.userConfig
        val mergedUserId = requestUser?.id ?: globalUser?.id
        val mergedUserIp = requestUser?.ip ?: globalUser?.ip
        val mergedUserTimezone = requestUser?.timezone ?: globalUser?.timezone ?: this.deviceConfig?.timezone
        val mergedUserConsent = requestUser?.consent ?: globalUser?.consentData
        val mergedUser: User? = if (mergedUserId != null || mergedUserIp != null || mergedUserTimezone != null || mergedUserConsent != null) {
            User(id = mergedUserId, ip = mergedUserIp, timezone = mergedUserTimezone, consent = mergedUserConsent)
        } else { null }

        val requestTargeting = initialDecisionRequest.targeting
        val finalCustomTargetingInfo = mutableListOf<CustomTargetingInfo>()
        // Add any custom targeting from the original request if not already added
        requestTargeting?.custom?.let { finalCustomTargetingInfo.addAll(it) }

        // Device and app data are now added as top-level fields in the JSON request
        // when the respective collectDeviceData and collectAppData flags are enabled
        // No need to add to custom targeting anymore

        val mergedTargeting: Targeting? = if (requestTargeting?.geo != null || requestTargeting?.location != null || finalCustomTargetingInfo.isNotEmpty()) {
            Targeting(
                geo = requestTargeting?.geo,
                location = requestTargeting?.location,
                custom = if (finalCustomTargetingInfo.isNotEmpty()) finalCustomTargetingInfo.toList() else null
            )
        } else { null }

        // Create app object if app data collection is enabled
        val appObject = if (initialDecisionRequest.collectAppData && this.appConfig != null) {
            val appConf = this.appConfig!!
            App(
                name = appConf.appName,
                version = appConf.appVersion,
                identifier = appConf.packageName,
                buildNumber = appConf.buildNumber,
                language = appConf.language
            )
        } else null

        // Create device object if device data collection is enabled
        val deviceObject = if (initialDecisionRequest.collectDeviceData && this.deviceConfig != null) {
            val devConf = this.deviceConfig!!
            Device(
                os = devConf.osName,
                osVersion = devConf.osVersion,
                model = devConf.model,
                manufacturer = devConf.manufacturer,
                id = devConf.deviceId,
                timezone = devConf.timezone,
                language = devConf.language
            )
        } else null

        return DecisionRequest(
            placements = initialDecisionRequest.placements,
            user = mergedUser,
            targeting = mergedTargeting,
            app = appObject,
            device = deviceObject,
            collectAppData = initialDecisionRequest.collectAppData,
            collectDeviceData = initialDecisionRequest.collectDeviceData
        )
    }

    /**
     * Request ads from the AdMoai API.
     * 
     * @param initialDecisionRequest The decision request
     * @return Flow emitting the decision response
     * @throws AdMoaiConfigurationException if the SDK is not initialized or configured
     */
    fun requestAds(initialDecisionRequest: DecisionRequest): Flow<DecisionResponse> {
        if (!isInitialized() || sdkConfig == null) {
            throw AdMoaiConfigurationException("AdMoai SDK not initialized or configured.")
        }
        val currentApiService = apiService ?: throw AdMoaiConfigurationException("ApiService not initialized.")
        log("Initial request for placements: ${initialDecisionRequest.placements.joinToString { it.key }}.")
        val finalDecisionRequest = prepareFinalDecisionRequest(initialDecisionRequest)
        log("Final request after merging: $finalDecisionRequest")
        return currentApiService.requestAds(finalDecisionRequest)
    }

    /**
     * Get the HTTP request data that would be sent to the AdMoai API without actually sending it.
     * 
     * @param initialDecisionRequest The decision request to prepare
     * @return The HTTP request data that would be sent
     * @throws AdMoaiConfigurationException if the SDK is not initialized or configured
     */
    fun getHttpRequestData(initialDecisionRequest: DecisionRequest): AdmoaiHttpRequest {
        if (!isInitialized() || sdkConfig == null) {
            throw AdMoaiConfigurationException("AdMoai SDK not initialized or configured.")
        }
        val currentApiService = apiService ?: throw AdMoaiConfigurationException("ApiService not initialized.")
        val finalDecisionRequest = prepareFinalDecisionRequest(initialDecisionRequest)
        log("Prepared final decision request for getHttpRequestData: $finalDecisionRequest")
        return currentApiService.getHttpRequestData(finalDecisionRequest)
    }

    /**
     * Track an impression event.
     *
     * @param trackingInfo The tracking information containing impression URLs
     * @param key The key for the specific impression URL to fire
     * @return Flow that completes when the tracking request is done
     */
    fun fireImpression(trackingInfo: TrackingInfo, key: String = "default"): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit).also { log("ApiService not initialized. Cannot fire impression.", LogLevel.WARNING) }
        val url = trackingInfo.impressions?.find { it.key == key }?.url
        return if (url != null) {
            log("Firing impression for key '$key': $url")
            currentApiService.fireTrackingUrl(url)
        } else {
            log("No impression tracking URL found for key '$key'.", LogLevel.WARNING)
            flowOf(Unit)
        }
    }

    /**
     * Track a click event.
     *
     * @param trackingInfo The tracking information containing click URLs
     * @param key The key for the specific click URL to fire
     * @return Flow that completes when the tracking request is done
     */
    fun fireClick(trackingInfo: TrackingInfo, key: String = "default"): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit).also { log("ApiService not initialized. Cannot fire click.", LogLevel.WARNING) }
        val url = trackingInfo.clicks?.find { it.key == key }?.url
        return if (url != null) {
            log("Firing click for key '$key': $url")
            currentApiService.fireTrackingUrl(url)
        } else {
            log("No click tracking URL found for key '$key'.", LogLevel.WARNING)
            flowOf(Unit)
        }
    }

    /**
     * Track a custom event.
     *
     * @param trackingInfo The tracking information containing custom event URLs
     * @param key The key identifying the specific custom event to track
     * @return Flow that completes when the tracking request is done
     */
    fun fireCustomEvent(trackingInfo: TrackingInfo, key: String): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit).also { log("ApiService not initialized. Cannot fire custom event.", LogLevel.WARNING) }
        val url = trackingInfo.custom?.find { it.key == key }?.url
        return if (url != null) {
            log("Firing custom event for key '$key': $url")
            currentApiService.fireTrackingUrl(url)
        } else {
            log("No custom event tracking URL found for key '$key'.", LogLevel.WARNING)
            flowOf(Unit)
        }
    }

    internal fun log(message: String, level: LogLevel = LogLevel.INFO, throwable: Throwable? = null) {
        if (sdkConfig?.enableLogging == true || level == LogLevel.ERROR) {
            val logMessage = "[AdMoaiSDK ${level.name}]: $message"
            println(logMessage)
            throwable?.printStackTrace()
        }
    }

    enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

    companion object {
        @Volatile private var INSTANCE: Admoai? = null
        private val singletonMutex = Mutex()
        private var isSdkInitialized = false

        /**
         * Convenience initializer that matches iOS SDK pattern - only baseUrl required.
         */
        @JvmStatic
        @JvmOverloads
        /**
         * Initialize the SDK with the minimum required configuration.
         * 
         * @param baseUrl The base URL for the AdMoai API (e.g., "https://api.admoai.com")
         * @param enableLogging Whether to enable debug logging (default: false)
         * @param defaultLanguage The preferred language for responses (optional, format: "en-US")
         */
        fun initialize(baseUrl: String, enableLogging: Boolean = false, defaultLanguage: String? = null) {
            val config = SDKConfig(
                baseUrl = baseUrl,
                enableLogging = enableLogging,
                defaultLanguage = defaultLanguage
            )
            initialize(config)
        }
        
        /**
         * Initialize the SDK with a complete SDKConfig object.
         * 
         * @param sdkConfig A configuration object containing all necessary SDK settings
         * @throws IllegalStateException if the SDK has already been initialized
         */
        @JvmStatic
        fun initialize(sdkConfig: SDKConfig) {
            // Use a defensive copy of sdkConfig to prevent external modification
            val immutableSdkConfig = sdkConfig.copy()

            synchronized(singletonMutex) { // Ensure thread-safe initialization
                if (INSTANCE == null) {
                    INSTANCE = Admoai()
                    INSTANCE!!.log("AdMoai SDK initializing with new instance.", LogLevel.INFO)
                } else {
                    INSTANCE!!.log("AdMoai SDK re-initializing existing instance.", LogLevel.INFO)
                }
                // Apply configuration (this will also handle closing the old ApiService via the custom setter)
                INSTANCE!!.applyConfiguration(immutableSdkConfig)
                isSdkInitialized = true
                INSTANCE!!.log("AdMoai SDK initialized successfully.")
            }
        }

        /**
         * Get the singleton instance of the AdMoai SDK.
         * @return The Admoai instance
         * @throws IllegalStateException if the SDK has not been initialized
         */
        @JvmStatic
        fun getInstance(): Admoai {
            return INSTANCE ?: throw IllegalStateException("AdMoai SDK not initialized. Call Admoai.initialize() first.")
        }

        fun isInitialized(): Boolean = isSdkInitialized

        @VisibleForTesting
        internal fun resetForTesting() {
            synchronized(singletonMutex) {
                (INSTANCE?.apiService as? Closeable)?.let {
                    try {
                        it.close()
                        // Instance might be null, so cannot use INSTANCE.log reliably here
                        println("[AdMoaiSDK TestUtil]: ApiService closed successfully during resetForTesting.")
                    } catch (e: Exception) {
                        println("[AdMoaiSDK TestUtil]: Error closing ApiService during resetForTesting: ${e.message}")
                        e.printStackTrace()
                    }
                }
                INSTANCE = null
                isSdkInitialized = false
                println("[AdMoaiSDK TestUtil]: AdMoai SDK has been reset for testing.")
            }
        }
    }
}