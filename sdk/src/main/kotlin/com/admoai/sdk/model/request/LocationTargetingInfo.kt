package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

// The OpenAPI implies 'location' would be an array of objects,
// but doesn't explicitly define a schema for these objects.
// Assuming a simple lat/lon structure based on common practice.
// If the OpenAPI has more details for "Location Targeting", we should use that.
// For now, let's assume it's not directly part of the request model unless specified under 'Targeting'.
// The iOS SDK's addLocationTargeting() might be a builder method rather than a direct model field.
// Let's hold off on this specific model until we define the main DecisionRequest and Targeting models,
// as it's not clear from the OpenAPI Decision Request schema if this is directly included.

// For now, an empty file or a placeholder if a specific structure isn't confirmed for the request body.
// Based on the iOS SDK `setLocationTargeting(), addLocationTargeting()` and the OpenAPI, `Targeting` is the main container.
// Let's create this placeholder for now, and refine it when we define Targeting.kt

@Serializable
data class LocationTargetingInfo(
    val latitude: Double,
    val longitude: Double
)
