package com.admoai.sdk.model.request

import com.admoai.sdk.model.common.JourneyOpt
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
 * @property sessionId Publisher-provided Journey session identifier (top-level, camelCase). Store the
 *   normalized wire form (trimmed; blank → null). Requires `apiVersion` to be set to take effect.
 * @property journeyOpt Journey opt-in/opt-out for this session; emitted only as `"in"`/`"out"`.
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
    val sessionId: String? = null,
    val journeyOpt: JourneyOpt? = null,
    @Transient
    val collectAppData: Boolean = true,
    @Transient
    val collectDeviceData: Boolean = true
)
