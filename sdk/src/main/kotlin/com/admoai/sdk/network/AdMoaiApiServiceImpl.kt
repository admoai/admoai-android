package com.admoai.sdk.network

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.Closeable // Added import

// API Key header removed as per requirements

// AdMoaiApiServiceImpl implements AdMoaiApiService and Closeable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class AdMoaiApiServiceImpl(
    private val sdkConfig: SDKConfig,
    private val engine: HttpClientEngine? // Engine can be null, CIO is default
) : AdMoaiApiService, Closeable { // Implement Closeable

    // Serializer for request/response bodies
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Important for sending all fields, even if default
        explicitNulls = false // Don't include null values in output JSON
        prettyPrint = sdkConfig.enableLogging // For debugging purposes
    }

    // HTTP Client Setup
    private val httpClient: HttpClient = HttpClient(engine ?: CIO.create()) {
        // JSON
        install(ContentNegotiation) {
            json(json)
        }
        // Logging
        if (sdkConfig.enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        // Ensure logs are noticeable and clearly from Ktor
                        println("[AdMoaiSDK Ktor]: $message")
                    }
                }
                level = LogLevel.ALL // Log everything: headers, body, etc.
            }
        }
        // Timeout
        install(HttpTimeout) {
            requestTimeoutMillis = sdkConfig.networkRequestTimeoutMs
            connectTimeoutMillis = sdkConfig.networkConnectTimeoutMs
            socketTimeoutMillis = sdkConfig.networkSocketTimeoutMs
        }
        // Default request parameters
        defaultRequest {
            url(sdkConfig.baseUrl) // Base URL for all requests
            // Note: defaultLanguage is handled per-request in requestAds if needed
        }
    }

    private fun logMessage(prefix: String, message: String, details: Any? = null) {
        if (sdkConfig.enableLogging) {
            val fullMessage = details?.let { "$prefix: $message - Details: $it" } ?: "$prefix: $message"
            println(fullMessage) // Using println for direct test output
        }
    }

    private fun logRequest(message: String, request: Any?) {
        logMessage("[AdMoaiSDK Request]", message, request)
    }

    private fun logResponse(message: String, response: Any?) {
        logMessage("[AdMoaiSDK Response]", message, response)
    }

    private fun logError(message: String, throwable: Throwable) {
        logMessage("[AdMoaiSDK Error]", "$message: ${throwable.message}", throwable.cause ?: throwable)
        // For testing, ensure stack trace is visible
        if (sdkConfig.enableLogging) { // Or a more specific test-only flag
            throwable.printStackTrace()
        }
    }
    private fun logInfo(message: String) {
        if (sdkConfig.enableLogging) {
            println("[AdMoaiSDK Info]: $message")
        }
    }

    override fun requestAds(request: DecisionRequest): Flow<DecisionResponse> = flow {
        logRequest("Attempting to request ads", request)
        try {
            val response: DecisionResponse = httpClient.post("v1/decision") { // Endpoint path relative to baseUrl
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                // Add Accept-Language header if defaultLanguage is set in SDKConfig
                sdkConfig.defaultLanguage?.let { lang ->
                    header(HttpHeaders.AcceptLanguage, lang)
                    logInfo("Added Accept-Language header: $lang")
                }
                setBody(request) // DecisionRequest is set as the body
            }.body() // Deserialize the response body to DecisionResponse

            logResponse("Ads decision successfully received", response)
            emit(response)
        } catch (e: CancellationException) {
            logError("Ktor request cancelled for /v1/decision", e)
            throw e // Re-throw cancellation exceptions
        } catch (e: Exception) {
            logError("Ktor request failed for /v1/decision", e)
            throw AdMoaiNetworkException(
                message = "Network request failed: ${e.message}",
                cause = e
            )
        }
    }.flowOn(Dispatchers.IO) // Ensure network operations run on IO dispatcher for actual SDK usage


    override fun fireTrackingUrl(url: String): Flow<Unit> = flow {
        logRequest("Firing tracking URL", url)
        try {
            val response: HttpResponse = httpClient.get(url) // Using provided URL directly
            // No custom headers like API key needed for tracking, unless specified by AdMoai requirements
            logResponse("Tracking URL response status: ${response.status}", null)
            // Optionally, check response.status for success (e.g., 200 OK, 204 No Content)
            if (!response.status.isSuccess()) {
                logError("Tracking URL request failed with status ${response.status}", Exception("Non-success status code"))
                // Decide if this should throw an exception or just log
            }
            emit(Unit)
        } catch (e: CancellationException) {
            logError("Tracking request cancelled for URL: $url", e)
            throw e
        } catch (e: Exception) {
            logError("Tracking request failed for URL: $url", e)
            // Decide if this should throw an exception or just log
            // For now, let's wrap and throw to indicate failure, consistent with requestAds
            throw AdMoaiNetworkException("Tracking request failed for URL: $url. Error: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)


    override fun getHttpRequestData(request: DecisionRequest): AdmoaiHttpRequest {
        // This is a simplified example. A real implementation would need to
        // fully construct the request as Ktor would, including headers and body serialization.
        // This is non-trivial to do perfectly outside of Ktor's internal mechanisms.
        logRequest("Preparing HTTP request data (simplified)", request)

        val url = sdkConfig.baseUrl.trimEnd('/') + "/v1/decision"
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = ContentType.Application.Json.toString()
        headers["Accept"] = ContentType.Application.Json.toString()
        sdkConfig.defaultLanguage?.let { lang ->
            headers[HttpHeaders.AcceptLanguage] = lang
        }
        // Body serialization (simplified, real Ktor handles this complexly)
        val body = json.encodeToString(request)

        return AdmoaiHttpRequest(
            url = url,
            method = "POST",
            headers = headers,
            body = body
        )
    }

    override fun close() {
        logInfo("Closing AdMoaiApiService and Ktor HttpClient.")
        httpClient.close()
    }
}
