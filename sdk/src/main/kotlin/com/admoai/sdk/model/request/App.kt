package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Represents application information included in ad requests.
 *
 * @property name Name of the app
 * @property version Version of the app
 * @property identifier Package name/bundle identifier of the app
 * @property buildNumber Build number of the app
 * @property language Default language of the app
 */
@Serializable
data class App(
    val name: String? = null,
    val version: String? = null,
    val identifier: String? = null,
    val buildNumber: String? = null,
    val language: String? = null
)
