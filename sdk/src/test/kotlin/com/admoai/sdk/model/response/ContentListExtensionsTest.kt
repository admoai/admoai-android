package com.admoai.sdk.model.response

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentListExtensionsTest {

    private val contents = listOf(
        Content(key = "headline", value = JsonPrimitive("Book a ride"), type = ContentType.TEXT),
        Content(key = "image", value = JsonPrimitive("https://cdn.example.com/img.jpg"), type = ContentType.IMAGE),
        Content(key = "cta", value = JsonPrimitive("Book Now"), type = ContentType.TEXT),
        Content(key = "discount", value = JsonPrimitive(15), type = ContentType.INTEGER)
    )

    // --- getContent ---

    @Test
    fun `given content list with headline when getContent called then returns matching content`() {
        val result = contents.getContent("headline")
        assertEquals("headline", result?.key)
        assertEquals(JsonPrimitive("Book a ride"), result?.value)
    }

    @Test
    fun `given content list when getContent called for non-existent key then returns null safely`() {
        assertNull(contents.getContent("nonexistent_field"))
    }

    @Test
    fun `given empty content list when getContent called then returns null`() {
        assertNull(emptyList<Content>().getContent("headline"))
    }

    @Test
    fun `given content list when getContent for image key then returns image content`() {
        val result = contents.getContent("image")
        assertEquals(ContentType.IMAGE, result?.type)
    }

    // --- hasContents ---

    @Test
    fun `given non-empty content list when hasContents called then returns true`() {
        assertTrue(contents.hasContents())
    }

    @Test
    fun `given empty content list when hasContents called then returns false`() {
        assertFalse(emptyList<Content>().hasContents())
    }

    // --- isType ---

    @Test
    fun `given image content when isType called with IMAGE then returns true`() {
        assertTrue(contents.isType("image", ContentType.IMAGE))
    }

    @Test
    fun `given text content when isType called with wrong type then returns false`() {
        assertFalse(contents.isType("headline", ContentType.IMAGE))
    }

    @Test
    fun `given non-existent key when isType called then returns false`() {
        assertFalse(contents.isType("missing", ContentType.TEXT))
    }

    @Test
    fun `given integer content when isType called with INTEGER then returns true`() {
        assertTrue(contents.isType("discount", ContentType.INTEGER))
    }
}
