package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.CreativeListSerializer
import kotlinx.serialization.Serializable

/**
 * Represents an ad decision for a specific placement in the response.
 *
 * Each AdData object contains the placement identifier that the decision is for
 * and a list of creative objects that represent the actual ads to be displayed.
 *
 * @property placement The placement key this ad data is for, matching the requested placement
 * @property creatives List of creative objects containing the ad content and tracking information.
 *   Stays a non-null list: the engine's ordinary no-fill (`"creatives": null`) and the takeover
 *   `[]` both decode to an empty list (never a crash), and malformed entries are dropped.
 */
@Serializable
data class AdData(
    val placement: String = "", // The placement key this ad data is for
    @Serializable(with = CreativeListSerializer::class)
    val creatives: List<Creative> = emptyList()
)

/** True when this placement returned at least one creative. */
fun AdData.hasCreative(): Boolean = creatives.isNotEmpty()

/**
 * True when this placement returned no ad. Treats `[]`, `null`, and absent creatives uniformly —
 * the SDK never distinguishes takeover-protected no-ad from ordinary no-fill (the engine emits no
 * reliable signal) and never substitutes a local ad.
 */
fun AdData.isNoAd(): Boolean = creatives.isEmpty()
