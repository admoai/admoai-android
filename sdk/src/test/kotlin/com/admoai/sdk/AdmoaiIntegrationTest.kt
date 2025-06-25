package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.AdData
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdmoaiIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var sdkConfig: SDKConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Explicitly use 127.0.0.1 for the baseUrl
        val baseUrl = "http://127.0.0.1:${server.port}/"
        sdkConfig = SDKConfig(
            baseUrl = baseUrl,
            enableLogging = true, // Logging is enabled
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
}