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
import com.admoai.sdk.model.common.normalizeSessionId
import com.admoai.sdk.model.common.sessionIdRejectionReason
import com.admoai.sdk.model.request.User
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.network.AdMoaiApiService
import com.admoai.sdk.network.AdMoaiApiServiceImpl
import com.admoai.sdk.network.AdmoaiHttpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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

    /** Sticky publisher-provided Journey session id; seeded into each builder, per-request setter wins. */
    @Volatile
    private var stickySessionId: String? = null

    /** Test seam: when set, [log] routes here instead of `android.util.Log` (a stub in JVM unit tests). */
    @VisibleForTesting
    internal var logSink: ((String, LogLevel, Throwable?) -> Unit)? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }

    @VisibleForTesting
    internal var sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    private fun applyConfiguration(newConfig: SDKConfig) {
        this.sdkConfig = newConfig
        this.apiService = AdMoaiApiServiceImpl(newConfig, newConfig.networkClientEngine) { message ->
            log(message, LogLevel.WARNING)
        }
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

    /**
     * Sets the sticky Journey session id (seeded into each new builder). Rotation is explicit — the
     * SDK never generates or changes it. Blank/over-length values log a PII-safe reason token only.
     */
    fun setSessionId(sessionId: String?) {
        sessionIdRejectionReason(sessionId)?.let {
            log("Journey sessionId rejected ($it)", LogLevel.WARNING)
        }
        this.stickySessionId = normalizeSessionId(sessionId)
    }

    fun clearSessionId() {
        this.stickySessionId = null
    }

    fun getSessionId(): String? = this.stickySessionId

    fun createRequestBuilder(): DecisionRequestBuilder =
        DecisionRequestBuilder(stickySessionId) { reason ->
            log("Journey sessionId rejected ($reason)", LogLevel.WARNING)
        }

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

        val mergedTargeting: Targeting? = if (requestTargeting?.geo != null || requestTargeting?.location != null || requestTargeting?.destination != null || finalCustomTargetingInfo.isNotEmpty()) {
            Targeting(
                geo = requestTargeting?.geo,
                location = requestTargeting?.location,
                destination = requestTargeting?.destination,
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

        if ((initialDecisionRequest.sessionId != null || initialDecisionRequest.journeyOpt != null) &&
            sdkConfig?.apiVersion == null
        ) {
            log("Journey context set but apiVersion is null; Journey will be ignored.", LogLevel.WARNING)
        }

        return DecisionRequest(
            placements = initialDecisionRequest.placements,
            user = mergedUser,
            targeting = mergedTargeting,
            app = appObject,
            device = deviceObject,
            // Journey context flows through the builder; do not re-inject sticky here (honors a
            // per-request clear). Normalize so a directly-built DecisionRequest is trimmed too.
            sessionId = normalizeSessionId(initialDecisionRequest.sessionId),
            journeyOpt = initialDecisionRequest.journeyOpt,
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

    fun fireTracking(url: String) {
        val currentApiService = apiService ?: return
        if (!isAbsoluteHttpUrl(url)) {
            // PII-safe: never log the opaque tracking URL / e= token, only a redacted reason.
            log("Tracking URL rejected: not an absolute http(s) URL", LogLevel.WARNING)
            return
        }
        sdkScope.launch {
            try {
                currentApiService.fireTrackingUrl(url).collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // fire-and-forget: network failures never propagate to the caller
            }
        }
    }

    /**
     * Fires the custom_event Journey completion beacon for [key] verbatim. Quiet by design: a no-op
     * when there are no completions (normal ads and final_stage serves carry none); warns only on a
     * key-miss against a non-empty list, and when firing with a null apiVersion (billing-critical —
     * the callback would hit the legacy tracking handler and not record).
     */
    fun fireCompletion(trackingInfo: TrackingInfo, key: String) {
        val completions = trackingInfo.completions
        if (completions.isNullOrEmpty()) return
        val url = completions.firstOrNull { it.key == key }?.url
        if (url == null) {
            log("Journey completion key not found in completions list", LogLevel.WARNING)
            return
        }
        if (sdkConfig?.apiVersion == null) {
            log("Firing Journey completion without apiVersion; it may not record.", LogLevel.WARNING)
        }
        fireTracking(url)
    }

    private fun isAbsoluteHttpUrl(url: String): Boolean = try {
        val uri = java.net.URI(url)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    fun fireImpression(trackingInfo: TrackingInfo, key: String = "default") {
        val url = trackingInfo.impressions?.find { it.key == key }?.url ?: return
        fireTracking(url)
    }

    fun fireClick(trackingInfo: TrackingInfo, key: String = "default") {
        val url = trackingInfo.clicks?.find { it.key == key }?.url ?: return
        fireTracking(url)
    }

    fun fireCustomEvent(trackingInfo: TrackingInfo, key: String) {
        val url = trackingInfo.custom?.find { it.key == key }?.url ?: return
        fireTracking(url)
    }

    fun fireVideoEvent(trackingInfo: TrackingInfo, key: String) {
        val url = trackingInfo.videoEvents?.find { it.key == key }?.url ?: return
        fireTracking(url)
    }

    internal fun log(message: String, level: LogLevel = LogLevel.INFO, throwable: Throwable? = null) {
        // WARNING/ERROR always emit (misuse must surface even with debug logging off).
        if (sdkConfig?.enableLogging == true || level == LogLevel.WARNING || level == LogLevel.ERROR) {
            logSink?.let { it(message, level, throwable); return }
            val tag = "AdMoaiSDK"
            when (level) {
                LogLevel.DEBUG -> android.util.Log.d(tag, message, throwable)
                LogLevel.INFO -> android.util.Log.i(tag, message, throwable)
                LogLevel.WARNING -> android.util.Log.w(tag, message, throwable)
                LogLevel.ERROR -> android.util.Log.e(tag, message, throwable)
            }
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
        fun initialize(baseUrl: String, apiVersion: String? = null, enableLogging: Boolean = false, defaultLanguage: String? = null, sessionId: String? = null) {
            val config = SDKConfig(
                baseUrl = baseUrl,
                apiVersion = apiVersion,
                enableLogging = enableLogging,
                defaultLanguage = defaultLanguage
            )
            initialize(config)
            // initialize() runs once at startup before any request, so setting sessionId here is safe.
            sessionId?.let { getInstance().setSessionId(it) }
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