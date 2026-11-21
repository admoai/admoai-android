package com.admoai.sdk.e2e

import com.admoai.sdk.Admoai
import com.admoai.sdk.config.DeviceConfig
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

    private fun assertRunnerRefuses(fragment: String, block: () -> Unit) {
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
        assertRunnerRefuses("X3 request.custom \"age\"") {
            applyCustom(builder(), "X3", parse("""[{"key":"age","value":42}]"""))
        }
    }

    @Test
    fun `an unknown custom entry key fails`() {
        assertRunnerRefuses("[extra]") {
            applyCustom(builder(), "X4", parse("""[{"key":"a","value":"b","extra":1}]"""))
        }
    }

    @Test
    fun `apiVersion none sends no version header, absent uses the run default`() {
        assertNull(manifestApiVersion("none", "2025-11-01"))
        assertEquals("2025-11-01", manifestApiVersion(null, "2025-11-01"))
        assertEquals("2025-01-01", manifestApiVersion("2025-01-01", "2025-11-01"))

        Admoai.resetForTesting()
        Admoai.initialize(
            SDKConfig(
                baseUrl = "https://test.admoai.com",
                apiVersion = manifestApiVersion("none", "2025-11-01"),
                networkClientEngine = null
            )
        )
        val headers = Admoai.getInstance().getHttpRequestData(
            DecisionRequestBuilder().addPlacement("home").build()
        ).headers
        assertTrue(
            "no X-Decision-Version in $headers",
            headers.keys.none {
                it.equals("X-Decision-Version", ignoreCase = true)
            }
        )
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
    fun `a device keeps the fields of the config it is laid over`() {
        val config = deviceConfigFrom(
            "X5d",
            parse("""{"carrier":"Movistar"}"""),
            DeviceConfig(language = "en", model = "Pixel")
        )!!
        assertEquals("en", config.language)
        assertEquals("Pixel", config.model)
        assertEquals("Movistar", config.carrier)
        assertNull(config.category)
    }

    private fun manifestDeviceWire(id: String): JsonObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!
        val manifest = json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val scenario = manifest["scenarios"]!!.jsonArray.map { it.jsonObject }
            .single { (it["id"] as JsonPrimitive).content == id }
        // The harness's own config (JourneyE2eRunner), then the scenario's device, as the runner applies it.
        Admoai.getInstance().setDeviceConfig(DeviceConfig(language = "en"))
        applyManifestDevice(id, scenario["request"]!!.jsonObject["device"])
        return json.parseToJsonElement(wire(builder())).jsonObject["device"]!!.jsonObject
    }

    @Test
    fun `AS6 sends the manifest's carrier and category on the wire`() {
        val device = manifestDeviceWire("AS6")
        assertEquals(JsonPrimitive("Example Mobile"), device["carrier"])
        assertEquals(JsonPrimitive("phone"), device["category"])
        assertEquals(JsonPrimitive("en"), device["language"])
    }

    @Test
    fun `AS7 sends the tablet category and no carrier, because wifi is cleaned`() {
        val device = manifestDeviceWire("AS7")
        assertNull(device["carrier"])
        assertEquals(JsonPrimitive("tablet"), device["category"])
        assertEquals(JsonPrimitive("en"), device["language"])
    }

    @Test
    fun `AS8 sends neither carrier nor category`() {
        val device = manifestDeviceWire("AS8")
        assertNull(device["carrier"])
        assertNull(device["category"])
        assertEquals(JsonPrimitive("en"), device["language"])
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
        assertRunnerRefuses("[model]") {
            deviceConfigFrom("X6", parse("""{"carrier":"Movistar","model":"Pixel"}"""))
        }
    }

    @Test
    fun `an unknown device category fails`() {
        assertRunnerRefuses("\"watch\"") {
            deviceConfigFrom("X7", parse("""{"category":"watch"}"""))
        }
    }

    @Test
    fun `an unknown top-level request key is refused`() {
        assertRunnerRefuses("[geo]") {
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

    /** IE1 and IE3's version: below it the SDK logs a warning, and android.util.Log is not mocked on the JVM. */
    private fun initOn20251101() {
        Admoai.resetForTesting()
        Admoai.initialize(
            SDKConfig(baseUrl = "https://test.admoai.com", apiVersion = "2025-11-01", networkClientEngine = null)
        )
    }

    private fun ieWire(b: DecisionRequestBuilder): JsonObject? =
        json.parseToJsonElement(wire(b)).jsonObject["interactiveExperience"] as? JsonObject

    @Test
    fun `an interactiveExperience request applies the opt-in and the wire carries the declaration`() {
        val ie = parse("""{"supported":true,"availableAttributeKeys":["fullName","email","phone"]}""")
        try {
            initOn20251101()
            val b = builder()
            applyInteractiveExperience("X11", ie)
            expectInteractiveExperienceOnWire("X11", ie, b)
            val sent = ieWire(b)!!
            assertEquals(JsonPrimitive(true), sent["supported"])
            assertEquals(
                parse("""["fullName","email","phone"]"""),
                sent["availableAttributeKeys"]
            )
        } finally {
            resetInteractiveExperience()
        }
    }

    @Test
    fun `an empty key list sends an empty availableAttributeKeys`() {
        val ie = parse("""{"supported":true,"availableAttributeKeys":[]}""")
        try {
            initOn20251101()
            val b = builder()
            applyInteractiveExperience("X12", ie)
            expectInteractiveExperienceOnWire("X12", ie, b)
            assertEquals(JsonArray(emptyList()), ieWire(b)!!["availableAttributeKeys"])
        } finally {
            resetInteractiveExperience()
        }
    }

    @Test
    fun `keys are sent in catalog order whatever order the manifest lists them`() {
        val ie = parse("""{"supported":true,"availableAttributeKeys":["phone","email"]}""")
        try {
            initOn20251101()
            val b = builder()
            applyInteractiveExperience("X13", ie)
            expectInteractiveExperienceOnWire("X13", ie, b)
            assertEquals(parse("""["email","phone"]"""), ieWire(b)!!["availableAttributeKeys"])
        } finally {
            resetInteractiveExperience()
        }
    }

    @Test
    fun `resetting the opt-in stops the declaration`() {
        applyInteractiveExperience("X14", parse("""{"supported":true,"availableAttributeKeys":[]}"""))
        resetInteractiveExperience()
        assertNull(ieWire(builder()))
    }

    @Test
    fun `an absent interactiveExperience changes nothing`() {
        applyInteractiveExperience("X15", null)
        assertNull(ieWire(builder()))
    }

    @Test
    fun `supported false is refused because the SDK never sends it`() {
        assertRunnerRefuses("never sends") {
            applyInteractiveExperience("X16", parse("""{"supported":false,"availableAttributeKeys":[]}"""))
        }
    }

    @Test
    fun `an unknown attribute key name is refused naming the index, not the value`() {
        try {
            applyInteractiveExperience(
                "X17",
                parse("""{"supported":true,"availableAttributeKeys":["email","shoeSize"]}""")
            )
            throw AssertionError("expected a failure")
        } catch (e: AssertionError) {
            assertTrue(e.message, e.message.orEmpty().contains("[1]"))
            assertTrue(e.message, !e.message.orEmpty().contains("shoeSize"))
        } finally {
            resetInteractiveExperience()
        }
    }

    @Test
    fun `an unknown key inside interactiveExperience is refused`() {
        assertRunnerRefuses("[values]") {
            applyInteractiveExperience(
                "X18",
                parse("""{"supported":true,"availableAttributeKeys":[],"values":{}}""")
            )
        }
    }

    @Test
    fun `schemaVersion 6 and 7 are accepted, 8 and an absent one are refused`() {
        checkSchemaVersion(6)
        checkSchemaVersion(7)
        for (bad in listOf(5, 8, null)) {
            try {
                checkSchemaVersion(bad)
                throw AssertionError("schemaVersion $bad must be refused")
            } catch (e: IllegalStateException) {
                assertTrue(e.message, e.message.orEmpty().contains("schemaVersion $bad"))
            }
        }
    }
}
