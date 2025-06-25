package com.admoai.sdk.exception

/**
 * Exception indicating an error related to SDK configuration.
 * For example, if a required configuration parameter is missing or invalid.
 *
 * @param message A descriptive message for the exception.
 */
class AdMoaiConfigurationException(message: String) : AdMoaiException(message)