package com.admoai.sdk.utils

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
