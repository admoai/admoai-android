package com.admoai.sdk.utils

import com.admoai.sdk.model.response.Creative
import com.admoai.sdk.model.response.VerificationScriptResource

/**
 * Extracts Open Measurement verification script resources from a Creative.
 *
 * Returns the list of verification script resources if available, or null if none exist.
 * Publishers can use these resources to integrate with OM SDK-compatible verification vendors
 * (e.g., IAS, DoubleVerify).
 *
 * @return List of VerificationScriptResource objects, or null if no verification resources are present
 *
 * @example
 * ```kotlin
 * val verificationResources = creative.getVerificationResources()
 * verificationResources?.forEach { resource ->
 *     // Use resource.vendorKey, resource.scriptUrl, resource.verificationParameters
 *     // with your OM SDK integration
 * }
 * ```
 */
fun Creative.getVerificationResources(): List<VerificationScriptResource>? {
    return verificationScriptResources
}

/**
 * Checks if a Creative contains Open Measurement verification data.
 *
 * Returns true if the creative has one or more verification script resources,
 * indicating that OM SDK integration is available for this ad.
 *
 * @return true if verification resources are present, false otherwise
 *
 * @example
 * ```kotlin
 * if (creative.hasOMVerification()) {
 *     // Integrate with OM SDK using creative.getVerificationResources()
 * }
 * ```
 */
fun Creative.hasOMVerification(): Boolean {
    return !verificationScriptResources.isNullOrEmpty()
}

