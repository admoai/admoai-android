package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import com.admoai.sdk.model.request.Consent

/**
 * User configuration for ad personalization and targeting.
 *
 * @property id Unique identifier for the user
 * @property ip IP address of the user
 * @property timezone User's timezone (e.g., "America/New_York")
 * @property consentData User's consent information for privacy regulations
 */
data class UserConfig(
    val id: String? = null,
    val ip: String? = null,
    val timezone: String? = null,
    val consentData: Consent? = null
) : Clearable<UserConfig> {

    /**
     * Returns a new instance with all properties set to their defaults (null).
     */
    override fun resetToDefaults(): UserConfig = UserConfig()

    /**
     * Returns a new instance with all properties set to null.
     */
    override fun clear(): UserConfig = UserConfig()
}
