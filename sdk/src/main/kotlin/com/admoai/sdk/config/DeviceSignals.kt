package com.admoai.sdk.config

import android.content.Context
import android.hardware.display.DisplayManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import com.admoai.sdk.model.request.DeviceCategory

/**
 * Reads the device signals the SDK sends as `device.carrier` and `device.category`, and applies
 * the one normalization both collected and hand-set carrier values go through.
 */
internal object DeviceSignals {

    /** Shortest side, in dp, from which a display is a tablet. */
    private const val TABLET_MIN_DP = 600f

    private val PLACEHOLDER_CARRIERS = setOf("--", "unknown", "wifi", "wi-fi")

    /**
     * Cleans a carrier name: trims it, and returns null for an empty value or a placeholder the
     * platform reports when there is no operator ("--", "unknown", "wifi", "wi-fi", any case).
     * Otherwise the trimmed value is returned with its case preserved.
     */
    fun normalizeCarrier(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.lowercase() in PLACEHOLDER_CARRIERS) return null
        return trimmed
    }

    /**
     * Classifies a display by its shortest side in dp, so rotation does not matter:
     * 600 dp or more is [DeviceCategory.TABLET], less is [DeviceCategory.PHONE]. A non-positive
     * dimension or density cannot be classified and gives [DeviceCategory.OTHER].
     */
    fun categoryFor(widthPx: Int, heightPx: Int, density: Float): DeviceCategory {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return DeviceCategory.OTHER
        val smallestDp = minOf(widthPx, heightPx) / density
        return if (smallestDp >= TABLET_MIN_DP) DeviceCategory.TABLET else DeviceCategory.PHONE
    }

    /**
     * Operator name of the default DATA subscription, normalized; null when unavailable.
     * Needs no permission and never requests one. If there is no default data subscription
     * the plain [TelephonyManager] is used. Any failure yields null.
     */
    fun readCarrier(context: Context): String? = try {
        val telephony = context.applicationContext.getSystemService(TelephonyManager::class.java)
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val manager = if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            telephony
        } else {
            telephony?.createForSubscriptionId(subId)
        }
        normalizeCarrier(manager?.networkOperatorName)
    } catch (_: Exception) {
        null
    }

    /**
     * Device-level category of the default display, ignoring split-screen and rotation.
     * Uses the same source as the Flutter SDK's native channel (cross-SDK ruling): the real
     * metrics of [Display.DEFAULT_DISPLAY]. A missing display or any failure yields
     * [DeviceCategory.OTHER].
     */
    fun readCategory(context: Context): DeviceCategory = try {
        val display = context.applicationContext
            .getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            DeviceCategory.OTHER
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            categoryFor(metrics.widthPixels, metrics.heightPixels, metrics.density)
        }
    } catch (_: Exception) {
        DeviceCategory.OTHER
    }
}
