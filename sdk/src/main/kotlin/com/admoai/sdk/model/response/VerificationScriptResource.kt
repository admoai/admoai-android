package com.admoai.sdk.model.response

import kotlinx.serialization.Serializable

/**
 * Open Measurement (OM) verification script resource for ad verification.
 *
 * This data class represents verification resources provided by third-party
 * verification vendors (e.g., IAS, DoubleVerify) for Open Measurement SDK integration.
 *
 * @property vendorKey The identifier for the verification vendor (e.g., "ias", "doubleverify")
 * @property scriptUrl The URL to the verification script that needs to be loaded
 * @property verificationParameters Additional parameters required for verification setup
 */
@Serializable
data class VerificationScriptResource(
    val vendorKey: String,
    val scriptUrl: String,
    val verificationParameters: String? = null
)

