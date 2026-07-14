package com.admoai.sdk.journey

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.network.deprecationWarningMessage
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BDD coverage for deprecation-aware logging (X-API-Deprecated on the decision response).
 */
class DeprecationLoggingTest {

    @After
    fun tearDown() = Admoai.resetForTesting()

    // --- pure helper ---

    @Test
    fun `deprecationWarningMessage is null when not deprecated`() {
        assertNull(deprecationWarningMessage(null, null))
        assertNull(deprecationWarningMessage("false", "2026-01-01"))
    }

    @Test
    fun `deprecationWarningMessage includes sunset when present`() {
        val msg = deprecationWarningMessage("true", "2026-01-01")!!
        assertTrue(msg.contains("deprecated"))
        assertTrue(msg.contains("2026-01-01"))
    }

    // --- integration: warning surfaces on a deprecated response ---

    @Test
    fun `deprecated decision response logs a warning`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"success":true,"data":[]}""")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-API-Deprecated", "true")
                    .addHeader("Sunset", "2026-06-01")
            )
            Admoai.initialize(
                SDKConfig(baseUrl = "http://127.0.0.1:${server.port}/", networkClientEngine = CIO.create())
            )
            val warnings = mutableListOf<String>()
            Admoai.getInstance().logSink = { msg, level, _ ->
                if (level == Admoai.LogLevel.WARNING) warnings.add(msg)
            }

            Admoai.getInstance().requestAds(DecisionRequestBuilder().addPlacement("p").build()).first()

            assertTrue(warnings.any { it.contains("deprecated") && it.contains("2026-06-01") })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `non-deprecated response logs no deprecation warning`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"success":true,"data":[]}""")
                    .addHeader("Content-Type", "application/json")
            )
            Admoai.initialize(
                SDKConfig(baseUrl = "http://127.0.0.1:${server.port}/", networkClientEngine = CIO.create())
            )
            val warnings = mutableListOf<String>()
            Admoai.getInstance().logSink = { msg, _, _ -> warnings.add(msg) }

            Admoai.getInstance().requestAds(DecisionRequestBuilder().addPlacement("p").build()).first()

            assertFalse(warnings.any { it.contains("deprecated") })
        } finally {
            server.shutdown()
        }
    }
}
