package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * User-specific information for ad targeting and personalization.
 *
 * @property id Unique identifier for the user
 * @property ip IP address of the user
 * @property timezone User's timezone (e.g., "America/New_York")
 * @property consent User's consent information for privacy regulations
 */
@Serializable
data class User(
    val id: String? = null,
    val ip: String? = null,
    val timezone: String? = null,
    val consent: Consent? = null
)
