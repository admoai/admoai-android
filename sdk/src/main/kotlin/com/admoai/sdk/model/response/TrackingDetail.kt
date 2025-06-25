package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Single tracking URL with an associated key.
 *
 * @property key Identifier for this tracking URL (e.g., "default", "secondary")
 * @property url Tracking URL to be fired when the event occurs
 */
@Serializable
data class TrackingDetail(
    val key: String,
    val url: String 
)
