package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Request for ad decisions from the AdMoai API.
 *
 * @property placements Ad placements to request decisions for
 * @property targeting Targeting parameters for the request
 * @property user User information for personalization
 * @property app Application information
 * @property device Device information
 * @property collectAppData Controls automatic collection of app data (not serialized)
 * @property collectDeviceData Controls automatic collection of device data (not serialized)
 *
 * @see DecisionRequestBuilder
 */
@Serializable
data class DecisionRequest(
    val placements: List<Placement>,
    val targeting: Targeting? = null,
    val user: User? = null,
    val app: App? = null,
    val device: Device? = null,
    @Transient
    val collectAppData: Boolean = true,
    @Transient
    val collectDeviceData: Boolean = true
)
