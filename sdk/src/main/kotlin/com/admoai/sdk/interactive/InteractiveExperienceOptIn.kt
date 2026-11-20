package com.admoai.sdk.interactive

import com.admoai.sdk.model.request.InteractiveExperienceCapability

/** Turns the app's opt-in state into the declaration a decision request carries. */
@OptIn(ExperimentalInteractiveExperienceApi::class)
internal object InteractiveExperienceOptIn {
    /**
     * The declaration for this request, or null when nothing must be declared: no opt-in, or a
     * device without a secure form surface. A provider that fails declares no key.
     */
    fun requestExtension(
        provider: LeadFormValueProvider?,
        secureSurfaceAvailable: () -> Boolean,
        onProviderFailure: (Throwable) -> Unit
    ): InteractiveExperienceCapability? {
        if (provider == null) return null
        if (!secureSurfaceAvailable()) return null
        val keys = try {
            provider.availableKeys()
        } catch (t: Throwable) {
            onProviderFailure(t)
            emptySet()
        }
        return InteractiveExperienceCapability(
            supported = true,
            availableAttributeKeys = StandardAttributeKey.entries.filter { it in keys }.map { it.wireName }
        )
    }
}
