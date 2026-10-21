package com.admoai.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        val ids = scenarios.mapNotNull { (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content }
        assertEquals("every scenario has an id", scenarios.size, ids.size)
        assertEquals("scenario ids are unique", ids.size, ids.toSet().size)
        scenarios.forEach { s: JsonObject ->
            assertTrue("every scenario has a request block", s["request"] is JsonObject)
            assertTrue("every scenario has an expect block", s["expect"] is JsonObject)
        }
    }
}
