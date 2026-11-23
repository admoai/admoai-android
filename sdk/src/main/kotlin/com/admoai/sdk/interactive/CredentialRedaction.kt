package com.admoai.sdk.interactive

/** Removes Lead Generation credentials from text the SDK may print, such as HTTP logs. */
internal object CredentialRedaction {
    private val names = listOf("preparationToken", "submissionToken", "recoveryToken")

    /** A member name as JSON may write it: each character literal or as a `\uXXXX` escape. */
    private fun escapable(name: String): String = name.map { c ->
        "(?:${Regex.escape(c.toString())}|\\\\u(?i:${"%04x".format(c.code)}))"
    }.joinToString("")

    private val credential = Regex(
        "\"(${names.joinToString("|") { escapable(it) }})\"(\\s*:\\s*)\"((?:[^\"\\\\]|\\\\.)*)\""
    )

    fun redact(text: String): String =
        credential.replace(text) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}\"REDACTED\"" }
}
