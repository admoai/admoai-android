package com.admoai.sdk.exception

import com.admoai.sdk.model.common.Error

/**
 * Exception thrown when the decision-engine rejects a request with a validation error (HTTP 422),
 * or when the SDK rejects a parameter before a request is made.
 *
 * [errors] carries the engine's parsed `errors[]` envelope so a caller can branch on the specific
 * code instead of string-matching the message. It is empty for SDK-side validation failures, and
 * for a 422 whose body could not be parsed.
 *
 * This now extends [AdMoaiException] like every other SDK exception. It previously extended
 * `Exception` directly, so `catch (e: AdMoaiException)` silently did not catch it.
 */
class AdMoaiValidationException(
    message: String,
    val errors: List<Error> = emptyList()
) : AdMoaiException(message) {

    /**
     * Builds an exception from the engine's parsed error envelope, formatting code/message pairs
     * into a readable summary. Mirrors the iOS `APIError.validationError` and Flutter
     * `ValidationError` message shape, so the same engine rejection reads the same on all three
     * platforms in a support ticket.
     */
    constructor(errors: List<Error>) : this(formatErrors(errors), errors)

    private companion object {
        fun formatErrors(errors: List<Error>): String =
            if (errors.isEmpty()) {
                "Validation error: Unknown"
            } else {
                "Validation errors:\n" + errors.joinToString("\n") { "[${it.code}] ${it.message}" }
            }
    }
}
