package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.ThirdPartyTracker
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.network.ThirdPartyTrackerDispatcher
import com.admoai.sdk.network.ThirdPartyTrackerEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Third-party Event Trackers — tolerant model + credential-isolated fan-out (mission E06).
 *
 * Spec: adhub `features/third-party-trackers/specs/E06-sdk.md` — the parity matrix (§A model,
 * §B impression fan-out, §C click fan-out, §D dedupe+limit, §E dispatcher isolation, §F
 * sanitized logging). Test names reference matrix numbers. Mirrors the iOS reference suite
 * (`ThirdPartyTrackerTests.swift`).
 */
class ThirdPartyTrackerTest {

    /** Same tolerant settings as [com.admoai.sdk.network.AdMoaiApiServiceImpl]. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun decodeTracking(raw: String): TrackingInfo = json.decodeFromString(raw)

    private fun tracker(
        id: String = "tpt_01ARZ3NDEKTSV4RRFFQ69G5FAV",
        eventType: String = "impression",
        matchType: String? = null,
        eventKey: String? = null,
        url: String = "https://agency.example/imp"
    ) = ThirdPartyTracker(
        trackerId = id, eventType = eventType, matchType = matchType, eventKey = eventKey, url = url
    )

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val capturedLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        Admoai.resetForTesting()
        capturedLogs.clear()
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
        testScope.cancel()
    }

    // MARK: §A — model / decoding

    @Test
    fun `A1 A2 - absent or null field decodes to null and helpers are unaffected`() {
        val absent = decodeTracking("""{"impressions": [{"key": "default", "url": "https://t/imp"}]}""")
        assertNull(absent.thirdPartyTrackers)
        assertEquals("https://t/imp", absent.impressions?.first()?.url)

        val explicitNull = decodeTracking("""{"thirdPartyTrackers": null}""")
        assertNull(explicitNull.thirdPartyTrackers)
    }

    @Test
    fun `A3 A12 - impression entry decodes verbatim including macros and query order`() {
        val rawUrl = "https://Agency.example/Track?b=2&a=1&cb=%%CACHEBUSTER%%&x=a%20b"
        val tracking = decodeTracking(
            """{"thirdPartyTrackers": [
                {"trackerId": "tpt_01ARZ3NDEKTSV4RRFFQ69G5FAV", "eventType": "impression", "url": "$rawUrl"}
            ]}"""
        )
        val entry = requireNotNull(tracking.thirdPartyTrackers).single()
        assertEquals("tpt_01ARZ3NDEKTSV4RRFFQ69G5FAV", entry.trackerId)
        assertEquals("impression", entry.eventType)
        assertNull(entry.matchType)
        assertNull(entry.eventKey)
        assertEquals(rawUrl, entry.url)
    }

    @Test
    fun `A4 A5 - any and specific click entries decode`() {
        val tracking = decodeTracking(
            """{"thirdPartyTrackers": [
                {"trackerId": "tpt_A", "eventType": "click", "matchType": "any", "url": "https://a.example/c"},
                {"trackerId": "tpt_B", "eventType": "click", "matchType": "specific", "eventKey": "cta_tap", "url": "https://a.example/s"}
            ]}"""
        )
        val entries = requireNotNull(tracking.thirdPartyTrackers)
        assertEquals("any", entries[0].matchType)
        assertNull(entries[0].eventKey)
        assertEquals("specific", entries[1].matchType)
        assertEquals("cta_tap", entries[1].eventKey)
    }

    @Test
    fun `A6 - unknown extra fields are ignored`() {
        val tracking = decodeTracking(
            """{"thirdPartyTrackers": [
                {"trackerId": "tpt_C", "eventType": "impression", "url": "https://a.example/i", "futureField": {"nested": true}}
            ]}"""
        )
        assertEquals(1, tracking.thirdPartyTrackers?.size)
    }

    @Test
    fun `A7 A11 - a structurally malformed entry is dropped and siblings survive`() {
        val tracking = decodeTracking(
            """{"thirdPartyTrackers": [
                {"trackerId": "tpt_NO_URL", "eventType": "impression"},
                "not-an-object",
                {"trackerId": "tpt_OK", "eventType": "impression", "url": "https://a.example/i"}
            ]}"""
        )
        val entries = requireNotNull(tracking.thirdPartyTrackers)
        assertEquals(listOf("tpt_OK"), entries.map { it.trackerId })
    }

    @Test
    fun `A11 - a malformed block never fails the whole tracking decode`() {
        val tracking = decodeTracking(
            """{"thirdPartyTrackers": "garbage", "clicks": [{"key": "default", "url": "https://t/c"}]}"""
        )
        assertEquals(emptyList<ThirdPartyTracker>(), tracking.thirdPartyTrackers ?: emptyList<ThirdPartyTracker>())
        assertEquals("https://t/c", tracking.clicks?.first()?.url)
    }

    // MARK: §A semantic validation + §F sanitized reasons

    @Test
    fun `A8 - non-HTTPS urls are rejected`() {
        for (url in listOf(
            "http://agency.example/imp", "ftp://agency.example/imp",
            "javascript:alert(1)", "agency.example/imp", ""
        )) {
            assertNotNull("expected rejection for $url",
                ThirdPartyTrackerDispatcher.rejectionReason(tracker(url = url)))
        }
    }

    @Test
    fun `A8b - urls with embedded credentials or fragments are rejected`() {
        // Defense in depth: the Ad Manager blocks these at creation, but a row written
        // past the BFF must still never dispatch — credentials would reach the agency's
        // access logs, and fragments are client-side-only.
        for (url in listOf(
            "https://user:pass@agency.example/imp",
            "https://user@agency.example/imp",
            "https://agency.example/imp#frag"
        )) {
            assertNotNull("expected rejection for credentialed/fragment url",
                ThirdPartyTrackerDispatcher.rejectionReason(tracker(url = url)))
        }
    }

    @Test
    fun `A9 A10 - unknown eventType or matchType and keyless specific clicks are rejected`() {
        assertNotNull(ThirdPartyTrackerDispatcher.rejectionReason(tracker(eventType = "conversion")))
        assertNotNull(
            ThirdPartyTrackerDispatcher.rejectionReason(tracker(eventType = "click", matchType = "fuzzy")))
        assertNotNull(
            ThirdPartyTrackerDispatcher.rejectionReason(tracker(eventType = "click", matchType = null)))
        assertNotNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(eventType = "click", matchType = "specific", eventKey = null)))
        assertNotNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(eventType = "click", matchType = "specific", eventKey = "")))
    }

    @Test
    fun `valid shapes pass validation`() {
        assertNull(ThirdPartyTrackerDispatcher.rejectionReason(tracker()))
        assertNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(eventType = "click", matchType = "any", url = "https://a.example/c")))
        assertNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(eventType = "click", matchType = "specific", eventKey = "cta_tap",
                    url = "https://a.example/s")))
    }

    @Test
    fun `A12 E30 - a URL Ktor cannot parse or round-trip verbatim is rejected`() {
        // %%CACHEBUSTER%% is an invalid percent-sequence: Ktor's URL parser throws on it,
        // and any normalization would corrupt what the agency counts — discard, never mutate.
        assertNotNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(url = "https://agency.example/imp?cb=%%CACHEBUSTER%%")))
        // A clean RFC-3986 URL round-trips and passes.
        assertNull(
            ThirdPartyTrackerDispatcher.rejectionReason(
                tracker(url = "https://agency.example/imp?b=2&a=1&ord=12345")))
    }

    @Test
    fun `F35 - rejection reasons never contain the URL`() {
        val poisonUrl = "http://leak.example/secret?campaign=X"
        val invalid = listOf(
            tracker(url = poisonUrl),
            tracker(eventType = "conversion", url = poisonUrl),
            tracker(eventType = "click", matchType = "fuzzy", url = poisonUrl),
            tracker(eventType = "click", matchType = "specific", eventKey = null, url = poisonUrl)
        )
        for (entry in invalid) {
            val reason = requireNotNull(ThirdPartyTrackerDispatcher.rejectionReason(entry))
            assertFalse(reason.contains("leak.example"))
            assertFalse(reason.contains("secret"))
        }
    }

    // MARK: matching

    @Test
    fun `C18-C20 C22 - event types never cross-match and click keys match per matchType`() {
        val imp = tracker()
        val anyClick = tracker(eventType = "click", matchType = "any", url = "https://a.example/c")
        val specific = tracker(
            eventType = "click", matchType = "specific", eventKey = "cta_tap",
            url = "https://a.example/s")

        assertTrue(ThirdPartyTrackerDispatcher.matches(imp, ThirdPartyTrackerEvent.Impression))
        assertFalse(ThirdPartyTrackerDispatcher.matches(imp, ThirdPartyTrackerEvent.Click("default")))
        assertFalse(ThirdPartyTrackerDispatcher.matches(anyClick, ThirdPartyTrackerEvent.Impression))
        assertTrue(ThirdPartyTrackerDispatcher.matches(anyClick, ThirdPartyTrackerEvent.Click("default")))
        assertTrue(ThirdPartyTrackerDispatcher.matches(anyClick, ThirdPartyTrackerEvent.Click("cta_tap")))
        assertTrue(ThirdPartyTrackerDispatcher.matches(specific, ThirdPartyTrackerEvent.Click("cta_tap")))
        assertFalse(ThirdPartyTrackerDispatcher.matches(specific, ThirdPartyTrackerEvent.Click("other")))
    }

    // MARK: §B/§C/§D/§E fan-out through the network (Ktor MockEngine)

    /** Requests the engine has served, most recent last. */
    private fun MockEngine.requestUrls(): List<String> =
        requestHistory.map { it.url.toString() }

