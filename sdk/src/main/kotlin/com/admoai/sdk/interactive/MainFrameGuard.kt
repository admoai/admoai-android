package com.admoai.sdk.interactive

/**
 * Decides whether the secure form view may make a main-frame request. The form view allows exactly
 * one: a GET of its own launch URL. A page's POST form submission never reaches
 * `shouldOverrideUrlLoading`, so this is the defence that refuses it (and any second page load).
 */
internal object MainFrameGuard {

    /**
     * True only for a GET (any case) of [launchUrl] while [launchAlreadyRequested] is false. The
     * comparison ignores the fragment, the case of scheme and host, and a default `:443` on https;
     * path and query must match exactly.
     */
    fun allow(method: String, url: String, launchUrl: String, launchAlreadyRequested: Boolean): Boolean =
        method.equals("GET", ignoreCase = true) && !launchAlreadyRequested && sameAddress(url, launchUrl)

    /** True when [url] and [launchUrl] are the same address under the normalization above. */
    fun sameAddress(url: String, launchUrl: String): Boolean {
        val a = normalize(url) ?: return false
        return a == normalize(launchUrl)
    }

    private fun normalize(url: String): String? = try {
        val uri = java.net.URI(url.substringBefore('#'))
        val host = uri.host
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || host.isNullOrEmpty()) {
            null
        } else {
            val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443)) "" else ":${uri.port}"
            "$scheme://${host.lowercase()}$port${uri.rawPath.orEmpty()}?${uri.rawQuery ?: ""}"
        }
    } catch (_: Exception) {
        null
    }
}
