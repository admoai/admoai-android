package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Similar to LocationTargetingInfo, this is likely part of the Targeting object.
// The value can be of mixed types, so JsonElement is a flexible choice.

@Serializable
data class CustomTargetingInfo(
    val key: String,
    val value: JsonElement // Using JsonElement to support various primitive types or structures
)