    /**
     * Deterministic wait: every fire-and-forget coroutine (canonical fireTracking AND
     * third-party dispatch) launches into the SDK scope this test controls, so joining the
     * scope's children is a complete, race-free barrier — no wall-clock polling, and
     * negative assertions become exact instead of "nothing arrived within 500 ms".
     */
    private fun awaitFires(scope: CoroutineScope = Admoai.getInstance().sdkScope) =
        runBlocking {
            requireNotNull(scope.coroutineContext[Job]).children.toList().joinAll()
        }

    private fun newMockEngine(status: HttpStatusCode = HttpStatusCode.OK, headers: io.ktor.http.Headers = headersOf()): MockEngine =
        MockEngine { respond(content = "", status = status, headers = headers) }

    private fun initSdk(engine: MockEngine) {
        Admoai.initialize(
            SDKConfig(
                baseUrl = "https://api.mock.admoai.com/",
                apiVersion = "2025-11-01",
                defaultLanguage = "en",
                networkClientEngine = engine
            )
        )
        // JVM unit tests have no android.util.Log; route SDK logs to a captured sink.
        Admoai.getInstance().logSink = { message, _, _ -> capturedLogs.add(message) }
        // Production builds the dispatcher on its own private CIO engine (isolation);
        // tests swap in one built on the SAME mock engine so fan-out is observable.
        Admoai.getInstance().thirdPartyDispatcher = ThirdPartyTrackerDispatcher(
            engine = engine,
            scopeProvider = { Admoai.getInstance().sdkScope }
        ) { message, _ -> capturedLogs.add(message) }
    }

