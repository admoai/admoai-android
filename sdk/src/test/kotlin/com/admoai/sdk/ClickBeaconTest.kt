package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Feature: a click beacon records the click and nothing else.
 *
 * `/v1/tracking` answers a click with `302 Location: <destination>` so a browser can record and
 * land in one hop. `fireClick` is a background beacon, not navigation: the app opens the
 * destination itself, so the SDK must take the first response as final and never request the
 * `Location`. Following it once made every click on an Android publisher's banner fail — the
 * advertiser's host rejected the beacon's `Accept: application/json` with a 500.
 *
 * Runs against a real socket on both engines a publisher can pass as `networkClientEngine`
 * (CIO, the default, and OkHttp), because redirect handling lives in the client stack and a
 * mocked service would prove nothing about it.
 */
class ClickBeaconTest {

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

    private fun initialize(engine: HttpClientEngine) = Admoai.initialize(
        SDKConfig(
            baseUrl = "http://127.0.0.1:${server.port}/",
            apiVersion = "2025-11-01",
            networkClientEngine = engine
        )
    )

    private fun clickUrl() = "http://127.0.0.1:${server.port}/v1/tracking?e=click"
    private fun destinationUrl() = "http://127.0.0.1:${server.port}/landing"

    private fun clicks() = TrackingInfo(clicks = listOf(TrackingDetail(key = "default", url = clickUrl())))

    private fun enqueueRedirectToDestination() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destinationUrl()))
        // What a content-negotiating landing page answers a JSON request with.
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody("""{"error":"Only HTML requests are supported here"}""")
        )
    }

    private fun assertOnlyTheBeaconWasRequested() {
        val beacon = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertTrue(beacon.path!!.startsWith("/v1/tracking"))
        assertNull(
            "the SDK requested the redirect's Location",
            server.takeRequest(500, TimeUnit.MILLISECONDS)
        )
    }

    // Scenario: the tracking endpoint answers the click with a redirect
    @Test
    fun `a 302 click is sent once and its Location is never requested (CIO)`() = runTest {
        initialize(CIO.create())
        enqueueRedirectToDestination()

        Admoai.getInstance().fireClick(clicks())

        assertOnlyTheBeaconWasRequested()
    }

    @Test
    fun `a 302 click is sent once and its Location is never requested (OkHttp)`() = runTest {
        initialize(OkHttp.create())
        enqueueRedirectToDestination()

        Admoai.getInstance().fireClick(clicks())

        assertOnlyTheBeaconWasRequested()
    }

    // Scenario: the redirect is the engine accepting the click, not a failure
    @Test
    fun `a 302 click completes as an accepted beacon`() = runTest {
        initialize(CIO.create())
        enqueueRedirectToDestination()

        val service = requireNotNull(Admoai.getInstance().apiService)
        assertEquals(Unit, service.fireTrackingUrl(clickUrl()).first())

        assertOnlyTheBeaconWasRequested()
    }

    // Scenario: the tracking endpoint answers the click with a 2xx
    @Test
    fun `a 2xx click is sent once and nothing else`() = runTest {
        initialize(CIO.create())
        server.enqueue(MockResponse().setResponseCode(202))

        Admoai.getInstance().fireClick(clicks())

        assertOnlyTheBeaconWasRequested()
    }

    // Scenario: the click key is not in the creative's tracking
    @Test
    fun `a click key with no tracking URL sends nothing`() = runTest {
        initialize(CIO.create())

        Admoai.getInstance().fireClick(clicks(), key = "cta_tap")

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }
}
