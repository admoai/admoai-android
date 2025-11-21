package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import io.ktor.client.engine.HttpClientEngine

data class SDKConfig(
    val baseUrl: String,
    val apiVersion: String? = null,
    val enableLogging: Boolean = false,
    val defaultLanguage: String? = null,
    val networkClientEngine: HttpClientEngine? = null,
    val networkRequestTimeoutMs: Long = 10000L,
    val networkConnectTimeoutMs: Long = 10000L,
    val networkSocketTimeoutMs: Long = 10000L
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
