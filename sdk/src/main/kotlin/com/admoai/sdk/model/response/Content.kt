package com.admoai.sdk.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Defines the type of content in a creative element.
 *
 * These types determine how the content value should be interpreted and displayed.
 * For example, TEXT is for simple text strings, IMAGE for image URLs,
 * HTML for HTML markup, etc.
 */
@Serializable
enum class ContentType {
    @SerialName("text")
    TEXT,
    @SerialName("textarea")
    TEXTAREA,
    @SerialName("markdown")
    MARKDOWN,
    @SerialName("html")
    HTML,
    @SerialName("image")
    IMAGE,
    @SerialName("images")
    IMAGES,
    @SerialName("integer")
    INTEGER,
    @SerialName("number")
    NUMBER,
    @SerialName("float")
    FLOAT,
    @SerialName("url")
    URL,
    @SerialName("color")
    COLOR,
    @SerialName("video")
    VIDEO
}

/**
 * Represents a single piece of content in a creative.
 *
 * Each Content object has a key that identifies what this content represents
 * (e.g., "headline", "description", "coverImage"), a value containing the actual
 * content data, and a type that indicates how to interpret the value.
 *
 * Common content keys include:
 * - "headline" - The main title text
 * - "description" - Longer descriptive text
 * - "coverImage" - Main image URL
 * - "callToAction" - Call-to-action button text
 * - "sponsoredBy" - Attribution text
 *
 * @property key The identifier for this content piece
 * @property value The actual content value, represented as JsonElement to support multiple data types
 * @property type The content type that indicates how to interpret the value
 */
@Serializable
data class Content(
    val key: String,
    val value: JsonElement,
    val type: ContentType
)

fun List<Content>.getContent(key: String): Content? = firstOrNull { it.key == key }

fun List<Content>.hasContents(): Boolean = isNotEmpty()

fun List<Content>.isType(key: String, type: ContentType): Boolean =
    firstOrNull { it.key == key }?.type == type
