package com.admoai.sdk.utils

import com.admoai.sdk.model.response.Creative
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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
 * @return VAST tag URL or null if not available
 */
fun Creative.getVastTagUrl(): String? = vast?.tagUrl

/**
 * Gets the base64 encoded VAST XML from the creative's VAST data.
 * @return Base64 encoded VAST XML or null if not available
 */
fun Creative.getVastXmlBase64(): String? = vast?.xmlBase64

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
