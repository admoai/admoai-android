package com.admoai.sdk

import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
import com.admoai.sdk.model.response.getContent
import com.admoai.sdk.model.response.slides
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carousel slides and standard-format metadata, beyond the shared manifest.
 *
 * Feature: A publisher's app reads a Carousel's slides and the standard format metadata
 *   As a publisher's tech team
 *   I want slides in server order and a decode that a malformed slide cannot break
 *   So that one bad slide costs one slide, never the creative or the ad
 *
 * The shared fixture scenarios ([FixtureScenariosTest]) pin the golden wire; these pin the tolerance
 * rules the golden files do not exercise. Mirrors the iOS reference suite (`SlidesDecodingTests.swift`).
 */
class SlidesDecodingTest {

    // Mirrors the production Json config (AdMoaiApiServiceImpl).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun creative(contents: String, metadata: String = "{}"): Creative {
        val body = """
            {"success": true, "data": [{"placement": "home", "creatives": [{
                "contents": $contents, "metadata": $metadata, "advertiser": {}, "tracking": {}
            }]}]}
        """
        return json.decodeFromString<DecisionResponse>(body).data!!.first().creatives.first()
    }

    private fun slide(id: String, position: Int) = """
        {"slideId": "$id", "position": $position,
         "contents": [{"key": "image", "value": "https://cdn.example/$id.png", "type": "image"}],
         "tracking": {"views": [{"key": "slide_view", "url": "https://t.example/v/$id"}],
                      "clicks": [{"key": "default", "url": "https://t.example/c/$id"}]}}
    """

    private fun slides(entries: List<String>) =
        """[{"key": "slides", "type": "slides", "value": [${entries.joinToString(",")}]}]"""

    @Test
    fun `a malformed slide is dropped and the order is kept`() {
        val result = creative(
            slides(
                listOf(
                    slide("a", 1),
                    """{"position": 2, "contents": []}""", // no slideId
                    slide("b", 2),
                    """{"slideId": "s_bad", "position": "2"}""", // mistyped position
                    """{"slideId": 7, "position": 2}""", // mistyped slideId
                    "42", // not an object
                    slide("c", 3)
                )
            )
        )
        assertEquals(listOf("a", "b", "c"), result.slides.map { it.slideId })
        assertEquals(listOf(1, 2, 3), result.slides.map { it.position })
    }

    @Test
    fun `position out of order on the wire keeps the wire order`() {
        val result = creative(slides(listOf(slide("third", 3), slide("first", 1), slide("second", 2))))
        assertEquals(listOf("third", "first", "second"), result.slides.map { it.slideId })
        assertEquals(listOf(3, 1, 2), result.slides.map { it.position })
    }

    @Test
    fun `a malformed slide content entry is dropped on its own`() {
        val entry = """
            {"slideId": "a", "position": 1, "contents": [
                {"key": "image", "value": "https://cdn.example/a.png", "type": "image"},
                "not an object",
                {"key": "headline", "value": "Hi", "type": "text"}
            ], "tracking": "nope"}
        """
        val result = creative(slides(listOf(entry)))
        assertEquals(1, result.slides.size)
        assertEquals(listOf("image", "headline"), result.slides.first().contents.map { it.key })
        assertNull("a malformed tracking block is absent, not a failure", result.slides.first().tracking)
    }

    @Test
    fun `the slides entry stays in contents`() {
        val result = creative(slides(listOf(slide("a", 1))))
        assertNotNull(result.contents.getContent("slides"))
        assertEquals(1, result.slides.size)
    }

    @Test
    fun `a non-carousel creative has no slides`() {
        val plain = creative("""[{"key": "headline", "value": "Hi", "type": "text"}]""")
        assertTrue(plain.slides.isEmpty())
        val malformed = creative("""[{"key": "slides", "type": "slides", "value": "nope"}]""")
        assertTrue("a slides entry that is not a list yields no slides and no failure", malformed.slides.isEmpty())
    }

    @Test
    fun `the standard format version accepts a number and nothing else`() {
        fun version(literal: String): Int? =
            creative("[]", """{"effectiveStandardFormatVersion": $literal}""").metadata?.effectiveStandardFormatVersion
        assertEquals(1, version("1"))
        assertEquals(1, version("1.0"))
        assertNull(version(""""1""""))
        assertNull(version("null"))
        assertNull(version("true"))
    }

    @Test
    fun `the template source is open-set and wrong types are absent`() {
        val known = creative("[]", """{"templateSource": "based_on_standard"}""")
        assertEquals("based_on_standard", known.metadata?.templateSource)
        val future = creative("[]", """{"templateSource": "from_the_future"}""")
        assertEquals("an unknown value is kept verbatim", "from_the_future", future.metadata?.templateSource)
        val mistyped = creative("[]", """{"templateSource": 3, "effectiveStandardFormatKey": false}""")
        assertNull(mistyped.metadata?.templateSource)
        assertNull(mistyped.metadata?.effectiveStandardFormatKey)
    }

    private fun golden(name: String): Creative = json.decodeFromString<DecisionResponse>(
        javaClass.classLoader!!.getResourceAsStream("fixtures/golden-wire/$name.json")!!.bufferedReader().readText()
    ).data!!.first().creatives.first()

    @Test
    fun `the asset context is read from Branded Assets and absent elsewhere`() {
        assertEquals("vehicle_marker", golden("branded_assets.minimal").metadata?.assetContext)
        assertEquals("vehicle_marker", golden("branded_assets.full").metadata?.assetContext)
        assertNull(golden("card.minimal").metadata?.assetContext)
        val future = creative("[]", """{"assetContext": "from_the_future"}""")
        assertEquals("an unknown value is kept verbatim", "from_the_future", future.metadata?.assetContext)
        assertNull("a wrong type is absent", creative("[]", """{"assetContext": 3}""").metadata?.assetContext)
    }
}
