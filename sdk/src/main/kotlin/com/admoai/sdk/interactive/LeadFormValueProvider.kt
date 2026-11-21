package com.admoai.sdk.interactive

/**
 * Tells the SDK which standard attributes the app holds for the currently signed-in user.
 *
 * [availableKeys] must answer from memory, without reading or fetching any value, and be safe to call
 * on any thread. It is called each time a decision request is built, on the thread that builds it:
 * a background thread for `requestAds`, the caller's thread for `getHttpRequestData`. Only key names
 * leave the device with the request. Looking a value up is added in a later release.
 */
@ExperimentalInteractiveExperienceApi
interface LeadFormValueProvider {
    /** The keys the app holds for the signed-in user right now. */
    fun availableKeys(): Set<StandardAttributeKey>
}
