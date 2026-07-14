package com.admoai.sdk.journey

import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.hasCreative
import com.admoai.sdk.model.response.isNoAd
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BDD coverage for no-ad & single-brand takeover safety.
 *
 * The engine emits `"creatives": null` for ordinary no-fill and `[]` for takeover-protected
 * no-ad; both must decode to a safe empty list (no crash) and read as no-ad.
 */
class JourneyNoAdTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun firstAdData(body: String) =
        json.decodeFromString<DecisionResponse>(body).data!!.first()

    @Test
    fun `ordinary no-fill (creatives null) decodes to empty and reads as no-ad`() {
        val ad = firstAdData("""{"success":true,"data":[{"placement":"p1","creatives":null}]}""")
        assertTrue(ad.creatives.isEmpty())
        assertTrue(ad.isNoAd())
        assertFalse(ad.hasCreative())
    }

    @Test
    fun `takeover-protected empty (creatives empty array) reads as no-ad`() {
        val ad = firstAdData("""{"success":true,"data":[{"placement":"p1","creatives":[]}]}""")
        assertTrue(ad.isNoAd())
    }

    @Test
    fun `absent creatives reads as no-ad`() {
        val ad = firstAdData("""{"success":true,"data":[{"placement":"p1"}]}""")
        assertTrue(ad.isNoAd())
    }

    @Test
    fun `a real creative reads as has-creative`() {
        val ad = firstAdData(
            """{"success":true,"data":[{"placement":"p1","creatives":[
               {"contents":[],"advertiser":{},"tracking":{}}]}]}"""
        )
        assertTrue(ad.hasCreative())
        assertFalse(ad.isNoAd())
    }

    @Test
    fun `malformed creative entries are dropped, valid ones kept`() {
        val ad = firstAdData(
            """{"success":true,"data":[{"placement":"p1","creatives":[
               {"contents":[],"advertiser":{},"tracking":{}},"garbage"]}]}"""
        )
        assertEquals(1, ad.creatives.size)
    }
}
