package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.MatchedPoint
import com.admoai.sdk.model.response.matchedPoints
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pin views are reported once per decision response — the scenarios of
 * `src/main/kotlin/com/admoai/sdk/PointViewLedger.feature`, one test per scenario, named after it.
 * Mirrors the iOS suite (`PointViewLedgerTests.swift`).
 *
 * There is no Gherkin runner in this module, so `every scenario has a test` holds the binding
 * instead: a scenario added to the feature without a test here, or a test whose scenario was
 * renamed away, fails the build.
 */
class PointViewLedgerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

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

    private fun sdk(): Admoai {
        Admoai.initialize(
            SDKConfig(
                baseUrl = "http://127.0.0.1:${server.port}/",
                apiVersion = "2025-11-01",
                networkClientEngine = CIO.create()
            )
        )
        return Admoai.getInstance()
    }

    /**
     * Decodes the points of [range] as decision response [response] would carry them. The view
     * URL embeds the response the way the engine's per-event token does: the same pin in two
     * responses has two different view URLs.
     */
    private fun points(range: IntRange, response: String): List<MatchedPoint> {
        val entries = range.joinToString(",") { i ->
            """
            { "id": "p$i", "name": "P$i", "latitude": 1, "longitude": 1, "distance": $i,
              "tracking": {
                "views":  [{ "key": "default", "url": "${url(response, "view", i)}" }],
                "taps":   [{ "key": "default", "url": "${url(response, "tap", i)}" }],
                "clicks": [{ "key": "default", "url": "${url(response, "click", i)}" }]
              } }
            """
        }
        return json.decodeFromString<Creative>(
            """
            { "contents": [{ "key": "matched_points", "type": "matched_points", "value": [$entries] }],
              "advertiser": { "name": "Cabify" },
              "tracking": {} }
            """
        ).matchedPoints
    }

    private fun url(response: String, event: String, pin: Int) =
        "http://127.0.0.1:${server.port}/$response/$event/p$pin"

    private fun viewPaths(range: IntRange, response: String) = range.map { "/$response/view/p$it" }.toSet()

    private fun respondOk(times: Int) = repeat(times) { server.enqueue(MockResponse().setResponseCode(200)) }

    private fun fired(count: Int): List<String> =
        (1..count).mapNotNull { server.takeRequest(3, TimeUnit.SECONDS)?.path }

    private fun assertNothingMoreFired() = assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))

    @Test
    fun `every scenario has a test`() {
        val feature = File("src/main/kotlin/com/admoai/sdk/PointViewLedger.feature")
        val titles = feature.readLines()
            .map { it.trim() }
            .filter { it.startsWith("Scenario:") }
            .map { it.removePrefix("Scenario:").trim() }
            .toSet()
        val tests = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()

        assertTrue("the feature file has scenarios", titles.isNotEmpty())
        assertEquals(emptySet<String>(), titles - tests)
    }

    @Test
    fun `the first render fires every pin's view URLs`() {
        respondOk(50)
        val admoai = sdk()

        admoai.trackPointViews(points(0 until 50, "r"))

        assertEquals(viewPaths(0 until 50, "r"), fired(50).toSet())
        assertNothingMoreFired()
    }

    @Test
    fun `a re-render of the same response fires nothing`() {
        respondOk(50)
        val admoai = sdk()
        val r = points(0 until 50, "r")
        admoai.trackPointViews(r)
        assertEquals(50, fired(50).size)

        admoai.trackPointViews(r)

        assertNothingMoreFired()
    }

    @Test
    fun `a re-render with new pins fires only the new ones`() {
        // A..J is 0..9, F..T is 5..19, K..T is 10..19.
        respondOk(20)
        val admoai = sdk()
        val r = points(0 until 20, "r")
        admoai.trackPointViews(r.subList(0, 10))
        assertEquals(10, fired(10).size)

        admoai.trackPointViews(r.subList(5, 20))

        assertEquals(viewPaths(10 until 20, "r"), fired(10).toSet())
        assertNothingMoreFired()
    }

    @Test
    fun `a new decision response starts fresh`() {
        respondOk(2)
        val admoai = sdk()
        val r1 = points(0..0, "r1")
        val r2 = points(0..0, "r2")
        assertEquals("the same location in both responses", r1.single().id, r2.single().id)
        admoai.trackPointViews(r1)
        assertEquals(listOf("/r1/view/p0"), fired(1))

        admoai.trackPointViews(r2)

        assertEquals(listOf("/r2/view/p0"), fired(1))
    }

    @Test
    fun `memory is bounded`() {
        val ledger = PointViewLedger()
        assertEquals(500, ledger.capacity)

        for (response in 0 until 100) {
            for (pin in 0 until 50) ledger.claim("https://t/r$response/view/p$pin")
        }

        assertEquals("the ten most recent full responses", 500, ledger.size)
        assertFalse("the newest response is remembered", ledger.claim("https://t/r99/view/p0"))
        assertFalse("so is the tenth newest", ledger.claim("https://t/r90/view/p49"))
        assertTrue("the eleventh is forgotten and fires", ledger.claim("https://t/r89/view/p49"))
        assertEquals(500, ledger.size)
    }

    /**
     * A pin re-rendered keeps its place: the next eviction takes the pin nobody has shown for
     * longest, not the one the user is looking at.
     */
    @Test
    fun `a rendered pin is not the next evicted`() {
        val ledger = PointViewLedger(capacity = 2)
        ledger.claim("a")
        ledger.claim("b")
        ledger.claim("a")
        ledger.claim("c")

        assertFalse(ledger.claim("a"))
        assertTrue(ledger.claim("b"))
    }

    /** Map callbacks arrive on any thread. Renders racing over one screenful send each view once. */
    @Test
    fun `concurrent renders fire each view once`() {
        respondOk(50)
        val admoai = sdk()
        val r = points(0 until 50, "r")
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) {
            pool.execute {
                start.await()
                admoai.trackPointViews(r)
            }
        }

        start.countDown()
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        val paths = fired(50)
        assertEquals(50, paths.size)
        assertEquals(viewPaths(0 until 50, "r"), paths.toSet())
        assertNothingMoreFired()
    }

    /**
     * `initialize()` produces what iOS and Flutter get from a new instance: a fresh record, the
     * same way it resets the Journey session.
     */
    @Test
    fun `re-initializing forgets the views already fired`() {
        respondOk(2)
        val r = points(0..0, "r")
        sdk().trackPointViews(r)
        assertEquals(listOf("/r/view/p0"), fired(1))

        sdk().trackPointViews(r)

        assertEquals(listOf("/r/view/p0"), fired(1))
    }

    @Test
    fun `single-point tracking follows the same rule`() {
        respondOk(1)
        val admoai = sdk()
        val point = points(0..0, "r").single()
        admoai.trackPointView(point)
        assertEquals(listOf("/r/view/p0"), fired(1))

        admoai.trackPointView(point)
        admoai.trackPointViews(listOf(point))

        assertNothingMoreFired()
    }

    @Test
    fun `taps and clicks are not deduplicated by this mechanism`() {
        respondOk(5)
        val admoai = sdk()
        val point = points(0..0, "r").single()
        admoai.trackPointView(point)
        assertEquals(listOf("/r/view/p0"), fired(1))

        admoai.trackPointTap(point)
        admoai.trackPointTap(point)
        admoai.trackPointClick(point)
        admoai.trackPointClick(point)

        val paths = fired(4)
        assertEquals(2, paths.count { it == "/r/tap/p0" })
        assertEquals(2, paths.count { it == "/r/click/p0" })
        assertNothingMoreFired()
    }
}
