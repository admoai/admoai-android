package com.admoai.sdk.interactive

/** The answer to [LeadFormValueProvider.lookUpValues]. Answer once; a second answer is ignored. */
fun interface LeadFormValues {
    /** Hands over the values the app holds for the keys asked. A key the app does not hold is absent. */
    fun complete(values: Map<StandardAttributeKey, String>)
}

/**
 * Tells the SDK which standard attributes the app holds for the currently signed-in user, and hands
 * their values to a form the viewer opened.
 *
 * [availableKeys] must answer from memory, without reading or fetching any value, and be safe to call
 * on any thread. It is called each time a decision request is built, on the thread that builds it:
 * a background thread for `requestAds`, the caller's thread for `getHttpRequestData`. Only key names
 * leave the device with the request.
 */
interface LeadFormValueProvider {
    /** The keys the app holds for the signed-in user right now. */
    fun availableKeys(): Set<StandardAttributeKey>

    /**
     * Called on the main thread only after the viewer opened the form and its version is fixed, only
     * for the keys that version requests, for the signed-in user. Answer once, from any thread, from
     * what the app already holds: never fetch, never block. A key you do not hold is simply absent.
     * Answers after the contract's lookup time, or after the user changed, are ignored.
     */
    fun lookUpValues(keys: Set<StandardAttributeKey>, completion: LeadFormValues)
}
