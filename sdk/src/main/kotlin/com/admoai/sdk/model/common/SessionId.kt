package com.admoai.sdk.model.common

/** Maximum `sessionId` length the engine accepts, in UTF-8 BYTES (not runes). */
internal const val MAX_SESSION_ID_BYTES = 256

/**
 * Normalizes a raw `sessionId` to its wire form: trims whitespace, and maps blank → null.
 *
 * The trimmed value is sent untruncated even when it exceeds the limit — the SDK never truncates
 * (a truncated id could collide with a different valid session); the engine treats over-length as
 * absent.
 */
internal fun normalizeSessionId(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

/**
 * PII-safe rejection reason for a `sessionId`, or null if acceptable. Returns only a diagnostic
 * token — never the value itself. Tokens match iOS/Flutter for cross-SDK parity.
 */
internal fun sessionIdRejectionReason(raw: String?): String? {
    if (raw == null) return null
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> "blank_after_trim"
        trimmed.toByteArray(Charsets.UTF_8).size > MAX_SESSION_ID_BYTES -> "exceeds_256_bytes"
        else -> null
    }
}
