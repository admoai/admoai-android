package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AdmoaiIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var sdkConfig: SDKConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = "http://127.0.0.1:${server.port}/"
        sdkConfig = SDKConfig(
            baseUrl = baseUrl,
            enableLogging = true,
            networkClientEngine = CIO.create()
        )
        Admoai.initialize(sdkConfig)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Admoai.resetForTesting()
    }

    @Test
    fun `requestAds - successful response - parses correctly`() = runTest {
        val mockDecision = AdData(
            placement = "test-placement",
            creatives = emptyList<Creative>()
        )
        val mockApiResponse = DecisionResponse(success = true, data = listOf(mockDecision))
        val mockJsonResponse = Json.encodeToString(mockApiResponse)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json")
        )

        val admoai = Admoai.getInstance()
        val decisionRequest = DecisionRequestBuilder()
            .addPlacement("test-placement")
            .build()

        try {
            val actualResponse = admoai.requestAds(decisionRequest).first()

            assertNotNull("Response should not be null", actualResponse)
            assertTrue("Response should be successful", actualResponse.success)
            assertNotNull("Response data should not be null", actualResponse.data)
            assertEquals("Should have one decision in data", 1, actualResponse.data?.size)
            assertEquals("Placement key should match", "test-placement", actualResponse.data?.first()?.placement)

            val recordedRequest = server.takeRequest()
            assertEquals("/v1/decision", recordedRequest.path)
            assertEquals("POST", recordedRequest.method)

        } catch (e: AdMoaiNetworkException) {
            println("AdMoaiNetworkException caught in test. Full stack trace:")
            e.printStackTrace()
            throw e
        } catch (e: Exception) {
            println("Unexpected exception caught in test. Full stack trace:")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun `fireTrackingUrl - apiVersion configured - sends X-Decision-Version header`() {
        Admoai.resetForTesting()
        val baseUrl = "http://127.0.0.1:${server.port}/"
        Admoai.initialize(
            SDKConfig(
                baseUrl = baseUrl,
                apiVersion = "1.2.0",
                enableLogging = true,
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))
        val trackingUrl = "${baseUrl}track/impression"
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = trackingUrl))
        )

        Admoai.getInstance().fireImpression(trackingInfo)

        val recordedRequest = server.takeRequest(3, TimeUnit.SECONDS)
        assertEquals("1.2.0", recordedRequest?.getHeader("X-Decision-Version"))
    }

    @Test
    fun `fireTrackingUrl - no apiVersion configured - omits X-Decision-Version header`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val trackingUrl = "http://127.0.0.1:${server.port}/track/impression"
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = trackingUrl))
        )

        Admoai.getInstance().fireImpression(trackingInfo)

        val recordedRequest = server.takeRequest(3, TimeUnit.SECONDS)
        assertNull(recordedRequest?.getHeader("X-Decision-Version"))
    }

    @Test
    fun `fireImpression fires without requiring collection - true fire-and-forget`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val trackingUrl = "http://127.0.0.1:${server.port}/track/impression"
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = trackingUrl))
        )

        Admoai.getInstance().fireImpression(trackingInfo)

        val recorded = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("HTTP request must be fired even without collection", recorded)
        assertEquals("/track/impression", recorded?.path)
    }

    @Test
    fun `fireTracking with raw url fires without requiring collection`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val rawUrl = "http://127.0.0.1:${server.port}/track/raw"

        Admoai.getInstance().fireTracking(rawUrl)

        val recorded = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("Raw tracking URL must be fired", recorded)
    }

    @Test
    fun `tracking failure does not throw to caller`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val trackingInfo = TrackingInfo(
            impressions = listOf(TrackingDetail(key = "default", url = "http://127.0.0.1:${server.port}/track"))
        )

        Admoai.getInstance().fireImpression(trackingInfo)
        server.takeRequest(3, TimeUnit.SECONDS)
        // Test passes if no exception propagated
    }
}
