package com.admoai.sdk.interactive

/**
 * Removes Lead Generation credentials from text the SDK may print, such as HTTP logs: the three
 * form tokens, and every query value of every URL (its scheme, host, path and keys stay, so the
 * line shows the address without the opaque token). Form event URLs may sit on any path.
 */
internal object CredentialRedaction {
    private val names = listOf("preparationToken", "submissionToken", "recoveryToken")

    /** A member name as JSON may write it: each character literal or as a `\uXXXX` escape. */
    private fun escapable(name: String): String = name.map { c ->
        "(?:${Regex.escape(c.toString())}|\\\\u(?i:${"%04x".format(c.code)}))"
    }.joinToString("")

    private val credential = Regex(
        "\"(${names.joinToString("|") { escapable(it) }})\"(\\s*:\\s*)\"((?:[^\"\\\\]|\\\\.)*)\""
    )

    private val httpUrl = Regex("https?://[^\\s\"'\\\\<>]+")

    private val queryValue = Regex("([^&=?#]+)=([^&#]*)")

    fun redact(text: String): String {
        val withoutCredentials =
            credential.replace(text) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}\"REDACTED\"" }
        return httpUrl.replace(withoutCredentials) { m -> redactQuery(m.value) }
    }

    private fun redactQuery(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val rest = url.substring(queryStart + 1)
        val fragmentStart = rest.indexOf('#').let { if (it < 0) rest.length else it }
        val query = rest.substring(0, fragmentStart)
        val redacted = queryValue.replace(query) { m -> "${m.groupValues[1]}=REDACTED" }
        return url.substring(0, queryStart + 1) + redacted + rest.substring(fragmentStart)
    }
}
