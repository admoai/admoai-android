package com.admoai.sdk.journey

import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.hasCreative
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-response Tolerant Reader (PR B). The SDK must never throw on a well-formed HTTP body:
 * unknown fields ignored, unknown enums -> null, malformed list entries dropped, required
 * sub-objects degraded to safe defaults, and renderable creatives preserved.
 */
class TolerantResponseTest {

    // Mirrors the production Json config.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun decode(body: String) = json.decodeFromString<DecisionResponse>(body)

    @Test
    fun `future-version response never throws and used fields still read`() {
        val body = """
            {
              "success": true,
              "unknownTopLevel": {"x": 1},
              "data": [
                {
                  "placement": "p1",
                  "futureField": [1,2,3],
                  "creatives": [
                    {
                      "contents": [],
                      "advertiser": {"name": "Acme", "surpriseField": true},
                      "tracking": {"impressions": [{"key":"default","url":"https://t/imp"}]},
                      "journey": {"dealId":"d","optStatus":"paused","newKey":"v"},
                      "metadata": {"adId":"a","creativeId":"c","placementId":"p","templateId":"t",
                                   "priority":"ultra","impId":"i"}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val response = decode(body)
        val creative = response.data!!.first().creatives.first()
        assertTrue(response.success)
        assertEquals("Acme", creative.advertiser.name)
        assertEquals("https://t/imp", creative.tracking.impressions!!.first().url)
        // unknown enum values degrade rather than throw
        assertEquals(null, creative.journey?.optStatus)
    }

    @Test
    fun `success defaults to false when absent`() {
        assertFalse(decode("""{"data":[]}""").success)
    }

    @Test
    fun `malformed advertiser degrades to default and the creative is kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":"not-an-object","tracking":{}}]}]}"""
        val ad = decode(body).data!!.first()
        assertTrue(ad.hasCreative())
        // degraded to an empty Advertiser, not dropped
        assertEquals(null, ad.creatives.first().advertiser.name)
    }

    @Test
    fun `malformed tracking degrades to default and the creative is kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":123}]}]}"""
        val ad = decode(body).data!!.first()
        assertTrue(ad.hasCreative())
        assertEquals(null, ad.creatives.first().tracking.impressions)
    }

    @Test
    fun `malformed and unknown-type content entries are dropped, valid ones kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[
               {"key":"title","value":"Hi","type":"text"},
               "garbage",
               {"key":"weird","value":"x","type":"hologram"}
            ],"advertiser":{},"tracking":{}}]}]}"""
        val creative = decode(body).data!!.first().creatives.first()
        assertEquals(1, creative.contents.size)
        assertEquals("title", creative.contents.first().key)
    }

    @Test
    fun `malformed tracking detail entries are dropped`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":{"impressions":[
               {"key":"default","url":"https://t/imp"},
               {"key":"missing-url"}
            ]}}]}]}"""
        val impressions = decode(body).data!!.first().creatives.first().tracking.impressions!!
        assertEquals(1, impressions.size)
    }

    @Test
    fun `top-level data drops a non-object entry`() {
        val body = """{"success":true,"data":[
            {"placement":"p","creatives":[]},
            "garbage"
        ]}"""
        assertEquals(1, decode(body).data!!.size)
    }

    @Test
    fun `object-shaped verificationParameters is preserved as JSON text and the resource kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":{},"verificationScriptResources":[
               {"vendorKey":"ias","scriptUrl":"https://v/s.js","verificationParameters":{"k":"v"}}
            ]}]}]}"""
        val resources = decode(body).data!!.first().creatives.first().verificationScriptResources!!
        assertEquals(1, resources.size)
        assertTrue(resources.first().verificationParameters!!.contains("\"k\":\"v\""))
    }

    @Test
    fun `OM resource missing scriptUrl is dropped, usable one kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":{},"verificationScriptResources":[
               {"vendorKey":"ias","scriptUrl":"https://v/s.js"},
               {"vendorKey":"broken"}
            ]}]}]}"""
        val resources = decode(body).data!!.first().creatives.first().verificationScriptResources!!
        assertEquals(1, resources.size)
        assertEquals("ias", resources.first().vendorKey)
    }
}
