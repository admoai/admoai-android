package com.admoai.sdk.exception

/**
 * Exception for network-related errors during AdMoai SDK operations.
 *
 * @param message A descriptive message for the exception.
 * @param cause The underlying cause of the exception, if any.
 * @param statusCode The HTTP status code, if available.
 */
class AdMoaiNetworkException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null // Added statusCode
) : AdMoaiException(message, cause)
