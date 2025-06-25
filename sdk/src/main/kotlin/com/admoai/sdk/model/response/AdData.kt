package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Represents an ad decision for a specific placement in the response.
 *
 * Each AdData object contains the placement identifier that the decision is for
 * and a list of creative objects that represent the actual ads to be displayed.
 *
 * @property placement The placement key this ad data is for, matching the requested placement
 * @property creatives List of creative objects containing the ad content and tracking information
 */
@Serializable
data class AdData(
    val placement: String, // The placement key this ad data is for
    val creatives: List<Creative>
)
