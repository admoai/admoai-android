package com.admoai.sdk.network

import com.admoai.sdk.SDK_VERSION
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiException
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.exception.AdMoaiValidationException
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.response.DecisionResponse
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
import java.io.Closeable

/** Builds the API-deprecation warning message, or null when not deprecated. Pure/testable. */
internal fun deprecationWarningMessage(deprecated: String?, sunset: String?): String? {
    if (!deprecated.equals("true", ignoreCase = true)) return null
    return buildString {
        append("AdMoai API version is deprecated; upgrade the SDK / apiVersion.")
        if (!sunset.isNullOrBlank()) append(" Sunset: $sunset.")
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class AdMoaiApiServiceImpl(
    private val sdkConfig: SDKConfig,
    private val engine: HttpClientEngine?,
    private val logWarning: ((String) -> Unit)? = null
) : AdMoaiApiService, Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true // null -> default, invalid-enum -> default (Tolerant Reader)
        prettyPrint = sdkConfig.enableLogging
    }

    private val httpClient: HttpClient = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) {
            json(json)
        }
        if (sdkConfig.enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("[Ktor]: $message")
                    }
                }
                level = LogLevel.ALL
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = sdkConfig.networkRequestTimeoutMs
            connectTimeoutMillis = sdkConfig.networkConnectTimeoutMs
            socketTimeoutMillis = sdkConfig.networkSocketTimeoutMs
        }
        defaultRequest {
            url(sdkConfig.baseUrl)
        }
    }


    override fun requestAds(request: DecisionRequest): Flow<DecisionResponse> = flow {
        try {
            val httpResponse = httpClient.post("v1/decision") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "AdMoaiSDK/$SDK_VERSION")
                sdkConfig.defaultLanguage?.let { lang ->
                    header(HttpHeaders.AcceptLanguage, lang)
                }
                sdkConfig.apiVersion?.let { version ->
                    header("X-Decision-Version", version)
                }
                setBody(request)
            }
            warnIfDeprecated(httpResponse)

            // Gate on status BEFORE decoding. Ktor's `expectSuccess` defaults to false and no
            // HttpResponseValidator is installed, so without this the engine's JSON error envelope
            // — which is shape-identical to a success envelope — deserializes cleanly into
            // DecisionResponse(success=false, data=null, errors=[...]) and is emitted as a normal
            // Flow item. A publisher checking `data.isEmpty()` then reads a rejected request as
            // no-fill. iOS and Flutter both branch on status and raise; this makes the three agree.
            if (!httpResponse.status.isSuccess()) {
                throw errorForStatus(httpResponse)
            }

            val response: DecisionResponse = httpResponse.body()

            emit(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdMoaiException) {
            // Already a typed SDK failure (e.g. the validation error raised just above) — rethrow
            // rather than re-wrapping it as a generic network error and losing `errors[]`.
            throw e
        } catch (e: Exception) {
            throw AdMoaiNetworkException(
                message = "Network request failed: ${e.message}",
                cause = e
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Maps a non-2xx decision response to a typed SDK exception.
     *
     * 422 is the engine's validation rejection and is the one status whose body is worth parsing:
     * its `errors[]` tells the publisher which placement key, custom-targeting key or journeyOpt
     * value was refused. Everything else surfaces as [AdMoaiNetworkException] carrying the status
     * code, matching the pre-existing contract asserted by Ktor3IntegrationTest.
     *
     * A 422 whose body is absent or unparseable still raises a validation exception, just with an
     * empty `errors` list — the status alone is enough to know the request was rejected, and
     * degrading to "network error" here would lose that.
     */
    private suspend fun errorForStatus(response: HttpResponse): AdMoaiException {
        val status = response.status
        if (status == HttpStatusCode.UnprocessableEntity) {
            val errors = runCatching { response.body<DecisionResponse>().errors }
                .getOrNull()
                .orEmpty()
            return AdMoaiValidationException(errors)
        }
        val kind = if (status.value in 500..599) "Server" else "Client"
        return AdMoaiNetworkException(
            message = "$kind error: ${status.value} ${status.description}",
            statusCode = status.value
        )
    }

    override fun fireTrackingUrl(url: String): Flow<Unit> = flow {
        try {
            val response: HttpResponse = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "AdMoaiSDK/$SDK_VERSION")
                sdkConfig.defaultLanguage?.let { lang ->
                    header(HttpHeaders.AcceptLanguage, lang)
                }
                // Tracking version-routes on X-Tracking-Version, NOT X-Decision-Version; the wrong
                // header silently falls back to the legacy handler and breaks CPT completion.
                sdkConfig.apiVersion?.let { version ->
                    header("X-Tracking-Version", version)
                }
            }
            if (!response.status.isSuccess()) {
                throw AdMoaiNetworkException("Tracking request failed with status ${response.status}")
            }
            emit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AdMoaiNetworkException("Tracking request failed: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)

    override fun getHttpRequestData(request: DecisionRequest): AdmoaiHttpRequest {
        val url = sdkConfig.baseUrl.trimEnd('/') + "/v1/decision"
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = ContentType.Application.Json.toString()
        headers["Accept"] = ContentType.Application.Json.toString()
        headers[HttpHeaders.UserAgent] = "AdMoaiSDK/$SDK_VERSION"
        sdkConfig.defaultLanguage?.let { lang ->
            headers[HttpHeaders.AcceptLanguage] = lang
        }
        sdkConfig.apiVersion?.let { version ->
            headers["X-Decision-Version"] = version
        }
        val body = json.encodeToString(request)

        return AdmoaiHttpRequest(
            url = url,
            method = "POST",
            headers = headers,
            body = body
        )
    }

    private fun warnIfDeprecated(response: HttpResponse) {
        val message = deprecationWarningMessage(
            deprecated = response.headers["X-API-Deprecated"],
            sunset = response.headers["Sunset"] ?: response.headers["X-API-Sunset"]
        ) ?: return
        logWarning?.invoke(message)
    }

    override fun close() {
        httpClient.close()
    }
}
