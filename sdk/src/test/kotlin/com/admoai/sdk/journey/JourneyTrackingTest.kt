@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.admoai.sdk.journey

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.getCompletionUrl
import com.admoai.sdk.network.AdMoaiApiService
import io.ktor.client.engine.cio.CIO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * BDD coverage for Journey tracking: completions parsing, fireCompletion behaviour, the
 * X-Tracking-Version routing fix, the URL guard, and 302 characterization.
 */
class JourneyTrackingTest {

    @After
    fun tearDown() {
        Admoai.resetForTesting()
        unmockkAll()
    }

    // --- completions parsing ---

    @Test
    fun `getCompletionUrl resolves the completion beacon by key`() {
        val info = TrackingInfo(
            completions = listOf(TrackingDetail(key = "purchase", url = "https://track/complete"))
        )
        assertEquals("https://track/complete", info.getCompletionUrl("purchase"))
        assertNull(info.getCompletionUrl("missing"))
    }

    // --- fireCompletion behaviour (mocked service) ---

    private fun withMockedService(
        apiVersion: String? = "2025-11-01",
        block: (Admoai, AdMoaiApiService, MutableList<Pair<String, Admoai.LogLevel>>) -> Unit
    ) = runTest(UnconfinedTestDispatcher()) {
        val service: AdMoaiApiService = mockk(relaxed = true)
        coEvery { service.fireTrackingUrl(any()) } returns flowOf(Unit)
        Admoai.initialize(SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = apiVersion))
        val sdk = Admoai.getInstance()
        sdk.sdkScope = this
        sdk.apiService = service
        val warnings = mutableListOf<Pair<String, Admoai.LogLevel>>()
        sdk.logSink = { msg, level, _ -> warnings.add(msg to level) }
        block(sdk, service, warnings)
    }

    @Test
    fun `fireCompletion fires the matching URL verbatim`() = withMockedService { sdk, service, warnings ->
        val info = TrackingInfo(completions = listOf(TrackingDetail("purchase", "https://track/complete")))
        sdk.fireCompletion(info, "purchase")
        coVerify(exactly = 1) { service.fireTrackingUrl("https://track/complete") }
        assertTrue(warnings.none { it.second == Admoai.LogLevel.WARNING })
    }

    @Test
    fun `fireCompletion is a silent no-op when there are no completions`() = withMockedService { sdk, service, warnings ->
        sdk.fireCompletion(TrackingInfo(completions = null), "anything")
        sdk.fireCompletion(TrackingInfo(completions = emptyList()), "anything")
        coVerify(exactly = 0) { service.fireTrackingUrl(any()) }
        assertTrue("no warning for the common no-completions case", warnings.isEmpty())
    }

    @Test
    fun `fireCompletion warns on a key-miss against a non-empty completions list`() = withMockedService { sdk, service, warnings ->
        val info = TrackingInfo(completions = listOf(TrackingDetail("purchase", "https://track/complete")))
        sdk.fireCompletion(info, "wrong-key")
        coVerify(exactly = 0) { service.fireTrackingUrl(any()) }
        assertTrue(warnings.any { it.second == Admoai.LogLevel.WARNING })
    }

    @Test
    fun `fireCompletion warns when firing without apiVersion`() = withMockedService(apiVersion = null) { sdk, service, warnings ->
        val info = TrackingInfo(completions = listOf(TrackingDetail("purchase", "https://track/complete")))
        sdk.fireCompletion(info, "purchase")
        coVerify(exactly = 1) { service.fireTrackingUrl("https://track/complete") }
        assertTrue(warnings.any { it.second == Admoai.LogLevel.WARNING && it.first.contains("apiVersion") })
    }

    // --- URL guard ---

    @Test
    fun `fireTracking rejects a non-absolute URL and logs a redacted reason`() = withMockedService { sdk, service, warnings ->
        // A scheme-less URL carrying a sensitive-looking token; must be rejected and never logged.
        sdk.fireTracking("//track.example/v1/tracking?e=SENSITIVE_TOKEN_VALUE")
        coVerify(exactly = 0) { service.fireTrackingUrl(any()) }
        val warn = warnings.firstOrNull { it.second == Admoai.LogLevel.WARNING }
        assertNotNull(warn)
        assertTrue(warn!!.first.contains("Tracking URL rejected"))
        // Redaction: neither the token nor the raw URL query may appear in the log.
        assertFalse(warn.first.contains("SENSITIVE_TOKEN_VALUE"))
        assertFalse(warn.first.contains("e="))
        assertFalse(warn.first.contains("track.example"))
    }

    // --- X-Tracking-Version routing (positive + negative) ---

    @Test
    fun `tracking GET carries X-Tracking-Version and NOT X-Decision-Version`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            Admoai.initialize(
                SDKConfig(
                    baseUrl = "http://127.0.0.1:${server.port}/",
                    apiVersion = "2025-11-01",
                    networkClientEngine = CIO.create()
                )
            )
            Admoai.getInstance().fireTracking("http://127.0.0.1:${server.port}/v1/tracking?e=token")

            val recorded = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertEquals("2025-11-01", recorded.getHeader("X-Tracking-Version"))
            assertNull(recorded.getHeader("X-Decision-Version"))
        } finally {
            server.shutdown()
        }
    }

    // --- 302 characterization (pre-existing follow-redirect behaviour, not changed) ---

    @Test
    fun `tracking GET follows a 302 redirect (characterization)`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(302)
                    .addHeader("Location", "http://127.0.0.1:${server.port}/dest")
            )
            server.enqueue(MockResponse().setResponseCode(200))
            Admoai.initialize(
                SDKConfig(
                    baseUrl = "http://127.0.0.1:${server.port}/",
                    apiVersion = "2025-11-01",
                    networkClientEngine = CIO.create()
                )
            )
            Admoai.getInstance().fireTracking("http://127.0.0.1:${server.port}/v1/tracking?e=click")

            val first = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertTrue(first.path!!.startsWith("/v1/tracking"))
            // Documents that Ktor follows the redirect by default (a second request reaches /dest).
            val second = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertEquals("/dest", second.path)
        } finally {
            server.shutdown()
        }
    }
}
