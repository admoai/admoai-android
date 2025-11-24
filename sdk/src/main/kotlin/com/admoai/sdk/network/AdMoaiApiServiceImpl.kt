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
import java.io.Closeable

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class AdMoaiApiServiceImpl(
    private val sdkConfig: SDKConfig,
    private val engine: HttpClientEngine?
) : AdMoaiApiService, Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
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
            val response: DecisionResponse = httpClient.post("v1/decision") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                sdkConfig.defaultLanguage?.let { lang ->
                    header(HttpHeaders.AcceptLanguage, lang)
                }
                sdkConfig.apiVersion?.let { version ->
                    header("X-Decision-Version", version)
                }
                setBody(request)
            }.body()

            emit(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AdMoaiNetworkException(
                message = "Network request failed: ${e.message}",
                cause = e
            )
        }
    }.flowOn(Dispatchers.IO)


    override fun fireTrackingUrl(url: String): Flow<Unit> = flow {
        try {
            val response: HttpResponse = httpClient.get(url)
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

    override fun close() {
        httpClient.close()
    }
}
