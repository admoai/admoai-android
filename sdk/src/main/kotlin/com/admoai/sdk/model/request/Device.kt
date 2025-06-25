package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * Represents device information included in ad requests.
 *
 * @property os Operating system name (e.g., "Android", "iOS") 
 * @property osVersion Operating system version
 * @property model Device model
 * @property manufacturer Device manufacturer
 * @property id Device identifier
 * @property timezone Device timezone
 * @property language Device language
 */
@Serializable
data class Device(
    val os: String? = null,
    val osVersion: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val id: String? = null,
    val timezone: String? = null,
    val language: String? = null
)
