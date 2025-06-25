package com.admoai.sdk.model.common

import kotlinx.serialization.Serializable

@Serializable
data class Error(
    val code: Int,
    val message: String
)
