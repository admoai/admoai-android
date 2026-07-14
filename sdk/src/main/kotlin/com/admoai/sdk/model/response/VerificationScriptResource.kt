package com.admoai.sdk.model.response

import com.admoai.sdk.serialization.VerificationParametersSerializer
import kotlinx.serialization.Serializable

/**
 * Open Measurement (OM) verification script resource for ad verification.
 *
 * This data class represents verification resources provided by third-party
 * verification vendors (e.g., IAS, DoubleVerify) for Open Measurement SDK integration.
 *
 * @property vendorKey The identifier for the verification vendor (e.g., "ias", "doubleverify")
 * @property scriptUrl The URL to the verification script that needs to be loaded
 * @property verificationParameters Additional parameters for verification setup. Kept as `String?`
 *   for source-compat; an object/array value from a future engine is preserved as compact JSON text.
 */
@Serializable
data class VerificationScriptResource(
    val vendorKey: String,
    val scriptUrl: String,
    @Serializable(with = VerificationParametersSerializer::class)
    val verificationParameters: String? = null
)

