package com.admoai.sdk.model.response

import com.admoai.sdk.model.common.Error
import com.admoai.sdk.model.common.Warning
import com.admoai.sdk.serialization.AdDataListSerializer
import com.admoai.sdk.serialization.ErrorListSerializer
import com.admoai.sdk.serialization.WarningListSerializer
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
    val success: Boolean = false,
    @Serializable(with = AdDataListSerializer::class)
    val data: List<AdData>? = null,
    @Serializable(with = ErrorListSerializer::class)
    val errors: List<Error>? = null,
    @Serializable(with = WarningListSerializer::class)
    val warnings: List<Warning>? = null,
    val metadata: JsonElement? = null
)
