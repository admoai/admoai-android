package com.admoai.sdk.model.common

import kotlinx.serialization.Serializable

@Serializable
data class Warning(
    val code: Int,
    val message: String
)
