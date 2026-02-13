package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.CreativeMetadata
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.MetadataPriority
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Integration tests that exercise the real Ktor HTTP client (no mocking of the HTTP layer).
 * Uses MockWebServer as a real HTTP server and CIO as the Ktor engine.
 */
class Ktor3IntegrationTest {

    private lateinit var server: MockWebServer

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Admoai.resetForTesting()
    }

    private fun initSdk(
        requestTimeoutMs: Long = 10000L,
        connectTimeoutMs: Long = 10000L,
        socketTimeoutMs: Long = 10000L,
        apiVersion: String? = null,
        defaultLanguage: String? = null
    ) {
        val baseUrl = "http://127.0.0.1:${server.port}/"
        val config = SDKConfig(
            baseUrl = baseUrl,
            enableLogging = false,
            networkClientEngine = CIO.create(),
            networkRequestTimeoutMs = requestTimeoutMs,
            networkConnectTimeoutMs = connectTimeoutMs,
            networkSocketTimeoutMs = socketTimeoutMs,
            apiVersion = apiVersion,
            defaultLanguage = defaultLanguage
        )
        Admoai.initialize(config)
    }

    // --- Request/Response tests ---

    @Test
    fun `POST request sends correct method and path`() = runTest {
        val response = DecisionResponse(success = true, data = emptyList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("banner-top").build()
        Admoai.getInstance().requestAds(request).first()

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/decision", recorded.path)
    }

    @Test
    fun `request sends Content-Type and Accept headers`() = runTest {
        val response = DecisionResponse(success = true, data = emptyList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        Admoai.getInstance().requestAds(request).first()

        val recorded = server.takeRequest()
        assertTrue(
            "Content-Type should be application/json",
            recorded.getHeader("Content-Type")?.contains("application/json") == true
        )
        assertTrue(
            "Accept should be application/json",
            recorded.getHeader("Accept")?.contains("application/json") == true
        )
    }

    @Test
    fun `request sends X-Decision-Version header when configured`() = runTest {
        val response = DecisionResponse(success = true, data = emptyList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk(apiVersion = "2025-11-01")

        val request = DecisionRequestBuilder().addPlacement("test").build()
        Admoai.getInstance().requestAds(request).first()

        val recorded = server.takeRequest()
        assertEquals("2025-11-01", recorded.getHeader("X-Decision-Version"))
    }

    @Test
    fun `request sends Accept-Language header when configured`() = runTest {
        val response = DecisionResponse(success = true, data = emptyList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk(defaultLanguage = "es")

        val request = DecisionRequestBuilder().addPlacement("test").build()
        Admoai.getInstance().requestAds(request).first()

        val recorded = server.takeRequest()
        assertEquals("es", recorded.getHeader("Accept-Language"))
    }

    // --- JSON serialization/deserialization ---

    @Test
    fun `deserializes full response with creatives and tracking`() = runTest {
        val fullResponse = DecisionResponse(
            success = true,
            data = listOf(
                AdData(
                    placement = "banner-top",
                    creatives = listOf(
                        Creative(
                            contents = listOf(
                                Content(key = "headline", value = JsonPrimitive("Buy Now"), type = ContentType.TEXT),
                                Content(key = "coverImage", value = JsonPrimitive("https://img.example.com/ad.jpg"), type = ContentType.IMAGE)
                            ),
                            advertiser = Advertiser(id = "adv-1", name = "Test Brand"),
                            tracking = TrackingInfo(
                                impressions = listOf(TrackingDetail(key = "default", url = "https://track.example.com/imp")),
                                clicks = listOf(TrackingDetail(key = "default", url = "https://track.example.com/click"))
                            ),
                            metadata = CreativeMetadata(
                                adId = "ad-1",
                                creativeId = "cr-1",
                                placementId = "pl-1",
                                templateId = "tpl-1",
                                priority = MetadataPriority.STANDARD
                            )
                        )
                    )
                )
            )
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(fullResponse))
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("banner-top").build()
        val result = Admoai.getInstance().requestAds(request).first()

        assertTrue(result.success)
        assertEquals(1, result.data?.size)
        val ad = result.data!!.first()
        assertEquals("banner-top", ad.placement)
        assertEquals(2, ad.creatives.first().contents.size)
        assertEquals("Buy Now", (ad.creatives.first().contents[0].value as JsonPrimitive).content)
        assertEquals("default", ad.creatives.first().tracking.impressions?.first()?.key)
        assertEquals("https://track.example.com/imp", ad.creatives.first().tracking.impressions?.first()?.url)
        assertEquals(MetadataPriority.STANDARD, ad.creatives.first().metadata.priority)
    }

    @Test
    fun `deserializes response with multiple placements`() = runTest {
        val response = DecisionResponse(
            success = true,
            data = listOf(
                AdData(placement = "banner-top", creatives = emptyList()),
                AdData(placement = "sidebar", creatives = emptyList()),
                AdData(placement = "footer", creatives = emptyList())
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder()
            .addPlacement("banner-top")
            .addPlacement("sidebar")
            .addPlacement("footer")
            .build()
        val result = Admoai.getInstance().requestAds(request).first()

        assertEquals(3, result.data?.size)
        assertEquals("banner-top", result.data!![0].placement)
        assertEquals("sidebar", result.data!![1].placement)
        assertEquals("footer", result.data!![2].placement)
    }

    @Test
    fun `request body contains placement in JSON`() = runTest {
        val response = DecisionResponse(success = true, data = emptyList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(response))
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("my-placement").build()
        Admoai.getInstance().requestAds(request).first()

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue("Body should contain placement name", body.contains("my-placement"))
    }

    // --- HTTP error status codes ---

    @Test
    fun `server 500 error throws AdMoaiNetworkException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        try {
            Admoai.getInstance().requestAds(request).first()
            fail("Should have thrown AdMoaiNetworkException")
        } catch (e: AdMoaiNetworkException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `server 400 error throws AdMoaiNetworkException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        try {
            Admoai.getInstance().requestAds(request).first()
            fail("Should have thrown AdMoaiNetworkException")
        } catch (e: AdMoaiNetworkException) {
            assertNotNull(e.message)
        }
    }

    // --- Tracking URL tests ---

    @Test
    fun `fireImpression sends GET to tracking URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        initSdk()

        val trackingInfo = TrackingInfo(
            impressions = listOf(
                TrackingDetail(key = "default", url = "http://127.0.0.1:${server.port}/track/imp")
            )
        )
        Admoai.getInstance().fireImpression(trackingInfo).first()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/track/imp", recorded.path)
    }

    @Test
    fun `fireClick sends GET to tracking URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        initSdk()

        val trackingInfo = TrackingInfo(
            clicks = listOf(
                TrackingDetail(key = "default", url = "http://127.0.0.1:${server.port}/track/click")
            )
        )
        Admoai.getInstance().fireClick(trackingInfo).first()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/track/click", recorded.path)
    }

    // --- Timeout tests ---

    @Test
    fun `request timeout throws AdMoaiNetworkException`() = runTest {
        // MockWebServer will not respond (no enqueued response), causing a timeout
        server.enqueue(
            MockResponse()
                .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                .setResponseCode(200)
                .setBody("{\"success\":true}")
                .addHeader("Content-Type", "application/json")
        )
        initSdk(requestTimeoutMs = 500, connectTimeoutMs = 500, socketTimeoutMs = 500)

        val request = DecisionRequestBuilder().addPlacement("test").build()
        try {
            Admoai.getInstance().requestAds(request).first()
            fail("Should have thrown AdMoaiNetworkException due to timeout")
        } catch (e: AdMoaiNetworkException) {
            assertNotNull(e.message)
        }
    }

    // --- Content negotiation ---

    @Test
    fun `handles response with unknown fields gracefully`() = runTest {
        // Response with extra fields not in our model
        val jsonWithExtraFields = """
            {
                "success": true,
                "data": [{"placement": "test", "creatives": []}],
                "unknownField": "should be ignored",
                "anotherUnknown": 42
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonWithExtraFields)
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        val result = Admoai.getInstance().requestAds(request).first()

        assertTrue(result.success)
        assertEquals("test", result.data?.first()?.placement)
    }

    @Test
    fun `handles empty data array`() = runTest {
        val response = """{"success": true, "data": []}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(response)
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        val result = Admoai.getInstance().requestAds(request).first()

        assertTrue(result.success)
        assertNotNull(result.data)
        assertTrue(result.data!!.isEmpty())
    }

    @Test
    fun `handles null data field`() = runTest {
        val response = """{"success": false}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(response)
                .addHeader("Content-Type", "application/json")
        )
        initSdk()

        val request = DecisionRequestBuilder().addPlacement("test").build()
        val result = Admoai.getInstance().requestAds(request).first()

        assertEquals(false, result.success)
        assertEquals(null, result.data)
    }
}