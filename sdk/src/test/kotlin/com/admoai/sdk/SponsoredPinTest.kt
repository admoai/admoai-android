package com.admoai.sdk

import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DistanceBounds
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.matchedPoints
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
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
import java.util.concurrent.TimeUnit

/**
 * Sponsored Pin Locations — distance requests, Matched Point models, point tracking.
 *
 * Spec: adhub `features/sponsored-pin-locations/specs/E11-sdk.md`. Test names reference its
 * acceptance-criteria numbers, the way [ThirdPartyTrackerTest] references E06's parity matrix.
 * Mirrors the iOS reference suite (`SponsoredPinTests.swift`).
 *
 * AC8c (no bulk tap or click exists) has no test: the absence of an API is not observable at
 * runtime, so it is a review item.
 */
class SponsoredPinTest {

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

    private fun builder() = sdk().createRequestBuilder().addPlacement("map")

    private fun creative(contents: String): Creative = json.decodeFromString(
        """
        { "contents": $contents,
          "advertiser": { "name": "Cabify" },
          "tracking": { "impressions": [{ "key": "default", "url": "https://t/imp" }],
                        "clicks": [{ "key": "default", "url": "https://t/click" }] } }
        """
    )

    private fun pointsJson(vararg points: String) =
        """[{ "key": "matched_points", "type": "matched_points", "value": [${points.joinToString(",")}] }]"""

    private fun trackedPoint(id: String, path: String) = """
        { "id": "$id", "name": "$id", "latitude": 1, "longitude": 2, "distance": 10,
          "tracking": {
            "views":  [{ "key": "default", "url": "http://127.0.0.1:${server.port}/$path/view" }],
            "taps":   [{ "key": "default", "url": "http://127.0.0.1:${server.port}/$path/tap" }],
            "clicks": [{ "key": "default", "url": "http://127.0.0.1:${server.port}/$path/click" }]
          } }
    """

    private fun firedPaths(count: Int): List<String> =
        (1..count).mapNotNull { server.takeRequest(3, TimeUnit.SECONDS)?.path }

    // ---- Request side ----

    /** AC1 — the radius overload builds a radius search and no bounds. */
    @Test
    fun `AC1 - radius search serializes`() {
        val request = builder()
            .setDistanceTargeting(latitude = -33.4175, longitude = -70.6065, radiusMeters = 8000.0)
            .build()
        val encoded = Json.encodeToString(request)

        assertTrue(encoded.contains("\"radius\":8000"))
        assertTrue(encoded.contains("\"latitude\":-33.4175"))
        assertFalse(encoded.contains("\"bounds\""))
    }

    /** AC1 — the bounds overload keeps the origin, because "nearest first" needs one. */
    @Test
    fun `AC1 - bounds search serializes and keeps the origin`() {
        val request = builder()
            .setDistanceTargeting(
                latitude = -33.4175, longitude = -70.6065,
                bounds = DistanceBounds(north = -33.38, south = -33.46, east = -70.54, west = -70.68)
            )
            .build()
        val encoded = Json.encodeToString(request)

        assertTrue(encoded.contains("\"bounds\""))
        assertTrue(encoded.contains("\"latitude\":-33.4175"))
        assertFalse(encoded.contains("\"radius\""))
    }

    /**
     * A distance-only request must still carry its targeting. `build()` drops the targeting
     * object when every axis is empty, and a Sponsored Pin request commonly sets nothing else.
     */
    @Test
    fun `AC1 - a distance-only request still sends targeting`() {
        val request = builder()
            .setDistanceTargeting(latitude = 1.0, longitude = 2.0, radiusMeters = 500.0)
            .build()

        assertTrue(Json.encodeToString(request).contains("\"distance\""))
    }

