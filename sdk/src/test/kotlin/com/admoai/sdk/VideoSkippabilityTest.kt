package com.admoai.sdk

import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.utils.getSkipOffset
import com.admoai.sdk.utils.isSkippable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-SDK parity coverage for skippability resolution.
 *
 * `isSkippable()` / `getSkipOffset()` matched the content keys `isSkippable` and `skipOffset` in
 * camelCase. The platform creates template fields in snake_case, and a live journey video serve
 * returns `is_skippable` / `skip_offset` — the only such keys in the whole template_fields table.
 * So neither helper could ever match: `isSkippable()` always returned false and `getSkipOffset()`
 * always returned null. Neither had any test coverage at all, which is why it survived.
 *
 * Same class of defect as #2483 (the journey click resolver matched a hand-maintained snake_case
 * list while the platform wrote camelCase) — the same seam, the opposite direction.
 *
 * Both helpers now prefer the engine-owned metadata fields, which is the only source the iOS SDK
 * reads, and accept either casing in the content fallback. The identical change is applied to the
 * Flutter SDK, which had the same mismatch.
 */
class VideoSkippabilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun creative(contents: String, metadata: String? = null): Creative =
        json.decodeFromString(
            """
            {
              "contents": [$contents],
              "advertiser": {},
              "template": {"key": "normal_video"},
              "tracking": {},
              "delivery": "json"
              ${if (metadata != null) ", \"metadata\": $metadata" else ""}
            }
            """.trimIndent()
        )

    private fun metadata(extra: String): String =
        """
        {
          "adId": "a",
          "creativeId": "c",
          "templateId": "t",
          "placementId": "p",
          "priority": "standard"
          ${if (extra.isBlank()) "" else ", $extra"}
        }
        """.trimIndent()

    // ── snake_case content keys: the shape a live serve actually returns ──────────

    @Test
    fun `is_skippable integer 1 is skippable`() {
        val c = creative(
            """
            {"key": "is_skippable", "value": 1, "type": "integer"},
            {"key": "skip_offset", "value": "5", "type": "text"}
            """.trimIndent()
        )

        assertTrue(c.isSkippable())
        assertEquals("5", c.getSkipOffset())
    }

    @Test
    fun `is_skippable integer 0 is not skippable`() {
        val c = creative("""{"key": "is_skippable", "value": 0, "type": "integer"}""")

        assertFalse(c.isSkippable())
    }

    @Test
    fun `is_skippable string true is skippable`() {
        val c = creative("""{"key": "is_skippable", "value": "true", "type": "integer"}""")

        assertTrue(c.isSkippable())
    }

    @Test
    fun `a non-numeric placeholder value is not skippable and does not throw`() {
        // The mock seed fills these fields with placeholder text, so the helper must degrade
        // rather than throw or guess.
        val c = creative(
            """
            {"key": "is_skippable", "value": "is_skippable (demo)", "type": "integer"},
            {"key": "skip_offset", "value": "Journey Ad demo", "type": "text"}
            """.trimIndent()
        )

        assertFalse(c.isSkippable())
        assertEquals("Journey Ad demo", c.getSkipOffset())
    }

    // ── camelCase content keys keep working ──────────────────────────────────────

    @Test
    fun `camelCase isSkippable and skipOffset remain supported`() {
        val c = creative(
            """
            {"key": "isSkippable", "value": true, "type": "integer"},
            {"key": "skipOffset", "value": "00:00:05", "type": "text"}
            """.trimIndent()
        )

        assertTrue(c.isSkippable())
        assertEquals("00:00:05", c.getSkipOffset())
    }

    // ── engine metadata wins over content ────────────────────────────────────────

    @Test
    fun `metadata isSkippable takes precedence over content`() {
        val c = creative(
            """{"key": "is_skippable", "value": 0, "type": "integer"}""",
            metadata(""""isSkippable": true""")
        )

        assertTrue(c.isSkippable())
    }

    @Test
    fun `metadata skipOffsetSeconds takes precedence over content`() {
        val c = creative(
            """{"key": "skip_offset", "value": "99", "type": "text"}""",
            metadata(""""skipOffsetSeconds": 5""")
        )

        assertEquals("5", c.getSkipOffset())
    }

    @Test
    fun `content is used when metadata omits the fields`() {
        val c = creative(
            """
            {"key": "is_skippable", "value": 1, "type": "integer"},
            {"key": "skip_offset", "value": "7", "type": "text"}
            """.trimIndent(),
            metadata("")
        )

        assertTrue(c.isSkippable())
        assertEquals("7", c.getSkipOffset())
    }

    // ── absent everywhere ────────────────────────────────────────────────────────

    @Test
    fun `no metadata and no content fields`() {
        val c = creative("")

        assertFalse(c.isSkippable())
        assertNull(c.getSkipOffset())
    }
}
