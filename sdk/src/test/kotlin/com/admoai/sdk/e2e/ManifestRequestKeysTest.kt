package com.admoai.sdk.e2e

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.SDKConfig
import com.admoai.sdk.model.request.DecisionRequestBuilder
import com.admoai.sdk.model.request.DeviceCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Offline: how the manifest runner reads `request.custom`, `request.device` and the request keys. */
class ManifestRequestKeysTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        Admoai.resetForTesting()
        Admoai.initialize(SDKConfig(baseUrl = "https://test.admoai.com", networkClientEngine = null))
    }

    @After
    fun tearDown() {
        Admoai.resetForTesting()
    }

    private fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    private fun builder() = DecisionRequestBuilder().addPlacement("home")

    private fun wire(b: DecisionRequestBuilder): String =
        json.encodeToString(Admoai.getInstance().prepareFinalDecisionRequest(b.build()))

    private fun assertFailsWith(fragment: String, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(fragment))
            return
        }
        throw AssertionError("expected a failure mentioning \"$fragment\"")
    }

    @Test
    fun `a string custom value is sent as a string`() {
        val b = builder()
        applyCustom(b, "X1", parse("""[{"key":"tier","value":"gold"}]"""))
        val entry = b.build().targeting!!.custom!!.single()
        assertEquals("tier", entry.key)
        assertEquals(JsonPrimitive("gold"), entry.value)
    }

    @Test
    fun `an array custom value is sent as a JSON array`() {
        val b = builder()
        applyCustom(b, "X2", parse("""[{"key":"publisher_segments","value":["vip","commuter"]}]"""))
        val entry = b.build().targeting!!.custom!!.single()
        assertEquals(JsonArray(listOf(JsonPrimitive("vip"), JsonPrimitive("commuter"))), entry.value)
        assertTrue(wire(b), wire(b).contains(""""value":["vip","commuter"]"""))
    }

    @Test
    fun `a number custom value fails naming the scenario and key`() {
        assertFailsWith("X3 request.custom \"age\"") {
            applyCustom(builder(), "X3", parse("""[{"key":"age","value":42}]"""))
        }
    }

    @Test
    fun `an unknown custom entry key fails`() {
        assertFailsWith("[extra]") {
            applyCustom(builder(), "X4", parse("""[{"key":"a","value":"b","extra":1}]"""))
        }
    }

    @Test
    fun `a device with carrier and category becomes a DeviceConfig`() {
        val config = deviceConfigFrom("X5", parse("""{"carrier":"Movistar","category":"tablet"}"""))
        assertNotNull(config)
        assertEquals("Movistar", config!!.carrier)
        assertEquals(DeviceCategory.TABLET, config.category)
    }

    @Test
    fun `an absent device is no DeviceConfig`() {
        assertNull(deviceConfigFrom("X5b", null))
    }

    @Test
    fun `an empty device is no manual DeviceConfig`() {
        assertNull(deviceConfigFrom("X5c", parse("{}")))
    }

    @Test
    fun `a raw custom list is sent as given, duplicates and empty included`() {
        val dup = builder()
        applyCustom(dup, "X9", parse("""[{"key":"publisher_segments","value":["a","a"]}]"""))
        assertTrue(wire(dup), wire(dup).contains(""""value":["a","a"]"""))
        val empty = builder()
        applyCustom(empty, "X10", parse("""[{"key":"publisher_segments","value":[]}]"""))
        assertTrue(wire(empty), wire(empty).contains(""""value":[]"""))
    }

    @Test
    fun `every custom value and device of the vendored manifest is accepted`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!
        val manifest = json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        for (scenario in manifest["scenarios"]!!.jsonArray.map { it.jsonObject }) {
            val id = (scenario["id"] as JsonPrimitive).content
            val request = scenario["request"]!!.jsonObject
            applyCustom(builder(), id, request["custom"])
            deviceConfigFrom(id, request["device"])
        }
    }

    @Test
    fun `a device with an unknown key fails`() {
        assertFailsWith("[model]") {
            deviceConfigFrom("X6", parse("""{"carrier":"Movistar","model":"Pixel"}"""))
        }
    }

    @Test
    fun `an unknown device category fails`() {
        assertFailsWith("\"watch\"") {
            deviceConfigFrom("X7", parse("""{"category":"watch"}"""))
        }
    }

    @Test
    fun `an unknown top-level request key is refused`() {
        assertFailsWith("[geo]") {
            refuseUnknownKeys("X8 request", setOf("placements", "geo"), EXECUTED_REQUEST_KEYS)
        }
    }

    @Test
    fun `every request key in the vendored manifest is executed`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!
        val manifest = json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val used = manifest["scenarios"]!!.jsonArray
            .flatMap { (it as JsonObject)["request"]!!.jsonObject.keys }
            .toSet()
        assertTrue("manifest uses $used", EXECUTED_REQUEST_KEYS.containsAll(used))
    }
}
