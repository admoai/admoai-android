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
 * @property interactiveExperience Lead Generation support this request declares. Set by the SDK from
 *   the app's opt-in, never by hand (a value set here is replaced when the request is sent)
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
    val collectDeviceData: Boolean = true,
    val interactiveExperience: InteractiveExperienceCapability? = null
) {

    // Binary compatibility: integrators construct and copy this class, so the 9-field constructor
    // and copy that apps compiled before Lead Generation link against must keep existing. HIDDEN
    // keeps them in the bytecode but out of source resolution, so new code always binds to the
    // primary constructor and the generated copy.
    @Deprecated("Binary compatibility with callers compiled before Lead Generation", level = DeprecationLevel.HIDDEN)
    constructor(
        placements: List<Placement>,
        targeting: Targeting? = null,
        user: User? = null,
        app: App? = null,
        device: Device? = null,
        sessionId: String? = null,
        journeyOpt: JourneyOpt? = null,
        collectAppData: Boolean = true,
        collectDeviceData: Boolean = true
    ) : this(
        placements, targeting, user, app, device, sessionId, journeyOpt, collectAppData, collectDeviceData, null
    )

    @Deprecated("Binary compatibility with callers compiled before Lead Generation", level = DeprecationLevel.HIDDEN)
    fun copy(
        placements: List<Placement> = this.placements,
        targeting: Targeting? = this.targeting,
        user: User? = this.user,
        app: App? = this.app,
        device: Device? = this.device,
        sessionId: String? = this.sessionId,
        journeyOpt: JourneyOpt? = this.journeyOpt,
        collectAppData: Boolean = this.collectAppData,
        collectDeviceData: Boolean = this.collectDeviceData
    ): DecisionRequest = DecisionRequest(
        placements, targeting, user, app, device, sessionId, journeyOpt, collectAppData, collectDeviceData,
        interactiveExperience
    )
}
