package com.admoai.sdk.config

import android.content.Context
import android.os.Build
import com.admoai.sdk.core.Clearable
import com.admoai.sdk.model.request.DeviceCategory
import java.util.TimeZone

/**
 * Configuration related to the user's device.
 * Contains information about the device that can be used for tracking and targeting purposes.
 *
 * @property model The device model name (e.g., "Pixel 6")
 * @property osName The operating system name (e.g., "Android")
 * @property osVersion The operating system version (e.g., "13")
 * @property timezone The device's timezone (e.g., "America/New_York")
 * @property deviceId A unique identifier for the device (e.g., Advertising ID)
 * @property manufacturer The device manufacturer (e.g., "Google")
 * @property language The device language (e.g., "en-US")
 * @property carrier The mobile network operator name (e.g., "Movistar"); omitted from the request when null.
 *   [systemDefault] leaves it null; the publisher supplies it.
 * @property category The device category ([DeviceCategory.PHONE], [DeviceCategory.TABLET] or
 *   [DeviceCategory.OTHER]); omitted from the request when null. [systemDefault] leaves it null.
 */
data class DeviceConfig(
    val model: String? = null,
    val osName: String? = null, // e.g., "Android"
    val osVersion: String? = null, // e.g., "13"
    val timezone: String? = null, // e.g., "Europe/London"
    val deviceId: String? = null, // e.g., Advertising ID
    val manufacturer: String? = null, // e.g., "Google", "Samsung"
    // e.g., "en-US"
    val language: String? = null,
    val carrier: String? = null,
    val category: DeviceCategory? = null
) : Clearable<DeviceConfig> {

    // Binary compatibility (CONTRIBUTING, "When the API check fails"): integrators construct and
    // copy this class, so the 7-field constructor and copy that 1.8.x apps were compiled against
    // must keep existing. HIDDEN keeps them in the bytecode but out of source resolution, so new
    // code always binds to the primary constructor and the generated copy.
    @Deprecated("Binary compatibility with 1.8.x callers", level = DeprecationLevel.HIDDEN)
    constructor(
        model: String? = null,
        osName: String? = null,
        osVersion: String? = null,
        timezone: String? = null,
        deviceId: String? = null,
        manufacturer: String? = null,
        language: String? = null
    ) : this(model, osName, osVersion, timezone, deviceId, manufacturer, language, null, null)

    @Deprecated("Binary compatibility with 1.8.x callers", level = DeprecationLevel.HIDDEN)
    fun copy(
        model: String? = this.model,
        osName: String? = this.osName,
        osVersion: String? = this.osVersion,
        timezone: String? = this.timezone,
        deviceId: String? = this.deviceId,
        manufacturer: String? = this.manufacturer,
        language: String? = this.language
    ): DeviceConfig = DeviceConfig(
        model, osName, osVersion, timezone, deviceId, manufacturer, language, carrier, category
    )

    /**
     * Returns a new instance with all properties set to their defaults (null).
     */
    override fun resetToDefaults(): DeviceConfig = DeviceConfig()

    /**
     * Returns a new instance with all properties set to null.
     */
    override fun clear(): DeviceConfig = DeviceConfig()

    companion object {
        /**
         * Creates a DeviceConfig with system default values automatically populated from the device.
         *
         * This factory method automatically retrieves device information including:
         * - Device model (from Build.MODEL)
         * - OS name (set to "Android")
         * - OS version (from Build.VERSION.RELEASE)
         * - Current timezone (from TimeZone.getDefault().id)
         *
         * @param androidId Optional unique identifier for the device. If not provided, deviceId will be null
         * @return A DeviceConfig instance populated with system information
         */
        @JvmStatic
        fun systemDefault(androidId: String? = null): DeviceConfig = DeviceConfig(
            model = Build.MODEL,
            osName = "Android",
            osVersion = Build.VERSION.RELEASE,
            timezone = TimeZone.getDefault().id,
            deviceId = androidId,
            manufacturer = Build.MANUFACTURER,
            language = java.util.Locale.getDefault().toLanguageTag()
        )
    }
}
