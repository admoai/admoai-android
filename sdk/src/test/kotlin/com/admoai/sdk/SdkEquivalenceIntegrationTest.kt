package com.admoai.sdk

import com.admoai.sdk.config.DeviceConfig
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.DestinationTargetingInfo
import com.admoai.sdk.model.request.LocationTargetingInfo
import com.admoai.sdk.model.response.Advertiser
import com.admoai.sdk.model.response.Content
import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.TrackingDetail
import com.admoai.sdk.model.response.TrackingInfo
import com.admoai.sdk.model.response.TrackingType
import com.admoai.sdk.model.response.getClickUrl
import com.admoai.sdk.model.response.getContent
import com.admoai.sdk.model.response.getCustomUrl
import com.admoai.sdk.model.response.getImpressionUrl
import com.admoai.sdk.model.response.getTrackingUrl
import com.admoai.sdk.model.response.getVideoEventUrl
import com.admoai.sdk.model.response.hasContents
import com.admoai.sdk.model.response.hasTrackingFor
import com.admoai.sdk.model.response.isType
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests validating Android/iOS/Flutter SDK equivalence.
 *
 * Each test group corresponds to one of the 11 gaps identified in the cross-SDK
 * analysis. Tests use MockWebServer where network behaviour matters, and pure
 * in-process assertions where logic alone is sufficient.
 */
class SdkEquivalenceIntegrationTest {

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

    private fun baseUrl() = "http://127.0.0.1:${server.port}/"
    private fun okJson(body: String = """{"success":true,"data":[]}""") =
        MockResponse().setResponseCode(200).setBody(body).addHeader("Content-Type", "application/json")

    // ─────────────────────────────────────────────
    // 1. HTTP HEADER PARITY
    // All three headers must travel together on every request type.
    // ─────────────────────────────────────────────

    @Test
    fun `gap 1 - all SDK identity headers present on decision request`() = runTest {
        Admoai.initialize(
            SDKConfig(
                baseUrl = baseUrl(),
                apiVersion = "2025-11-01",
                defaultLanguage = "es",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(okJson())

        Admoai.getInstance()
            .requestAds(DecisionRequestBuilder().addPlacement("home").build())
            .first()

        val req = server.takeRequest()
        assertNotNull("User-Agent must be present", req.getHeader("User-Agent"))
        assertTrue("User-Agent must identify SDK", req.getHeader("User-Agent")!!.startsWith("AdMoaiSDK/"))
        assertEquals("Accept-Language must match config", "es", req.getHeader("Accept-Language"))
        assertEquals("X-Decision-Version must match config", "2025-11-01", req.getHeader("X-Decision-Version"))
    }

    @Test
    fun `gap 1 - all SDK identity headers present on tracking request`() {
        Admoai.initialize(
            SDKConfig(
                baseUrl = baseUrl(),
                apiVersion = "2025-11-01",
                defaultLanguage = "pt",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))
        val tracking = TrackingInfo(
            impressions = listOf(TrackingDetail("default", "${baseUrl()}track/imp"))
        )

        Admoai.getInstance().fireImpression(tracking)

        val req = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("Tracking request must arrive", req)
        assertNotNull("User-Agent must be present on tracking", req!!.getHeader("User-Agent"))
        assertTrue(req.getHeader("User-Agent")!!.startsWith("AdMoaiSDK/"))
        assertEquals("Accept-Language on tracking must match config", "pt", req.getHeader("Accept-Language"))
        assertEquals("X-Decision-Version on tracking must match config", "2025-11-01", req.getHeader("X-Decision-Version"))
    }

    @Test
    fun `gap 1 - headers absent when config fields are null`() = runTest {
        Admoai.initialize(
            SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create())
        )
        server.enqueue(okJson())

        Admoai.getInstance()
            .requestAds(DecisionRequestBuilder().addPlacement("home").build())
            .first()

        val req = server.takeRequest()
        assertNull("Accept-Language must be absent when not configured", req.getHeader("Accept-Language"))
        assertNull("X-Decision-Version must be absent when not configured", req.getHeader("X-Decision-Version"))
        assertNotNull("User-Agent must always be present", req.getHeader("User-Agent"))
    }

    // ─────────────────────────────────────────────
    // 2. ADVERTISER.ID NULLABILITY
    // Server-omitted id must arrive as null, not "".
    // ─────────────────────────────────────────────

    @Test
    fun `gap 2 - advertiser id is null when server omits the field`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val advertiser = json.decodeFromString<Advertiser>("""{"name":"Acme"}""")
        assertNull("id must be null when server omits it", advertiser.id)
    }

