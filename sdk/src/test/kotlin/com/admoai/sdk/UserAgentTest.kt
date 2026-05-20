package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class UserAgentTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        Admoai.resetForTesting()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Admoai.resetForTesting()
    }

    private fun baseUrl() = "http://127.0.0.1:${server.port}/"

    @Test
    fun `given sdk initialized when decision request made then User-Agent header is present`() = runTest {
        // Given: SDK initialized
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        val mockResponse = DecisionResponse(success = true)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Json.encodeToString(mockResponse))
                .addHeader("Content-Type", "application/json")
        )

        // When: a decision request is made
        val request = DecisionRequestBuilder().addPlacement("home").build()
        Admoai.getInstance().requestAds(request).first()

        // Then: User-Agent header must be present
        val recorded = server.takeRequest()
        val userAgent = recorded.getHeader("User-Agent")
        assertNotNull("User-Agent header must be present", userAgent)
        assertEquals("AdMoaiSDK/$SDK_VERSION", userAgent)
    }

    @Test
    fun `given sdk initialized when tracking request fired then User-Agent header is present`() {
        // Given: SDK initialized with a tracking URL
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        server.enqueue(MockResponse().setResponseCode(200))
        val trackingUrl = "${baseUrl()}track/impression"
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = trackingUrl))
        )

        // When: tracking is fired (fire-and-forget, no collection required)
        Admoai.getInstance().fireImpression(trackingInfo)

        // Then: User-Agent header must be present
        val recorded = server.takeRequest(3, TimeUnit.SECONDS)
        val userAgent = recorded?.getHeader("User-Agent")
        assertNotNull("User-Agent header must be present on tracking", userAgent)
        assertEquals("AdMoaiSDK/$SDK_VERSION", userAgent)
    }

    @Test
    fun `given sdk with no apiVersion and no defaultLanguage when request made then User-Agent is still present`() = runTest {
        // Given: SDK with minimal config (no apiVersion, no defaultLanguage)
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        val mockResponse = DecisionResponse(success = true)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Json.encodeToString(mockResponse))
                .addHeader("Content-Type", "application/json")
        )

        // When: decision request made
        Admoai.getInstance().requestAds(DecisionRequestBuilder().addPlacement("p1").build()).first()

        // Then: User-Agent is always present regardless of other config
        val recorded = server.takeRequest()
        assertNotNull("User-Agent must always be present", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `sdk version constant matches expected format`() {
        // Given/When: SDK_VERSION constant accessed
        // Then: must be a non-empty semantic version string
        assertNotNull(SDK_VERSION)
        assert(SDK_VERSION.isNotEmpty()) { "SDK_VERSION must not be empty" }
        assert(SDK_VERSION.matches(Regex("""\d+\.\d+\.\d+.*"""))) {
            "SDK_VERSION must follow semantic versioning: $SDK_VERSION"
        }
    }
}
