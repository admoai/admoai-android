package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.ContentSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Defines the type of content in a creative element.
 *
 * These types determine how the content value should be interpreted and displayed.
 * For example, TEXT is for simple text strings, IMAGE for image URLs,
 * HTML for HTML markup, etc.
 *
 * This is an OPEN set. The authoritative list is the `template_fields_valid_type` CHECK in the
 * decision-engine schema, and the engine forwards `template_fields.type` to the response verbatim,
 * so the platform can introduce a new field type without an SDK release. Any value this SDK version
 * does not recognise decodes to [UNKNOWN] rather than dropping the content entry — read
 * [Content.rawType] to handle it. See the Tolerant Reader policy at docs.admoai.com
 * ("handle unexpected enum values").
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

    /**
     * Operator-selectable option list. Allowed by the engine's `template_fields_valid_type` CHECK
     * but missing from this enum until now, so every `dropdown` field served was silently dropped
     * from the creative on Android while iOS and Flutter rendered it.
     */
    @SerialName("dropdown")
    DROPDOWN,

    @SerialName("url")
    URL,
    @SerialName("color")
    COLOR,
    @SerialName("video")
    VIDEO,

    /** Any type this SDK version does not recognise. The wire value is kept in [Content.rawType]. */
    @SerialName("unknown")
    UNKNOWN
}

/** The `template_fields.type` string this enum constant corresponds to on the wire. */
val ContentType.wireName: String
    get() = when (this) {
        ContentType.TEXT -> "text"
        ContentType.TEXTAREA -> "textarea"
        ContentType.MARKDOWN -> "markdown"
        ContentType.HTML -> "html"
        ContentType.IMAGE -> "image"
        ContentType.IMAGES -> "images"
        ContentType.INTEGER -> "integer"
        ContentType.NUMBER -> "number"
        ContentType.FLOAT -> "float"
        ContentType.DROPDOWN -> "dropdown"
        ContentType.URL -> "url"
        ContentType.COLOR -> "color"
        ContentType.VIDEO -> "video"
        ContentType.UNKNOWN -> "unknown"
    }

/** Parses a wire `type` string, returning [ContentType.UNKNOWN] for anything unrecognised. */
fun contentTypeFromWire(raw: String?): ContentType =
    ContentType.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() } ?: ContentType.UNKNOWN

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
 * @property value The actual content value, represented as JsonElement to support multiple data
 *   types. [JsonNull] when the server sends null or omits the field.
 * @property type The content type indicating how to interpret the value, or [ContentType.UNKNOWN]
 *   for a type this SDK version does not know
 * @property rawType The `type` string exactly as the engine sent it. Equal to the wire form of
 *   [type] for known types; for [ContentType.UNKNOWN] it is the only place the real type survives,
 *   so a publisher can support a newly-introduced field type without waiting for an SDK release.
 */
@Serializable(with = ContentSerializer::class)
data class Content(
    val key: String,
    val value: JsonElement = JsonNull,
    val type: ContentType,
    val rawType: String = type.wireName
)

fun List<Content>.getContent(key: String): Content? = firstOrNull { it.key == key }

fun List<Content>.hasContents(): Boolean = isNotEmpty()

fun List<Content>.isType(key: String, type: ContentType): Boolean =
    firstOrNull { it.key == key }?.type == type

/**
 * Matches on the raw wire type, for content types newer than this SDK version. Use this instead of
 * [isType] when handling a field type that decodes to [ContentType.UNKNOWN].
 */
fun List<Content>.isRawType(key: String, rawType: String): Boolean =
    firstOrNull { it.key == key }?.rawType == rawType
