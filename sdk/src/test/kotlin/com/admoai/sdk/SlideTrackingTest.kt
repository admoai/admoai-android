package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.slides
import com.admoai.sdk.network.ThirdPartyTrackerDispatcher
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Carousel slide tracking — the helpers a publisher's app calls per slide.
 *
 * Feature: A Carousel's slides report their own views and clicks
 *   As a publisher's tech team
 *   I want one call per slide view and per slide click
 *   So that each slide is counted once per served response and every tap is billed once
 *
 * The shared cross-SDK scenarios (F16-F20) run in [FixtureScenariosTest]; these tests pin the same
 * rules on hand-built creatives, plus the edges the manifest does not name. Mirrors the iOS suite
 * (`SlideTrackingTests.swift`).
 */
class SlideTrackingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val firstPartyArrived = Collections.synchronizedList(mutableListOf<String>())
    private val thirdPartyArrived = Collections.synchronizedList(mutableListOf<String>())
    private val dispatched = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        Admoai.resetForTesting()
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun sdk(): Admoai {
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://decision.test/",
                apiVersion = "2025-11-01",
                networkClientEngine = MockEngine { request ->
                    firstPartyArrived += request.url.toString()
                    respond("", HttpStatusCode.OK)
                }
            )
        )
        val sdk = Admoai.getInstance()
        sdk.thirdPartyDispatcher = ThirdPartyTrackerDispatcher(
            engine = MockEngine { request ->
                thirdPartyArrived += request.url.toString()
                respond("", HttpStatusCode.OK)
            },
            scopeProvider = { sdk.sdkScope }
        ) { _, _ -> }
        sdk.beaconObserver = { dispatched += it }
        return sdk
    }

    private fun slideJson(n: Int, views: Boolean = true, clicks: Boolean = true): String {
        val tracking = listOfNotNull(
            if (views) """"views": [{"key": "slide_view", "url": "https://t/slide$n/view"}]""" else null,
            if (clicks) """"clicks": [{"key": "default", "url": "https://t/slide$n/click"}]""" else null
        ).joinToString(",")
        return """{"slideId": "slide_$n", "position": $n, "contents": [], "tracking": {$tracking}}"""
    }

    private fun creative(vararg slides: String): Creative = json.decodeFromString(
        """
        {
          "contents": [{ "key": "slides", "type": "slides", "value": [${slides.joinToString(",")}] }],
          "advertiser": { "name": "Acme" },
          "tracking": {
            "thirdPartyTrackers": [
              {"trackerId": "any", "eventType": "click", "matchType": "any", "url": "https://agency.example/any"},
              {"trackerId": "default", "eventType": "click", "matchType": "specific",
               "eventKey": "default", "url": "https://agency.example/default"},
              {"trackerId": "other", "eventType": "click", "matchType": "specific",
               "eventKey": "save", "url": "https://agency.example/other"},
              {"trackerId": "imp", "eventType": "impression", "url": "https://agency.example/imp"}
            ]
          }
        }
        """
    )

    /** Waits for the expected arrivals, then proves nothing more arrives. */
    private fun settle(firstParty: Int, thirdParty: Int = 0) = runBlocking {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline &&
            (firstPartyArrived.size < firstParty || thirdPartyArrived.size < thirdParty)
        ) {
            delay(20)
        }
        delay(250)
    }

    @Test
    fun `a slide view is sent once per response`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        sdk.trackSlideView(creative.slides[0])
        sdk.trackSlideView(creative.slides[0])
        settle(1)
        assertEquals(listOf("https://t/slide1/view"), firstPartyArrived.toList())
    }

    @Test
    fun `bulk views send each slide once`() {
        val creative = creative(slideJson(1), slideJson(2), slideJson(3))
        val sdk = sdk()
        sdk.trackSlideViews(creative.slides)
        sdk.trackSlideViews(creative.slides.drop(1))
        settle(3)
        assertEquals(
            setOf("https://t/slide1/view", "https://t/slide2/view", "https://t/slide3/view"),
            firstPartyArrived.toSet()
        )
        assertEquals(3, firstPartyArrived.size)
    }

    @Test
    fun `a click before the view sends the view then the click`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(2, 2)
        // Dispatch order is the contract; arrival order at the mock is the network's.
        assertEquals(listOf("https://t/slide1/view", "https://t/slide1/click"), dispatched.toList())
        assertEquals(2, firstPartyArrived.size)
    }

    @Test
    fun `a tap on a slide without a click tracker still counts its view`() {
        val creative = creative(slideJson(1, clicks = false))
        val sdk = sdk()
        sdk.trackSlideClick(creative.slides[0], creative)
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(1)
        assertEquals(
            "the tap proves the slide was seen; no click, no fan-out",
            listOf("https://t/slide1/view"),
            firstPartyArrived.toList()
        )
        assertTrue(thirdPartyArrived.isEmpty())
    }

    private fun templatedSlide(views: List<String>, clicks: List<String>): String {
        fun list(keys: List<String>) = keys.joinToString(",") { """{"key": "$it", "url": "https://t/s1/$it"}""" }
        return """{"slideId": "slide_1", "position": 1, "contents": [],
            "tracking": {"views": [${list(views)}], "clicks": [${list(clicks)}]}}"""
    }

    @Test
    fun `a view sends only slide_view even when the template adds other view events`() {
        val creative =
            creative(templatedSlide(listOf("slide_view", "slide_shown_custom"), listOf("save_offer", "default")))
        val sdk = sdk()
        sdk.trackSlideView(creative.slides[0])
        settle(1)
        assertEquals(listOf("https://t/s1/slide_view"), dispatched.toList())
    }

    @Test
    fun `a tap sends the pending slide_view then only the default click and fans out for default`() {
        val creative =
            creative(templatedSlide(listOf("slide_view", "slide_shown_custom"), listOf("save_offer", "default")))
        val sdk = sdk()
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(2, 2)
        assertEquals(listOf("https://t/s1/slide_view", "https://t/s1/default"), dispatched.toList())
        assertEquals(
            "the default-specific and any trackers, never save_offer's",
            listOf("https://agency.example/any", "https://agency.example/default"),
            thirdPartyArrived.toList().sorted()
        )
    }

    @Test
    fun `a slide whose only click is a custom one counts its view and nothing else`() {
        val creative = creative(templatedSlide(listOf("slide_view"), listOf("save_offer")))
        val sdk = sdk()
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(1)
        assertEquals(listOf("https://t/s1/slide_view"), dispatched.toList())
        assertTrue(thirdPartyArrived.isEmpty())
    }

    @Test
    fun `a click after the view sends only the click`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        sdk.trackSlideView(creative.slides[0])
        settle(1)
        firstPartyArrived.clear()
        sdk.trackSlideView(creative.slides[0])
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(1, 2)
        assertEquals(listOf("https://t/slide1/click"), firstPartyArrived.toList())
    }

    @Test
    fun `every click fans out the applicable third-party trackers once`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        sdk.trackSlideClick(creative.slides[0], creative)
        sdk.trackSlideClick(creative.slides[0], creative)
        settle(3, 4)
        assertEquals(
            listOf(
                "https://agency.example/any",
                "https://agency.example/any",
                "https://agency.example/default",
                "https://agency.example/default"
            ),
            thirdPartyArrived.toList().sorted()
        )
    }

    @Test
    fun `a slide view never fans out third-party trackers`() {
        val creative = creative(slideJson(1), slideJson(2))
        val sdk = sdk()
        sdk.trackSlideViews(creative.slides)
        settle(2)
        assertTrue(thirdPartyArrived.isEmpty())
        assertTrue(firstPartyArrived.all { it.startsWith("https://t/") })
    }

    @Test
    fun `a slide without tracking is a no-op`() {
        val creative = creative(
            """{"slideId": "slide_1", "position": 1, "contents": []}""",
            slideJson(2, views = false, clicks = false)
        )
        val sdk = sdk()
        sdk.trackSlideViews(creative.slides)
        sdk.trackSlideClick(creative.slides[0], creative)
        sdk.trackSlideClick(creative.slides[1], creative)
        settle(0)
        assertTrue("no tracker at all: nothing to send, and no fan-out", firstPartyArrived.isEmpty())
        assertTrue(thirdPartyArrived.isEmpty())
    }

    @Test
    fun `slide and pin views keep independent ledgers`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        assertNotSame(sdk.pinViewLedger, sdk.slideViewLedger)
        assertTrue(sdk.slideViewLedger.claim("https://t/shared"))
        assertTrue("claiming as a slide does not claim as a pin", sdk.pinViewLedger.claim("https://t/shared"))
        sdk.trackSlideView(creative.slides[0])
        settle(1)
        assertEquals(listOf("https://t/slide1/view"), firstPartyArrived.toList())
    }

    @Test
    fun `re-initializing forgets slide views`() {
        val creative = creative(slideJson(1))
        sdk().trackSlideView(creative.slides[0])
        settle(1)
        firstPartyArrived.clear()
        val sdk = sdk()
        sdk.trackSlideView(creative.slides[0])
        settle(1)
        assertEquals(listOf("https://t/slide1/view"), firstPartyArrived.toList())
    }

    /**
     * The Carousel has no creative-level click: [Admoai.fireClick] records nothing for it, and the
     * third-party trackers do not fan out for a click the creative cannot record.
     */
    @Test
    fun `fireClick on a creative without a click sends nothing`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        sdk.fireClick(creative.tracking)
        settle(0)
        assertTrue(firstPartyArrived.isEmpty())
        assertTrue(thirdPartyArrived.isEmpty())
    }

    @Test
    fun `a click racing another thread's view still dispatches the view first`() {
        val creative = creative(slideJson(1))
        val sdk = sdk()
        val log = Collections.synchronizedList(mutableListOf<String>())
        val aInside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bDone = CountDownLatch(1)
        sdk.beaconObserver = { url ->
            if (url == "https://t/slide1/view") {
                aInside.countDown()
                release.await(1, TimeUnit.SECONDS)
            }
            log += url
        }
        val a = thread { sdk.trackSlideView(creative.slides[0]) }
        assertTrue(aInside.await(2, TimeUnit.SECONDS))
        val b = thread {
            sdk.trackSlideClick(creative.slides[0], creative)
            bDone.countDown()
        }
        // Without the lock B finishes at once, recording the click while A is still blocked.
        bDone.await(300, TimeUnit.MILLISECONDS)
        release.countDown()
        a.join(3_000)
        b.join(3_000)
        assertEquals(listOf("https://t/slide1/view", "https://t/slide1/click"), log.toList())
    }
}
