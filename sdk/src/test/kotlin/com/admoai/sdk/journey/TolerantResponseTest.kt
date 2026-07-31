package com.admoai.sdk.journey

import com.admoai.sdk.model.response.ContentType
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.hasCreative
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `malformed optional creative objects degrade to null and the creative is kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":{},
             "metadata":"garbage","template":123,"vast":[1,2],"journey":"nope"}]}]}"""
        val ad = decode(body).data!!.first()
        assertTrue(ad.hasCreative())
        val creative = ad.creatives.first()
        assertNull(creative.metadata)
        assertNull(creative.template)
        assertNull(creative.vast)
        assertNull(creative.journey)
    }

    @Test
    fun `metadata missing required fields degrades to null, creative kept`() {
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[],"advertiser":{},"tracking":{},"metadata":{"onlyUnknown":"x"}}]}]}"""
        val creative = decode(body).data!!.first().creatives.first()
        assertNull(creative.metadata)
    }

    @Test
    fun `malformed errors and warnings entries are dropped without throwing`() {
        val body = """{"success":false,
            "errors":[{"code":1,"message":"bad"},"garbage",123],
            "warnings":["oops",{"code":2,"message":"warn"}],
            "data":[]}"""
        val response = decode(body)
        assertEquals(1, response.errors!!.size)
        assertEquals(1, response.warnings!!.size)
    }

    @Test
    fun `structurally malformed content entries are dropped, unknown types are kept`() {
        // Scenario: a creative mixes a known type, a non-object entry, and a type newer than
        // this SDK version.
        //
        // Contract: only the structurally broken entry is dropped. An unknown `type` degrades to
        // ContentType.UNKNOWN with the wire value preserved in `rawType` — it must NOT drop the
        // entry, which is what iOS and Flutter do (both keep `type` as an open string).
        //
        // The previous version of this test asserted the opposite and claimed in a comment that
        // dropping "matches iOS/Flutter". It never did. That drop was a real data-loss bug: the
        // engine's `template_fields_valid_type` CHECK allows `dropdown`, which was missing from
        // ContentType, so Android silently discarded every dropdown field a publisher configured.
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[
               {"key":"title","value":"Hi","type":"text"},
               "garbage",
               {"key":"weird","value":"x","type":"hologram"}
            ],"advertiser":{},"tracking":{}}]}]}"""
        val creative = decode(body).data!!.first().creatives.first()

        assertEquals(2, creative.contents.size)
        assertEquals("title", creative.contents[0].key)
        assertEquals(ContentType.TEXT, creative.contents[0].type)

        val unknown = creative.contents[1]
        assertEquals("weird", unknown.key)
        assertEquals(ContentType.UNKNOWN, unknown.type)
        assertEquals("hologram", unknown.rawType)
        assertEquals("x", (unknown.value as JsonPrimitive).content)
    }

    @Test
    fun `dropdown content is decoded, not dropped`() {
        // Scenario: the engine serves a `dropdown` template field.
        //
        // Contract: `dropdown` is in the engine's template_fields_valid_type CHECK, so it is a
        // first-class type and must decode to ContentType.DROPDOWN. This is the exact field type
        // that regressed — pinned separately from the open-set case above so a future edit cannot
        // quietly demote it back to UNKNOWN.
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[{"key":"size","value":"large","type":"dropdown"}],
             "advertiser":{},"tracking":{}}]}]}"""
        val content = decode(body).data!!.first().creatives.first().contents.single()

        assertEquals(ContentType.DROPDOWN, content.type)
        assertEquals("dropdown", content.rawType)
    }

    @Test
    fun `content with an absent value is kept`() {
        // Scenario: a content entry omits `value` entirely.
        //
        // Contract: keep the entry with a JsonNull value. iOS degrades to a null AnyCodable and
        // Flutter passes null through; Android used to require the field and drop the entry, the
        // same data-loss shape as the unknown-type case.
        val body = """{"success":true,"data":[{"placement":"p","creatives":[
            {"contents":[{"key":"headline","type":"text"}],
             "advertiser":{},"tracking":{}}]}]}"""
        val content = decode(body).data!!.first().creatives.first().contents.single()

        assertEquals("headline", content.key)
        assertEquals(JsonNull, content.value)
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
