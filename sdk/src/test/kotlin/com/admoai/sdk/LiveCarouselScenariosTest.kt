package com.admoai.sdk

import com.admoai.sdk.e2e.EXECUTED_CREATIVE_KEYS
import com.admoai.sdk.e2e.assertCreative
import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.DecisionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The live manifest runner's Carousel expectations (`@admoai/sdk-scenarios` schemaVersion 5).
 *
 * Feature: A publisher's tech team trusts the live Carousel scenarios to check what they state
 *   As an SDK maintainer
 *   I want `expect.creative.slides`, `clickKeys` and a live `slideTracking` to pass on what the
 *     engine should serve and send, and to fail on anything else
 *   So that a green live run means the engine served the slides and accepted the beacons the manifest says
 */
class LiveCarouselScenariosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun slideJson(id: String, position: Int) = """
        {"slideId":"$id","position":$position,
         "contents":[{"key":"image","value":"https://cdn.example/$id.png","type":"image"},
                     {"key":"destination_url","value":"https://example.com/$id","type":"url"}],
         "tracking":{"views":[{"key":"slide_view","url":"https://e/v/$id"}],
                     "clicks":[{"key":"default","url":"https://e/c/$id"}]}}
    """

    private fun carousel(slideIds: List<String> = listOf("s1", "s2"), creativeClicks: String? = null): Creative {
        val slides = slideIds.mapIndexed { i, id -> slideJson(id, i + 1) }.joinToString(",")
        val clicks = creativeClicks?.let { ""","clicks":[{"key":"default","url":"$it"}]""" }.orEmpty()
        val body = """
            {"success": true, "data": [{"placement": "p", "creatives": [{
              "template": {"key": "carousel"},
              "contents": [{"key":"slides","value":[$slides],"type":"slides"}],
              "advertiser": {}, "tracking": {"impressions":[]$clicks}
            }]}]}
        """
        return json.decodeFromString<DecisionResponse>(body).data!!.first().creatives.first()
    }

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun slideExpect(id: String, position: Int) =
        """{"slideId":"$id","position":$position,"contentKeys":["destination_url","image"],""" +
            """"viewKeys":["slide_view"],"clickKeys":["default"]}"""

    private val bothSlides = """{"slides":[${slideExpect("s1", 1)},${slideExpect("s2", 2)}]}"""

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

    // slides

    @Test
    fun `slides pass on the served slides in order`() = passes(bothSlides, carousel())

    @Test
    fun `slides fail when the order differs`() = failsWith(
        "slideId",
        """{"slides":[${slideExpect("s2", 1)},${slideExpect("s1", 2)}]}""",
        carousel()
    )

    @Test
    fun `slides fail when a slide is missing from the served creative`() =
        failsWith("slide count", bothSlides, carousel(listOf("s1")))

    @Test
    fun `slides fail when the creative serves an extra slide`() =
        failsWith("slide count", bothSlides, carousel(listOf("s1", "s2", "s3")))

    @Test
    fun `slides fail on a content key set that differs`() = failsWith(
        "contentKeys",
        """{"slides":[{"slideId":"s1","position":1,"contentKeys":["image"],"viewKeys":["slide_view"],"clickKeys":["default"]}]}""",
        carousel(listOf("s1"))
    )

    @Test
    fun `slides fail on an extra click key`() = failsWith(
        "clickKeys",
        """{"slides":[{"slideId":"s1","position":1,"contentKeys":["destination_url","image"],"viewKeys":["slide_view"],"clickKeys":[]}]}""",
        carousel(listOf("s1"))
    )

    @Test
    fun `slides fail on a view key that is not served`() = failsWith(
        "viewKeys",
        """{"slides":[{"slideId":"s1","position":1,"contentKeys":["destination_url","image"],"viewKeys":["slide_view","other"],"clickKeys":["default"]}]}""",
        carousel(listOf("s1"))
    )

    @Test
    fun `slides fail on a wrong position`() = failsWith(
        "position",
        """{"slides":[{"slideId":"s1","position":2,"contentKeys":["destination_url","image"],"viewKeys":["slide_view"],"clickKeys":["default"]}]}""",
        carousel(listOf("s1"))
    )

    @Test
    fun `slides refuse a slide key the manifest does not define`() = failsWith(
        "unknown slide expect key",
        """{"slides":[{"slideId":"s1","colour":"red"}]}""",
        carousel(listOf("s1"))
    )

    // creative-level clickKeys

    @Test
    fun `clickKeys empty passes on a carousel without a creative click`() = passes("""{"clickKeys":[]}""", carousel())

    @Test
    fun `clickKeys fail on an extra served creative click key`() =
        failsWith("clickKeys", """{"clickKeys":[]}""", carousel(creativeClicks = "https://e/c"))

    @Test
    fun `clickKeys fail on a missing creative click key`() =
        failsWith("clickKeys", """{"clickKeys":["default"]}""", carousel())

    // slideTracking beacons

    private val v1 = "https://e/v/s1"
    private val c1 = "https://e/c/s1"
    private val c2 = "https://e/c/s2"
    private val v2 = "https://e/v/s2"

    private fun beacons(text: String): JsonArray = json.parseToJsonElement(text).jsonArray

    private fun beaconsFail(fragment: String, expected: String, dispatched: List<String>) {
        try {
            checkDispatchedBeacons("ST", beacons(expected), carousel(), dispatched)
        } catch (e: AssertionError) {
            assertTrue("message '${e.message}' should mention '$fragment'", e.message.orEmpty().contains(fragment))
            return
        }
        fail("expected the beacons $dispatched not to match $expected")
    }

    @Test
    fun `beacons pass when dispatched in the expected order`() = checkDispatchedBeacons(
        "ST",
        beacons("""[{"slide":2,"view":"slide_view"},{"slide":2,"click":"default"}]"""),
        carousel(),
        listOf(v2, c2)
    )

    @Test
    fun `no beacons pass when nothing was dispatched`() =
        checkDispatchedBeacons("ST", beacons("[]"), carousel(), emptyList())

    @Test
    fun `beacons fail when the order differs`() = beaconsFail(
        "dispatch order",
        """[{"slide":2,"view":"slide_view"},{"slide":2,"click":"default"}]""",
        listOf(c2, v2)
    )

    @Test
    fun `beacons fail when one is missing`() = beaconsFail(
        "slide 2, click default",
        """[{"slide":2,"view":"slide_view"},{"slide":2,"click":"default"}]""",
        listOf(v2)
    )

    @Test
    fun `beacons fail on a duplicate view that the SDK should have deduplicated`() = beaconsFail(
        "slide 1, view slide_view",
        """[{"slide":1,"view":"slide_view"}]""",
        listOf(v1, v1)
    )

    @Test
    fun `beacons fail on a dispatched URL that is no tracker of the creative`() =
        beaconsFail("unknown beacon", "[]", listOf("https://elsewhere/x"))

    @Test
    fun `beacons fail when a beacon of another slide was sent`() = beaconsFail(
        "slide 1",
        """[{"slide":2,"click":"default"}]""",
        listOf(c1)
    )

    // engineAcceptsEveryBeacon

    @Test
    fun `the engine accepting every beacon passes, a 302 included`() =
        checkEngineAcceptedEveryBeacon(2, listOf(v1 to 200, c1 to 302))

    @Test
    fun `a rejected beacon fails`() {
        try {
            checkEngineAcceptedEveryBeacon(2, listOf(v1 to 200, c1 to 400))
        } catch (e: AssertionError) {
            assertTrue(e.message.orEmpty().contains("rejected"))
            return
        }
        fail("a 400 must fail")
    }

    @Test
    fun `a beacon the engine never answered fails`() {
        try {
            checkEngineAcceptedEveryBeacon(2, listOf(v1 to 200))
        } catch (e: AssertionError) {
            assertTrue(e.message.orEmpty().contains("1/2"))
            return
        }
        fail("an unanswered beacon must fail")
    }

    @Test
    fun `every schemaVersion 5 creative key of the vendored manifest is executed`() {
        val manifest = obj(
            javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!.bufferedReader().readText()
        )
        assertEquals(5, manifest["schemaVersion"].toString().toInt())
        for (scenario in manifest["scenarios"]!!.jsonArray.map { it.jsonObject }) {
            val creativeKeys = (scenario["expect"]!!.jsonObject["creative"] as? JsonObject)?.keys.orEmpty()
            assertEquals(
                "${scenario["id"]} expect.creative keys",
                emptySet<String>(),
                creativeKeys - EXECUTED_CREATIVE_KEYS
            )
        }
    }
}
