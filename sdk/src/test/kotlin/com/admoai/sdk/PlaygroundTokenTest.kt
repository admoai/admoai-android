package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.response.DecisionResponse
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Feature: a publisher's tech team points the SDK at a playground with `playgroundToken`.
 *
 * Given no token (or a blank one) → the decision request is byte-identical to the live one.
 * Given a token → the same request, sent to `/v1/playground/decision` with `X-Playground-Token`.
 */
class PlaygroundTokenTest {

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

    private fun liveConfig() = SDKConfig(
        baseUrl = "http://127.0.0.1:${server.port}/",
        apiVersion = "2025-11-01",
        defaultLanguage = "es",
        networkClientEngine = CIO.create()
    )

    private suspend fun send(config: SDKConfig, reason: String? = null): Pair<RecordedRequest, DecisionResponse> {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"success":true,"data":[{"placement":"home","creatives":[]}]}""")
            .addHeader("Content-Type", "application/json")
        reason?.let { response.addHeader("X-Playground-Reason", it) }
        server.enqueue(response)
        Admoai.initialize(config)
        val sdk = Admoai.getInstance()
        sdk.setSessionId("pg-session")
        val decoded = sdk.requestAds(sdk.createRequestBuilder().addPlacement("home").build()).first()
        return server.takeRequest() to decoded
    }

    private fun RecordedRequest.headerPairs(): List<Pair<String, String>> =
        headers.map { (name, value) -> name.lowercase() to value }.sortedBy { it.first }

    private fun liveHeaders(bodySize: Long) = listOf(
        "accept" to "application/json",
        "accept-charset" to "UTF-8",
        "accept-language" to "es",
        "content-length" to bodySize.toString(),
        "content-type" to "application/json",
        "host" to "127.0.0.1:${server.port}",
        "user-agent" to "AdMoaiSDK/$SDK_VERSION",
        "x-decision-version" to "2025-11-01"
    )

    @Test
    fun `without a token the decision request is the live request - path, headers and body`() = runTest {
        val (recorded, _) = send(liveConfig())

        assertEquals("POST", recorded.method)
        assertEquals("/v1/decision", recorded.path)
        assertEquals(liveHeaders(recorded.bodySize), recorded.headerPairs())
        assertEquals(LIVE_BODY, recorded.body.readUtf8())
    }

    @Test
    fun `a blank token is treated as unset and sends the live request unchanged`() = runTest {
        for (blank in listOf("", "   ")) {
            val (recorded, _) = send(liveConfig().copy(playgroundToken = blank))
            assertEquals("/v1/decision", recorded.path)
            assertEquals(liveHeaders(recorded.bodySize), recorded.headerPairs())
            assertEquals(LIVE_BODY, recorded.body.readUtf8())
        }
    }

    @Test
    fun `with a token the request goes to the playground and nothing else changes`() = runTest {
        val (recorded, _) = send(liveConfig().copy(playgroundToken = "pg_test_token"))

        assertEquals("POST", recorded.method)
        assertEquals("/v1/playground/decision", recorded.path)
        val expected = (liveHeaders(recorded.bodySize) + ("x-playground-token" to "pg_test_token"))
            .sortedBy { it.first }
        assertEquals(expected, recorded.headerPairs())
        assertEquals(LIVE_BODY, recorded.body.readUtf8())
    }

    @Test
    fun `a playground reason is logged as a warning and the response stays an ordinary no-ad`() = runTest {
        val warnings = mutableListOf<String>()
        val config = liveConfig().copy(playgroundToken = "pg_revoked")
        Admoai.initialize(config)
        Admoai.getInstance().logSink = { msg, level, _ -> if (level == Admoai.LogLevel.WARNING) warnings.add(msg) }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":[{"placement":"home","creatives":[]}]}""")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Playground-Reason", "revoked")
        )

        val sdk = Admoai.getInstance()
        val response = sdk.requestAds(DecisionRequestBuilder().addPlacement("home").build()).first()

        assertTrue(response.data!!.single().creatives.isEmpty())
        assertTrue(warnings.toString(), warnings.any { it.contains("X-Playground-Reason=revoked") })
        assertTrue("the token is never logged", warnings.none { it.contains("pg_revoked") })
    }

    @Test
    fun `getHttpRequestData previews the same route and headers the SDK sends`() {
        val request = DecisionRequestBuilder().addPlacement("home").build()

        Admoai.initialize(SDKConfig(baseUrl = "https://api.example.test/", apiVersion = "2025-11-01"))
        val live = Admoai.getInstance().getHttpRequestData(request)
        assertEquals("https://api.example.test/v1/decision", live.url)
        assertNull(live.headers["X-Playground-Token"])

        Admoai.initialize(
            SDKConfig(baseUrl = "https://api.example.test/", apiVersion = "2025-11-01", playgroundToken = "pg_t")
        )
        val playground = Admoai.getInstance().getHttpRequestData(request)
        assertEquals("https://api.example.test/v1/playground/decision", playground.url)
        assertEquals(live.headers + ("X-Playground-Token" to "pg_t"), playground.headers)
        assertEquals(live.body, playground.body)
    }

    @Test
    fun `the token survives copy and resetToDefaults, and the pre-token constructor leaves it unset`() {
        val config = SDKConfig(baseUrl = "https://api.example.test/", playgroundToken = "pg_keep")

        assertEquals("pg_keep", config.copy(enableLogging = true).playgroundToken)
        assertEquals("pg_keep", config.resetToDefaults().playgroundToken)
        assertNull(SDKConfig("https://api.example.test/", null, false, null, null, 1L, 2L, 3L).playgroundToken)
    }

    @Test
    fun `toString redacts the token and prints null when unset`() {
        val withToken = SDKConfig(baseUrl = "https://api.example.test/", playgroundToken = "pg_secret_value")
        val unset = SDKConfig(baseUrl = "https://api.example.test/")

        assertFalse(withToken.toString().contains("pg_secret_value"))
        assertTrue(withToken.toString().endsWith("playgroundToken=<redacted>)"))
        assertEquals(
            "SDKConfig(baseUrl=https://api.example.test/, apiVersion=null, enableLogging=false, " +
                "defaultLanguage=null, networkClientEngine=null, networkRequestTimeoutMs=10000, " +
                "networkConnectTimeoutMs=10000, networkSocketTimeoutMs=10000, playgroundToken=null)",
            unset.toString()
        )
    }

    @Test
    fun `the initialize shortcut with a token routes to the playground`() = runTest {
        server.enqueue(okResponse())
        Admoai.initialize(
            baseUrl = "http://127.0.0.1:${server.port}/",
            apiVersion = "2025-11-01",
            playgroundToken = "pg_shortcut"
        )
        Admoai.getInstance().requestAds(DecisionRequestBuilder().addPlacement("home").build()).first()

        val recorded = server.takeRequest()
        assertEquals("/v1/playground/decision", recorded.path)
        assertEquals("pg_shortcut", recorded.getHeader("X-Playground-Token"))
        assertEquals("2025-11-01", recorded.getHeader("X-Decision-Version"))
    }

    @Test
    fun `the existing initialize shortcut still sends the live request`() = runTest {
        server.enqueue(okResponse())
        Admoai.initialize("http://127.0.0.1:${server.port}/", "2025-11-01", false, "es", "pg-session")
        Admoai.getInstance().requestAds(Admoai.getInstance().createRequestBuilder().addPlacement("home").build())
            .first()

        val recorded = server.takeRequest()
        assertEquals("/v1/decision", recorded.path)
        assertEquals(liveHeaders(recorded.bodySize), recorded.headerPairs())
        assertEquals(LIVE_BODY, recorded.body.readUtf8())
    }

    private fun okResponse() = MockResponse()
        .setResponseCode(200)
        .setBody("""{"success":true,"data":[{"placement":"home","creatives":[]}]}""")
        .addHeader("Content-Type", "application/json")

    private companion object {
        const val LIVE_BODY = """{"placements":[{"key":"home"}],"sessionId":"pg-session"}"""
    }
}
