package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Tracking URLs for creative events.
 *
 * @property impressions Impression tracking URLs with named keys
 * @property clicks Click tracking URLs with named keys
 * @property custom Custom event tracking URLs with named keys
 * @property videoEvents Video-specific event tracking URLs with named keys
 */
@Serializable
data class TrackingInfo(
    val impressions: List<TrackingDetail>? = null,
    val clicks: List<TrackingDetail>? = null,
    val custom: List<TrackingDetail>? = null,
    val videoEvents: List<TrackingDetail>? = null
)
