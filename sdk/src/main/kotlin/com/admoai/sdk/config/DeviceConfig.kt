package com.admoai.sdk.config

import com.admoai.sdk.core.Clearable
import android.content.Context
import android.os.Build
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
 */
data class DeviceConfig(
    val model: String? = null,
    val osName: String? = null, // e.g., "Android"
    val osVersion: String? = null, // e.g., "13"
    val timezone: String? = null, // e.g., "Europe/London"
    val deviceId: String? = null, // e.g., Advertising ID
    val manufacturer: String? = null, // e.g., "Google", "Samsung"
    val language: String? = null // e.g., "en-US"
) : Clearable<DeviceConfig> {

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
        fun systemDefault(androidId: String? = null): DeviceConfig {
            return DeviceConfig(
                model = Build.MODEL,
                osName = "Android",
                osVersion = Build.VERSION.RELEASE,
                timezone = TimeZone.getDefault().id,
                deviceId = androidId
            )
        }
    }
}
