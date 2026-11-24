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

    /**
     * A URL as log text shows it: everything up to whitespace, a double quote, a backslash or an
     * angle bracket. The one backslash sequence it keeps is the JSON escape of an ampersand
     * (backslash, `u0026`, any hex case), which the engine writes between query parameters. Apostrophes are legal in a path and in a query, so they stay inside; only a
     * final one is treated as the quote that closes a URL written between apostrophes.
     */
    private val httpUrl = Regex("https?://(?:[^\\s\"\\\\<>]|\\\\u0026)+", RegexOption.IGNORE_CASE)

    fun redact(text: String): String {
        val withoutCredentials =
            credential.replace(text) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}\"REDACTED\"" }
        return httpUrl.replace(withoutCredentials) { m ->
            val url = m.value
            if (url.endsWith("'")) redactQuery(url.dropLast(1)) + "'" else redactQuery(url)
        }
    }

    private val separator = Regex("&|\\\\u0026")

    /** Splits on `&` and on its JSON escape, keeping each separator exactly as written. */
    private fun String.redactParts(): String {
        val out = StringBuilder()
        var from = 0
        for (m in separator.findAll(this)) {
            out.append(substring(from, m.range.first).redactPart()).append(m.value)
            from = m.range.last + 1
        }
        return out.append(substring(from).redactPart()).toString()
    }

    private fun String.redactPart(): String {
        val eq = indexOf('=')
        return when {
            isEmpty() -> this
            eq < 0 -> "REDACTED"
            else -> substring(0, eq) + "=REDACTED"
        }
    }

    /**
     * Keeps the address, each parameter's name and the fragment; every value goes, including one
     * after an empty name, and a part with no `=` is replaced whole.
     */
    private fun redactQuery(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val rest = url.substring(queryStart + 1)
        val fragmentStart = rest.indexOf('#').let { if (it < 0) rest.length else it }
        val redacted = rest.substring(0, fragmentStart).redactParts()
        return url.substring(0, queryStart + 1) + redacted + rest.substring(fragmentStart)
    }
}
