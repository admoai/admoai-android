package com.admoai.sdk.model.response

import com.admoai.sdk.model.common.Error
import com.admoai.sdk.model.common.Warning
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response from an ad request to the AdMoai API.
 *
 * @property success Whether the request was successful
 * @property data Ad decisions returned by the API
 * @property errors Errors that occurred during the request
 * @property warnings Warnings generated during the request
 * @property metadata Additional response metadata
 */
@Serializable
data class DecisionResponse(
    val success: Boolean,
    val data: List<AdData>? = null,
    val errors: List<Error>? = null,
    val warnings: List<Warning>? = null,
    val metadata: JsonElement? = null
)
