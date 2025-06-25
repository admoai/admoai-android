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
    val advertiserId: String? = null, // Added as per OpenAPI spec
    val placementId: String,
    val templateId: String,
    val priority: MetadataPriority,
    val language: String? = null, // Added as per OpenAPI spec
    val style: String? = null // e.g., "default", "verticalComposite"
)
