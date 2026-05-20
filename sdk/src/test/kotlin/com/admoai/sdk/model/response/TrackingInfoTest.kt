package com.admoai.sdk.model.response

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingInfoTest {

    private val sampleTracking = TrackingInfo(
        impressions = listOf(
            TrackingDetail(key = "default", url = "https://track.admoai.com/imp/default"),
            TrackingDetail(key = "secondary", url = "https://track.admoai.com/imp/secondary")
        ),
        clicks = listOf(
            TrackingDetail(key = "default", url = "https://track.admoai.com/click/default")
        ),
        custom = listOf(
            TrackingDetail(key = "cta_tap", url = "https://track.admoai.com/custom/cta")
        ),
        videoEvents = listOf(
            TrackingDetail(key = "start", url = "https://track.admoai.com/video/start"),
            TrackingDetail(key = "complete", url = "https://track.admoai.com/video/complete")
        )
    )

    // --- getImpressionUrl ---

    @Test
    fun `given impression with default key when getImpressionUrl called then returns url`() {
        assertEquals(
            "https://track.admoai.com/imp/default",
            sampleTracking.getImpressionUrl()
        )
    }

    @Test
    fun `given impression with named key when getImpressionUrl called then returns correct url`() {
        assertEquals(
            "https://track.admoai.com/imp/secondary",
            sampleTracking.getImpressionUrl("secondary")
        )
    }

    @Test
    fun `given non-existent key when getImpressionUrl called then returns null safely`() {
        assertNull(sampleTracking.getImpressionUrl("nonexistent"))
    }

    @Test
    fun `given null impressions when getImpressionUrl called then returns null safely`() {
        val tracking = TrackingInfo(impressions = null)
        assertNull(tracking.getImpressionUrl("default"))
    }

    // --- getClickUrl ---

    @Test
    fun `given click tracking when getClickUrl called then returns url`() {
        assertEquals(
            "https://track.admoai.com/click/default",
            sampleTracking.getClickUrl()
        )
    }

    @Test
    fun `given null clicks when getClickUrl called then returns null safely`() {
        val tracking = TrackingInfo(clicks = null)
        assertNull(tracking.getClickUrl("default"))
    }

    // --- getCustomUrl ---

    @Test
    fun `given custom event when getCustomUrl called then returns url`() {
        assertEquals(
            "https://track.admoai.com/custom/cta",
            sampleTracking.getCustomUrl("cta_tap")
        )
    }

    @Test
    fun `given missing custom key when getCustomUrl called then returns null safely`() {
        assertNull(sampleTracking.getCustomUrl("missing_event"))
    }

    // --- getVideoEventUrl ---

    @Test
    fun `given video event start when getVideoEventUrl called then returns url`() {
        assertEquals(
            "https://track.admoai.com/video/start",
            sampleTracking.getVideoEventUrl("start")
        )
    }

    @Test
    fun `given video event complete when getVideoEventUrl called then returns url`() {
        assertEquals(
            "https://track.admoai.com/video/complete",
            sampleTracking.getVideoEventUrl("complete")
        )
    }

    @Test
    fun `given null video events when getVideoEventUrl called then returns null safely`() {
        val tracking = TrackingInfo(videoEvents = null)
        assertNull(tracking.getVideoEventUrl("start"))
    }

    // --- getTrackingUrl ---

    @Test
    fun `given TrackingType IMPRESSION when getTrackingUrl called then returns impression url`() {
        assertEquals(
            "https://track.admoai.com/imp/default",
            sampleTracking.getTrackingUrl(TrackingType.IMPRESSION, "default")
        )
    }

    @Test
    fun `given TrackingType CLICK when getTrackingUrl called then returns click url`() {
        assertEquals(
            "https://track.admoai.com/click/default",
            sampleTracking.getTrackingUrl(TrackingType.CLICK, "default")
        )
    }

    @Test
    fun `given TrackingType VIDEO_EVENT when getTrackingUrl called then returns video url`() {
        assertEquals(
            "https://track.admoai.com/video/start",
            sampleTracking.getTrackingUrl(TrackingType.VIDEO_EVENT, "start")
        )
    }

    // --- hasTrackingFor ---

    @Test
    fun `given existing impression key when hasTrackingFor called then returns true`() {
        assertTrue(sampleTracking.hasTrackingFor(TrackingType.IMPRESSION, "default"))
    }

    @Test
    fun `given missing impression key when hasTrackingFor called then returns false`() {
        assertFalse(sampleTracking.hasTrackingFor(TrackingType.IMPRESSION, "missing"))
    }

    @Test
    fun `given existing click key when hasTrackingFor called then returns true`() {
        assertTrue(sampleTracking.hasTrackingFor(TrackingType.CLICK, "default"))
    }

    @Test
    fun `given null impressions when hasTrackingFor called then returns false`() {
        val tracking = TrackingInfo(impressions = null)
        assertFalse(tracking.hasTrackingFor(TrackingType.IMPRESSION, "default"))
    }
}
