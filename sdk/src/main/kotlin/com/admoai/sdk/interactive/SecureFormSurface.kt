package com.admoai.sdk.interactive

import androidx.webkit.WebViewFeature

/**
 * Whether this device's WebView can host the Lead Generation form safely. The form needs a message
 * listener scoped to the form's origin ([WebViewFeature.WEB_MESSAGE_LISTENER]); without it the SDK
 * does not declare support. There is no fallback to a broadly exposed JavaScript interface, and the
 * minimum OS is unchanged.
 */
internal object SecureFormSurface {
    @Volatile
    private var cached: Boolean? = null

    fun isAvailable(): Boolean {
        cached?.let { return it }
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER).also { cached = it }
        } catch (_: Throwable) {
            false
        }
    }
}
