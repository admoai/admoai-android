package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AcceptLanguageTrackingTest {

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

    private fun trackingInfo() = TrackingInfo(
        impressions = listOf(
            TrackingDetail(key = "default", url = "http://127.0.0.1:${server.port}/track/imp")
        )
    )

    @Test
    fun `given defaultLanguage set when tracking request fired then Accept-Language header is present`() {
        // Given: SDK configured with defaultLanguage
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1:${server.port}/",
                defaultLanguage = "es",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))

        // When: a tracking request is fired (fire-and-forget)
        Admoai.getInstance().fireImpression(trackingInfo())

        // Then: Accept-Language must be present on tracking
        val recorded = server.takeRequest(3, TimeUnit.SECONDS)
        assertEquals("es", recorded?.getHeader("Accept-Language"))
    }

    @Test
    fun `given no defaultLanguage when tracking request fired then Accept-Language header is absent`() {
        // Given: SDK configured without defaultLanguage
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1:${server.port}/",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))

        // When: a tracking request is fired (fire-and-forget)
        Admoai.getInstance().fireImpression(trackingInfo())

        // Then: Accept-Language must NOT be present
        val recorded = server.takeRequest(3, TimeUnit.SECONDS)
        assertNull(recorded?.getHeader("Accept-Language"))
    }

    @Test
    fun `given defaultLanguage set when decision request made then Accept-Language is on decision too`() = runTest {
        // Given: SDK configured with defaultLanguage (existing behavior preserved)
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1:${server.port}/",
                defaultLanguage = "fr",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true}""")
                .addHeader("Content-Type", "application/json")
        )

        // When: a decision request is made
        com.admoai.sdk.model.request.DecisionRequestBuilder()
            .addPlacement("home")
            .build()
            .let { Admoai.getInstance().requestAds(it).first() }

        // Then: decision request still carries Accept-Language
        val recorded = server.takeRequest()
        assertEquals("fr", recorded.getHeader("Accept-Language"))
    }
}
