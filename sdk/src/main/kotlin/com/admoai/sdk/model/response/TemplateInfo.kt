package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Represents template information for a creative.
 *
 * @property key The template key (e.g., "wideWithCompanion")
 * @property style The style variation of the template (e.g., "imageLeft", "wideImageOnly")
 */
@Serializable
data class TemplateInfo(
    val key: String,
    val style: String? = null
)
