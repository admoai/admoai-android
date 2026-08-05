package com.admoai.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The shared cross-SDK E2E scenario manifest is mirrored byte-identically into all three SDK repos.
 * This pins its SHA-256 so an edit to one copy fails that repo's build until the constant is
 * updated — and because the SAME constant appears in all three repos, three matching hashes is
 * mechanical proof the copies agree. Divergence is otherwise invisible: it was exactly how the
 * hand-written suites drifted (this suite's K1 asserted less than iOS's and Flutter's).
 *
 * If this fails after you intentionally changed the manifest: update the copy in ALL THREE repos,
 * then update this constant in all three. Cross-repo enforcement in CI belongs in adhub, the only
 * place that can see all three at once.
 */
class ManifestIntegrityTest {

    private val expectedSha256 =
        "5b7c2b3d261d68b5b4e52091d0b00ac7ec1bd09cf950f482ce968e1e1a34d2ca"

    private fun manifestBytes(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("e2e-scenarios.json")!!.readBytes()

    @Test
    fun `the manifest matches the cross-SDK hash`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(manifestBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "e2e-scenarios.json changed. Mirror the edit into admoai-ios and admoai-flutter, " +
                "then update expectedSha256 in all three repos.",
            expectedSha256,
            digest,
        )
    }

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