    /** AC4 — the limit is emitted when given and absent when not. */
    @Test
    fun `AC4 - limit is emitted only when given`() {
        val withLimit = Json.encodeToString(
            builder().setDistanceTargeting(0.0, 0.0, radiusMeters = 500.0, limit = 5).build()
        )
        val without = Json.encodeToString(
            builder().setDistanceTargeting(0.0, 0.0, radiusMeters = 500.0).build()
        )

        assertTrue(withLimit.contains("\"limit\":5"))
        assertFalse(without.contains("\"limit\""))
    }

    /** AC5 — a request that never asks for pins carries no `distance` at all. */
    @Test
    fun `AC5 - no distance key when never set`() {
        val encoded = Json.encodeToString(builder().setGeoTargets(listOf(123)).build())

        assertFalse(encoded.contains("\"distance\""))
    }

    /** AC2 — coordinates off the globe are refused locally, not by a 422. */
    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses impossible latitude`() {
        builder().setDistanceTargeting(latitude = 91.0, longitude = 0.0, radiusMeters = 100.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses impossible longitude`() {
        builder().setDistanceTargeting(latitude = 0.0, longitude = -181.0, radiusMeters = 100.0)
    }

    /** AC2 — a radius of zero or less is a bug worth refusing. */
    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses non-positive radius`() {
        builder().setDistanceTargeting(latitude = 0.0, longitude = 0.0, radiusMeters = 0.0)
    }

    /** AC2 — a rectangle that encloses nothing is refused. */
    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses inverted bounds`() {
        builder().setDistanceTargeting(
            0.0, 0.0, DistanceBounds(north = -33.46, south = -33.38, east = -70.54, west = -70.68)
        )
    }