    private fun trackingInfo(trackers: List<ThirdPartyTracker>?) = TrackingInfo(
        impressions = listOf(TrackingDetail(key = "default", url = "https://api.mock.admoai.com/v1/t/imp")),
        clicks = listOf(
            TrackingDetail(key = "default", url = "https://api.mock.admoai.com/v1/t/click"),
            TrackingDetail(key = "cta_tap", url = "https://api.mock.admoai.com/v1/t/click-cta")
        ),
        thirdPartyTrackers = trackers
    )

    @Test
    fun `B13 E30 - impression fires canonical plus tracker GET with the exact URL`() {
        val engine = newMockEngine()
        initSdk(engine)
        val rawUrl = "https://agency.example/imp?b=2&a=1&ord=12345"
        Admoai.getInstance().fireImpression(trackingInfo(listOf(tracker(url = rawUrl))))

        awaitFires()
        val trackerReq = engine.requestHistory.single { it.url.host == "agency.example" }
        assertEquals(HttpMethod.Get, trackerReq.method)
        assertEquals(rawUrl, trackerReq.url.toString())
        assertEquals(1, engine.requestHistory.count { it.url.host == "api.mock.admoai.com" })
    }

    @Test
    fun `B14 C22 - only impression trackers fire on fireImpression`() {
        val engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(
            trackingInfo(
                listOf(
                    tracker(id = "tpt_1", url = "https://agency.example/i1"),
                    tracker(id = "tpt_2", url = "https://agency.example/i2"),
                    tracker(id = "tpt_3", eventType = "click", matchType = "any", url = "https://agency.example/c1")
                )
            )
        )
        awaitFires()
        val agencyUrls = engine.requestUrls().filter { it.contains("agency.example") }
        assertEquals(
            listOf("https://agency.example/i1", "https://agency.example/i2"),
            agencyUrls.sorted()
        )
    }

