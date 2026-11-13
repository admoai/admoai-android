package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Represents device information included in ad requests.
 *
 * @property os Operating system name (e.g., "Android", "iOS")
 * @property osVersion Operating system version
 * @property model Device model
 * @property manufacturer Device manufacturer
 * @property id Device identifier
 * @property timezone Device timezone
 * @property language Device language
 * @property carrier Mobile network operator name; absent from the request when unknown
 * @property category Device category, one of "phone" | "tablet" | "other" (see [DeviceCategory.wireValue])
 */
@Serializable
data class Device(
    val os: String? = null,
    val osVersion: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val id: String? = null,
    val timezone: String? = null,
    val language: String? = null,
    val carrier: String? = null,
    val category: String? = null
) {
    // Binary compatibility (CONTRIBUTING, "When the API check fails"): a request model integrators
    // may construct, so the 1.8.x 7-field constructor and copy stay in the bytecode. HIDDEN keeps
    // them out of source resolution.
    @Deprecated("Binary compatibility with 1.8.x callers", level = DeprecationLevel.HIDDEN)
    constructor(
        os: String? = null,
        osVersion: String? = null,
        model: String? = null,
        manufacturer: String? = null,
        id: String? = null,
        timezone: String? = null,
        language: String? = null
    ) : this(os, osVersion, model, manufacturer, id, timezone, language, null, null)

    @Deprecated("Binary compatibility with 1.8.x callers", level = DeprecationLevel.HIDDEN)
    fun copy(
        os: String? = this.os,
        osVersion: String? = this.osVersion,
        model: String? = this.model,
        manufacturer: String? = this.manufacturer,
        id: String? = this.id,
        timezone: String? = this.timezone,
        language: String? = this.language
    ): Device = Device(os, osVersion, model, manufacturer, id, timezone, language, carrier, category)
}
