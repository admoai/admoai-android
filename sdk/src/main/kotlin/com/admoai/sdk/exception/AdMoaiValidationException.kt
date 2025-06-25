package com.admoai.sdk.exception

/**
 * Exception thrown when a validation error occurs within the Admoai SDK.
 * For example, if a required parameter is missing or invalid.
 */
class AdMoaiValidationException(message: String) : Exception(message)