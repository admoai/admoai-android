package com.admoai.sdk

import com.admoai.sdk.e2e.EXECUTED_CREATIVE_KEYS
import com.admoai.sdk.e2e.EXECUTED_EXPECT_KEYS
import com.admoai.sdk.e2e.assertCreative
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The live manifest runner's standard-format expectations (`@admoai/sdk-scenarios` schemaVersion 4).
 *
 * Feature: A publisher's tech team trusts the live scenarios to check what they state
 *   As an SDK maintainer
 *   I want each live `expect.creative` key to pass on a matching creative and fail on any other
 *   So that a green live run means the engine served what the manifest says, in every SDK
 *
 * The creative is decoded through the SDK's own parser configuration, as a 200 from the engine is.
 */
class LiveStandardScenariosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun creative(
        templateKey: String = "card",
        style: String? = "tall",
        metadata: String =
            """"effectiveStandardFormatKey":"card","effectiveStandardFormatVersion":1,"templateSource":"admoai_standard"""",
        stageKey: String? = null
    ): Creative {
        val styleJson = style?.let { ""","style":"$it"""" }.orEmpty()
        val journey = stageKey?.let { ""","journey":{"stageKey":"$it"}""" }.orEmpty()
        val body = """
            {"success": true, "data": [{"placement": "p", "creatives": [{
              "template": {"key": "$templateKey"$styleJson},
              "contents": [
                {"key":"image","value":"https://cdn.example/i.png","type":"image"},
                {"key":"headline","value":"Hi","type":"text"},
                {"key":"slides","value":[],"type":"slides"}
              ],
              "metadata": {$metadata},
              "advertiser": {}, "tracking": {}$journey
            }]}]}
        """
        return json.decodeFromString<DecisionResponse>(body).data!!.first().creatives.first()
    }

    private fun passes(expect: String, creative: Creative) = assertCreative(creative, obj(expect))

    private fun failsWith(fragment: String, expect: String, creative: Creative) {
        try {
            assertCreative(creative, obj(expect))
        } catch (e: AssertionError) {
            assertTrue("message '${e.message}' should mention '$fragment'", e.message.orEmpty().contains(fragment))
            return
        }
        fail("expected the expectation $expect to fail")
    }

    // metadata

    @Test
    fun `metadata passes when every listed key equals the served value`() = passes(
        """{"metadata":{"effectiveStandardFormatKey":"card","effectiveStandardFormatVersion":1,"templateSource":"admoai_standard","assetContext":null}}""",
        creative()
    )

    @Test
    fun `metadata fails on a different value`() = failsWith(
        "effectiveStandardFormatKey",
        """{"metadata":{"effectiveStandardFormatKey":"carousel"}}""",
        creative()
    )

    @Test
    fun `metadata null fails when the key is served`() = failsWith(
        "templateSource",
        """{"metadata":{"templateSource":null}}""",
        creative()
    )

    @Test
    fun `metadata null passes for a legacy creative`() = passes(
        """{"metadata":{"effectiveStandardFormatKey":null,"effectiveStandardFormatVersion":null,"templateSource":null,"assetContext":null}}""",
        creative(metadata = """"format":"native"""")
    )

    @Test
    fun `metadata assetContext reads the served value`() {
        val branded = creative(metadata = """"assetContext":"vehicle_marker"""")
        passes("""{"metadata":{"assetContext":"vehicle_marker"}}""", branded)
        failsWith("assetContext", """{"metadata":{"assetContext":"vehicle_marker"}}""", creative())
        failsWith("assetContext", """{"metadata":{"assetContext":null}}""", branded)
        passes("""{"metadata":{"assetContext":null}}""", creative())
    }

    @Test
    fun `metadata compares the JSON type, not only the content`() {
        failsWith(
            "effectiveStandardFormatVersion",
            """{"metadata":{"effectiveStandardFormatVersion":"1"}}""",
            creative()
        )
        failsWith(
            "effectiveStandardFormatKey",
            """{"metadata":{"effectiveStandardFormatKey":1}}""",
            creative(metadata = """"effectiveStandardFormatKey":"1"""")
        )
        passes("""{"metadata":{"effectiveStandardFormatVersion":1}}""", creative())
    }

    @Test
    fun `metadata refuses a key the manifest does not define`() =
        failsWith("unknown metadata expect key", """{"metadata":{"colour":"red"}}""", creative())

    // templateKey

    @Test
    fun `templateKey passes on the served template key`() = passes("""{"templateKey":"card"}""", creative())

    @Test
    fun `templateKey fails on another template`() =
        failsWith("templateKey", """{"templateKey":"carousel"}""", creative())

    // contentKeys

    @Test
    fun `contentKeys is an unordered exact set that ignores slides`() =
        passes("""{"contentKeys":["headline","image"]}""", creative())

    @Test
    fun `contentKeys fails on a missing key`() =
        failsWith("contentKeys", """{"contentKeys":["image","headline","cta_text"]}""", creative())

    @Test
    fun `contentKeys fails on an extra served key`() =
        failsWith("contentKeys", """{"contentKeys":["image"]}""", creative())

    // styleIn

    @Test
    fun `styleIn passes when the served style is listed`() =
        passes("""{"styleIn":["default","tall"]}""", creative(style = "tall"))

    @Test
    fun `styleIn fails when the served style is not listed`() =
        failsWith("template.style", """{"styleIn":["default"]}""", creative(style = "tall"))

    @Test
    fun `styleIn fails when no style is served`() =
        failsWith("template.style", """{"styleIn":["default","tall"]}""", creative(style = null))

    // journeyStageKey

    @Test
    fun `journeyStageKey passes on the served stage`() =
        passes("""{"journeyStageKey":"complete"}""", creative(stageKey = "complete"))

    @Test
    fun `journeyStageKey fails on another stage`() =
        failsWith("journey.stageKey", """{"journeyStageKey":"complete"}""", creative(stageKey = "start"))

    @Test
    fun `journeyStageKey fails when the creative is not a journey screen`() =
        failsWith("journey.stageKey", """{"journeyStageKey":"complete"}""", creative())

    // the unknown-key guard

    @Test
    fun `an unknown creative key fails instead of passing unchecked`() =
        failsWith("fancyNewKey", """{"templateKey":"card","fancyNewKey":true}""", creative())

    @Test
    fun `every expect key of the vendored manifest is executed by the runner`() {
        val manifest = obj(
            javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!.bufferedReader().readText()
        )
        for (scenario in manifest["scenarios"]!!.jsonArray.map { it.jsonObject }) {
            val expect = scenario["expect"]!!.jsonObject
            val id = scenario["id"]
            assertEquals("$id top-level expect keys", emptySet<String>(), expect.keys - EXECUTED_EXPECT_KEYS)
            val creativeKeys = (expect["creative"] as? JsonObject)?.keys.orEmpty()
            assertEquals("$id expect.creative keys", emptySet<String>(), creativeKeys - EXECUTED_CREATIVE_KEYS)
        }
    }
}
