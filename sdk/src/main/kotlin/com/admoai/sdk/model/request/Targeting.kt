package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Targeting parameters for ad requests.
 *
 * @property geo Geographic IDs to target (e.g., country or city identifiers)
 * @property location Precise location coordinates for location-based targeting
 * @property destination Predicted destination coordinates with minimum confidence thresholds for targeting
 * @property custom Custom targeting parameters as key-value pairs
 * @property distance The Sponsored Pin search (Sponsored Pin Locations, epic #3138). Additive:
 *   a request that omits it behaves exactly as it did before.
 *
 *   This is **not** [location], which it resembles on the wire and differs from entirely.
 *   `location` is where the viewer is, matched against a fence the advertiser drew, and it
 *   decides whether an Ad may serve at all. `distance` decides which pins a serving creative
 *   carries, and filters no candidate by itself. They compose, and neither implies the other.
 */
@Serializable
data class Targeting(
    val geo: List<Int>? = null, // Assuming geo targeting is by a list of IDs
    val location: List<LocationTargetingInfo>? = null,
    val destination: List<DestinationTargetingInfo>? = null,
    val custom: List<CustomTargetingInfo>? = null,
    val distance: Distance? = null
)