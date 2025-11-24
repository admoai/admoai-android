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
        return SDKConfig(
            baseUrl = this.baseUrl,
            apiVersion = this.apiVersion,
            enableLogging = false,
            defaultLanguage = null,
            networkClientEngine = this.networkClientEngine,
            networkRequestTimeoutMs = 10000L,
            networkConnectTimeoutMs = 10000L,
            networkSocketTimeoutMs = 10000L
        )
    }

    override fun clear(): SDKConfig {
        return resetToDefaults()
    }
}
