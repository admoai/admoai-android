package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Targeting parameters for ad requests.
 *
 * @property geo Geographic IDs to target (e.g., country or city identifiers)
 * @property location Precise location coordinates for location-based targeting
 * @property custom Custom targeting parameters as key-value pairs
 */
@Serializable
data class Targeting(
    val geo: List<Int>? = null, // Assuming geo targeting is by a list of IDs
    val location: List<LocationTargetingInfo>? = null,
    val custom: List<CustomTargetingInfo>? = null
)