package com.admoai.sdk.journey

import com.admoai.sdk.model.response.CreativeMetadata
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BDD coverage for the Journey video metadata fields (impId, skipOffsetSeconds, endCardMode).
 */
class JourneyMetadataTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `parses impId, skipOffsetSeconds and endCardMode`() {
        val body = """
            {"adId":"a","creativeId":"c","placementId":"p","templateId":"t","priority":"standard",
             "impId":"imp-123","skipOffsetSeconds":5,"endCardMode":"auto"}
        """.trimIndent()
        val metadata = json.decodeFromString<CreativeMetadata>(body)
        assertEquals("imp-123", metadata.impId)
        assertEquals(5, metadata.skipOffsetSeconds)
        assertEquals("auto", metadata.endCardMode)
    }

    @Test
    fun `absent Journey metadata fields default to null (backward compatible)`() {
        val body = """
            {"adId":"a","creativeId":"c","placementId":"p","templateId":"t","priority":"standard"}
        """.trimIndent()
        val metadata = json.decodeFromString<CreativeMetadata>(body)
        assertNull(metadata.impId)
        assertNull(metadata.skipOffsetSeconds)
        assertNull(metadata.endCardMode)
    }
}
