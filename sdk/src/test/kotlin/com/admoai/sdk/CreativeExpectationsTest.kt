package com.admoai.sdk

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Feature: The live scenario runner explains itself and refuses malformed manifests
 *   As the SDK team reading a red cross-SDK run
 *   I want failures to name the beacon or step that is wrong
 *   So that a typo in a manifest never passes, crashes with an NPE, or reads as an opaque URL
 */
class CreativeExpectationsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private val creative: Creative = json.decodeFromString(
        """
        {
          "contents": [],
          "advertiser": {"name": "Acme"},
          "tracking": {
            "clicks": [{"key": "default", "url": "https://t/click"}],
            "custom": [{"key": "expand", "url": "https://t/custom/expand"}]
          }
        }
        """
    )

    private fun refusal(step: String): String = try {
        parseSlideStep("F99", obj(step))
        fail("expected the step to be refused: $step")
        ""
    } catch (e: AssertionError) {
        e.message.orEmpty()
    }

    @Test
    fun `a custom beacon resolves, is described and is expected under the same name`() {
        val beacon = obj("""{"custom": "expand"}""")
        val url = resolveBeacon(beacon, creative)
        assertEquals("https://t/custom/expand", url)
        assertEquals("{custom expand}", describeBeacon(url!!, creative))
        assertEquals("{custom expand}", describeExpected(beacon))
    }

    @Test
    fun `a dispatched custom beacon that was expected matches, and a missing one names the key`() {
        checkDispatchedBeacons(
            "F99",
            json.parseToJsonElement("""[{"custom": "expand"}]"""),
            creative,
            listOf("https://t/custom/expand")
        )
        val message = try {
            checkDispatchedBeacons("F99", json.parseToJsonElement("""[{"custom": "expand"}]"""), creative, emptyList())
            ""
        } catch (e: AssertionError) {
            e.message.orEmpty()
        }
        assertTrue(message, message.contains("{custom expand}"))
    }

    @Test
    fun `an unknown url is not described`() {
        assertNull(describeBeacon("https://t/unknown", creative))
    }

    @Test
    fun `well-formed steps parse`() {
        assertEquals(SlideStep.SlideView(2), parseSlideStep("F99", obj("""{"slideView": 2}""")))
        assertEquals(SlideStep.SlideClick(1), parseSlideStep("F99", obj("""{"slideClick": 1}""")))
        assertEquals(SlideStep.FireClick(null), parseSlideStep("F99", obj("""{"fireClick": null}""")))
        assertEquals(SlideStep.FireClick("save"), parseSlideStep("F99", obj("""{"fireClick": "save"}""")))
    }

    @Test
    fun `a slide position that is not a positive whole number is refused with the step shown`() {
        listOf("0", "-1", "\"1\"", "1.5", "null", "true", "{}").forEach { bad ->
            val step = """{"slideView": $bad}"""
            assertTrue("$step -> ${refusal(step)}", refusal(step).contains("slideView must be a positive whole number"))
        }
        assertTrue(refusal("""{"slideClick": "x"}""").contains("""{"slideClick":"x"}"""))
    }

    @Test
    fun `fireClick that is neither null nor a string is refused`() {
        listOf("1", "true", "{}", "[]").forEach { bad ->
            val message = refusal("""{"fireClick": $bad}""")
            assertTrue(message, message.contains("fireClick must be null or a click key string"))
        }
    }

    @Test
    fun `an empty or multi-action step is refused`() {
        assertTrue(refusal("{}").contains("exactly one action"))
        assertTrue(refusal("""{"slideView": 1, "slideClick": 1}""").contains("exactly one action"))
    }
}
