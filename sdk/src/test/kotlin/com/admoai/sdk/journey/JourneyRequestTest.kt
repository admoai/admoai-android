package com.admoai.sdk.journey

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.common.normalizeSessionId
import com.admoai.sdk.model.common.sessionIdRejectionReason
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.request.DecisionRequestBuilder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BDD coverage for forwarding Journey request context (sessionId, journeyOpt).
 *
 * Given no Journey fields → request is byte-identical to pre-Journey behaviour.
 * Given a sticky sessionId → the same value is seeded into every builder; per-request overrides win.
 * Given a blank/over-length sessionId → omitted/sent-as-is with a PII-safe reason.
 * Given Journey context without apiVersion → a warning is emitted even when logging is off.
 */
class JourneyRequestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    // --- Serialization ---

    @Test
    fun `journeyOpt serializes to in and out wire values`() {
        val inBody = json.encodeToString(
            DecisionRequest(placements = emptyList(), journeyOpt = JourneyOpt.OPT_IN)
        )
        val outBody = json.encodeToString(
            DecisionRequest(placements = emptyList(), journeyOpt = JourneyOpt.OPT_OUT)
        )
        assertTrue(inBody.contains("\"journeyOpt\":\"in\""))
        assertTrue(outBody.contains("\"journeyOpt\":\"out\""))
    }

    @Test
    fun `sessionId and journeyOpt serialize as top-level camelCase keys`() {
        val body = json.encodeToString(
            DecisionRequest(
                placements = emptyList(),
                sessionId = "sess-123",
                journeyOpt = JourneyOpt.OPT_IN
            )
        )
        assertTrue(body.contains("\"sessionId\":\"sess-123\""))
        assertTrue(body.contains("\"journeyOpt\":\"in\""))
    }

    @Test
    fun `no Journey fields are omitted from the body`() {
        val body = json.encodeToString(DecisionRequest(placements = emptyList()))
        assertFalse(body.contains("sessionId"))
        assertFalse(body.contains("journeyOpt"))
    }

    @Test
    fun `blank sessionId is omitted after builder normalization`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setSessionId("   ")
            .build()
        assertNull(request.sessionId)
        assertFalse(json.encodeToString(request).contains("sessionId"))
    }

    @Test
    fun `over-length sessionId is sent as-is, never truncated`() {
        val long = "a".repeat(300)
        val request = DecisionRequestBuilder().addPlacement("p1").setSessionId(long).build()
        assertEquals(long, request.sessionId)
    }

    // --- Reason helper (PII-safe tokens, byte semantics) ---

    @Test
    fun `rejection reason tokens match cross-SDK parity`() {
        assertNull(sessionIdRejectionReason(null))
        assertNull(sessionIdRejectionReason("ok"))
        assertEquals("blank_after_trim", sessionIdRejectionReason("   "))
        assertEquals("exceeds_256_bytes", sessionIdRejectionReason("a".repeat(257)))
    }

    @Test
    fun `rejection reason counts UTF-8 bytes not chars`() {
        // 65 emoji = 65 chars but 260 bytes (4 bytes each) → over the 256-byte limit.
        val emoji = "😀".repeat(65)
        assertTrue(emoji.length < 256)
        assertEquals("exceeds_256_bytes", sessionIdRejectionReason(emoji))
    }

    @Test
    fun `normalizeSessionId trims and maps blank to null`() {
        assertEquals("x", normalizeSessionId("  x  "))
        assertNull(normalizeSessionId("   "))
        assertNull(normalizeSessionId(null))
    }

    // --- Tolerant opt parse (used by the response side) ---

    @Test
    fun `JourneyOpt fromWire is tolerant`() {
        assertEquals(JourneyOpt.OPT_IN, JourneyOpt.fromWire("in"))
        assertEquals(JourneyOpt.OPT_OUT, JourneyOpt.fromWire(" OUT "))
        assertNull(JourneyOpt.fromWire("paused"))
        assertNull(JourneyOpt.fromWire(null))
    }

    // --- Sticky session semantics ---

    @Test
    fun `sticky sessionId is seeded into every builder`() {
        initSdk(sessionId = "sticky-1")
        val sdk = Admoai.getInstance()
        val r1 = sdk.createRequestBuilder().addPlacement("p1").build()
        val r2 = sdk.createRequestBuilder().addPlacement("p2").build()
        assertEquals("sticky-1", r1.sessionId)
        assertEquals("sticky-1", r2.sessionId)
    }

    @Test
    fun `per-request sessionId overrides the sticky seed`() {
        initSdk(sessionId = "sticky-1")
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("p1")
            .setSessionId("override-9")
            .build()
        assertEquals("override-9", request.sessionId)
    }

    @Test
    fun `setSessionId rotates the sticky value`() {
        initSdk(sessionId = "old")
        val sdk = Admoai.getInstance()
        sdk.setSessionId("new")
        assertEquals("new", sdk.getSessionId())
        assertEquals("new", sdk.createRequestBuilder().addPlacement("p").build().sessionId)
    }

    @Test
    fun `clearAll clears journeyOpt but preserves the sticky sessionId`() {
        initSdk(sessionId = "sticky-1")
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("p1")
            .setJourneyOpt(JourneyOpt.OPT_OUT)
            .clearAll()
            .addPlacement("p2")
            .build()
        assertNull(request.journeyOpt)
        assertEquals("sticky-1", request.sessionId)
    }

    @Test
    fun `resetForTesting clears the sticky session`() {
        initSdk(sessionId = "sticky-1")
        Admoai.resetForTesting()
        initSdk() // fresh instance, no sessionId
        assertNull(Admoai.getInstance().getSessionId())
    }

    // --- prepareFinalDecisionRequest carries Journey context to the wire ---

    @Test
    fun `sessionId reaches the wire body through the full request path`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(json.encodeToString(DecisionResponse(success = true, data = emptyList())))
                    .addHeader("Content-Type", "application/json")
            )
            Admoai.initialize(
                SDKConfig(
                    baseUrl = "http://127.0.0.1:${server.port}/",
                    apiVersion = "2025-11-01",
                    networkClientEngine = CIO.create()
                )
            )
            Admoai.getInstance().setSessionId("wire-sess")
            val request = Admoai.getInstance().createRequestBuilder()
                .addPlacement("p1")
                .setJourneyOpt(JourneyOpt.OPT_IN)
                .build()
            Admoai.getInstance().requestAds(request).first()

            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"sessionId\":\"wire-sess\""))
            assertTrue(body.contains("\"journeyOpt\":\"in\""))
        } finally {
            server.shutdown()
        }
    }

    // --- Misuse warning surfaces even with logging OFF ---

    @Test
    fun `warns on Journey context without apiVersion even when logging is disabled`() {
        initSdk(sessionId = null, apiVersion = null, enableLogging = false)
        val sdk = Admoai.getInstance()
        val captured = mutableListOf<Pair<String, Admoai.LogLevel>>()
        sdk.logSink = { message, level, _ -> captured.add(message to level) }

        val request = sdk.createRequestBuilder()
            .addPlacement("p1")
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .build()
        sdk.prepareFinalDecisionRequest(request)

        assertTrue(
            "expected a WARNING about missing apiVersion",
            captured.any { it.second == Admoai.LogLevel.WARNING && it.first.contains("apiVersion") }
        )
    }

    @Test
    fun `over-length sessionId warning is PII-safe and never contains the value`() {
        initSdk(apiVersion = "2025-11-01")
        val sdk = Admoai.getInstance()
        val captured = mutableListOf<String>()
        sdk.logSink = { message, _, _ -> captured.add(message) }

        val secret = "SECRET_SESSION_MARKER" + "a".repeat(300)
        sdk.setSessionId(secret)

        val warn = captured.firstOrNull { it.contains("exceeds_256_bytes") }
        assertTrue("expected an exceeds_256_bytes reason", warn != null)
        assertFalse("must not log the sessionId value", captured.any { it.contains("SECRET_SESSION_MARKER") })
    }

    @Test
    fun `getHttpRequestData preview includes sessionId and journeyOpt`() {
        initSdk(sessionId = "preview-sess", apiVersion = "2025-11-01")
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("p1")
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .build()
        val body = Admoai.getInstance().getHttpRequestData(request).body.orEmpty()
        assertTrue(body.contains("\"sessionId\":\"preview-sess\""))
        assertTrue(body.contains("\"journeyOpt\":\"in\""))
    }

    private fun initSdk(
        sessionId: String? = null,
        apiVersion: String? = null,
        enableLogging: Boolean = false
    ) {
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1/",
                apiVersion = apiVersion,
                enableLogging = enableLogging
            )
        )
        sessionId?.let { Admoai.getInstance().setSessionId(it) }
    }
}
