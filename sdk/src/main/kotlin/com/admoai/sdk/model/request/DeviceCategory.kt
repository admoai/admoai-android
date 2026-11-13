package com.admoai.sdk.model.request

/**
 * Broad class of the device, sent as `device.category` in a decision request.
 *
 * @property wireValue The exact string put on the wire: "phone", "tablet" or "other".
 */
enum class DeviceCategory(val wireValue: String) {
    /** A handset. */
    PHONE("phone"),

    /** A tablet. */
    TABLET("tablet"),

    /** Anything else (TV, wearable, foldable-as-desktop, unknown). */
    OTHER("other")
}