    @Test
    fun `B15 - two invocations fire the tracker twice (no cross-invocation dedupe)`() {
        val engine = newMockEngine()
        initSdk(engine)
        val info = trackingInfo(listOf(tracker(url = "https://agency.example/imp")))
        Admoai.getInstance().fireImpression(info)
        Admoai.getInstance().fireImpression(info)
        awaitFires()
        assertEquals(2, engine.requestUrls().count { it.contains("agency.example") })
    }

    @Test
    fun `B16 C21 - a key without a canonical URL fires nothing at all`() {
        val engine = newMockEngine()
        initSdk(engine)
        val info = trackingInfo(
            listOf(
                tracker(url = "https://agency.example/imp"),
                tracker(id = "tpt_2", eventType = "click", matchType = "any", url = "https://agency.example/c")
            )
        )
        Admoai.getInstance().fireImpression(info, key = "nonexistent")
        Admoai.getInstance().fireClick(info, key = "nonexistent")
        awaitFires()
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `B17 - no trackers means canonical only`() {
        val engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(trackingInfo(null))
        awaitFires()
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `C18-C20 - any-click fires on every valid key and specific only on its key`() {
        val engine = newMockEngine()
        initSdk(engine)
        val info = trackingInfo(
            listOf(
                tracker(id = "tpt_any", eventType = "click", matchType = "any", url = "https://agency.example/any"),
                tracker(id = "tpt_spec", eventType = "click", matchType = "specific", eventKey = "cta_tap", url = "https://agency.example/spec")
            )
        )
        Admoai.getInstance().fireClick(info) // "default": any fires, specific does not
        awaitFires()
        assertEquals(
            listOf("https://agency.example/any"),
            engine.requestUrls().filter { it.contains("agency.example") }
        )

        Admoai.getInstance().fireClick(info, key = "cta_tap") // both fire
        awaitFires()
        assertEquals(
            listOf("https://agency.example/any", "https://agency.example/any", "https://agency.example/spec"),
            engine.requestUrls().filter { it.contains("agency.example") }.sorted()
        )
    }

    @Test
    fun `D23 - byte-identical URLs dedupe within one invocation`() {
        val engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(
            trackingInfo(
                listOf(
                    tracker(id = "tpt_1", url = "https://agency.example/same"),
                    tracker(id = "tpt_2", url = "https://agency.example/same"),
                    tracker(id = "tpt_3", url = "https://agency.example/same?x=1")
                )
            )
        )
        awaitFires()
        assertEquals(
            listOf("https://agency.example/same", "https://agency.example/same?x=1"),
            engine.requestUrls().filter { it.contains("agency.example") }.sorted()
        )
    }

    @Test
    fun `D24 - the same URL on impression and click trackers fires once per event`() {
        val engine = newMockEngine()
        initSdk(engine)
        val info = trackingInfo(
            listOf(
                tracker(id = "tpt_1", url = "https://agency.example/shared"),
                tracker(id = "tpt_2", eventType = "click", matchType = "any", url = "https://agency.example/shared")
            )
        )
        Admoai.getInstance().fireImpression(info)
        Admoai.getInstance().fireClick(info)
        awaitFires()
        assertEquals(2, engine.requestUrls().count { it.contains("agency.example") })
    }

    @Test
    fun `D25 D26 D27 - the limit counts valid entries only`() {
        fun entries(count: Int, invalidExtra: Int = 0): List<ThirdPartyTracker> =
            (0 until count).map { tracker(id = "tpt_$it", url = "https://agency.example/t$it") } +
                (0 until invalidExtra).map { tracker(id = "tpt_bad", url = "http://insecure.example/x") }

        // 10 valid → all fire.
        var engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(trackingInfo(entries(10)))
        awaitFires()
        assertEquals(10, engine.requestUrls().count { it.contains("agency.example") })

        // 11 valid → none fire (canonical still does).
        Admoai.resetForTesting()
        engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(trackingInfo(entries(11)))
        awaitFires()
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, engine.requestUrls().count { it.contains("agency.example") })
        // D26 + F35: the over-limit warn fired once and carries no URL.
        val warns = capturedLogs.filter { it.contains("exceed the limit") }
        assertEquals(1, warns.size)
        assertFalse(warns.single().contains("agency.example"))

        // 9 valid + 2 invalid (11 raw) → the 9 fire, invalid never dispatch.
        Admoai.resetForTesting()
        engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(trackingInfo(entries(9, invalidExtra = 2)))
        awaitFires()
        assertEquals(9, engine.requestUrls().count { it.contains("agency.example") })
        assertEquals(0, engine.requestUrls().count { it.contains("insecure.example") })
    }

    @Test
    fun `E28 - tracker requests carry no Admoai identity while canonical does`() {
        val engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(trackingInfo(listOf(tracker(url = "https://agency.example/imp"))))
        awaitFires()

        val trackerReq = engine.requestHistory.single { it.url.host == "agency.example" }
        assertNull(trackerReq.headers["X-Decision-Version"])
        assertNull(trackerReq.headers["X-Tracking-Version"])
        assertNull(trackerReq.headers[HttpHeaders.AcceptLanguage])
        assertNull(trackerReq.headers[HttpHeaders.Authorization])
        assertFalse(trackerReq.headers[HttpHeaders.UserAgent].orEmpty().contains("AdMoaiSDK"))
        assertNull(trackerReq.headers[HttpHeaders.Cookie])
        // E32: no conditional-cache revalidation headers.
        assertNull(trackerReq.headers[HttpHeaders.IfNoneMatch])
        assertNull(trackerReq.headers[HttpHeaders.IfModifiedSince])

        val canonicalReq = engine.requestHistory.single { it.url.host == "api.mock.admoai.com" }
        assertEquals("2025-11-01", canonicalReq.headers["X-Tracking-Version"])
        assertEquals("en", canonicalReq.headers[HttpHeaders.AcceptLanguage])
        assertEquals("AdMoaiSDK/$SDK_VERSION", canonicalReq.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `A12 E30 wire - a macro tracker is discarded end-to-end while a clean sibling fires`() {
        val engine = newMockEngine()
        initSdk(engine)
        Admoai.getInstance().fireImpression(
            trackingInfo(
                listOf(
                    tracker(id = "tpt_macro", url = "https://agency.example/imp?cb=%%CACHEBUSTER%%"),
                    tracker(id = "tpt_ok", url = "https://agency.example/clean")
                )
            )
        )
        awaitFires()
        assertEquals(
            listOf("https://agency.example/clean"),
            engine.requestUrls().filter { it.contains("agency.example") }
        )
    }

    @Test
    fun `E29 - a Set-Cookie from a tracker is not persisted or re-sent`() {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.SetCookie, "session=abc123; Path=/")
            )
        }
        val dispatcher = ThirdPartyTrackerDispatcher(engine, { testScope }) { _, _ -> }
        dispatcher.dispatch(
            listOf(tracker(url = "https://agency.example/imp")), ThirdPartyTrackerEvent.Impression)
        awaitFires(testScope)
        dispatcher.dispatch(
            listOf(tracker(url = "https://agency.example/imp")), ThirdPartyTrackerEvent.Impression)
        awaitFires(testScope)
        assertEquals(2, engine.requestHistory.size)
        assertNull(engine.requestHistory[1].headers[HttpHeaders.Cookie])
        dispatcher.close()
    }

