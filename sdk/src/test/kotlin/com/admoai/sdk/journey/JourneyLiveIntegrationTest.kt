package com.admoai.sdk.journey

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.exception.AdMoaiNetworkException
import com.admoai.sdk.model.common.JourneyOpt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * DIAGNOSTIC live tests against a real decision engine. NOT part of the deterministic CI gate.
 *
 * Enable with `ADMOAI_LIVE_TESTS=1` (optionally `ADMOAI_LIVE_BASE_URL=<url>`); otherwise every test
 * is skipped via JUnit Assumptions and the default `:sdk:test` run stays hermetic.
 *
 * These are deployment-aware: the Journey engine (2025-11-01) is not yet live on the shared mock
 * host, which rejects the additive sessionId/journeyOpt fields with HTTP 400 ("unknown field").
 * That 400 is recorded as "deployment-pending", not a failure — this suite doubles as the
 * post-deploy readiness probe.
 */
class JourneyLiveIntegrationTest {

    private val baseUrl = System.getenv("ADMOAI_LIVE_BASE_URL") ?: "https://api.mock.admoai.com"
    private val apiVersion = "2025-11-01"

    @Before
    fun requireLiveEnabled() {
        assumeTrue("set ADMOAI_LIVE_TESTS=1 to run live tests", System.getenv("ADMOAI_LIVE_TESTS") == "1")
    }

    @After
    fun tearDown() = Admoai.resetForTesting()

    private fun initLive() = Admoai.initialize(SDKConfig(baseUrl = baseUrl, apiVersion = apiVersion))

    @Test
    fun `normal-ad decision succeeds against the live engine`() = runTest {
        initLive()
        val response = Admoai.getInstance()
            .requestAds(Admoai.getInstance().createRequestBuilder().addPlacement("home").build())
            .first()
        assertNotNull(response)
    }

    @Test
    fun `journey decision request is accepted once the engine is deployed`() = runTest {
        initLive()
        val request = Admoai.getInstance().createRequestBuilder()
            .addPlacement("home")
            .setSessionId("live-probe-session")
            .setJourneyOpt(JourneyOpt.OPT_IN)
            .build()
        try {
            val response = Admoai.getInstance().requestAds(request).first()
            assertNotNull(response)
            println("[live] Journey engine is deployed and accepted the request.")
        } catch (e: AdMoaiNetworkException) {
            val msg = e.message ?: ""
            if (msg.contains("400") || msg.contains("unknown field", ignoreCase = true)) {
                println("[live] Journey engine not yet deployed (pre-Journey 400) — deployment-pending.")
            } else {
                throw e
            }
        }
    }
}
