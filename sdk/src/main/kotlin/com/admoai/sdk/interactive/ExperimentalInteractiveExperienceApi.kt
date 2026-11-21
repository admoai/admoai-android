package com.admoai.sdk.interactive

/**
 * Marks the Lead Generation (interactive experience) integration surface, which is still in
 * development. Using it requires an explicit opt-in.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Lead Generation is in development: the integration (form controller, close and link " +
        "handling) is not complete. Do not opt in against a production engine yet."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ExperimentalInteractiveExperienceApi
