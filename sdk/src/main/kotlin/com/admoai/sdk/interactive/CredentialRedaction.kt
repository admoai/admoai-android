package com.admoai.sdk.interactive

/** Removes Lead Generation credentials from text the SDK may print, such as HTTP logs. */
internal object CredentialRedaction {
    private val credential = Regex(
        "\"(preparationToken|submissionToken|recoveryToken)\"(\\s*:\\s*)\"((?:[^\"\\\\]|\\\\.)*)\""
    )

    fun redact(text: String): String =
        credential.replace(text) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}\"REDACTED\"" }
}
