package com.admoai.sdk.exception

/**
 * Exception indicating an error related to data processing,
 * such as parsing issues or unexpected data format from the API.
 *
 * @param message A descriptive message for the exception.
 * @param cause The underlying cause of the exception (e.g., serialization exception).
 */
class AdMoaiDataException(
    message: String,
    cause: Throwable? = null
) : AdMoaiException(message, cause)