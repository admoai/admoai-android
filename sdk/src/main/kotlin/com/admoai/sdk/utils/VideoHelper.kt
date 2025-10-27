package com.admoai.sdk.utils

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import android.util.Base64

/**
 * Helper methods for video ad operations.
 * 
 * These extension functions provide convenient access to video-specific
 * properties and VAST (Video Ad Serving Template) functionality.
 */

/**
 * Checks if the creative uses VAST tag delivery method.
 * @return true if delivery method is "vast_tag"
 */
fun Creative.isVastTagDelivery(): Boolean = delivery == "vast_tag"

/**
 * Checks if the creative uses VAST XML delivery method.
 * @return true if delivery method is "vast_xml"
 */
fun Creative.isVastXmlDelivery(): Boolean = delivery == "vast_xml"

/**
 * Checks if the creative uses JSON delivery method.
 * @return true if delivery method is "json"
 */
fun Creative.isJsonDelivery(): Boolean = delivery == "json"

/**
 * Gets the VAST tag URL from the creative's VAST data.
 * Optionally appends query parameters to override MediaFile attributes.
 * 
 * @param mediaType Optional MIME type for the MediaFile (e.g., "video/mp4", "application/vnd.apple.mpegurl")
 * @param mediaDelivery Optional delivery method for the MediaFile (e.g., "progressive", "streaming")
 * @return VAST tag URL with optional query parameters, or null if not available
 */
fun Creative.getVastTagUrl(mediaType: String? = null, mediaDelivery: String? = null): String? {
    val baseUrl = vast?.tagUrl ?: return null
    
    // Build query parameters if overrides are provided
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

/**
 * Gets the base64 encoded VAST XML from the creative's VAST data.
 * Optionally decodes and modifies the MediaFile type and delivery attributes.
 * 
 * @param mediaType Optional MIME type to override in the MediaFile element (e.g., "video/mp4", "application/vnd.apple.mpegurl")
 * @param mediaDelivery Optional delivery method to override in the MediaFile element (e.g., "progressive", "streaming")
 * @return Base64 encoded VAST XML (modified if overrides provided), or null if not available
 */
fun Creative.getVastXmlBase64(mediaType: String? = null, mediaDelivery: String? = null): String? {
    val base64Xml = vast?.xmlBase64 ?: return null
    
    // If no overrides, return as-is
    if (mediaType == null && mediaDelivery == null) {
        return base64Xml
    }
    
    return try {
        // Decode base64 to XML string
        val decodedBytes = Base64.decode(base64Xml, Base64.DEFAULT)
        var xmlString = String(decodedBytes, Charsets.UTF_8)
        
        // Modify MediaFile attributes using regex
        // Pattern matches <MediaFile ...> with any attributes
        val mediaFilePattern = """(<MediaFile[^>]*?)(\s+type="[^"]*")?([^>]*?)(\s+delivery="[^"]*")?([^>]*?>)""".toRegex()
        
        xmlString = mediaFilePattern.replace(xmlString) { matchResult ->
            var result = matchResult.value
            
            // Override type attribute if provided
            mediaType?.let { newType ->
                result = if (result.contains("type=")) {
                    // Replace existing type attribute
                    result.replace("""type="[^"]*"""""".toRegex(), """type="$newType"""")
                } else {
                    // Add type attribute before the closing >
                    result.replace(">", " type=\"$newType\">")
                }
            }
            
            // Override delivery attribute if provided
            mediaDelivery?.let { newDelivery ->
                result = if (result.contains("delivery=")) {
                    // Replace existing delivery attribute
                    result.replace("""delivery="[^"]*"""""".toRegex(), """delivery="$newDelivery"""")
                } else {
                    // Add delivery attribute before the closing >
                    result.replace(">", " delivery=\"$newDelivery\">")
                }
            }
            
            result
        }
        
        // Re-encode to base64
        Base64.encodeToString(xmlString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    } catch (e: Exception) {
        // If modification fails, return original
        android.util.Log.e("VideoHelper", "Failed to modify VAST XML: ${e.message}")
        base64Xml
    }
}

/**
 * Checks if the video ad is skippable based on content properties.
 * @return true if the ad has isSkippable content set to true, false otherwise
 */
fun Creative.isSkippable(): Boolean = 
    contents.find { it.key == "isSkippable" }?.value?.let { jsonElement ->
        (jsonElement as? JsonPrimitive)?.booleanOrNull
    } ?: false

/**
 * Gets the skip offset value for skippable video ads.
 * @return Skip offset value as string or null if not available
 */
fun Creative.getSkipOffset(): String? = 
    contents.find { it.key == "skipOffset" }?.value?.let { jsonElement ->
        (jsonElement as? JsonPrimitive)?.contentOrNull
    }
