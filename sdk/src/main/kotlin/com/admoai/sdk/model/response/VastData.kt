package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * VAST (Video Ad Serving Template) data for video advertisements.
 *
 * @property tagUrl URL to the VAST XML tag
 * @property xmlBase64 Base64 encoded VAST XML content
 */
@Serializable
data class VastData(
    val tagUrl: String? = null,
    val xmlBase64: String? = null
)
