package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiException
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.exception.AdMoaiValidationException
import com.admoai.sdk.model.request.DecisionRequestBuilder
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The `/v1/decision` HTTP error contract.
 *
 * These exist because of a gap that every existing error test missed. Ktor's `expectSuccess`
 * defaults to false and the SDK installs no `HttpResponseValidator`, so `requestAds` used to call
 * `httpResponse.body()` regardless of status. The engine's error envelope is shape-identical to a
 * success envelope (`success`/`data`/`errors`/`warnings`), so a rejected request deserialized
 * cleanly into `DecisionResponse(success=false, data=null, errors=[...])` and was emitted as a
 * normal Flow item — indistinguishable from no-fill to a publisher checking `data.isEmpty()`.
 *
 * The pre-existing 400/500 tests did not catch it because they enqueue a plain-text body
 * ("Bad Request"): those threw on JSON deserialization, not on status, so they passed for the
 * wrong reason. Every case here therefore sends a REAL engine-shaped JSON envelope.
 *
 * iOS (release) and Flutter have always branched on status. This pins Android to the same contract.
 */
class DecisionErrorContractTest {

    private lateinit var server: MockWebServer

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

    private fun initSdk() {
        Admoai.initialize(
            SDKConfig(
                baseUrl = server.url("/").toString(),
                apiVersion = "2025-11-01",
                networkClientEngine = CIO.create()
            )
        )
    }

    private fun jsonBody(code: Int, message: String) = """
        {"success":false,"data":null,
         "errors":[{"code":$code,"message":"$message"}],
         "warnings":null,"metadata":null}
    """.trimIndent()

    private fun requestAds() = runTest {
        initSdk()
        Admoai.getInstance()
            .requestAds(DecisionRequestBuilder().addPlacement("home").build())
            .first()
    }

    // Scenario: the engine rejects the request with a validation error and a JSON envelope.
    @Test
    fun `422 with a JSON error envelope throws a validation exception carrying the errors`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody(10004, "placement key home was not found"))
        )
        try {
            requestAds()
            fail("A 422 must not be emitted as a successful decision response")
        } catch (e: AdMoaiValidationException) {
            assertEquals(1, e.errors.size)
            assertEquals(10004, e.errors.first().code)
            assertEquals("placement key home was not found", e.errors.first().message)
            // The formatted message must carry the engine's code + text, not a generic string:
            // this is what lands in a publisher's crash report.
            assertTrue(e.message!!.contains("10004"))
            assertTrue(e.message!!.contains("placement key home was not found"))
        }
    }

    // Scenario: a 422 arrives with a body the SDK cannot parse.
    @Test
    fun `422 with an unparseable body still throws a validation exception`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("<html>gateway</html>"))
        try {
            requestAds()
            fail("A 422 must be raised even when its body cannot be parsed")
        } catch (e: AdMoaiValidationException) {
            // Status alone is enough to know the request was rejected; degrading this to a generic
            // network error would lose that signal.
            assertTrue(e.errors.isEmpty())
        }
    }

    // Scenario: a 400 carries the same JSON envelope shape as a success response.
    @Test
    fun `400 with a JSON error envelope throws instead of decoding as a decision`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody(10009, "unknown field journeyOpt"))
        )
        try {
            requestAds()
            fail("A 400 must not be emitted as a successful decision response")
        } catch (e: AdMoaiNetworkException) {
            assertEquals(400, e.statusCode)
            assertTrue(e.message!!.contains("Client error"))
        }
    }

    // Scenario: the engine is unhealthy and returns a JSON-shaped 500.
    @Test
    fun `500 with a JSON body throws a network exception carrying the status`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody(50000, "internal"))
        )
        try {
            requestAds()
            fail("A 500 must not be emitted as a successful decision response")
        } catch (e: AdMoaiNetworkException) {
            assertEquals(500, e.statusCode)
            assertTrue(e.message!!.contains("Server error"))
        }
    }

    // Scenario: a publisher catches the SDK's base exception type.
    @Test
    fun `validation exceptions are catchable as AdMoaiException`() {
        // AdMoaiValidationException used to extend Exception directly, so the idiomatic
        // `catch (e: AdMoaiException)` silently missed it.
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonBody(10004, "nope"))
        )
        try {
            requestAds()
            fail("expected a validation exception")
        } catch (e: AdMoaiException) {
            assertTrue(e is AdMoaiValidationException)
        }
    }

    // Scenario: a normal 200 is unaffected by the new status gate.
    @Test
    fun `200 still emits the decision response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"success":true,"data":[{"placement":"home","creatives":[]}]}""")
        )
        initSdk()
        val response = Admoai.getInstance()
            .requestAds(DecisionRequestBuilder().addPlacement("home").build())
            .first()

        assertTrue(response.success)
        assertEquals(1, response.data!!.size)
        assertEquals("home", response.data!!.first().placement)
    }
}