    /** AC2 — V1 refuses an antimeridian crossing, keeping `west less than east` flat. */
    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses antimeridian bounds`() {
        builder().setDistanceTargeting(
            0.0, 179.0, DistanceBounds(north = 10.0, south = -10.0, east = -179.0, west = 179.0)
        )
    }

    /** AC2 — asking for no points is a bug, not a way to ask for all of them. */
    @Test(expected = IllegalArgumentException::class)
    fun `AC2 - refuses non-positive limit`() {
        builder().setDistanceTargeting(0.0, 0.0, radiusMeters = 100.0, limit = 0)
    }

    /**
     * AC3 — the SDK hard-codes NO ceiling. 50 km and 100 km are server policy, and a client that
     * bakes them in refuses what a newer engine would accept.
     */
    @Test
    fun `AC3 - accepts a radius above the servers current ceiling`() {
        val encoded = Json.encodeToString(
            builder().setDistanceTargeting(0.0, 0.0, radiusMeters = 80_000.0).build()
        )

        assertTrue(encoded.contains("\"radius\":80000"))
    }

    @Test
    fun `AC3 - accepts bounds larger than the servers current ceiling`() {
        val encoded = Json.encodeToString(
            builder().setDistanceTargeting(
                0.0, 0.0, DistanceBounds(north = 5.0, south = -5.0, east = 5.0, west = -5.0)
            ).build()
        )

        assertTrue(encoded.contains("\"bounds\""))
    }

    @Test
    fun `clear removes the search and nothing else`() {
        val encoded = Json.encodeToString(
            builder()
                .setDistanceTargeting(0.0, 0.0, radiusMeters = 100.0)
                .setGeoTargets(listOf(42))
                .clearDistanceTargeting()
                .build()
        )

        assertFalse(encoded.contains("\"distance\""))
        assertTrue(encoded.contains("\"geo\":[42]"))
    }

    @Test
    fun `another targeting axis does not drop the search`() {
        val encoded = Json.encodeToString(
            builder()
                .setDistanceTargeting(-33.4175, -70.6065, radiusMeters = 8000.0)
                .setGeoTargets(listOf(42))
                .addLocationTarget(1.0, 2.0)
                .build()
        )

        assertTrue(encoded.contains("\"radius\":8000"))
        assertTrue(encoded.contains("\"geo\":[42]"))
    }

    // ---- Response side ----

    /** AC4 — every field maps, in the server's order. */
    @Test
    fun `AC4 - matched points decode with every field`() {
        val c = creative(
            pointsJson(
                """
                { "id": "advertiser_location_01ARZ3NDEKTSV4RRFFQ69G5FAV", "name": "Parque Arauco",
                  "address": "Parque Arauco, Santiago", "latitude": -33.4030, "longitude": -70.5680,
                  "distance": 1834, "clickUrl": "https://shop.example/parque-arauco",
                  "tracking": { "views": [{ "key": "default", "url": "https://t/pin/view" }] } }
                """
            )
        )
        val point = c.matchedPoints.single()

        assertEquals("advertiser_location_01ARZ3NDEKTSV4RRFFQ69G5FAV", point.id)
        assertEquals("Parque Arauco", point.name)
        assertEquals("Parque Arauco, Santiago", point.address)
        assertEquals(-33.4030, point.latitude, 0.0)
        assertEquals(1834, point.distance)
        assertEquals("https://shop.example/parque-arauco", point.clickUrl)
        assertEquals("https://t/pin/view", point.tracking?.views?.single()?.url)
    }

    /** AC5 — a creative with no such entry yields an empty list and no error. */
    @Test
    fun `AC5 - creative without matched points is empty`() {
        val c = creative("""[{ "key": "headline", "type": "text", "value": "Hi" }]""")

        assertTrue(c.matchedPoints.isEmpty())
        assertEquals(1, c.contents.size)
    }

    /**
     * AC6 — a Sponsored Pin response stays parseable by code that knows nothing about pins: the
     * entry decodes as an ordinary content item, as it always did.
     */
    @Test
    fun `AC6 - matched points remain readable as an ordinary content entry`() {
        val c = creative(pointsJson(trackedPoint("loc_1", "a")))

        assertEquals(1, c.contents.size)
        assertEquals("matched_points", c.contents.single().key)
    }

    /** An absent address or click URL is absent, never an empty string. */
    @Test
    fun `absent address and click url are null`() {
        val c = creative(
            pointsJson("""{ "id": "loc_1", "name": "Shop", "latitude": 1, "longitude": 2, "distance": 10 }""")
        )
        val point = c.matchedPoints.single()

        assertNull(point.address)
        assertNull(point.clickUrl)
        assertNull(point.tracking)
    }

    /** A point gaining a field in a later server release must not break a shipped app. */
    @Test
    fun `unknown point fields are ignored`() {
        val c = creative(
            pointsJson(
                """{ "id": "loc_1", "name": "Shop", "latitude": 1, "longitude": 2,
                     "distance": 10, "openingHours": "9-5" }"""
            )
        )

        assertEquals(1, c.matchedPoints.size)
    }

    /** A malformed point drops; its siblings survive; the creative never fails. */
    @Test
    fun `malformed point is dropped and siblings survive`() {
        val c = creative(
            pointsJson(
                """{ "id": "loc_1", "name": "Good", "latitude": 1, "longitude": 2, "distance": 10 }""",
                """{ "id": "loc_2" }""",
                """{ "id": "loc_3", "name": "Also", "latitude": 3, "longitude": 4, "distance": 20 }"""
            )
        )

        assertEquals(listOf("loc_1", "loc_3"), c.matchedPoints.map { it.id })
    }

    /** AC10 — points hang off their creative, so two winning ads cannot be confused. */
    @Test
    fun `AC10 - two creatives keep their own points`() {
        val a = creative(pointsJson(trackedPoint("loc_a", "a")))
        val b = creative(pointsJson(trackedPoint("loc_b", "b")))

        assertEquals(listOf("loc_a"), a.matchedPoints.map { it.id })
        assertEquals(listOf("loc_b"), b.matchedPoints.map { it.id })
    }

    // ---- Point tracking ----

    /** AC8 — each call fires exactly its own list and nothing else. */
    @Test
    fun `AC8 - each helper fires its own list`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200)) }
        val admoai = sdk()
        val point = creative(pointsJson(trackedPoint("loc_1", "p"))).matchedPoints.single()

        admoai.trackPointView(point)
        assertEquals("/p/view", firedPaths(1).single())

        admoai.trackPointTap(point)
        assertEquals("/p/tap", firedPaths(1).single())

        admoai.trackPointClick(point)
        assertEquals("/p/click", firedPaths(1).single())
    }

    /**
     * AC12 — opening a detail card is a tap and never a click. This is the one that costs money
     * when it is wrong, so it is asserted rather than documented.
     */
    @Test
    fun `AC12 - a tap never fires the click beacon`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val admoai = sdk()
        val point = creative(pointsJson(trackedPoint("loc_1", "p"))).matchedPoints.single()

        admoai.trackPointTap(point)

        assertEquals(listOf("/p/tap"), firedPaths(1))
        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** A point with nothing to report reports nothing, and raises nothing. */
    @Test
    fun `point without tracking fires nothing`() = runTest {
        val admoai = sdk()
        val point = creative(
            pointsJson("""{ "id": "loc_1", "name": "Shop", "latitude": 1, "longitude": 2, "distance": 10 }""")
        ).matchedPoints.single()

        admoai.trackPointView(point)
        admoai.trackPointTap(point)
        admoai.trackPointClick(point)

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** AC7 — parsing a response fires nothing. */
    @Test
    fun `AC7 - parsing fires nothing`() = runTest {
        sdk()
        creative(pointsJson(trackedPoint("loc_1", "p"))).matchedPoints.map { it.id }

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** AC8b — the bulk form fires one view per point. */
    @Test
    fun `AC8b - bulk fires one view per point`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200)) }
        val admoai = sdk()
        val points = creative(
            pointsJson(trackedPoint("a", "a"), trackedPoint("b", "b"), trackedPoint("c", "c"))
        ).matchedPoints

        admoai.trackPointViews(points)

        assertEquals(setOf("/a/view", "/b/view", "/c/view"), firedPaths(3).toSet())
    }

    /** AC8b — the same point named twice in one call is one view. */
    @Test
    fun `AC8b - bulk deduplicates within the invocation`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val admoai = sdk()
        val point = creative(pointsJson(trackedPoint("a", "a"))).matchedPoints.single()

        admoai.trackPointViews(listOf(point, point, point))

        assertEquals(listOf("/a/view"), firedPaths(1))
        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** AC8b — two renders are two views. No de-duplication across invocations. */
    @Test
    fun `AC8b - bulk does not deduplicate across invocations`() = runTest {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val admoai = sdk()
        val point = creative(pointsJson(trackedPoint("a", "a"))).matchedPoints.single()

        admoai.trackPointViews(listOf(point))
        admoai.trackPointViews(listOf(point))

        assertEquals(listOf("/a/view", "/a/view"), firedPaths(2))
    }

    /** AC8b — an empty list is a no-op, not a crash. */
    @Test
    fun `AC8b - bulk with no points fires nothing`() = runTest {
        sdk().trackPointViews(emptyList())

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** AC8b — a point with nothing to report is skipped; its siblings still report. */
    @Test
    fun `AC8b - bulk skips untrackable points without affecting siblings`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val admoai = sdk()
        val points = creative(
            pointsJson(
                trackedPoint("a", "a"),
                """{ "id": "silent", "name": "S", "latitude": 2, "longitude": 2, "distance": 2 }"""
            )
        ).matchedPoints

        admoai.trackPointViews(points)

        assertEquals(listOf("/a/view"), firedPaths(1))
        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    /** AC9 — the resolved destination is used verbatim. */
    @Test
    fun `AC9 - click url is used verbatim`() {
        val c = creative(
            pointsJson(
                """{ "id": "loc_1", "name": "Shop", "latitude": 1, "longitude": 2, "distance": 10,
                     "clickUrl": "https://shop.example/parque-arauco" }"""
            )
        )

        assertEquals("https://shop.example/parque-arauco", c.matchedPoints.single().clickUrl)
    }
}
