package com.admoai.sdk.config

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
     * dimension or density cannot be classified and gives null (no category is sent).
     */
    fun categoryFor(widthPx: Int, heightPx: Int, density: Float): DeviceCategory? {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return null
        val smallestDp = minOf(widthPx, heightPx) / density
        return if (smallestDp >= TABLET_MIN_DP) DeviceCategory.TABLET else DeviceCategory.PHONE
    }

    /**
     * Operator name of the default DATA subscription, normalized. Needs no permission and never
     * requests one. Omitted (null) when the device has no telephony feature, when there is no
     * valid default data subscription, or when the name is not reliable (empty or a placeholder).
     * It never falls back to the default voice SIM, an arbitrary subscription, or the home-SIM
     * name. Any failure yields null.
     */
    fun readCarrier(context: Context): String? = try {
        val app = context.applicationContext
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            null
        } else {
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                null
            } else {
                val telephony = app.getSystemService(TelephonyManager::class.java)
                normalizeCarrier(telephony?.createForSubscriptionId(subId)?.networkOperatorName)
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * True when the hardware is reliably a non-handheld class: TV ([PackageManager.FEATURE_LEANBACK]),
     * watch, automotive or desktop PC, or a UI mode type of television or watch. Car and desk UI
     * modes deliberately do not count: they can be a phone docked in a car or on a desk, and a
     * phone in car mode is still a phone.
     *
     * @param uiModeType The current UI mode type, or null when it cannot be read.
     */
    internal fun isNonHandheld(uiModeType: Int? = null, hasFeature: (String) -> Boolean): Boolean {
        if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || uiModeType == Configuration.UI_MODE_TYPE_WATCH) {
            return true
        }
        return listOf(
            PackageManager.FEATURE_LEANBACK,
            PackageManager.FEATURE_WATCH,
            PackageManager.FEATURE_AUTOMOTIVE,
            PackageManager.FEATURE_PC
        ).any(hasFeature)
    }

    /**
     * Device-level category of the device, ignoring split-screen and rotation. Known non-handheld
     * hardware (TV, watch, automotive, desktop) is [DeviceCategory.OTHER] whatever its screen
     * size; otherwise the real metrics of [Display.DEFAULT_DISPLAY] decide (the same source as
     * the Flutter SDK's native channel, cross-SDK ruling). A failing hardware check does not
     * decide: the display rule applies. A missing or unreadable display yields null: no category
     * is sent rather than a guess.
     */
    fun readCategory(context: Context): DeviceCategory? = try {
        val app = context.applicationContext
        val nonHandheld = try {
            val uiMode = try {
                app.getSystemService(UiModeManager::class.java)?.currentModeType
            } catch (_: Exception) {
                null
            }
            isNonHandheld(uiMode) { app.packageManager.hasSystemFeature(it) }
        } catch (_: Exception) {
            false
        }
        if (nonHandheld) {
            DeviceCategory.OTHER
        } else {
            val display = app
                .getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display == null) {
                null
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                categoryFor(metrics.widthPixels, metrics.heightPixels, metrics.density)
            }
        }
    } catch (_: Exception) {
        null
    }
}
