package com.admoai.sdk.model.request

/**
 * Broad class of the device, sent as `device.category` in a decision request.
 *
 * Reported, not targetable, in this version: an advertiser cannot target it yet.
 *
 * @property wireValue The exact string put on the wire: "phone", "tablet" or "other".
 */
enum class DeviceCategory(val wireValue: String) {
    /** A handheld device whose display is under 600 dp on its shortest side. */
    PHONE("phone"),

    /** A handheld device whose display is 600 dp or more on its shortest side. */
    TABLET("tablet"),

    /**
     * Known non-handheld hardware: a TV, a watch, a car head unit or a PC. Never "unknown": a category
     * the SDK cannot read is left out of the request.
     */
    OTHER("other")
}
