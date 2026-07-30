package com.admoai.sdk.utils

import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.CreativeMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import android.util.Base64

fun Creative.isVastTagDelivery(): Boolean = delivery == "vast_tag"

fun Creative.isVastXmlDelivery(): Boolean = delivery == "vast_xml"

fun Creative.isJsonDelivery(): Boolean = delivery == "json"

fun Creative.getVastTagUrl(mediaType: String? = null, mediaDelivery: String? = null): String? {
    val baseUrl = vast?.tagUrl ?: return null
    val queryParams = mutableListOf<String>()
    mediaType?.let { queryParams.add("mediaType=${java.net.URLEncoder.encode(it, "UTF-8")}") }
    mediaDelivery?.let { queryParams.add("mediaDelivery=${java.net.URLEncoder.encode(it, "UTF-8")}") }
    
    return if (queryParams.isEmpty()) {
        baseUrl
    } else {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        "$baseUrl$separator${queryParams.joinToString("&")}"
    }
}

fun Creative.getVastXmlBase64(mediaType: String? = null, mediaDelivery: String? = null): String? {
    val base64Xml = vast?.xmlBase64 ?: return null
    
    if (mediaType == null && mediaDelivery == null) {
        return base64Xml
    }
    
    return try {
        val decodedBytes = Base64.decode(base64Xml, Base64.DEFAULT)
        var xmlString = String(decodedBytes, Charsets.UTF_8)
        val mediaFilePattern = """(<MediaFile[^>]*?)(\s+type="[^"]*")?([^>]*?)(\s+delivery="[^"]*")?([^>]*?>)""".toRegex()
        
        xmlString = mediaFilePattern.replace(xmlString) { matchResult ->
            var result = matchResult.value
            
            mediaType?.let { newType ->
                result = if (result.contains("type=")) {
                    result.replace("""type="[^"]*"""".toRegex(), """type="$newType"""")
                } else {
                    result.replace(">", " type=\"$newType\">")
                }
            }
            
            mediaDelivery?.let { newDelivery ->
                result = if (result.contains("delivery=")) {
                    result.replace("""delivery="[^"]*"""".toRegex(), """delivery="$newDelivery"""")
                } else {
                    result.replace(">", " delivery=\"$newDelivery\">")
                }
            }
            
            result
        }
        
        Base64.encodeToString(xmlString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    } catch (e: Exception) {
        base64Xml
    }
}

/**
 * Whether the video may be skipped.
 *
 * Prefers [CreativeMetadata.isSkippable] — the engine-owned field, and the only source the iOS SDK
 * reads — then falls back to the creative's content fields.
 *
 * The fallback matches **both** `isSkippable` and `is_skippable`. It previously matched camelCase
 * only, while the platform creates template fields in snake_case (`is_skippable`), so it could never
 * hit and this function always returned `false`. Same class of defect as #2483, where the journey
 * click resolver matched a hand-maintained snake_case list while the platform wrote camelCase — the
 * same seam, the opposite direction.
 */
fun Creative.isSkippable(): Boolean {
    metadata?.isSkippable?.let { return it }

    val value = contents
        .find { it.key == "isSkippable" || it.key == "is_skippable" }
        ?.value as? JsonPrimitive
        ?: return false

    // The template field backing skippability is typed `integer`, so the value can arrive as a
    // boolean, a number, or a string depending on the template and the producer.
    value.booleanOrNull?.let { return it }
    value.intOrNull?.let { return it != 0 }
    return value.contentOrNull?.trim()?.lowercase() in setOf("true", "1")
}

/**
 * Seconds before a skippable video may be skipped, as a string.
 *
 * Prefers [CreativeMetadata.skipOffsetSeconds], then falls back to the creative's content fields,
 * matching both `skipOffset` and `skip_offset` for the reason above.
 *
 * Returns a `String?` to stay source-compatible; read `creative.metadata?.skipOffsetSeconds` for a
 * typed `Int?`.
 */
fun Creative.getSkipOffset(): String? {
    metadata?.skipOffsetSeconds?.let { return it.toString() }

    return contents
        .find { it.key == "skipOffset" || it.key == "skip_offset" }
        ?.value
        ?.let { (it as? JsonPrimitive)?.contentOrNull }
}
