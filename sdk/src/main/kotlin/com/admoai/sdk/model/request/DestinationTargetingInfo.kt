package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Represents destination targeting information for ad requests.
 *
 * Destination targeting allows you to target users based on predicted destinations
 * with a minimum confidence threshold.
 *
 * Serialized as `minConfidence`. The engine's canonical key is camelCase like every other field on
 * the request contract; `min_confidence` survives only as a back-compat alias kept for already
 * fielded SDKs, and camelCase wins when both are present. This SDK previously emitted the alias.
 *
 * @property latitude The latitude coordinate of the destination (-90.0 to 90.0)
 * @property longitude The longitude coordinate of the destination (-180.0 to 180.0)
 * @property minConfidence The minimum confidence level required for the destination prediction (0.0 to 1.0)
 */
@Serializable
data class DestinationTargetingInfo(
    val latitude: Double,
    val longitude: Double,
    val minConfidence: Double
)
