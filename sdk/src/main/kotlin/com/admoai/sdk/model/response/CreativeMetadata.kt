package com.admoai.sdk.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MetadataPriority {
    @SerialName("sponsorship")
    SPONSORSHIP,
    @SerialName("standard")
    STANDARD,
    @SerialName("house")
    HOUSE
}

@Serializable
data class CreativeMetadata(
    val adId: String,
    val creativeId: String,
    val advertiserId: String? = null,
    val placementId: String,
    val templateId: String,
    val priority: MetadataPriority,
    val language: String? = null,
    val style: String? = null,
    // Video-specific metadata (2025-11-01+)
    val format: String? = null,
    val duration: Int? = null,
    val aspectRatio: String? = null,
    val isSkippable: Boolean? = null
)
