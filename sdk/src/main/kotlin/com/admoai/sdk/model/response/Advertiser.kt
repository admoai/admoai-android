package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

@Serializable
data class Advertiser(
    val id: String? = null,
    val name: String? = null,
    val legalName: String? = null,
    val logoUrl: String? = null
)
