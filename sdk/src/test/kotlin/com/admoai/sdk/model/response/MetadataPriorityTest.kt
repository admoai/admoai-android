package com.admoai.sdk.model.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [MetadataPriority] serialization safety.
 *
 * Key invariant: any unrecognised server value must decode to [MetadataPriority.UNKNOWN]
 * rather than throwing [kotlinx.serialization.SerializationException].
 */
class MetadataPriorityTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Known values round-trip ───────────────────────────────────────────────

    @Test
    fun `deserializes sponsorship correctly`() {
        val metadata = decodeMetadata("sponsorship")
        assertEquals(MetadataPriority.SPONSORSHIP, metadata.priority)
    }

    @Test
    fun `deserializes standard correctly`() {
        val metadata = decodeMetadata("standard")
        assertEquals(MetadataPriority.STANDARD, metadata.priority)
    }

    @Test
    fun `deserializes house correctly`() {
        val metadata = decodeMetadata("house")
        assertEquals(MetadataPriority.HOUSE, metadata.priority)
    }

    @Test
    fun `serializes sponsorship back to sponsorship`() {
        val encoded = json.encodeToString(MetadataPrioritySerializer, MetadataPriority.SPONSORSHIP)
        assertEquals("\"sponsorship\"", encoded)
    }

    @Test
    fun `serializes standard back to standard`() {
        val encoded = json.encodeToString(MetadataPrioritySerializer, MetadataPriority.STANDARD)
        assertEquals("\"standard\"", encoded)
    }

    @Test
    fun `serializes house back to house`() {
        val encoded = json.encodeToString(MetadataPrioritySerializer, MetadataPriority.HOUSE)
        assertEquals("\"house\"", encoded)
    }

    // ── Unknown / future values ───────────────────────────────────────────────

    @Test
    fun `unknown server value decodes to UNKNOWN without throwing`() {
        val metadata = decodeMetadata("premium")
        assertEquals(MetadataPriority.UNKNOWN, metadata.priority)
    }

    @Test
    fun `empty string decodes to UNKNOWN without throwing`() {
        val metadata = decodeMetadata("")
        assertEquals(MetadataPriority.UNKNOWN, metadata.priority)
    }

    @Test
    fun `future value decodes to UNKNOWN without throwing`() {
        val metadata = decodeMetadata("guaranteed")
        assertEquals(MetadataPriority.UNKNOWN, metadata.priority)
    }

    @Test
    fun `UNKNOWN serializes to the string unknown`() {
        val encoded = json.encodeToString(MetadataPrioritySerializer, MetadataPriority.UNKNOWN)
        assertEquals("\"unknown\"", encoded)
    }

    // ── Full DecisionResponse payload ─────────────────────────────────────────

    @Test
    fun `full response with unknown priority decodes without throwing`() {
        val responseJson = """
            {
                "success": true,
                "data": [{
                    "placement": "home",
                    "creatives": [{
                        "contents": [{"key": "headline", "value": "Test Ad", "type": "text"}],
                        "advertiser": {"id": "adv1", "name": "Test Brand"},
                        "tracking": {
                            "impressions": [{"key": "default", "url": "https://track.example.com/imp"}]
                        },
                        "metadata": {
                            "adId": "ad1",
                            "creativeId": "cr1",
                            "placementId": "pl1",
                            "templateId": "tpl1",
                            "priority": "premium_plus"
                        }
                    }]
                }]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(responseJson)

        val priority = response.data!!.first().creatives!!.first().metadata!!.priority
        assertEquals(
            "An unrecognised priority must not crash the SDK",
            MetadataPriority.UNKNOWN,
            priority
        )
    }

    @Test
    fun `full response with known priority still decodes correctly`() {
        val responseJson = """
            {
                "success": true,
                "data": [{
                    "placement": "home",
                    "creatives": [{
                        "contents": [],
                        "advertiser": {"name": "Brand"},
                        "tracking": {},
                        "metadata": {
                            "adId": "ad1",
                            "creativeId": "cr1",
                            "placementId": "pl1",
                            "templateId": "tpl1",
                            "priority": "sponsorship"
                        }
                    }]
                }]
            }
        """.trimIndent()

        val response = json.decodeFromString<DecisionResponse>(responseJson)

        assertEquals(
            MetadataPriority.SPONSORSHIP,
            response.data!!.first().creatives!!.first().metadata!!.priority
        )
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun decodeMetadata(priorityValue: String): CreativeMetadata {
        val metadataJson = """
            {
                "adId": "ad1",
                "creativeId": "cr1",
                "placementId": "pl1",
                "templateId": "tpl1",
                "priority": "$priorityValue"
            }
        """.trimIndent()
        return json.decodeFromString(metadataJson)
    }
}