    @Test
    fun `gap 2 - advertiser id equals server value when provided`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val advertiser = json.decodeFromString<Advertiser>("""{"id":"adv_01XYZ","name":"Acme"}""")
        assertEquals("adv_01XYZ", advertiser.id)
    }

    // ─────────────────────────────────────────────
    // 3. TRACKING INFO URL HELPERS
    // All six helpers return null safely — never throw.
    // ─────────────────────────────────────────────

    private val richTracking = TrackingInfo(
        impressions = listOf(
            TrackingDetail("default", "https://t.admoai.com/imp/default"),
            TrackingDetail("secondary", "https://t.admoai.com/imp/secondary")
        ),
        clicks = listOf(TrackingDetail("default", "https://t.admoai.com/click")),
        custom = listOf(TrackingDetail("cta", "https://t.admoai.com/cta")),
        videoEvents = listOf(
            TrackingDetail("start", "https://t.admoai.com/start"),
            TrackingDetail("complete", "https://t.admoai.com/complete")
        )
    )

    @Test
    fun `gap 3 - getImpressionUrl returns correct url for default key`() {
        assertEquals("https://t.admoai.com/imp/default", richTracking.getImpressionUrl())
    }

    @Test
    fun `gap 3 - getImpressionUrl returns correct url for named key`() {
        assertEquals("https://t.admoai.com/imp/secondary", richTracking.getImpressionUrl("secondary"))
    }

    @Test
    fun `gap 3 - getImpressionUrl returns null for missing key without throwing`() {
        assertNull(richTracking.getImpressionUrl("missing"))
    }

    @Test
    fun `gap 3 - getImpressionUrl returns null when impressions list is null`() {
        assertNull(TrackingInfo(impressions = null).getImpressionUrl())
    }

    @Test
    fun `gap 3 - getClickUrl returns url for default key`() {
        assertEquals("https://t.admoai.com/click", richTracking.getClickUrl())
    }

    @Test
    fun `gap 3 - getCustomUrl returns url for named key`() {
        assertEquals("https://t.admoai.com/cta", richTracking.getCustomUrl("cta"))
    }

    @Test
    fun `gap 3 - getVideoEventUrl returns url for start and complete`() {
        assertEquals("https://t.admoai.com/start", richTracking.getVideoEventUrl("start"))
        assertEquals("https://t.admoai.com/complete", richTracking.getVideoEventUrl("complete"))
    }

    @Test
    fun `gap 3 - getTrackingUrl dispatches to correct list by TrackingType`() {
        assertEquals(richTracking.getImpressionUrl("default"), richTracking.getTrackingUrl(TrackingType.IMPRESSION, "default"))
        assertEquals(richTracking.getClickUrl("default"), richTracking.getTrackingUrl(TrackingType.CLICK, "default"))
        assertEquals(richTracking.getCustomUrl("cta"), richTracking.getTrackingUrl(TrackingType.CUSTOM, "cta"))
        assertEquals(richTracking.getVideoEventUrl("start"), richTracking.getTrackingUrl(TrackingType.VIDEO_EVENT, "start"))
    }

    @Test
    fun `gap 3 - hasTrackingFor returns true for existing key and false for missing`() {
        assertTrue(richTracking.hasTrackingFor(TrackingType.IMPRESSION, "default"))
        assertFalse(richTracking.hasTrackingFor(TrackingType.IMPRESSION, "ghost"))
        assertTrue(richTracking.hasTrackingFor(TrackingType.VIDEO_EVENT, "start"))
    }

    // ─────────────────────────────────────────────
    // 4. LIST<CONTENT> HELPERS
    // ─────────────────────────────────────────────

    private val adContents = listOf(
        Content("headline", JsonPrimitive("Book a ride"), ContentType.TEXT),
        Content("image", JsonPrimitive("https://cdn.example.com/img.jpg"), ContentType.IMAGE),
        Content("discount", JsonPrimitive(20), ContentType.INTEGER)
    )

    @Test
    fun `gap 4 - getContent returns matching content by key`() {
        val result = adContents.getContent("headline")
        assertEquals("headline", result?.key)
        assertEquals(ContentType.TEXT, result?.type)
    }

    @Test
    fun `gap 4 - getContent returns null for missing key without throwing`() {
        assertNull(adContents.getContent("nonexistent"))
    }

    @Test
    fun `gap 4 - hasContents returns true for non-empty list`() {
        assertTrue(adContents.hasContents())
    }

    @Test
    fun `gap 4 - hasContents returns false for empty list`() {
        assertFalse(emptyList<Content>().hasContents())
    }

    @Test
    fun `gap 4 - isType returns true for matching type and false for mismatch`() {
        assertTrue(adContents.isType("image", ContentType.IMAGE))
        assertFalse(adContents.isType("headline", ContentType.IMAGE))
        assertFalse(adContents.isType("missing", ContentType.TEXT))
    }

    // ─────────────────────────────────────────────
    // 5. BUILDER CLEAR METHODS — JOURNEY LIFECYCLE
    // A persistent builder resets targeting between journey segments.
    // ─────────────────────────────────────────────

    @Test
    fun `gap 5 - mobility journey lifecycle - destination cleared at trip end`() {
        val builder = DecisionRequestBuilder().addPlacement("home")

        // Trip starts — add destination
        builder.addDestinationTarget(48.8566, 2.3522, 0.9) // Paris
        val duringTrip = builder.build()
        assertNotNull(duringTrip.targeting?.destination)

        // Trip ends — clear destination only
        builder.clearDestinationTargeting()
        val afterTrip = builder.build()
        assertNull("Destination must be null after trip ends", afterTrip.targeting?.destination)
    }

    @Test
    fun `gap 5 - clearTargeting wipes all targeting dimensions preserving placements and user`() {
        val builder = DecisionRequestBuilder()
            .addPlacement("home")
            .setUserId("user123")
            .addGeoTarget(2643743)
            .addLocationTarget(51.5, -0.1)
            .addDestinationTarget(48.8, 2.3, 0.8)
            .addCustomTarget("ride_type", "premium")

        builder.clearTargeting()
        val request = builder.build()

        assertNull("All targeting must be null after clearTargeting", request.targeting)
        assertNotNull("User must survive clearTargeting", request.user)
        assertEquals("Placements must survive clearTargeting", 1, request.placements.size)
    }

    @Test
    fun `gap 5 - clearAll resets builder to initial state`() {
        val builder = DecisionRequestBuilder()
            .addPlacement("home")
            .setUserId("user123")
            .addGeoTarget(2643743)

        builder.clearAll()
        builder.addPlacement("feed")
        val request = builder.build()

        assertEquals("Only the new placement must remain", 1, request.placements.size)
        assertEquals("feed", request.placements[0].key)
        assertNull("Targeting must be null after clearAll", request.targeting)
        assertNull("User must be null after clearAll", request.user)
    }

    @Test
    fun `gap 5 - individual clear methods do not affect sibling dimensions`() {
        val builder = DecisionRequestBuilder()
            .addPlacement("p1")
            .addGeoTarget(123)
            .addLocationTarget(1.0, 2.0)
            .addCustomTarget("key", "value")

        builder.clearGeoTargeting()
        val request = builder.build()

        assertNull("Geo must be cleared", request.targeting?.geo)
        assertNotNull("Location must survive geo clear", request.targeting?.location)
        assertNotNull("Custom must survive geo clear", request.targeting?.custom)
    }

    // ─────────────────────────────────────────────
    // 6. TARGETING DEDUPLICATION
    // ─────────────────────────────────────────────

    @Test
    fun `gap 6 - duplicate location coordinates are silently deduplicated`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addLocationTarget(51.5074, -0.1278)
            .addLocationTarget(51.5074, -0.1278) // exact duplicate
            .addLocationTarget(48.8566, 2.3522)  // different — kept
            .build()

        assertEquals("Duplicate location must be removed", 2, request.targeting?.location?.size)
    }

    @Test
    fun `gap 6 - setLocationTargets deduplicates provided list`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .setLocationTargets(
                listOf(
                    LocationTargetingInfo(1.0, 2.0),
                    LocationTargetingInfo(1.0, 2.0), // duplicate
                    LocationTargetingInfo(3.0, 4.0)
                )
            )
            .build()

        assertEquals("Duplicates in list must be removed by set", 2, request.targeting?.location?.size)
    }

    @Test
    fun `gap 6 - duplicate destination triples are deduplicated`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addDestinationTarget(51.5, -0.1, 0.8)
            .addDestinationTarget(51.5, -0.1, 0.8) // exact duplicate
            .build()

        assertEquals("Duplicate destination triple must be removed", 1, request.targeting?.destination?.size)
    }

    @Test
    fun `gap 6 - different minConfidence for same coordinates are kept as separate entries`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addDestinationTarget(51.5, -0.1, 0.7)
            .addDestinationTarget(51.5, -0.1, 0.9) // different confidence — not a duplicate
            .build()

        assertEquals("Different confidence entries must both be kept", 2, request.targeting?.destination?.size)
    }

    // ─────────────────────────────────────────────
    // 7. MINCONFIDENCE VALIDATION
    // ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `gap 7 - minConfidence above 1 0 throws IllegalArgumentException`() {
        DecisionRequestBuilder().addPlacement("p1").addDestinationTarget(51.5, -0.1, 1.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gap 7 - minConfidence below 0 0 throws IllegalArgumentException`() {
        DecisionRequestBuilder().addPlacement("p1").addDestinationTarget(51.5, -0.1, -0.001)
    }

    @Test
    fun `gap 7 - minConfidence at exact boundaries 0 and 1 are valid`() {
        val request = DecisionRequestBuilder()
            .addPlacement("p1")
            .addDestinationTarget(51.5, -0.1, 0.0)
            .addDestinationTarget(48.8, 2.3, 1.0)
            .build()

        assertEquals("Both boundary values must be accepted", 2, request.targeting?.destination?.size)
    }

    @Test
    fun `gap 7 - setDestinationTargets validates all entries`() {
        try {
            DecisionRequestBuilder()
                .addPlacement("p1")
                .setDestinationTargets(listOf(DestinationTargetingInfo(51.5, -0.1, 1.5)))
            assertTrue("Expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("minConfidence") == true)
        }
    }

    // ─────────────────────────────────────────────
    // 8. FIRE-AND-FORGET TRACKING
    // Fires without collection; errors never reach the caller.
    // ─────────────────────────────────────────────

    @Test
    fun `gap 8 - fireImpression fires HTTP request without caller collection`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        server.enqueue(MockResponse().setResponseCode(200))
        val tracking = TrackingInfo(
            impressions = listOf(TrackingDetail("default", "${baseUrl()}track/imp"))
        )

        // No .collect {} or .first() — call and return
        Admoai.getInstance().fireImpression(tracking)

        val req = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("HTTP request must fire even without collection", req)
        assertEquals("/track/imp", req!!.path)
    }

    @Test
    fun `gap 8 - fireClick fires HTTP request without caller collection`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        server.enqueue(MockResponse().setResponseCode(200))
        val tracking = TrackingInfo(
            clicks = listOf(TrackingDetail("default", "${baseUrl()}track/click"))
        )

        Admoai.getInstance().fireClick(tracking)

        val req = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(req)
        assertEquals("/track/click", req!!.path)
    }

    @Test
    fun `gap 8 - tracking server 500 error does not throw to caller`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        server.enqueue(MockResponse().setResponseCode(500))
        val tracking = TrackingInfo(
            impressions = listOf(TrackingDetail("default", "${baseUrl()}track/fail"))
        )

        // Must not throw — fire and forget
        Admoai.getInstance().fireImpression(tracking)
        server.takeRequest(3, TimeUnit.SECONDS) // consume request, don't assert result
    }

    @Test
    fun `gap 8 - non-existent tracking key silently fires nothing`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        val tracking = TrackingInfo(impressions = listOf(TrackingDetail("default", "${baseUrl()}track")))

        // Key doesn't match — no request fired
        Admoai.getInstance().fireImpression(tracking, "nonexistent_key")

        val req = server.takeRequest(500, TimeUnit.MILLISECONDS)
        assertNull("No HTTP request must be made for missing key", req)
    }

    // ─────────────────────────────────────────────
    // 9. PUBLIC fireTracking(url)
    // Raw URL fired directly — for VAST, third-party events.
    // ─────────────────────────────────────────────

    @Test
    fun `gap 9 - fireTracking with raw url fires the url directly`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        server.enqueue(MockResponse().setResponseCode(200))
        val rawUrl = "${baseUrl()}vast/quartile/firstQuartile"

        Admoai.getInstance().fireTracking(rawUrl)

        val req = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("Raw URL must be fired", req)
        assertEquals("/vast/quartile/firstQuartile", req!!.path)
    }

    @Test
    fun `gap 9 - fireTracking carries SDK headers`() {
        Admoai.initialize(
            SDKConfig(
                baseUrl = baseUrl(),
                defaultLanguage = "de",
                networkClientEngine = CIO.create()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))

        Admoai.getInstance().fireTracking("${baseUrl()}track/raw")

        val req = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(req)
        assertNotNull("User-Agent must be on raw tracking fire", req!!.getHeader("User-Agent"))
        assertEquals("Accept-Language must be on raw tracking fire", "de", req.getHeader("Accept-Language"))
    }

    @Test
    fun `gap 9 - fireTracking with null apiService silently does nothing`() {
        Admoai.initialize(SDKConfig(baseUrl = baseUrl(), networkClientEngine = CIO.create()))
        Admoai.getInstance().apiService = null

        // Must not throw
        Admoai.getInstance().fireTracking("${baseUrl()}track/raw")
    }

    // ─────────────────────────────────────────────
    // 10. DEVICECONFIG SYSTEMDEFAULT COMPLETENESS
    // language field always populated (Locale is JVM-safe).
    // manufacturer is null in JVM stubs but non-null on device.
    // ─────────────────────────────────────────────

    @Test
    fun `gap 10 - DeviceConfig systemDefault always provides language from Locale`() {
        val config = DeviceConfig.systemDefault()
        assertNotNull("language from Locale.getDefault() must always be present", config.language)
        assertTrue("language must be a non-empty string", config.language!!.isNotEmpty())
    }

    @Test
    fun `gap 10 - DeviceConfig supports all 7 fields when set manually`() {
        val config = DeviceConfig(
            model = "Pixel 9",
            osName = "Android",
            osVersion = "15",
            timezone = "Europe/Madrid",
            deviceId = "abc-device-id",
            manufacturer = "Google",
            language = "es-ES"
        )
        assertEquals("Google", config.manufacturer)
        assertEquals("es-ES", config.language)
    }

    // ─────────────────────────────────────────────
    // CROSS-CUTTING: Full happy-path scenario
    // Exercises all features in one realistic chain.
    // ─────────────────────────────────────────────

    @Test
    fun `full happy path - build request use helpers parse response fire tracking`() = runTest {
        // Given: SDK configured with all options
        Admoai.initialize(
            SDKConfig(
                baseUrl = baseUrl(),
                apiVersion = "2025-11-01",
                defaultLanguage = "en",
                networkClientEngine = CIO.create()
            )
        )
        val responseJson = """
        {
          "success": true,
          "data": [{
            "placement": "home",
            "creatives": [{
              "contents": [
                {"key":"headline","value":"Try Premium","type":"text"},
                {"key":"image","value":"https://cdn.example.com/img.jpg","type":"image"}
              ],
              "advertiser": {"name":"RideCo"},
              "template": {"key":"native_card"},
              "tracking": {
                "impressions": [{"key":"default","url":"${baseUrl()}track/imp"}],
                "clicks": [{"key":"default","url":"${baseUrl()}track/click"}]
              },
              "metadata": {"adId":"ad_01ABC","creativeId":"cr_01DEF","placementId":"pl_01GHI","templateId":"tmpl_01JKL","priority":"standard"}
            }]
          }]
        }
        """.trimIndent()

        server.enqueue(okJson(responseJson))
        server.enqueue(MockResponse().setResponseCode(200)) // for impression tracking

        // Build request using builder
        val request = DecisionRequestBuilder()
            .addPlacement("home")
            .setUserId("user-42")
            .setUserTimezone("Europe/London")
            .addGeoTarget(2643743)
            .addDestinationTarget(51.5, -0.1, 0.85)
            .addCustomTarget("ride_type", "economy")
            .build()

        val response = Admoai.getInstance().requestAds(request).first()

        // Verify decision request
        val decisionReq = server.takeRequest()
        assertTrue("User-Agent present", decisionReq.getHeader("User-Agent")!!.startsWith("AdMoaiSDK/"))
        assertEquals("en", decisionReq.getHeader("Accept-Language"))
        assertEquals("2025-11-01", decisionReq.getHeader("X-Decision-Version"))

        // Parse and use helpers
        val creative = response.data!!.first().creatives!!.first()
        assertNull("Advertiser.id must be null when omitted", creative.advertiser.id)

        val headline = creative.contents.getContent("headline")
        assertNotNull("getContent must find headline", headline)
        assertTrue("contents are non-empty", creative.contents.hasContents())
        assertTrue("image field must be IMAGE type", creative.contents.isType("image", ContentType.IMAGE))

        val impUrl = creative.tracking.getImpressionUrl()
        assertNotNull("getImpressionUrl must return url", impUrl)
        assertTrue("hasTrackingFor IMPRESSION works", creative.tracking.hasTrackingFor(TrackingType.IMPRESSION, "default"))

        // Fire tracking — fire-and-forget, no collection needed
        Admoai.getInstance().fireImpression(creative.tracking)
        val trackReq = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("Impression tracking must fire", trackReq)
        assertTrue("Tracking User-Agent present", trackReq!!.getHeader("User-Agent")!!.startsWith("AdMoaiSDK/"))
    }
}
