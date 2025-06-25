package com.admoai.sdk.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents the format of an ad placement.
 * 
 * Currently only native format is supported, but this enum allows for future expansion.
 */
@Serializable
enum class PlacementFormat {
    @SerialName("native")
    NATIVE
}

/**
 * Represents a single ad placement in a request.
 * 
 * A placement defines where and how ads should appear in your application. Each placement
 * is identified by a unique key and can have additional configuration like format and count.
 * 
 * @property key A unique identifier for the placement (required)
 * @property count The number of ads to request for this placement (optional)
 * @property format The format of the placement (e.g., native) - marked as @Transient, not serialized
 * @property advertiserId Specific advertiser ID to request ads from (optional)
 * @property templateId Specific template ID for the creative (optional)
 */
@Serializable
data class Placement(
    val key: String,
    val count: Int? = null,
    @Transient
    val format: PlacementFormat? = PlacementFormat.NATIVE,
    val advertiserId: String? = null,
    val templateId: String? = null
)