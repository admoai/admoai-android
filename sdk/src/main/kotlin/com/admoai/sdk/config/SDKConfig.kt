package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import io.ktor.client.engine.HttpClientEngine

/**
 * @property playgroundToken Set it to talk to a playground; leave it unset (null or blank) for live.
 *   When set, decisions go to `/v1/playground/decision` with `X-Playground-Token`; nothing else changes.
 */
data class SDKConfig(
    val baseUrl: String,
    val apiVersion: String? = null,
    val enableLogging: Boolean = false,
    val defaultLanguage: String? = null,
    val networkClientEngine: HttpClientEngine? = null,
    val networkRequestTimeoutMs: Long = 10000L,
    val networkConnectTimeoutMs: Long = 10000L,
    val networkSocketTimeoutMs: Long = 10000L,
    val playgroundToken: String? = null
) : Clearable<SDKConfig> {

    // Pre-playgroundToken constructor and copy, kept so code compiled against earlier 1.x still links.
    constructor(
        baseUrl: String,
        apiVersion: String? = null,
        enableLogging: Boolean = false,
        defaultLanguage: String? = null,
        networkClientEngine: HttpClientEngine? = null,
        networkRequestTimeoutMs: Long = 10000L,
        networkConnectTimeoutMs: Long = 10000L,
        networkSocketTimeoutMs: Long = 10000L
    ) : this(
        baseUrl,
        apiVersion,
        enableLogging,
        defaultLanguage,
        networkClientEngine,
        networkRequestTimeoutMs,
        networkConnectTimeoutMs,
        networkSocketTimeoutMs,
        null
    )

    fun copy(
        baseUrl: String = this.baseUrl,
        apiVersion: String? = this.apiVersion,
        enableLogging: Boolean = this.enableLogging,
        defaultLanguage: String? = this.defaultLanguage,
        networkClientEngine: HttpClientEngine? = this.networkClientEngine,
        networkRequestTimeoutMs: Long = this.networkRequestTimeoutMs,
        networkConnectTimeoutMs: Long = this.networkConnectTimeoutMs,
        networkSocketTimeoutMs: Long = this.networkSocketTimeoutMs
    ): SDKConfig = SDKConfig(
        baseUrl,
        apiVersion,
        enableLogging,
        defaultLanguage,
        networkClientEngine,
        networkRequestTimeoutMs,
        networkConnectTimeoutMs,
        networkSocketTimeoutMs,
        playgroundToken
    )

    override fun resetToDefaults(): SDKConfig = SDKConfig(
        baseUrl = this.baseUrl,
        apiVersion = this.apiVersion,
        enableLogging = false,
        defaultLanguage = null,
        networkClientEngine = this.networkClientEngine,
        networkRequestTimeoutMs = 10000L,
        networkConnectTimeoutMs = 10000L,
        networkSocketTimeoutMs = 10000L,
        playgroundToken = this.playgroundToken
    )

    override fun clear(): SDKConfig = resetToDefaults()

    // The data-class default, with the token redacted so a logged config never leaks it.
    override fun toString(): String {
        val token = if (playgroundToken == null) "null" else "<redacted>"
        return "SDKConfig(baseUrl=$baseUrl, apiVersion=$apiVersion, enableLogging=$enableLogging, " +
            "defaultLanguage=$defaultLanguage, networkClientEngine=$networkClientEngine, " +
            "networkRequestTimeoutMs=$networkRequestTimeoutMs, networkConnectTimeoutMs=$networkConnectTimeoutMs, " +
            "networkSocketTimeoutMs=$networkSocketTimeoutMs, playgroundToken=$token)"
    }
}
