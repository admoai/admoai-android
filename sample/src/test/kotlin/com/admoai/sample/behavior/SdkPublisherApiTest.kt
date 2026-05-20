package com.admoai.sample.behavior

import com.admoai.sdk.Admoai
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consumer-perspective BDD tests for the AdMoai Android SDK.
 *
 * These tests exercise the SDK's public API surface exactly as a publisher would
 * integrate it — using only the documented public API, no internal access.
 * They catch API ergonomics regressions that internal SDK tests might miss.
 *
 * All tests are pure JVM — no Android framework, no network calls.
 */
class SdkPublisherApiTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ─────────────────────────────────────────────
    // REQUEST BUILDING — FLUENT BUILDER API
    // ─────────────────────────────────────────────

    @Test
    fun `publisher can build a complete decision request using the fluent builder`() {
        // Given: A publisher integrating the SDK for the first time
        val request = DecisionRequestBuilder()
            .addPlacement("home_banner", count = 1)
            .addPlacement("search_banner")
            .setUserId("user-42")
            .setUserIp("203.0.113.10")
            .setUserTimezone("America/Sao_Paulo")
            .setUserConsent(true)
            .addGeoTarget(3451190) // São Paulo GeoName ID
            .addLocationTarget(-23.5505, -46.6333)
            .addCustomTarget("ride_type", "economy")
            .addCustomTarget("surge_active", false)
            .build()

        // Then: the request reflects all the configured data
        assertEquals(2, request.placements.size)
        assertEquals("home_banner", request.placements[0].key)
        assertEquals(1, request.placements[0].count)
        assertEquals("user-42", request.user?.id)
        assertEquals("America/Sao_Paulo", request.user?.timezone)
        assertEquals(true, request.user?.consent?.gdpr)
        assertEquals(listOf(3451190), request.targeting?.geo)
        assertEquals(1, request.targeting?.location?.size)
        val lat = request.targeting?.location?.get(0)?.latitude
        assertNotNull(lat)
        assertEquals(-23.5505, lat!!, 0.0001)
        assertEquals(2, request.targeting?.custom?.size)
    }

    @Test
    fun `publisher can build a video placement request`() {
        // Given: A publisher integrating video ads
        val request = DecisionRequestBuilder()
            .addPlacement(
                key = "pre_trip_video",
                format = com.admoai.sdk.model.request.PlacementFormat.VIDEO
            )
            .build()

        assertEquals(com.admoai.sdk.model.request.PlacementFormat.VIDEO, request.placements[0].format)
    }

    @Test
    fun `publisher destination targeting for ride-hailing use case`() {
        // Given: A ride-hailing app where destination matters for ad relevance
        val request = DecisionRequestBuilder()
            .addPlacement("ride_banner")
            .addDestinationTarget(
                latitude = -22.9068,    // Rio de Janeiro
                longitude = -43.1729,
                minConfidence = 0.85
            )
            .build()

        val dest = request.targeting?.destination?.first()
        assertNotNull(dest)
        assertEquals(-22.9068, dest!!.latitude, 0.0001)
        assertEquals(0.85, dest.minConfidence, 0.001)
    }

    // ─────────────────────────────────────────────
    // BUILDER CLEAR METHODS — JOURNEY LIFECYCLE
    // ─────────────────────────────────────────────

    @Test
    fun `publisher clears destination targeting when trip ends`() {
        // Given: A persistent builder in a ViewModel used across trip lifecycle
        val builder = DecisionRequestBuilder().addPlacement("ride_banner")

        // Trip starts — add destination
        builder.addDestinationTarget(-22.9068, -43.1729, 0.9)
        assertNotNull("Destination must be set during trip", builder.build().targeting?.destination)

        // Trip ends — clear destination
        builder.clearDestinationTargeting()
        assertNull("Destination must be gone after trip ends", builder.build().targeting?.destination)
    }

    @Test
    fun `publisher resets full targeting between sessions without losing placement config`() {
        // Given: A builder configured with placements and targeting
        val builder = DecisionRequestBuilder()
            .addPlacement("home")
            .setUserId("user-99")
            .addGeoTarget(3451190)
            .addCustomTarget("tier", "premium")

        // When: User session ends — clear all targeting but keep user identity
        builder.clearTargeting()

        val request = builder.build()
        assertNull("All targeting cleared", request.targeting)
        assertNotNull("User preserved across clearTargeting", request.user)
        assertEquals(1, request.placements.size)
    }

    @Test
    fun `publisher uses clearAll to start fresh for a new session`() {
        // Given: A builder with accumulated state
        val builder = DecisionRequestBuilder()
            .addPlacement("home")
            .setUserId("user-99")
            .addGeoTarget(3451190)

        // When: completely starting over
        builder.clearAll()
        builder.addPlacement("onboarding")

        val request = builder.build()
        assertEquals(1, request.placements.size)
        assertEquals("onboarding", request.placements[0].key)
        assertNull(request.targeting)
        assertNull(request.user)
    }

    // ─────────────────────────────────────────────
    // TARGETING SAFETY — DEDUP + VALIDATION
    // ─────────────────────────────────────────────

    @Test
    fun `publisher calling addLocationTarget twice with same coords sends one entry`() {
        // Given: GPS updates firing rapidly — same location sent multiple times
        val request = DecisionRequestBuilder()
            .addPlacement("map_banner")
            .addLocationTarget(-23.5505, -46.6333) // first GPS update
            .addLocationTarget(-23.5505, -46.6333) // duplicate GPS update
            .build()

        assertEquals("Duplicate GPS coords must not duplicate targeting", 1, request.targeting?.location?.size)
    }

    @Test
    fun `publisher receives clear error when minConfidence is out of range`() {
        // Given: A publisher accidentally passing a percentage (85) instead of ratio (0.85)
        var exceptionMessage: String? = null
        try {
            DecisionRequestBuilder()
                .addPlacement("p1")
                .addDestinationTarget(51.5, -0.1, 85.0) // forgot to divide by 100
        } catch (e: IllegalArgumentException) {
            exceptionMessage = e.message
        }

        assertNotNull("Must throw IllegalArgumentException for out-of-range confidence", exceptionMessage)
        assertTrue("Error message must mention minConfidence", exceptionMessage!!.contains("minConfidence"))
    }

    // ─────────────────────────────────────────────
    // RESPONSE MODEL — ACCESSING CONTENT FIELDS
    // ─────────────────────────────────────────────

    private val sampleContents = listOf(
        Content("headline", JsonPrimitive("Book a ride"), ContentType.TEXT),
        Content("sub_headline", JsonPrimitive("Comfort Class"), ContentType.TEXT),
        Content("image", JsonPrimitive("https://cdn.example.com/ad.jpg"), ContentType.IMAGE),
        Content("cta_label", JsonPrimitive("Book Now"), ContentType.TEXT),
        Content("discount_pct", JsonPrimitive(20), ContentType.INTEGER)
    )

    @Test
    fun `publisher accesses content fields by key using getContent`() {
        val headline = sampleContents.getContent("headline")
        assertEquals("Book a ride", (headline?.value as? JsonPrimitive)?.content)
    }

    @Test
    fun `publisher getContent returns null for non-existent key without throwing`() {
        // This is the crash-prevention guarantee — getContent must never throw
        val missing = sampleContents.getContent("nonexistent_field")
        assertNull("Missing content field must return null, not crash", missing)
    }

    @Test
    fun `publisher checks content field type before rendering`() {
        assertTrue("image must be IMAGE type", sampleContents.isType("image", ContentType.IMAGE))
        assertFalse("headline is not IMAGE type", sampleContents.isType("headline", ContentType.IMAGE))
        assertFalse("missing field returns false from isType", sampleContents.isType("missing", ContentType.TEXT))
    }

    @Test
    fun `publisher checks if creative has any content before rendering`() {
        assertTrue("Non-empty contents list", sampleContents.hasContents())
        assertFalse("Empty contents list", emptyList<Content>().hasContents())
    }

    // ─────────────────────────────────────────────
    // RESPONSE MODEL — TRACKING URL HELPERS
    // ─────────────────────────────────────────────

    private val sampleTracking = TrackingInfo(
        impressions = listOf(TrackingDetail("default", "https://t.admoai.com/imp")),
        clicks = listOf(TrackingDetail("default", "https://t.admoai.com/click")),
        custom = listOf(TrackingDetail("cta_tap", "https://t.admoai.com/cta")),
        videoEvents = listOf(
            TrackingDetail("start", "https://t.admoai.com/v/start"),
            TrackingDetail("firstQuartile", "https://t.admoai.com/v/q1")
        )
    )

    @Test
    fun `publisher accesses impression URL without raw list traversal`() {
        val url = sampleTracking.getImpressionUrl()
        assertEquals("https://t.admoai.com/imp", url)
    }

    @Test
    fun `publisher gets null for missing tracking key without crashing`() {
        // This is the bug Flutter PR #28 fixed — unsafe firstWhere crash
        val url = sampleTracking.getImpressionUrl("nonexistent_key")
        assertNull("Missing key must return null, not crash", url)
    }

    @Test
    fun `publisher accesses tracking URLs for all event types`() {
        assertEquals("https://t.admoai.com/click", sampleTracking.getClickUrl())
        assertEquals("https://t.admoai.com/cta", sampleTracking.getCustomUrl("cta_tap"))
        assertEquals("https://t.admoai.com/v/start", sampleTracking.getVideoEventUrl("start"))
        assertEquals("https://t.admoai.com/v/q1", sampleTracking.getVideoEventUrl("firstQuartile"))
    }

    @Test
    fun `publisher uses getTrackingUrl with TrackingType for generic access`() {
        val url = sampleTracking.getTrackingUrl(TrackingType.CLICK, "default")
        assertEquals("https://t.admoai.com/click", url)
    }

    @Test
    fun `publisher checks tracking existence before firing`() {
        assertTrue(sampleTracking.hasTrackingFor(TrackingType.IMPRESSION, "default"))
        assertFalse(sampleTracking.hasTrackingFor(TrackingType.IMPRESSION, "secondary"))
        assertTrue(sampleTracking.hasTrackingFor(TrackingType.VIDEO_EVENT, "firstQuartile"))
    }

    @Test
    fun `publisher getImpressionUrl on null impressions list returns null safely`() {
        val emptyTracking = TrackingInfo(impressions = null)
        assertNull(emptyTracking.getImpressionUrl())
    }

    // ─────────────────────────────────────────────
    // RESPONSE MODEL — ADVERTISER ID NULLABILITY
    // ─────────────────────────────────────────────

    @Test
    fun `publisher receives null advertiser id when server omits the field`() {
        // Given: API response without advertiser id (common for house ads)
        val advertiser = json.decodeFromString<Advertiser>("""{"name":"House Ad"}""")

        // Then: publisher code checking advertiser.id != null works correctly
        assertNull("Advertiser id must be null when server omits it", advertiser.id)
    }

    @Test
    fun `publisher receives advertiser id when server provides it`() {
        val advertiser = json.decodeFromString<Advertiser>("""{"id":"adv_01ABC","name":"Acme Corp"}""")
        assertEquals("adv_01ABC", advertiser.id)
        assertEquals("Acme Corp", advertiser.name)
    }

    // ─────────────────────────────────────────────
    // FIRE-AND-FORGET TRACKING — PUBLIC CONTRACT
    // ─────────────────────────────────────────────

    @Test
    fun `publisher can call fireImpression without collecting any result`() {
        // Given: SDK initialized — fire methods return Unit, no collection needed
        Admoai.initialize(SDKConfig(baseUrl = "https://engine.admoai.com"))

        // When: publisher calls fireImpression the way they would in production
        // (no .collect{} or .first() — just call and move on)
        Admoai.getInstance().fireImpression(sampleTracking)
        // If this compiles and doesn't throw — the fire-and-forget contract is met
    }

    @Test
    fun `publisher can fire a raw tracking url for VAST integration`() {
        // Given: publisher obtained a VAST tracking pixel from the XML
        Admoai.initialize(SDKConfig(baseUrl = "https://engine.admoai.com"))
        val vastTrackingPixel = "https://track.admoai.com/vast/impression?cid=abc123"

        // When: publisher fires it directly (no TrackingInfo object needed)
        Admoai.getInstance().fireTracking(vastTrackingPixel)
        // Compiles and does not throw — contract met
    }

    @Test
    fun `publisher fire methods silently do nothing when key is missing`() {
        // Given: a tracking info that has no "secondary" impression key
        Admoai.initialize(SDKConfig(baseUrl = "https://engine.admoai.com"))
        val tracking = TrackingInfo(
            impressions = listOf(TrackingDetail("default", "https://t.admoai.com/imp"))
        )

        // When: publisher fires with a non-existent key (e.g. typo in production code)
        Admoai.getInstance().fireImpression(tracking, "secondary")
        // Must not crash — silent no-op is the correct behavior
    }
}
