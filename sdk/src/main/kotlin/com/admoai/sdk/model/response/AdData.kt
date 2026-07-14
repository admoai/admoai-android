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
 * @property creatives Non-null list; no-fill (`null`) and takeover (`[]`) both decode to empty.
 */
@Serializable
data class AdData(
    val placement: String = "", // The placement key this ad data is for
    @Serializable(with = CreativeListSerializer::class)
    val creatives: List<Creative> = emptyList()
)

/** True when this placement returned at least one creative. */
fun AdData.hasCreative(): Boolean = creatives.isNotEmpty()

/** True when this placement returned no ad (`[]`, `null`, and absent treated uniformly). */
fun AdData.isNoAd(): Boolean = creatives.isEmpty()
