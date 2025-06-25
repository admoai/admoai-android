package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * User consent information for privacy regulations.
 *
 * @property gdpr GDPR consent status (null=unspecified, true=granted, false=denied)
 */
@Serializable
data class Consent(
    val gdpr: Boolean? = null // Defaulting to null, can be false if API implies non-presence means false
)
