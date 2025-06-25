package com.admoai.sdk.exception

/**
 * Base class for all AdMoai SDK exceptions.
 *
 * @param message A descriptive message for the exception.
 * @param cause The underlying cause of the exception, if any.
 */
open class AdMoaiException(message: String, cause: Throwable? = null) : Exception(message, cause)