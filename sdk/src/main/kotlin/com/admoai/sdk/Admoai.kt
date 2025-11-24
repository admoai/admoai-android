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
import androidx.annotation.VisibleForTesting
import java.io.Closeable
class Admoai private constructor() {

    private var sdkConfig: SDKConfig? = null
    internal var apiService: AdMoaiApiService? = null
        internal set(value) {
            (field as? Closeable)?.close()
            field = value
        }

    private val configurationMutex = Mutex()

    private var userConfig: UserConfig? = null
    private var deviceConfig: DeviceConfig? = null
    private var appConfig: AppConfig? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }

    internal val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    private fun applyConfiguration(newConfig: SDKConfig) {
        this.sdkConfig = newConfig
        this.apiService = AdMoaiApiServiceImpl(newConfig, newConfig.networkClientEngine)
    }

    suspend fun configure(newConfig: SDKConfig) {
        configurationMutex.withLock {
            if (!isInitialized()) {
                throw AdMoaiConfigurationException("SDK not initialized")
            }
            applyConfiguration(newConfig)
        }
    }

    fun setUserConfig(newUserConfig: UserConfig?) {
        this.userConfig = newUserConfig
    }

    fun clearUserConfig() {
        this.userConfig = null
    }

    fun getUserConfig(): UserConfig? = this.userConfig

    fun setDeviceConfig(newDeviceConfig: DeviceConfig?) {
        this.deviceConfig = newDeviceConfig
    }

    fun clearDeviceConfig() {
        this.deviceConfig = null
    }

    fun getDeviceConfig(): DeviceConfig? = this.deviceConfig

    fun setAppConfig(newAppConfig: AppConfig?) {
        this.appConfig = newAppConfig
    }

    fun clearAppConfig() {
        this.appConfig = null
    }

    fun getAppConfig(): AppConfig? = this.appConfig

    fun createRequestBuilder(): DecisionRequestBuilder = DecisionRequestBuilder()

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
        requestTargeting?.custom?.let { finalCustomTargetingInfo.addAll(it) }

        val mergedTargeting: Targeting? = if (requestTargeting?.geo != null || requestTargeting?.location != null || finalCustomTargetingInfo.isNotEmpty()) {
            Targeting(
                geo = requestTargeting?.geo,
                location = requestTargeting?.location,
                custom = if (finalCustomTargetingInfo.isNotEmpty()) finalCustomTargetingInfo.toList() else null
            )
        } else { null }

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
     * Requests ads from the AdMoai decision engine.
     * 
     * @param initialDecisionRequest Decision request with placements and targeting
     * @return Flow emitting the decision response
     * @throws AdMoaiConfigurationException if SDK not initialized
     */
    fun requestAds(initialDecisionRequest: DecisionRequest): Flow<DecisionResponse> {
        if (!isInitialized() || sdkConfig == null) {
            throw AdMoaiConfigurationException("SDK not initialized")
        }
        val currentApiService = apiService ?: throw AdMoaiConfigurationException("API service not initialized")
        val finalDecisionRequest = prepareFinalDecisionRequest(initialDecisionRequest)
        return currentApiService.requestAds(finalDecisionRequest)
    }

    fun getHttpRequestData(initialDecisionRequest: DecisionRequest): AdmoaiHttpRequest {
        if (!isInitialized() || sdkConfig == null) {
            throw AdMoaiConfigurationException("SDK not initialized")
        }
        val currentApiService = apiService ?: throw AdMoaiConfigurationException("API service not initialized")
        val finalDecisionRequest = prepareFinalDecisionRequest(initialDecisionRequest)
        return currentApiService.getHttpRequestData(finalDecisionRequest)
    }

    fun fireImpression(trackingInfo: TrackingInfo, key: String = "default"): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit)
        val url = trackingInfo.impressions?.find { it.key == key }?.url
        return if (url != null) {
            currentApiService.fireTrackingUrl(url)
        } else {
            flowOf(Unit)
        }
    }

    fun fireClick(trackingInfo: TrackingInfo, key: String = "default"): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit)
        val url = trackingInfo.clicks?.find { it.key == key }?.url
        return if (url != null) {
            currentApiService.fireTrackingUrl(url)
        } else {
            flowOf(Unit)
        }
    }

    fun fireCustomEvent(trackingInfo: TrackingInfo, key: String): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit)
        val url = trackingInfo.custom?.find { it.key == key }?.url
        return if (url != null) {
            currentApiService.fireTrackingUrl(url)
        } else {
            flowOf(Unit)
        }
    }

    fun fireVideoEvent(trackingInfo: TrackingInfo, key: String): Flow<Unit> {
        val currentApiService = apiService ?: return flowOf(Unit)
        val url = trackingInfo.videoEvents?.find { it.key == key }?.url
        return if (url != null) {
            currentApiService.fireTrackingUrl(url)
        } else {
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
         * Initialize SDK with minimal configuration.
         */
        @JvmStatic
        @JvmOverloads
        fun initialize(baseUrl: String, apiVersion: String? = null, enableLogging: Boolean = false, defaultLanguage: String? = null) {
            val config = SDKConfig(
                baseUrl = baseUrl,
                apiVersion = apiVersion,
                enableLogging = enableLogging,
                defaultLanguage = defaultLanguage
            )
            initialize(config)
        }
        
        @JvmStatic
        fun initialize(sdkConfig: SDKConfig) {
            val immutableSdkConfig = sdkConfig.copy()
            synchronized(singletonMutex) {
                if (INSTANCE == null) {
                    INSTANCE = Admoai()
                }
                INSTANCE!!.applyConfiguration(immutableSdkConfig)
                isSdkInitialized = true
            }
        }

        @JvmStatic
        fun getInstance(): Admoai {
            return INSTANCE ?: throw IllegalStateException("SDK not initialized")
        }

        fun isInitialized(): Boolean = isSdkInitialized

        @VisibleForTesting
        internal fun resetForTesting() {
            synchronized(singletonMutex) {
                (INSTANCE?.apiService as? Closeable)?.close()
                INSTANCE = null
                isSdkInitialized = false
            }
        }
    }
}