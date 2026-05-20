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

fun TrackingInfo.getImpressionUrl(key: String = "default"): String? =
    impressions?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getClickUrl(key: String = "default"): String? =
    clicks?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getCustomUrl(key: String): String? =
    custom?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getVideoEventUrl(key: String): String? =
    videoEvents?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getTrackingUrl(type: TrackingType, key: String): String? = when (type) {
    TrackingType.IMPRESSION -> getImpressionUrl(key)
    TrackingType.CLICK -> getClickUrl(key)
    TrackingType.CUSTOM -> getCustomUrl(key)
    TrackingType.VIDEO_EVENT -> getVideoEventUrl(key)
}

fun TrackingInfo.hasTrackingFor(type: TrackingType, key: String): Boolean =
    getTrackingUrl(type, key) != null
