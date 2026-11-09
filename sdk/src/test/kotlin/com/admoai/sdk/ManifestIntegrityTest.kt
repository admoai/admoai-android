package com.admoai.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared cross-SDK scenario manifest is vendored from adhub's `@admoai/sdk-scenarios` package
 * (admoai/adhub#4158). Which version, and that the bytes are exactly that version, is CI's
 * "Scenario manifest" job (`.github/scripts/scenarios.sh check`); it replaced the SHA-256 this file
 * used to pin, so there is one pin, `sdk/src/test/resources/MANIFEST_VERSION`, not two. What stays
 * here, offline, is the shape the runner relies on.
 */
class ManifestIntegrityTest {

    private fun manifestBytes(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!.readBytes()

    @Test
    fun `the manifest is structurally valid`() {
        val root = Json.parseToJsonElement(String(manifestBytes())).jsonObject
        val scenarios = root["scenarios"]!!.jsonArray.map { it.jsonObject }

        assertTrue("the manifest declares at least one scenario", scenarios.isNotEmpty())
        val ids = scenarios.mapNotNull { (it["id"] as? JsonPrimitive)?.content }
        assertEquals("every scenario has an id", scenarios.size, ids.size)
        assertEquals("scenario ids are unique", ids.size, ids.toSet().size)
        scenarios.forEach { s: JsonObject ->
            assertTrue("every scenario has a request block", s["request"] is JsonObject)
            assertTrue("every scenario has an expect block", s["expect"] is JsonObject)
        }
    }

    /**
     * `schemaVersion` 2 added `fixtureScenarios` (3 added their `slideTracking`, 4 the live standard-format `expect.creative` keys, 5 the Carousel `slides`/`clickKeys` and live `slideTracking`); the live runner refuses any other version and
     * FixtureScenariosTest interprets them, so both are pinned here against the vendored copy.
     */
    @Test
    fun `the fixture scenarios are structurally valid`() {
        val root = Json.parseToJsonElement(String(manifestBytes())).jsonObject
        assertEquals(
            "this SDK vendors a schemaVersion 5 manifest",
            5,
            (root["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
        )

        val fixtureScenarios = root["fixtureScenarios"]!!.jsonArray.map { it.jsonObject }
        assertTrue("the manifest declares fixture scenarios", fixtureScenarios.isNotEmpty())
        val ids = fixtureScenarios.mapNotNull { (it["id"] as? JsonPrimitive)?.content }
        assertEquals("every fixture scenario has an id", fixtureScenarios.size, ids.size)
        assertEquals("fixture scenario ids are unique", ids.size, ids.toSet().size)
        fixtureScenarios.forEach { entry ->
            val id = (entry["id"] as? JsonPrimitive)?.content ?: "?"
            val title = (entry["title"] as? JsonPrimitive)?.content.orEmpty()
            assertTrue("$id has a title", title.isNotEmpty())
            val golden = ((entry["fixture"] as? JsonObject)?.get("golden") as? JsonPrimitive)?.content
            assertTrue("$id names a golden file", !golden.isNullOrEmpty())
            assertTrue(
                "$id: $golden.json is vendored",
                javaClass.classLoader!!.getResource("fixtures/golden-wire/$golden.json") != null
            )
            assertTrue("$id has an expect block", entry["expect"] is JsonObject)
        }
    }
}