    @Test
    fun `E33 - a connection error on one tracker never escapes and never stops siblings`() {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/boom") {
                throw java.net.ConnectException("refused")
            }
            respond(content = "", status = HttpStatusCode.OK)
        }
        val dispatcher = ThirdPartyTrackerDispatcher(engine, { testScope }) { _, _ -> }
        dispatcher.dispatch(
            listOf(
                tracker(id = "tpt_1", url = "https://agency.example/boom"),
                tracker(id = "tpt_2", url = "https://agency.example/ok")
            ),
            ThirdPartyTrackerEvent.Impression
        )
        awaitFires(testScope)
        // MockEngine records only requests whose handler completed: the throwing tracker
        // is a completed (failed) attempt that escaped nowhere, and the sibling fired.
        assertEquals(listOf("https://agency.example/ok"), engine.requestUrls())
        dispatcher.close()
    }

    @Test
    fun `E31 - a 3xx from a tracker is terminal and the redirect target is never requested`() {
        val engine = newMockEngine(
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, "https://redirect-target.example/next")
        )
        val dispatcher = ThirdPartyTrackerDispatcher(engine, { testScope }) { _, _ -> }
        dispatcher.dispatch(listOf(tracker(url = "https://agency.example/imp")), ThirdPartyTrackerEvent.Impression)
        awaitFires(testScope)
        assertEquals(listOf("agency.example"), engine.requestHistory.map { it.url.host })
        dispatcher.close()
    }

    @Test
    fun `E33 E34 - a failing tracker is never retried and never affects siblings`() {
        val engine = newMockEngine(status = HttpStatusCode.InternalServerError)
        val dispatcher = ThirdPartyTrackerDispatcher(engine, { testScope }) { _, _ -> }
        dispatcher.dispatch(
            listOf(
                tracker(id = "tpt_1", url = "https://agency.example/a"),
                tracker(id = "tpt_2", url = "https://agency.example/b")
            ),
            ThirdPartyTrackerEvent.Impression
        )
        awaitFires(testScope)
        assertEquals(2, engine.requestHistory.size)
        dispatcher.close()
    }

    @Test
    fun `F36 - discard logs reference trackerId only, never the URL`() {
        val engine = newMockEngine()
        val logged = mutableListOf<String>()
        val dispatcher = ThirdPartyTrackerDispatcher(engine, { testScope }) { message, _ -> logged.add(message) }
        dispatcher.dispatch(
            listOf(tracker(id = "tpt_bad", url = "http://insecure.example/secret?campaign=X")),
            ThirdPartyTrackerEvent.Impression
        )
        awaitFires(testScope)
        assertTrue(logged.isNotEmpty())
        for (message in logged) {
            assertTrue(message.contains("tpt_bad"))
            assertFalse(message.contains("insecure.example"))
            assertFalse(message.contains("secret"))
        }
        dispatcher.close()
    }
}
