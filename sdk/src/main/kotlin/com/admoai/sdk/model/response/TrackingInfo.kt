package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.ThirdPartyTrackerListSerializer
import com.admoai.sdk.serialization.TrackingDetailListSerializer
import kotlinx.serialization.Serializable

/**
 * Tracking URLs for creative events.
 *
 * @property impressions Impression tracking URLs with named keys
 * @property clicks Click tracking URLs with named keys
 * @property custom Custom event tracking URLs with named keys
 * @property videoEvents Video-specific event tracking URLs with named keys
 * @property completions Journey completion beacons (populated only for custom_event completion deals)
 * @property thirdPartyTrackers Third-party event trackers (agency ad servers such as CM360),
 *   served additively under `X-Decision-Version: 2025-11-01` and absent otherwise (the engine
 *   never sends `[]`). `fireImpression`/`fireClick` fan these out automatically through a
 *   credential-isolated dispatcher — publishers never fire them by hand. Not addressed by key,
 *   so deliberately outside [TrackingType]/[getTrackingUrl]: entries are matched by event
 *   semantics (`eventType`/`matchType`/`eventKey`).
 */
@Serializable
data class TrackingInfo(
    @Serializable(with = TrackingDetailListSerializer::class)
    val impressions: List<TrackingDetail>? = null,
    @Serializable(with = TrackingDetailListSerializer::class)
    val clicks: List<TrackingDetail>? = null,
    @Serializable(with = TrackingDetailListSerializer::class)
    val custom: List<TrackingDetail>? = null,
    @Serializable(with = TrackingDetailListSerializer::class)
    val videoEvents: List<TrackingDetail>? = null,
    @Serializable(with = TrackingDetailListSerializer::class)
    val completions: List<TrackingDetail>? = null,
    @Serializable(with = ThirdPartyTrackerListSerializer::class)
    val thirdPartyTrackers: List<ThirdPartyTracker>? = null
)

fun TrackingInfo.getImpressionUrl(key: String = "default"): String? =
    impressions?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getClickUrl(key: String = "default"): String? =
    clicks?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getCustomUrl(key: String): String? =
    custom?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getVideoEventUrl(key: String): String? =
    videoEvents?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getCompletionUrl(key: String): String? =
    completions?.firstOrNull { it.key == key }?.url

fun TrackingInfo.getTrackingUrl(type: TrackingType, key: String): String? = when (type) {
    TrackingType.IMPRESSION -> getImpressionUrl(key)
    TrackingType.CLICK -> getClickUrl(key)
    TrackingType.CUSTOM -> getCustomUrl(key)
    TrackingType.VIDEO_EVENT -> getVideoEventUrl(key)
    TrackingType.COMPLETION -> getCompletionUrl(key)
}

fun TrackingInfo.hasTrackingFor(type: TrackingType, key: String): Boolean =
    getTrackingUrl(type, key) != null
