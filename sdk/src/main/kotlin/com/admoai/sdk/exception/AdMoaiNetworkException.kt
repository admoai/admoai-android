package com.admoai.sdk.exception

import com.admoai.sdk.model.common.Error

/**
 * Exception for network-related errors during AdMoai SDK operations.
 *
 * @param message A descriptive message for the exception.
 * @param cause The underlying cause of the exception, if any.
 * @param statusCode The HTTP status code, if available.
 * @param errors The engine's error codes and messages, read from the JSON error envelope of a 4xx
 *   response: for example `10009` on the 400 a workspace without Audience Signals answers to a
 *   device carrier or category. Empty when there was no response, or its body could not be read.
 *   A 422 raises [AdMoaiValidationException] instead, which carries the same `errors`.
 */
class AdMoaiNetworkException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
    val errors: List<Error> = emptyList()
) : AdMoaiException(message, cause) {

    // Binary compatibility (CONTRIBUTING, "When the API check fails"): the 3-parameter constructor
    // and its default-arguments form that 1.8.x callers were compiled against keep existing.
    @Deprecated("Binary compatibility with 1.8.x callers", level = DeprecationLevel.HIDDEN)
    constructor(message: String, cause: Throwable? = null, statusCode: Int? = null) :
        this(message, cause, statusCode, emptyList())
}
