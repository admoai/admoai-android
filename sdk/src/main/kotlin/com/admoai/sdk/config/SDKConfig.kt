package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import io.ktor.client.engine.HttpClientEngine // Ensure this import is present

/**
 * Configuration for the AdMoai SDK.
 * 
 * @property baseUrl Base URL for the AdMoai API
 * @property apiVersion Optional API version to send in X-Decision-Version header (e.g., "2025-11-01")
 * @property enableLogging Whether to enable debug logging
 * @property defaultLanguage Preferred language for API responses (e.g., "en-US")
 * @property networkClientEngine Custom HTTP client engine for network requests
 * @property networkRequestTimeoutMs Timeout for the entire request in milliseconds
 * @property networkConnectTimeoutMs Timeout for establishing a connection in milliseconds
 * @property networkSocketTimeoutMs Timeout for socket reads in milliseconds
 */
data class SDKConfig(
    val baseUrl: String,
    val apiVersion: String? = null,
    val enableLogging: Boolean = false,
    val defaultLanguage: String? = null,
    val networkClientEngine: HttpClientEngine? = null, // Corrected type to HttpClientEngine
    val networkRequestTimeoutMs: Long = 10000L, // Default 10 seconds
    val networkConnectTimeoutMs: Long = 10000L, // Default 10 seconds
    val networkSocketTimeoutMs: Long = 10000L   // Default 10 seconds
) : Clearable<SDKConfig> {

    override fun resetToDefaults(): SDKConfig {
        // Retain base URL, reset others to typical defaults
        return SDKConfig(
            baseUrl = this.baseUrl, // Retain baseUrl
            apiVersion = this.apiVersion, // Retain apiVersion
            enableLogging = false,
            defaultLanguage = null,
            networkClientEngine = this.networkClientEngine, // Retain configured engine or null
            // Reset timeouts to their default values defined in the primary constructor
            networkRequestTimeoutMs = 10000L,
            networkConnectTimeoutMs = 10000L,
            networkSocketTimeoutMs = 10000L
        )
    }

    override fun clear(): SDKConfig {
        // Similar to resetToDefaults for this specific config,
        // as clearing doesn't make sense for apiKey/baseUrl.
        // If there were other more dynamic fields, they could be nulled out.
        return resetToDefaults()
    }
}
