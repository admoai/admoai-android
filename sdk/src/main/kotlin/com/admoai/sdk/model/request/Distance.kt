package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * A rectangle to search inside, in degrees.
 *
 * The engine refuses one that crosses the antimeridian, which keeps `west < east` a flat
 * invariant rather than a special case; supporting it later is additive.
 */
@Serializable
data class DistanceBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * One Sponsored Pin search: a point, exactly one shape around it, and an optional cap.
 *
 * Build it through [DecisionRequestBuilder.setDistanceTargeting], which validates before
 * anything leaves the device. The two shapes are mutually exclusive, which is why there are two
 * overloads: an ambiguous call is not writeable.
 *
 * The origin stays required for a bounds search, because "nearest first" needs somewhere to
 * measure from and the centre of a rectangle is not necessarily where the viewer is.
 *
 * @property radius Radius in **metres**. Mutually exclusive with [bounds].
 * @property limit Narrows the campaign's own cap on how many points come back; never widens it.
 */
@Serializable
data class Distance(
    val latitude: Double,
    val longitude: Double,
    val radius: Double? = null,
    val bounds: DistanceBounds? = null,
    val limit: Int? = null
)
